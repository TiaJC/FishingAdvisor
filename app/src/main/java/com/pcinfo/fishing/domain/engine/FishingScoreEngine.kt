package com.pcinfo.fishing.domain.engine

import com.pcinfo.fishing.domain.model.AdviceLevel
import com.pcinfo.fishing.domain.model.ActivityPattern
import com.pcinfo.fishing.domain.model.AlgorithmSpec
import com.pcinfo.fishing.domain.model.FactorSpec
import com.pcinfo.fishing.domain.model.FishingAssessment
import com.pcinfo.fishing.domain.model.FishSpecies
import com.pcinfo.fishing.domain.model.LevelSpec
import com.pcinfo.fishing.domain.model.OxygenTolerance
import com.pcinfo.fishing.domain.model.ScoreBand
import com.pcinfo.fishing.domain.model.EngineOptions
import com.pcinfo.fishing.domain.model.HourlyWeather
import com.pcinfo.fishing.domain.model.PressureDirection
import com.pcinfo.fishing.domain.model.PressureTrend
import com.pcinfo.fishing.domain.model.ScoreFactor
import com.pcinfo.fishing.domain.model.ScoreWeights
import com.pcinfo.fishing.domain.model.TimeWindow
import com.pcinfo.fishing.domain.model.WeatherSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 钓鱼指数评分引擎（纯函数，无 Android 依赖，便于单元测试）。
 *
 * 评分模型为多因子加权：每个因子先打 0-100 分，再按权重折算到总分（权重合计 100）。
 * 这样既能给出综合结论，又能向用户解释「为什么是这个分」。
 *
 * 默认因子与权重（可在设置页调整）：
 * - 气压绝对值      20  鱼类对气压最敏感，1010-1022 hPa 区间活性最好
 * - 气压趋势        25  比绝对值更关键，稳定或缓升最好，骤降几乎停口
 * - 风力            15  2-3 级最理想，既增氧又不影响抛竿观漂
 * - 风向            10  东/东南风多伴随溶氧改善，南/西南风闷热最差
 * - 水温            10  鱼是变温动物，水温决定代谢与摄食强度
 * - 降水            10  无雨或毛毛雨最好，大雨停口且有安全风险
 * - 时段            10  日出日落前后溶氧与捕食活性最高
 *
 * 权重由调用方传入 [ScoreWeights]，引擎内部按 `weight / total` 归一化，
 * 因此用户可以自由调节而不用担心总分溢出。
 */
object FishingScoreEngine {

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L
    private const val TREND_WINDOW_HOURS = 3

    /**
     * 对某个时刻的完整评估。
     *
     * @param snapshot 气象快照
     * @param species 目标鱼种，决定水温模型、活动节律与低气压耐受修正
     * @param nowEpochMs 评估基准时刻，默认取观测时刻；传入未来某小时即可做「那时值不值得钓」
     * @param weights 因子权重，默认取 [ScoreWeights.DEFAULT]
     * @param options 引擎开关，默认取 [EngineOptions.DEFAULT]
     */
    fun evaluate(
        snapshot: WeatherSnapshot,
        species: FishSpecies = FishSpecies.ANY,
        nowEpochMs: Long = snapshot.observedAtEpochMs,
        weights: ScoreWeights = ScoreWeights(),
        options: EngineOptions = EngineOptions()
    ): FishingAssessment {
        val hourly = snapshot.hourly.sortedBy { it.epochMs }
        val currentIndex = hourly.indexOfNearest(nowEpochMs)
        val current = hourly.getOrNull(currentIndex) ?: snapshot.toHourly(nowEpochMs)

        val pressureDelta = pressureDelta(hourly, currentIndex)
        val trend = buildPressureTrend(hourly, current, pressureDelta)

        // 鱼的行为由水温而非气温决定，这里由气温序列推算水温
        val waterTempC = WaterTempEstimator.estimate(hourly, current.temperatureC, nowEpochMs)
        val waterTempReliable = WaterTempEstimator.isReliable(hourly, nowEpochMs)
        // 开关关闭时退回用气温评分，并把因子名改回「气温」以免误导
        val effectiveTempC = if (options.useEstimatedWaterTemp) waterTempC else current.temperatureC

        val factors = buildFactors(
            snapshot = snapshot,
            point = current,
            pressureDelta = pressureDelta,
            epochMs = nowEpochMs,
            species = species,
            waterTempC = effectiveTempC,
            weights = weights,
            options = options
        )

        val total = factors.sumOf { it.contribution }.roundToInt().coerceIn(0, 100)
        val level = AdviceLevel.fromScore(total)
        val bestWindow = findBestWindow(snapshot, hourly, nowEpochMs, species, weights, options)

        val localSeconds = localSecondsOfDay(nowEpochMs, snapshot.utcOffsetSeconds)
        val sunriseSeconds = snapshot.sunriseEpochMs?.let { localSecondsOfDay(it, snapshot.utcOffsetSeconds) }
        val sunsetSeconds = snapshot.sunsetEpochMs?.let { localSecondsOfDay(it, snapshot.utcOffsetSeconds) }
        val speciesAdvice = FishAdviceEngine.build(
            AdviceContext(
                species = species,
                waterTempC = waterTempC,
                waterTempReliable = waterTempReliable,
                airTempC = current.temperatureC,
                pressureHpa = current.pressureHpa,
                pressureDelta3h = pressureDelta,
                windSpeedKmh = current.windSpeedKmh,
                windDirectionDeg = current.windDirectionDeg,
                precipitationMm = current.precipitationMm,
                cloudCoverPercent = current.cloudCoverPercent,
                localHour = localSeconds / 3600,
                dayPhase = dayPhaseOf(localSeconds, sunriseSeconds, sunsetSeconds)
            )
        )
        val tips = buildTips(
            snapshot = snapshot,
            point = current,
            pressureDelta = pressureDelta,
            totalScore = total,
            bestWindow = bestWindow,
            species = species,
            waterTempC = waterTempC
        )

        return FishingAssessment(
            totalScore = total,
            level = level,
            pressureTrend = trend,
            factors = factors,
            bestWindow = bestWindow,
            tips = tips,
            evaluatedAtEpochMs = nowEpochMs,
            species = species,
            waterTempC = waterTempC,
            speciesAdvice = speciesAdvice
        )
    }

