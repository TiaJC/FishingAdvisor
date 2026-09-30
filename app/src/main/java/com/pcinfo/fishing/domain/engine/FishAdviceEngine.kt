package com.pcinfo.fishing.domain.engine

import com.pcinfo.fishing.domain.model.FishSpecies
import com.pcinfo.fishing.domain.model.OxygenTolerance
import com.pcinfo.fishing.domain.model.SpotAdvice
import com.pcinfo.fishing.domain.model.SpotTag
import com.pcinfo.fishing.domain.model.SpeciesAdvice
import com.pcinfo.fishing.domain.model.WaterLayer

/** 昼夜相位 */
enum class DayPhase(val label: String) {
    DAWN_DUSK("晨昏"),
    DAY("白天"),
    NIGHT("夜间"),
    UNKNOWN("未知")
}

/** 生成建议所需的上下文 */
data class AdviceContext(
    val species: FishSpecies,
    /** 估算水温（℃） */
    val waterTempC: Double,
    val waterTempReliable: Boolean,
    val airTempC: Double,
    val pressureHpa: Double,
    /** 近 3 小时气压变化（hPa） */
    val pressureDelta3h: Double,
    val windSpeedKmh: Double,
    val windDirectionDeg: Int,
    val precipitationMm: Double,
    val cloudCoverPercent: Int,
    /** 当地小时 0-23 */
    val localHour: Int,
    val dayPhase: DayPhase
)

/**
 * 鱼种化作钓建议引擎（纯函数）。
 *
 * 与评分引擎的分工：
 * - FishingScoreEngine 回答「今天值不值得去、什么时候去」（0-100 分）。
 * - FishAdviceEngine 回答「去了之后钓多深、站哪里、用什么饵」（空间与技法）。
 *
 * 两者的推导都遵循同一条主线——**溶氧与水温**决定鱼的分布：
 * 1. 水温决定代谢速率与舒适区，鱼会主动游向接近其最适水温的水层；
 * 2. 溶氧决定摄食意愿，风浪、活水、浅水、水草光合作用是增氧因素，
 *    高温、静风、气压骤降是耗氧/降溶氧因素；
 * 3. 光照与掠食压力决定时间节律，多数淡水鱼在晨昏最敢靠边。
 *
 * 因此下面的规则不是孤立的 if-else，而是这三条主线的组合应用。
 */
object FishAdviceEngine {

    private const val TOP_SPOT_COUNT = 3

    fun build(ctx: AdviceContext): SpeciesAdvice {
        val profile = ctx.species.profile
        val depth = resolveDepth(ctx)
        val spots = rankSpots(ctx)
        val bait = resolveBait(ctx)
        val cautions = buildCautions(ctx)

        return SpeciesAdvice(
            species = ctx.species,
            waterTempC = ctx.waterTempC,
            waterTempReliable = ctx.waterTempReliable,
            depthMinM = depth.first,
            depthMaxM = depth.second,
            depthReason = depth.third,
            fishOffBottom = depth.fourth,
            spots = spots,
            baits = bait.first,
            baitReason = bait.second,
            rig = profile.rig,
            method = resolveMethod(ctx, depth.fourth),
            cautions = cautions
        )
    }

    // ---------------------------------------------------------------- 水深