    /**
     * 输出算法自描述：权重、分档规则与等级划分。
     * 界面据此渲染「算法说明」，与打分逻辑保持同源。
     */
    fun algorithmSpec(
        species: FishSpecies = FishSpecies.ANY,
        weights: ScoreWeights = ScoreWeights(),
        options: EngineOptions = EngineOptions()
    ): AlgorithmSpec {
        val w = weights.percentages()
        val tempLabel = if (options.useEstimatedWaterTemp) "水温" else "气温"
        return AlgorithmSpec(
            formula = "总分 = Σ(因子得分 × 归一化权重) ÷ 100，因子得分范围 0-100，权重合计恒为 100",
            factors = listOf(
                FactorSpec(
                    key = ScoreWeights.KEY_PRESSURE,
                    label = "气压",
                    weight = w[ScoreWeights.KEY_PRESSURE] ?: 0.0,
                    basis = "鱼类通过鱼鳔感知水压，气压直接决定水体溶氧与摄食意愿",
                    bands = listOf(
                        ScoreBand("1010 ~ 1022 hPa", 100),
                        ScoreBand("1005 ~ 1010 或 1022 ~ 1028 hPa", 78),
                        ScoreBand("1000 ~ 1005 或 1028 ~ 1033 hPa", 55),
                        ScoreBand("低于 1000 或高于 1033 hPa", 25)
                    )
                ),
                FactorSpec(
                    key = ScoreWeights.KEY_PRESSURE_TREND,
                    label = "气压趋势",
                    weight = w[ScoreWeights.KEY_PRESSURE_TREND] ?: 0.0,
                    basis = "比绝对值更关键：稳定或缓升时鱼口最稳，骤降会造成应激性停口",
                    bands = listOf(
                        ScoreBand("3 小时内变化 -1 ~ +1 hPa（平稳）", 100),
                        ScoreBand("缓升 +1 ~ +3 hPa", 82),
                        ScoreBand("急升 > +3 hPa", 60),
                        ScoreBand("缓降 -1 ~ -3 hPa", 40),
                        ScoreBand("骤降 ≤ -3 hPa", 15)
                    )
                ),
                FactorSpec(
                    key = ScoreWeights.KEY_WIND_SPEED,
                    label = "风力",
                    weight = w[ScoreWeights.KEY_WIND_SPEED] ?: 0.0,
                    basis = "2-3 级风能搅动水面增氧且不影响抛竿与观漂，过大过小都不利",
                    bands = listOf(
                        ScoreBand("2 ~ 3 级（6 ~ 19 km/h）", 100),
                        ScoreBand("1 级或 4 级", 78),
                        ScoreBand("0 级或 5 级", 55),
                        ScoreBand("6 级", 30),
                        ScoreBand("7 级及以上", 5)
                    )
                ),
                FactorSpec(
                    key = ScoreWeights.KEY_WIND_DIRECTION,
                    label = "风向",
                    weight = w[ScoreWeights.KEY_WIND_DIRECTION] ?: 0.0,
                    basis = if (options.useWindDirectionScoring) {
                        "东风/东南风带来水汽与高溶氧；南风、西南风闷热低压时鱼口最差"
                    } else {
                        "方位评分已关闭：风向项固定记 ${EngineOptions.NEUTRAL_WIND_DIRECTION_SCORE} 分，" +
                            "只保留风力的影响。方位口诀地域性强，可自行决定是否启用。"
                    },
                    bands = if (options.useWindDirectionScoring) {
                        listOf(
                            ScoreBand("东、东东北、东东南", 90),
                            ScoreBand("东北、东南", 85),
                            ScoreBand("北、北东北、西北、北西北、南东南", 70),
                            ScoreBand("西、南", 55),
                            ScoreBand("西南、南西南、西西南", 40)
                        )
                    } else {
                        listOf(ScoreBand("任意方位（已关闭方位评分）", EngineOptions.NEUTRAL_WIND_DIRECTION_SCORE))
                    }
                ),
                FactorSpec(
                    key = ScoreWeights.KEY_TEMPERATURE,
                    label = tempLabel,
                    weight = w[ScoreWeights.KEY_TEMPERATURE] ?: 0.0,
                    basis = "鱼是变温动物，水温决定代谢速率与摄食强度，是影响活性最直接的变量。" +
                        species.profile.name + "的最适水温为 " +
                        "%.0f~%.0f℃".format(
                            species.profile.tempOptimal.start,
                            species.profile.tempOptimal.endInclusive
                        ) +
                        if (options.useEstimatedWaterTemp) "" else "（当前已关闭水温估算，直接用气温评分）",
                    bands = listOf(
                        ScoreBand(
                            "%.0f ~ %.0f℃（最适区间）".format(
                                species.profile.tempOptimal.start,
                                species.profile.tempOptimal.endInclusive
                            ),
                            100
                        ),
                        ScoreBand("偏离最适区间 ≤ 3℃", 78),
                        ScoreBand("偏离 3 ~ 6℃", 55),
                        ScoreBand("偏离 > 6℃，但仍在可摄食区间内", 30),
                        ScoreBand(
                            "超出可摄食区间 %.0f ~ %.0f℃".format(
                                species.profile.tempActive.start,
                                species.profile.tempActive.endInclusive
                            ),
                            12
                        )
                    )
                ),
                FactorSpec(
                    key = ScoreWeights.KEY_PRECIPITATION,
                    label = "降水",
                    weight = w[ScoreWeights.KEY_PRECIPITATION] ?: 0.0,
                    basis = "无雨最稳，毛毛雨能增氧，中雨以上停口且存在安全风险",
                    bands = listOf(
                        ScoreBand("无降水且概率 ≤ 20%", 100),
                        ScoreBand("无降水但概率 > 20%", 88),
                        ScoreBand("小雨 < 0.5 mm", 85),
                        ScoreBand("0.5 ~ 2 mm", 62),
                        ScoreBand("2 ~ 5 mm", 35),
                        ScoreBand("大于 5 mm", 12)
                    )
                ),
                FactorSpec(
                    key = ScoreWeights.KEY_DAY_TIME,
                    label = "时段",
                    weight = w[ScoreWeights.KEY_DAY_TIME] ?: 0.0,
                    basis = "日出日落前后溶氧回升、弱光下鱼敢靠边；具体节律因鱼种而异，" +
                        species.profile.name + "以" + species.profile.activity.label + "（" +
                        dayTimeBandsOf(species).joinToString("、") { it.condition + " = " + it.score } + "）",
                    bands = dayTimeBandsOf(species) + listOf(
                        ScoreBand("缺少日出日落数据时退化为固定时段：5-7 点、17-19 点视为晨昏", 0)
                    )
                )
            ),
            levels = listOf(
                LevelSpec("80 ~ 100", AdviceLevel.EXCELLENT),
                LevelSpec("65 ~ 79", AdviceLevel.GOOD),
                LevelSpec("50 ~ 64", AdviceLevel.FAIR),
                LevelSpec("35 ~ 49", AdviceLevel.POOR),
                LevelSpec("0 ~ 34", AdviceLevel.BAD)
            ),
            notes = listOf(
                "推荐出钓窗口：自评估时刻起对未来 24 小时逐小时用同一套规则打分，" +
                    "取最高分并向两侧扩展到分数接近的时段。改变时间轴上的选中时刻，窗口也会随之重算。",
                "气压趋势取自「该时刻气压 − 3 小时前气压」，历史不足 3 小时时按线性折算，保证量纲一致。",
                "高海拔地区气压绝对值天然偏低，评分以实际气压为准，界面会给出海拔提示。",
                if (options.useEstimatedWaterTemp) {
                    "水温为估算值：取过去 24 小时平均气温 × 0.6 + 当前气温 × 0.4 − 1.0℃。" +
                        "水比热容大、存在蒸发降温，表层与深水还会分层，实测请用水温计校正。"
                } else {
                    "水温估算已在设置中关闭，当前直接用气温评分；" +
                        "气温与水温通常相差 1-4℃，夏季表层与深水差异更大，结论仅供参考。"
                },
                if (options.useOxygenToleranceAdjust) {
                    "鱼种修正：气压与气压趋势两项会按鱼种耐低氧能力缩放——" +
                        "极耐低氧鱼种（黑鱼、鲶鱼）在低气压下惩罚减半，" +
                        "极不耐低氧鱼种（鲢鳙）惩罚加重 40%。"
                } else {
                    "鱼种耐低氧修正已关闭：所有鱼种按同一套气压标准评分。"
                },
                "证据强度提示：气压项默认权重最高是沿用垂钓界的通行经验，但严格对照实验并不多；" +
                    "它更多是天气系统的代理指标（低气压常伴随高温、静风、阴天），" +
                    "而非溶氧的直接主因。可在设置页调低其权重或以本地实际鱼情校准。"
            )
        )
    }

    /** 按时段相位与鱼种活动节律给出分档说明 */
    private fun dayTimeBandsOf(species: FishSpecies): List<ScoreBand> {
        val s = dayTimeScoresOf(species)
        return listOf(
            ScoreBand("晨昏窗口（日出前 30 分钟 ~ 日出后 2.5 小时，日落前 2.5 小时 ~ 日落后 30 分钟）", s.first),
            ScoreBand("白天其它时段", s.second),
            ScoreBand("夜间", s.third)
        )
    }

    /** 晨昏 / 白天 / 夜间 三档得分 */
    private fun dayTimeScoresOf(species: FishSpecies): Triple<Int, Int, Int> =
        when (species.profile.activity) {
            ActivityPattern.CREPUSCULAR -> Triple(100, 65, 35)
            ActivityPattern.DIURNAL -> Triple(100, 85, 25)
            ActivityPattern.NOCTURNAL -> Triple(90, 45, 100)
            ActivityPattern.ALL_DAY -> Triple(100, 80, 60)
        }

    /** 气压绝对值评分 */
    fun scorePressure(hpa: Double): Int = when {
        hpa in 1010.0..1022.0 -> 100
        hpa in 1005.0..1010.0 || hpa in 1022.0..1028.0 -> 78
        hpa in 1000.0..1005.0 || hpa in 1028.0..1033.0 -> 55
        else -> 25
    }