    /**
     * 水深推算：以鱼种常规水深为基准，按水温偏离、时段、溶氧状况与风力做平移。
     *
     * @return (下限, 上限, 理由, 是否建议钓离底)
     */
    private fun resolveDepth(ctx: AdviceContext): Quadruple {
        val p = ctx.species.profile
        val reasons = mutableListOf<String>()
        var shift = 0.0
        var offBottom = false

        // 1) 水温偏离最适区间：鱼会向更接近最适水温的水层移动。
        //    过冷与过热都指向深水，但成因不同——前者是深水保温，后者是深水避暑。
        when {
            ctx.waterTempC < p.tempOptimal.start -> {
                shift += 0.6
                reasons += "水温 %.0f℃ 低于最适区间（%.0f-%.0f℃），鱼退向深水保温"
                    .format(ctx.waterTempC, p.tempOptimal.start, p.tempOptimal.endInclusive)
            }
            ctx.waterTempC > p.tempOptimal.endInclusive -> {
                shift += 0.8
                reasons += "水温 %.0f℃ 高于最适区间（%.0f-%.0f℃），鱼下潜至凉爽水层"
                    .format(ctx.waterTempC, p.tempOptimal.start, p.tempOptimal.endInclusive)
            }
            else -> reasons += "水温处于该鱼种最适区间，常规水深即可"
        }

        // 2) 时段：正午强光与高温把鱼压向深水，晨昏则靠边觅食。
        when (ctx.dayPhase) {
            DayPhase.DAWN_DUSK -> {
                shift -= 0.4
                reasons += "晨昏光线弱、鱼敢靠边，宜钓浅"
            }
            DayPhase.NIGHT -> {
                shift -= 0.2
                reasons += "夜间岸边安静、饵鱼靠边，可略钓浅"
            }
            DayPhase.DAY -> {
                if (ctx.localHour in 11..15) {
                    shift += 0.5
                    reasons += "正午光照强，鱼退向深水或有遮挡处"
                }
            }
            DayPhase.UNKNOWN -> Unit
        }

        // 3) 溶氧：这是「气压骤降要钓浮」这条经验的真正机理。
        //    气压下降本身对溶氧饱和度的直接贡献只有 3% 量级，真正致命的是
        //    它常伴随的高温、静风与阴天——表层以下先缺氧，鱼被迫离底上浮。
        val muggy = ctx.pressureDelta3h <= -3.0 && ctx.waterTempC >= 20.0
        if (muggy && p.layer != WaterLayer.UPPER) {
            shift -= 0.8
            offBottom = true
            reasons += "气压骤降且水温偏高，底层缺氧、鱼离底上浮，建议改钓浮或钓离底"
        }
        if (ctx.windSpeedKmh < 3.0 && ctx.airTempC >= 28.0) {
            shift -= 0.3
            reasons += "闷热无风，浅水与空气接触面大、溶氧相对更好"
        }

        // 4) 大风：浅水浑浊且观漂困难，转向背风深水。
        if (beaufortLevel(ctx.windSpeedKmh) >= 5) {
            shift += 0.3
            reasons += "风力偏大，浅水浑浊难观漂，宜找背风深水"
        }

        // 上层鱼本身就在水皮活动，深度平移幅度相应收敛，避免算出离谱的数值。
        val damping = when (p.layer) {
            WaterLayer.UPPER -> 0.4
            WaterLayer.MIDDLE -> 0.7
            WaterLayer.LOWER -> 0.85
            WaterLayer.BOTTOM -> 1.0
        }
        shift *= damping

        val min = (p.baseDepthM.start + shift).coerceIn(0.3, 8.0)
        val max = (p.baseDepthM.endInclusive + shift).coerceIn(min + 0.5, 12.0)
        return Quadruple(min, max, reasons.joinToString("；"), offBottom)
    }

    /** 简单的四元组承载：下限 / 上限 / 理由 / 是否钓离底 */
    private data class Quadruple(
        val first: Double,
        val second: Double,
        val third: String,
        val fourth: Boolean
    )

    // ---------------------------------------------------------------- 钓位