    /** 气压趋势评分：单位 hPa / 3h */
    fun scorePressureTrend(delta3h: Double): Int = when {
        delta3h <= -3.0 -> 15
        delta3h < -1.0 -> 40
        delta3h <= 1.0 -> 100
        delta3h <= 3.0 -> 82
        else -> 60
    }

    /**
     * 水温评分（按鱼种的最适 / 可摄食区间）。
     *
     * 注意入参语义为**水温**而非气温。鱼类是变温动物，体温随水温变化，
     * 消化酶活性与代谢速率直接由水温决定，因此这是证据最强的一个因子。
     *
     * @param waterTempC 水温（℃），通常由 WaterTempEstimator 估算得到
     */
    fun scoreTemperature(waterTempC: Double, species: FishSpecies = FishSpecies.ANY): Int {
        val p = species.profile
        return when {
            waterTempC in p.tempOptimal -> 100
            waterTempC in p.tempActive -> {
                val distance = if (waterTempC < p.tempOptimal.start) {
                    p.tempOptimal.start - waterTempC
                } else {
                    waterTempC - p.tempOptimal.endInclusive
                }
                when {
                    distance <= 3.0 -> 78
                    distance <= 6.0 -> 55
                    else -> 30
                }
            }
            else -> 12
        }
    }

    /**
     * 按鱼种耐低氧能力缩放「气压类」因子得分。
     *
     * 低气压之所以影响鱼口，主流解释是水体溶氧下降与鱼鳔受压。
     * 但不同鱼种的窒息点差异极大：鲢鳙约 1.7-2.3 mg/L 即浮头停口，
     * 而乌鳢、鲶鱼具辅助呼吸器官，低氧下反而照常摄食。
     * 因此同一气压条件对不同鱼种的影响不应一刀切。
     */
    fun adjustForOxygenTolerance(score: Int, tolerance: OxygenTolerance): Int {
        val gap = 100 - score
        return when (tolerance) {
            OxygenTolerance.VERY_HIGH -> (score + gap * 0.5).roundToInt()
            OxygenTolerance.HIGH -> (score + gap * 0.3).roundToInt()
            OxygenTolerance.MEDIUM -> score
            OxygenTolerance.LOW -> (score - gap * 0.2).roundToInt()
            OxygenTolerance.VERY_LOW -> (score - gap * 0.4).roundToInt()
        }.coerceIn(0, 100)
    }

    /** 降水评分 */
    fun scorePrecipitation(mm: Double, probabilityPercent: Int): Int = when {
        mm <= 0.0 -> if (probabilityPercent <= 20) 100 else 88
        mm < 0.5 -> 85
        mm < 2.0 -> 62
        mm < 5.0 -> 35
        else -> 12
    }

    /**
     * 时段评分：日出前后与日落前后是窗口期，具体节律按鱼种活动模式调整。
     *
     * 生物学依据：多数淡水鱼为晨昏性觅食者——弱光下（约 10-100 lux）
     * 视觉、嗅觉与侧线并用，捕食效率最高，同时岸边天敌压力小；
     * 而夜间光合作用停止、溶氧降至全天最低，正午强光又促使鱼退深水。
     * 但鲶鱼、黄颡鱼等底栖夜行鱼种恰好相反，需按鱼种区分。
     *
     * @param localSeconds 当地时间的当日秒数
     */
    fun scoreDayTime(
        localSeconds: Int,
        sunriseSeconds: Int?,
        sunsetSeconds: Int?,
        species: FishSpecies = FishSpecies.ANY
    ): Int {
        val bands = dayTimeScoresOf(species)
        return when (dayPhaseOf(localSeconds, sunriseSeconds, sunsetSeconds)) {
            DayPhase.DAWN_DUSK -> bands.first
            DayPhase.DAY -> bands.second
            DayPhase.NIGHT -> bands.third
            DayPhase.UNKNOWN -> ((bands.first + bands.second + bands.third) / 3.0).roundToInt()
        }
    }

    /**
     * 判定昼夜相位。缺少日出日落数据时退化为固定时段估算。
     */
    fun dayPhaseOf(localSeconds: Int, sunriseSeconds: Int?, sunsetSeconds: Int?): DayPhase {
        if (sunriseSeconds == null || sunsetSeconds == null) {
            return when (val hour = localSeconds / 3600) {
                in 5..7 -> DayPhase.DAWN_DUSK
                in 17..19 -> DayPhase.DAWN_DUSK
                in 8..16 -> DayPhase.DAY
                else -> DayPhase.NIGHT
            }
        }
        val morningStart = sunriseSeconds - 1_800
        val morningEnd = sunriseSeconds + 9_000
        val eveningStart = sunsetSeconds - 9_000
        val eveningEnd = sunsetSeconds + 1_800
        return when {
            localSeconds in morningStart..morningEnd -> DayPhase.DAWN_DUSK
            localSeconds in eveningStart..eveningEnd -> DayPhase.DAWN_DUSK
            localSeconds > morningEnd && localSeconds < eveningStart -> DayPhase.DAY
            else -> DayPhase.NIGHT
        }
    }

    /** 把 epochMs 换算为当地当日秒数 */
    fun localSecondsOfDay(epochMs: Long, utcOffsetSeconds: Int): Int {
        val raw = ((epochMs / 1000) + utcOffsetSeconds) % 86_400
        return ((raw + 86_400) % 86_400).toInt()
    }

    private fun buildFactors(
        snapshot: WeatherSnapshot,
        point: HourlyWeather,
        pressureDelta: Double,
        epochMs: Long,
        species: FishSpecies,
        waterTempC: Double,
        weights: ScoreWeights,
        options: EngineOptions
    ): List<ScoreFactor> {
        val w = weights.percentages()
        val tolerance = species.profile.oxygenTolerance
        // 气压类因子按鱼种耐低氧能力缩放：耐低氧鱼种惩罚减半，不耐低氧鱼种惩罚加重
        val pressureScore = if (options.useOxygenToleranceAdjust) {
            adjustForOxygenTolerance(scorePressure(point.pressureHpa), tolerance)
        } else {
            scorePressure(point.pressureHpa)
        }
        val trendScore = if (options.useOxygenToleranceAdjust) {
            adjustForOxygenTolerance(scorePressureTrend(pressureDelta), tolerance)
        } else {
            scorePressureTrend(pressureDelta)
        }
        val windSpeedScore = scoreWindSpeed(point.windSpeedKmh)
        val windDirScore = if (options.useWindDirectionScoring) {
            scoreWindDirection(point.windDirectionDeg)
        } else {
            EngineOptions.NEUTRAL_WIND_DIRECTION_SCORE
        }
        val temperatureScore = scoreTemperature(waterTempC, species)
        val precipitationScore = scorePrecipitation(
            point.precipitationMm,
            point.precipitationProbabilityPercent
        )
        val dayTimeScore = scoreDayTime(
            localSeconds = localSecondsOfDay(epochMs, snapshot.utcOffsetSeconds),
            sunriseSeconds = snapshot.sunriseEpochMs?.let { localSecondsOfDay(it, snapshot.utcOffsetSeconds) },
            sunsetSeconds = snapshot.sunsetEpochMs?.let { localSecondsOfDay(it, snapshot.utcOffsetSeconds) },
            species = species
        )
        val tempLabel = if (options.useEstimatedWaterTemp) "水温" else "气温"
        val oxygenFlag = options.useOxygenToleranceAdjust

        return listOf(
            ScoreFactor(
                key = ScoreWeights.KEY_PRESSURE,
                label = "气压",
                weight = w[ScoreWeights.KEY_PRESSURE] ?: 0.0,
                rawScore = pressureScore,
                displayValue = "%.1f hPa".format(point.pressureHpa),
                comment = pressureComment(point.pressureHpa, pressureScore) +
                    if (oxygenFlag) oxygenNote(tolerance, pressureScore) else ""
            ),
            ScoreFactor(
                key = ScoreWeights.KEY_PRESSURE_TREND,
                label = "气压趋势",
                weight = w[ScoreWeights.KEY_PRESSURE_TREND] ?: 0.0,
                rawScore = trendScore,
                displayValue = "%+.1f hPa/3h".format(pressureDelta),
                comment = trendComment(pressureDelta) +
                    if (oxygenFlag) oxygenNote(tolerance, trendScore) else ""
            ),
            ScoreFactor(
                key = ScoreWeights.KEY_WIND_SPEED,
                label = "风力",
                weight = w[ScoreWeights.KEY_WIND_SPEED] ?: 0.0,
                rawScore = windSpeedScore,
                displayValue = "%.1f km/h · %s".format(point.windSpeedKmh, beaufortLabel(point.windSpeedKmh)),
                comment = windSpeedComment(windSpeedScore)
            ),
            ScoreFactor(
                key = ScoreWeights.KEY_WIND_DIRECTION,
                label = "风向",
                weight = w[ScoreWeights.KEY_WIND_DIRECTION] ?: 0.0,
                rawScore = windDirScore,
                displayValue = "%d° · %s".format(point.windDirectionDeg, windDirectionLabel(point.windDirectionDeg)),
                comment = if (options.useWindDirectionScoring) {
                    windDirectionComment(point.windDirectionDeg, windDirScore)
                } else {
                    "方位评分已关闭，风向项记中性分 ${EngineOptions.NEUTRAL_WIND_DIRECTION_SCORE}"
                }
            ),
            ScoreFactor(
                key = ScoreWeights.KEY_TEMPERATURE,
                label = tempLabel,
                weight = w[ScoreWeights.KEY_TEMPERATURE] ?: 0.0,
                rawScore = temperatureScore,
                displayValue = if (options.useEstimatedWaterTemp) {
                    "%.1f℃（估算，气温 %.1f℃）".format(waterTempC, point.temperatureC)
                } else {
                    "%.1f℃（直接采用气温）".format(point.temperatureC)
                },
                comment = temperatureComment(waterTempC, temperatureScore, species)
            ),
            ScoreFactor(
                key = ScoreWeights.KEY_PRECIPITATION,
                label = "降水",
                weight = w[ScoreWeights.KEY_PRECIPITATION] ?: 0.0,
                rawScore = precipitationScore,
                displayValue = if (point.precipitationMm <= 0.0) {
                    "无降水（概率 %d%%）".format(point.precipitationProbabilityPercent)
                } else {
                    "%.1f mm（概率 %d%%）".format(point.precipitationMm, point.precipitationProbabilityPercent)
                },
                comment = precipitationComment(point.precipitationMm, precipitationScore)
            ),
            ScoreFactor(
                key = ScoreWeights.KEY_DAY_TIME,
                label = "时段",
                weight = w[ScoreWeights.KEY_DAY_TIME] ?: 0.0,
                rawScore = dayTimeScore,
                displayValue = dayTimeDisplay(dayTimeScore),
                comment = dayTimeComment(dayTimeScore)
            )
        )
    }