    /**
     * 钓位排序：先由环境条件给出每个结构类型的「基础分」，
     * 再叠加鱼种偏好权重，取前 3。
     *
     * 这样做的好处是解释链路清晰——推荐「下风口」不是因为口诀这么说，
     * 而是因为今天有风、风浪搅动增氧并把饵鱼推向一侧。
     */
    private fun rankSpots(ctx: AdviceContext): List<SpotAdvice> {
        val p = ctx.species.profile
        val windy = ctx.windSpeedKmh >= 6.0
        val strongWind = ctx.windSpeedKmh >= 20.0
        val calm = ctx.windSpeedKmh < 3.0
        val hot = ctx.waterTempC > p.tempOptimal.endInclusive
        val cold = ctx.waterTempC < p.tempOptimal.start
        val comfortable = !hot && !cold
        val lowPressure = ctx.pressureDelta3h <= -1.0
        val heavyRain = ctx.precipitationMm >= 5.0

        val scored = SpotTag.entries.map { tag ->
            val base = when (tag) {
                SpotTag.WINDWARD -> when {
                    strongWind -> 88
                    windy -> 80
                    else -> 32
                } to if (windy) {
                    "风把浮游生物与饵鱼推向下风岸，同时风浪增氧，掠食鱼随后而至"
                } else {
                    "几乎无风，下风口优势不明显"
                }

                SpotTag.INLET -> when {
                    heavyRain -> 52 to "大雨后进出水口浑浊、水位动荡，短时不利"
                    lowPressure -> 90 to "活水持续补氧，低气压天是少数仍高溶氧的位置，优先选这里"
                    else -> 72 to "活水带来氧气与食物，鱼常年在此聚集"
                }

                SpotTag.DROP_OFF -> when {
                    !comfortable -> 86 to "水温偏离舒适区，鱼在深浅之间频繁移动，交界处是必经鱼道"
                    else -> 74 to "深浅交界进退自如，是多数鱼种的巡游路线"
                }

                SpotTag.DEEP_BASIN -> when {
                    hot -> 84 to "水温偏高，深水凉爽且温度稳定"
                    cold -> 80 to "水温偏低，深水保温、温差小"
                    else -> 48 to "水温适宜，鱼不必躲深水"
                }

                SpotTag.SHALLOW_FLAT -> when {
                    cold -> 30 to "水温偏低，浅滩降温快、鱼少停留"
                    ctx.dayPhase == DayPhase.DAWN_DUSK && comfortable -> 80 to "晨昏水温适宜、饵鱼靠边，浅滩是觅食主场"
                    ctx.localHour in 11..15 -> 34 to "正午强光，浅滩少鱼"
                    else -> 58 to "浅滩升温快，水温适宜时可作钓"
                }

                SpotTag.WEED -> when {
                    lowPressure && ctx.dayPhase == DayPhase.NIGHT -> 34 to "低气压夜间水草呼吸耗氧，反而更缺氧"
                    hot -> 66 to "水草遮阴降温，白天是避暑点（但夜间耗氧，宜白天钓）"
                    comfortable -> 74 to "水草区藏饵、遮光，鱼有安全感"
                    else -> 52 to "水温偏低时水草区活性一般"
                }

                SpotTag.STRUCTURE -> 64 to "乱石、倒树、埂坎等硬结构是藏身与伏击点"

                SpotTag.SHADE -> when {
                    hot && ctx.dayPhase == DayPhase.DAY -> 80 to "高温强光下，树荫与桥墩下是避暑与避敌的首选"
                    else -> 44 to "光照不强，遮阴优势不明显"
                }

                SpotTag.BAY -> when {
                    strongWind -> 86 to "风大时洄湾水面平静、食物沉积，是避风良位"
                    else -> 54 to "洄湾水流缓、食物沉积，常规可作备选"
                }

                SpotTag.OPEN_WATER -> when {
                    p.layer == WaterLayer.UPPER -> 76 to "上层鱼追饵鱼于开阔水面，需远投大范围搜索"
                    comfortable -> 52 to "开阔水面缺乏遮挡，鱼缺少安全感"
                    else -> 42 to "水温不利且无遮挡，开阔面留鱼能力差"
                }
            }

            // 三项加权后取前 3：
            // 1) base 是环境条件给出的基础分；
            // 2) preferenceBonus 是鱼种的结构偏好；
            // 3) strongSignalBonus 是「极端环境信号」保底——当某个位置因环境
            //    条件本身已极具价值（如低气压下的活水入水口、大风天的背风洄湾），
            //    它应当压过鱼种的静态偏好。否则会出现「明明缺氧却推荐草区」的错误。
            val preferenceBonus = if (tag in p.preferredSpots) 15 else 0
            val strongSignalBonus = if (base.first >= 85) 10 else 0
            val score = (base.first + preferenceBonus + strongSignalBonus).coerceAtMost(100)

            val preferenceText = if (tag in p.preferredSpots) "；这也是${p.name}偏好的结构" else ""
            val signalText = if (strongSignalBonus > 0) "；当前条件下此项优先级被提升" else ""

            SpotAdvice(
                tag = tag,
                priority = score,
                reason = base.second + preferenceText + signalText
            )
        }

        return scored.sortedByDescending { it.priority }.take(TOP_SPOT_COUNT)
    }

    // ---------------------------------------------------------------- 饵料与钓法

    private fun resolveBait(ctx: AdviceContext): Pair<List<String>, String> {
        val p = ctx.species.profile
        val reason = when {
            ctx.waterTempC < 16.0 ->
                "水温偏低、鱼代谢慢，宜用高蛋白腥活饵，饵团做小、逗钓放慢，给足入口时间"
            ctx.waterTempC < p.tempOptimal.start ->
                "水温略低于最适区间，以腥香为主、适当加少量动物蛋白提味"
            ctx.waterTempC > p.tempOptimal.endInclusive ->
                "水温偏高，鱼偏好清淡，宜减腥增香或改用素饵，浓腥易招小鱼闹窝"
            ctx.pressureDelta3h <= -3.0 ->
                "低气压鱼口轻，饵要轻、软、小，降低吸入阻力"
            else ->
                "水温处于最适区间，按常规味型即可，鱼口好时可适度加大味型"
        }
        return p.baits to reason
    }

    private fun resolveMethod(ctx: AdviceContext, offBottom: Boolean): String {
        val p = ctx.species.profile
        val extra = mutableListOf<String>()
        if (offBottom) extra += "当前建议改为钓浮 / 钓离底，每隔 20-30 分钟调整一次水层找鱼"
        if (beaufortLevel(ctx.windSpeedKmh) >= 5) {
            extra += "风大时加大铅坠、缩短子线，或改用跑铅与大漂稳住钓组"
        }
        if (ctx.precipitationMm >= 2.0) extra += "雨天视线与安全性下降，注意防滑防雷电"
        return if (extra.isEmpty()) p.method else p.method + "（" + extra.joinToString("；") + "）"
    }

    // ---------------------------------------------------------------- 注意事项

    private fun buildCautions(ctx: AdviceContext): List<String> {
        val p = ctx.species.profile
        val list = mutableListOf<String>()

        if (!ctx.waterTempReliable) {
            list += "水温为气温推算的估算值（历史数据不足），建议以现场水温计实测校正"
        }
        if (ctx.species == FishSpecies.TILAPIA && ctx.waterTempC < 16.0) {
            list += "罗非鱼低于 15℃ 摄食骤减、10℃ 以下大量死亡，低温天不建议以它为目标鱼"
        }
        if (p.oxygenTolerance == OxygenTolerance.VERY_LOW && ctx.pressureDelta3h <= -3.0) {
            list += "${p.name}是极不耐低氧的鱼种，气压骤降时最先浮头停口，建议改期或换目标鱼"
        }
        if (p.oxygenTolerance == OxygenTolerance.VERY_HIGH) {
            list += "${p.name}耐低氧能力强，低气压天反而是它的机会窗口"
        }
        if (ctx.windSpeedKmh >= 39.0) {
            list += "风力已达 6 级及以上，抛竿与观漂困难，请优先评估人身与船只安全"
        }
        if (ctx.precipitationMm >= 5.0) {
            list += "有明显降水，岸边湿滑、水面能见度差，注意防雷与防滑"
        }
        if (ctx.airTempC >= 35.0 || ctx.airTempC <= 0.0) {
            list += "气温极端，注意防暑或保暖，缩短连续作钓时长"
        }
        return list
    }
}