    private fun buildPressureTrend(
        hourly: List<HourlyWeather>,
        current: HourlyWeather,
        delta: Double
    ): PressureTrend {
        val window = hourly.filter { it.epochMs <= current.epochMs }.takeLast(6)
        val series = if (window.isEmpty()) listOf(current) else window
        return PressureTrend(
            delta3hHpa = delta,
            direction = when {
                delta > 3.0 -> PressureDirection.RISING_FAST
                delta > 1.0 -> PressureDirection.RISING
                delta >= -1.0 -> PressureDirection.STEADY
                delta >= -3.0 -> PressureDirection.FALLING
                else -> PressureDirection.FALLING_FAST
            },
            currentHpa = current.pressureHpa,
            minHpa = series.minOf { it.pressureHpa },
            maxHpa = series.maxOf { it.pressureHpa }
        )
    }

    /**
     * 气压趋势：取当前点往前 3 小时的压力差；历史不足时退化为最早可用点。
     */
    private fun pressureDelta(hourly: List<HourlyWeather>, currentIndex: Int): Double {
        if (hourly.isEmpty() || currentIndex < 0) return 0.0
        val current = hourly[currentIndex]
        val target = current.epochMs - TREND_WINDOW_HOURS * HOUR_MS
        var bestIndex = -1
        var bestDiff = Long.MAX_VALUE
        for (i in 0 until currentIndex) {
            val diff = abs(hourly[i].epochMs - target)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIndex = i
            }
        }
        if (bestIndex < 0) return 0.0
        val hours = (current.epochMs - hourly[bestIndex].epochMs) / HOUR_MS.toDouble()
        if (hours <= 0.0) return 0.0
        // 归一化为「每 3 小时变化量」，避免窗口不足 3 小时时趋势被低估
        return (current.pressureHpa - hourly[bestIndex].pressureHpa) * (TREND_WINDOW_HOURS / hours)
    }

    /**
     * 在接下来 24 小时内寻找最佳出钓窗口：
     * 逐小时评分后取最高分时段，并向两侧扩展到分数接近的相邻小时。
     */
    private fun findBestWindow(
        snapshot: WeatherSnapshot,
        hourly: List<HourlyWeather>,
        nowEpochMs: Long,
        species: FishSpecies,
        weights: ScoreWeights,
        options: EngineOptions
    ): TimeWindow? {
        val candidates = hourly.filter { it.epochMs in (nowEpochMs + 1)..(nowEpochMs + DAY_MS) }
        if (candidates.isEmpty()) return null

        val scored = candidates.mapIndexedNotNull { index, point ->
            val absoluteIndex = hourly.indexOf(point)
            val delta = pressureDelta(hourly, absoluteIndex)
            val score = scoreAtPoint(snapshot, hourly, point, delta, species, weights, options)
            index to score
        }
        if (scored.isEmpty()) return null

        val best = scored.maxBy { it.second }
        val threshold = best.second - 6

        var startIndex = best.first
        var endIndex = best.first
        while (startIndex - 1 >= 0 && scored.any { it.first == startIndex - 1 && it.second >= threshold }) {
            startIndex--
        }
        while (scored.any { it.first == endIndex + 1 && it.second >= threshold } && (endIndex + 1 - startIndex) < 5) {
            endIndex++
        }

        val start = candidates[startIndex].epochMs
        val end = candidates[endIndex].epochMs + HOUR_MS
        return TimeWindow(
            startEpochMs = start,
            endEpochMs = end,
            score = best.second,
            reason = windowReason(snapshot, candidates[best.first], best.second, species)
        )
    }

    /** 某个小时点的综合评分（不含最佳窗口递归） */
    private fun scoreAtPoint(
        snapshot: WeatherSnapshot,
        hourly: List<HourlyWeather>,
        point: HourlyWeather,
        pressureDelta: Double,
        species: FishSpecies,
        weights: ScoreWeights,
        options: EngineOptions
    ): Int {
        val w = weights.percentages()
        val tolerance = species.profile.oxygenTolerance
        val pressureRaw = if (options.useOxygenToleranceAdjust) {
            adjustForOxygenTolerance(scorePressure(point.pressureHpa), tolerance)
        } else {
            scorePressure(point.pressureHpa)
        }
        val trendRaw = if (options.useOxygenToleranceAdjust) {
            adjustForOxygenTolerance(scorePressureTrend(pressureDelta), tolerance)
        } else {
            scorePressureTrend(pressureDelta)
        }
        val pressure = pressureRaw * (w[ScoreWeights.KEY_PRESSURE] ?: 0.0)
        val trend = trendRaw * (w[ScoreWeights.KEY_PRESSURE_TREND] ?: 0.0)
        val windSpeed = scoreWindSpeed(point.windSpeedKmh) * (w[ScoreWeights.KEY_WIND_SPEED] ?: 0.0)
        val windDir = (if (options.useWindDirectionScoring) {
            scoreWindDirection(point.windDirectionDeg)
        } else {
            EngineOptions.NEUTRAL_WIND_DIRECTION_SCORE
        }) * (w[ScoreWeights.KEY_WIND_DIRECTION] ?: 0.0)
        val waterTemp = if (options.useEstimatedWaterTemp) {
            WaterTempEstimator.estimate(hourly, point.temperatureC, point.epochMs)
        } else {
            point.temperatureC
        }
        val temperature = scoreTemperature(waterTemp, species) * (w[ScoreWeights.KEY_TEMPERATURE] ?: 0.0)
        val precipitation = scorePrecipitation(
            point.precipitationMm,
            point.precipitationProbabilityPercent
        ) * (w[ScoreWeights.KEY_PRECIPITATION] ?: 0.0)
        val dayTime = scoreDayTime(
            localSeconds = localSecondsOfDay(point.epochMs, snapshot.utcOffsetSeconds),
            sunriseSeconds = snapshot.sunriseEpochMs?.let { localSecondsOfDay(it, snapshot.utcOffsetSeconds) },
            sunsetSeconds = snapshot.sunsetEpochMs?.let { localSecondsOfDay(it, snapshot.utcOffsetSeconds) },
            species = species
        ) * (w[ScoreWeights.KEY_DAY_TIME] ?: 0.0)
        return ((pressure + trend + windSpeed + windDir + temperature + precipitation + dayTime) / 100.0)
            .roundToInt().coerceIn(0, 100)
    }

    private fun buildTips(
        snapshot: WeatherSnapshot,
        point: HourlyWeather,
        pressureDelta: Double,
        totalScore: Int,
        bestWindow: TimeWindow?,
        species: FishSpecies,
        waterTempC: Double
    ): List<String> {
        val tips = mutableListOf<String>()
        val tolerance = species.profile.oxygenTolerance

        when {
            pressureDelta <= -3.0 -> tips += when (tolerance) {
                OxygenTolerance.VERY_HIGH, OxygenTolerance.HIGH ->
                    "气压快速下降，但${species.profile.name}耐低氧，仍可出钓，建议主钓活水与浅水增氧区。"
                OxygenTolerance.VERY_LOW ->
                    "气压快速下降，${species.profile.name}最先浮头停口，建议改期或换目标鱼。"
                else ->
                    "气压快速下降，鱼会短暂停口，建议等气压回稳后再出钓。"
            }
            pressureDelta < -1.0 -> tips += "气压缓慢走低，鱼口会逐渐变轻，宜选早晚窗口、钓近岸浅水或入水口。"
            pressureDelta > 3.0 -> tips += "气压快速回升中，鱼口正在恢复，稍等数小时往往更好。"
            pressureDelta > 1.0 -> tips += "气压稳步上升，水中溶氧改善，是出钓的好时机。"
            else -> tips += "气压平稳，鱼情稳定，可按常规钓法作钓。"
        }

        val level = beaufortLevel(point.windSpeedKmh)
        when {
            level >= 6 -> tips += "风力已达 $level 级，抛竿与观漂困难，注意人身与船只安全。"
            level == 0 || level == 1 -> tips += "几乎无风，水体溶氧偏低，建议选入水口、下风口或活水处。"
            level in 2..3 -> tips += "风力 $level 级，风浪适中、溶氧充足，是理想作钓条件。"
            else -> tips += "风力 $level 级，建议加重铅坠并改用短竿，保证抛投精度。"
        }

        val dirScore = scoreWindDirection(point.windDirectionDeg)
        if (dirScore <= 55) {
            tips += "当前为${windDirectionLabel(point.windDirectionDeg)}，闷热低压、鱼口偏弱，建议钓深水、背阴处并改用清淡饵。"
        } else if (dirScore >= 88) {
            tips += "当前为${windDirectionLabel(point.windDirectionDeg)}，溶氧与食物条件好，可优先选下风口作钓。"
        }

        val optimal = species.profile.tempOptimal
        when {
            waterTempC > optimal.endInclusive ->
                tips += "估算水温 %.1f℃ 高于${species.profile.name}最适区间（%.0f-%.0f℃），鱼下潜避暑，宜钓深水、背阴并改用清淡饵。"
                    .format(waterTempC, optimal.start, optimal.endInclusive)
            waterTempC < optimal.start ->
                tips += "估算水温 %.1f℃ 低于${species.profile.name}最适区间（%.0f-%.0f℃），鱼活性不足，宜用腥活饵、钓向阳深水并放慢节奏。"
                    .format(waterTempC, optimal.start, optimal.endInclusive)
            else ->
                tips += "估算水温 %.1f℃ 处于${species.profile.name}最适区间，摄食活跃，可正常选择钓位与饵型。"
                    .format(waterTempC)
        }

        when (species.profile.activity) {
            ActivityPattern.NOCTURNAL ->
                tips += "${species.profile.name}以夜间觅食为主，白天口稀，建议傍晚打窝、前半夜作钓。"
            ActivityPattern.DIURNAL ->
                tips += "${species.profile.name}白天活性最高，正午可转深水或障荫处，不必强求早晚。"
            ActivityPattern.CREPUSCULAR ->
                tips += "${species.profile.name}晨昏活性最高，建议日出前或日落前到位。"
            ActivityPattern.ALL_DAY -> Unit
        }

        when {
            point.precipitationMm >= 5.0 -> tips += "有明显降水，注意防滑防雷；雨停后溶氧回升往往是好窗口。"
            point.precipitationMm in 0.1..2.0 -> tips += "小雨天气溶氧改善，鱼口常变好，注意保暖与防滑。"
        }

        if (bestWindow != null && bestWindow.score >= totalScore + 5) {
            tips += "当前时段评分 $totalScore 分，${bestWindowLabel(bestWindow)}更有利（${bestWindow.score} 分）。"
        }

        snapshot.elevationMeters?.let {
            if (it >= 1000.0) {
                tips += "海拔约 ${it.roundToInt()} 米，气压读数低于海平面标准值属正常现象，评分已按实际气压计算。"
            }
        }

        return tips
    }

    private fun bestWindowLabel(window: TimeWindow): String =
        "${formatHour(window.startEpochMs)}-${formatHour(window.endEpochMs)}"

    private fun formatHour(epochMs: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = epochMs
        return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }

    private fun windowReason(
        snapshot: WeatherSnapshot,
        point: HourlyWeather,
        score: Int,
        species: FishSpecies
    ): String {
        val dayPart = when (dayPhaseOf(
            localSecondsOfDay(point.epochMs, snapshot.utcOffsetSeconds),
            snapshot.sunriseEpochMs?.let { localSecondsOfDay(it, snapshot.utcOffsetSeconds) },
            snapshot.sunsetEpochMs?.let { localSecondsOfDay(it, snapshot.utcOffsetSeconds) }
        )) {
            DayPhase.DAWN_DUSK -> "处于晨昏觅食窗口"
            DayPhase.DAY -> "白天时段"
            DayPhase.NIGHT -> "夜间时段（${species.profile.activity.label}鱼种）"
            DayPhase.UNKNOWN -> "时段未知"
        }
        return "$dayPart，气压 %.0f hPa、%s，综合评分 %d 分".format(
            point.pressureHpa,
            beaufortLabel(point.windSpeedKmh),
            score
        )
    }

    private fun pressureComment(hpa: Double, score: Int): String = when (score) {
        100 -> "处于最适宜区间，鱼活性高"
        78 -> "接近适宜区间，鱼口正常"
        55 -> "偏离适宜区间，鱼口转轻"
        else -> "气压异常，鱼多上浮或停口"
    }

    private fun trendComment(delta: Double): String = when {
        delta <= -3.0 -> "骤降，鱼类应激、几乎停口"
        delta < -1.0 -> "缓降，鱼口逐渐变差"
        delta <= 1.0 -> "平稳，鱼情最稳定"
        delta <= 3.0 -> "缓升，鱼口转好"
        else -> "急升，鱼需时间适应"
    }

    private fun windSpeedComment(score: Int): String = when (score) {
        100 -> "风浪适中，溶氧与观漂兼顾"
        78 -> "风力尚可，略有影响"
        55 -> "偏大或过小，影响作钓"
        30 -> "风大，抛竿困难"
        else -> "风浪过大，不宜出钓"
    }

    private fun windDirectionComment(degrees: Int, score: Int): String = when {
        score >= 88 -> "${windDirectionLabel(degrees)}带来高溶氧，鱼口好"
        score >= 70 -> "${windDirectionLabel(degrees)}条件中等偏上"
        score >= 55 -> "${windDirectionLabel(degrees)}偏闷，鱼口一般"
        else -> "${windDirectionLabel(degrees)}闷热低压，鱼口最差"
    }

    private fun temperatureComment(waterTempC: Double, score: Int, species: FishSpecies): String {
        val optimal = species.profile.tempOptimal
        val base = when (score) {
            100 -> "处于该鱼种最适水温区间，摄食活跃"
            78 -> "略偏离最适区间，鱼口正常"
            55 -> "偏离较远，活性下降"
            30 -> "接近可摄食边界，鱼口很轻"
            else -> "超出可摄食区间，基本停口"
        }
        return "$base（最适 %.0f-%.0f℃，当前 %.1f℃）"
            .format(optimal.start, optimal.endInclusive, waterTempC)
    }

    /** 当鱼种耐低氧能力对气压类因子产生修正时，补充说明 */
    private fun oxygenNote(tolerance: OxygenTolerance, adjustedScore: Int): String =
        when (tolerance) {
            OxygenTolerance.VERY_HIGH, OxygenTolerance.HIGH ->
                if (adjustedScore < 100) "；该鱼种耐低氧，已按耐受能力上调" else ""
            OxygenTolerance.LOW, OxygenTolerance.VERY_LOW ->
                if (adjustedScore < 100) "；该鱼种不耐低氧，已按耐受能力下调" else ""
            OxygenTolerance.MEDIUM -> ""
        }

    private fun precipitationComment(mm: Double, score: Int): String = when {
        score >= 100 -> "无降水，条件理想"
        score >= 85 -> "毛毛雨，反而有助溶氧"
        score >= 62 -> "小雨，需注意防滑"
        score >= 35 -> "中雨，明显影响作钓"
        else -> "大雨，建议改期"
    }

    private fun dayTimeDisplay(score: Int): String = when {
        score >= 90 -> "黄金窗口"
        score >= 60 -> "常规时段"
        else -> "低谷时段"
    }

    private fun dayTimeComment(score: Int): String = when {
        score >= 90 -> "符合该鱼种的活动节律，捕食活性最高"
        score >= 60 -> "一般时段，鱼口正常"
        else -> "偏离该鱼种的活动节律，鱼口偏弱"
    }

    private fun List<HourlyWeather>.indexOfNearest(epochMs: Long): Int {
        if (isEmpty()) return -1
        var bestIndex = 0
        var bestDiff = Long.MAX_VALUE
        forEachIndexed { index, point ->
            val diff = abs(point.epochMs - epochMs)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun WeatherSnapshot.toHourly(epochMs: Long): HourlyWeather = HourlyWeather(
        epochMs = epochMs,
        temperatureC = temperatureC,
        pressureHpa = pressureHpa,
        windSpeedKmh = windSpeedKmh,
        windDirectionDeg = windDirectionDeg,
        precipitationMm = precipitationMm,
        precipitationProbabilityPercent = 0,
        cloudCoverPercent = cloudCoverPercent
    )
}
