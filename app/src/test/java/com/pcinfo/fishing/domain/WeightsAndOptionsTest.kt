package com.pcinfo.fishing.domain

import com.pcinfo.fishing.domain.engine.FishingScoreEngine
import com.pcinfo.fishing.domain.model.EngineOptions
import com.pcinfo.fishing.domain.model.FishSpecies
import com.pcinfo.fishing.domain.model.HourlyWeather
import com.pcinfo.fishing.domain.model.ScoreWeights
import com.pcinfo.fishing.domain.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 权重、引擎开关与「按指定时刻评估」的测试。
 *
 * 这三块正是本次界面改造引入的新能力：用户在时间轴上选时刻、在设置页调权重与开关，
 * 引擎必须给出对应变化，否则界面上的交互就是装饰。
 */
class WeightsAndOptionsTest {

    private val hourMs = 3_600_000L
    private val baseHour = 1_700_000_000_000L
    private val observedIndex = 24
    private val count = 55

    private fun hourlyAt(index: Int): HourlyWeather = HourlyWeather(
        epochMs = baseHour + (index - observedIndex) * hourMs,
        temperatureC = 20.0,
        pressureHpa = 1015.0,
        windSpeedKmh = 12.0,
        windDirectionDeg = 90,
        precipitationMm = 0.0,
        precipitationProbabilityPercent = 0,
        cloudCoverPercent = 20
    )

    private fun snapshot(
        pressureHpa: Double = 1015.0,
        mutate: ((Int, HourlyWeather) -> HourlyWeather)? = null
    ): WeatherSnapshot {
        val points = (0 until count).map { index ->
            val base = hourlyAt(index).copy(pressureHpa = pressureHpa)
            mutate?.invoke(index, base) ?: base
        }
        val observedAt = points[observedIndex].epochMs
        return WeatherSnapshot(
            observedAtEpochMs = observedAt,
            temperatureC = 20.0,
            apparentTemperatureC = 20.0,
            humidityPercent = 60,
            precipitationMm = 0.0,
            weatherCode = 0,
            pressureHpa = pressureHpa,
            windSpeedKmh = 12.0,
            windGustKmh = 12.0,
            windDirectionDeg = 90,
            cloudCoverPercent = 20,
            elevationMeters = null,
            timeZoneId = "Asia/Shanghai",
            utcOffsetSeconds = 8 * 3600,
            sunriseEpochMs = observedAt - 3_600_000L,
            sunsetEpochMs = observedAt + 8 * 3_600_000L,
            hourly = points
        )
    }

    // ------------------------------------------------------------ 权重归一化

    @Test
    fun `默认权重归一化后合计为 100`() {
        val sum = ScoreWeights.DEFAULT.percentages().values.sum()
        assertEquals(100.0, sum, 0.001)
    }

    @Test
    fun `任意权重组合归一化后仍合计为 100`() {
        val weird = ScoreWeights(7, 3, 11, 1, 5, 2, 9)
        val sum = weird.percentages().values.sum()
        assertEquals(100.0, sum, 0.001)
    }

    @Test
    fun `全部权重为 0 时兜底为均分而不是除零`() {
        val zero = ScoreWeights(0, 0, 0, 0, 0, 0, 0)
        val percentages = zero.percentages()
        assertEquals(100.0, percentages.values.sum(), 0.001)
        assertEquals(100.0 / 7, percentages[ScoreWeights.KEY_PRESSURE] ?: 0.0, 0.001)
        // 兜底后评估仍应产出合法分数
        val result = FishingScoreEngine.evaluate(snapshot(), weights = zero)
        assertTrue(result.totalScore in 0..100)
    }

    @Test
    fun `每个预设方案的权重合计都是 100`() {
        ScoreWeights.PRESETS.forEach { preset ->
            assertEquals(
                "预设「${preset.name}」合计应为 100",
                100,
                preset.weights.total
            )
        }
    }

    @Test
    fun `把水温权重调到最高后 得分对水温更敏感`() {
        // 冷水场景：水温远低于多数鱼种最适区间
        val cold = snapshot { index, point ->
            point.copy(temperatureC = 2.0)
        }
        val defaultScore = FishingScoreEngine.evaluate(cold, FishSpecies.ANY).totalScore
        val tempFirst = FishingScoreEngine.evaluate(
            cold,
            FishSpecies.ANY,
            weights = ScoreWeights(10, 15, 10, 5, 35, 10, 15)
        ).totalScore
        assertTrue(
            "重水温预设下冷水应扣更多分：默认 $defaultScore vs 重水温 $tempFirst",
            tempFirst < defaultScore
        )
    }

    @Test
    fun `单个因子权重拉满时总分仍在 0 到 100 之间`() {
        val s = snapshot()
        ScoreWeights.KEYS.forEach { key ->
            val boosted = ScoreWeights.DEFAULT.with(key, ScoreWeights.MAX_SINGLE)
            val score = FishingScoreEngine.evaluate(s, FishSpecies.ANY, weights = boosted).totalScore
            assertTrue("把 $key 拉到上限后总分应合法，实际 $score", score in 0..100)
        }
    }

    // ------------------------------------------------------------ 按时刻评估

    @Test
    fun `按指定时刻评估 会把该时刻写回评估结果`() {
        val s = snapshot()
        val target = s.hourly[observedIndex + 6].epochMs
        val result = FishingScoreEngine.evaluate(s, FishSpecies.ANY, target)
        assertEquals(target, result.evaluatedAtEpochMs)
    }

    @Test
    fun `选择恶劣时刻 得分应低于理想时刻`() {
        // 未来第 6 小时：大风 + 大雨
        val badIndex = observedIndex + 6
        val s = snapshot { index, point ->
            if (index == badIndex) {
                point.copy(windSpeedKmh = 55.0, precipitationMm = 8.0, precipitationProbabilityPercent = 95)
            } else {
                point
            }
        }
        val goodScore = FishingScoreEngine.evaluate(s, FishSpecies.ANY, s.observedAtEpochMs).totalScore
        val badScore = FishingScoreEngine.evaluate(
            s,
            FishSpecies.ANY,
            s.hourly[badIndex].epochMs
        ).totalScore
        assertTrue("恶劣时刻应明显更低：好 $goodScore vs 坏 $badScore", badScore < goodScore)
    }

    @Test
    fun `推荐窗口随评估时刻前移`() {
        val s = snapshot()
        val nowWindow = FishingScoreEngine.evaluate(s, FishSpecies.ANY, s.observedAtEpochMs)
            .bestWindow
        val laterTarget = s.hourly[observedIndex + 8].epochMs
        val laterWindow = FishingScoreEngine.evaluate(s, FishSpecies.ANY, laterTarget)
            .bestWindow
        assertTrue(nowWindow != null)
        assertTrue(laterWindow != null)
        assertTrue(
            "评估时刻后移，推荐窗口起点也应后移",
            laterWindow!!.startEpochMs > nowWindow!!.startEpochMs
        )
    }

    // ------------------------------------------------------------ 引擎开关

    @Test
    fun `关闭水温估算后 因子名改为气温`() {
        val options = EngineOptions(useEstimatedWaterTemp = false)
        val result = FishingScoreEngine.evaluate(snapshot(), FishSpecies.ANY, options = options)
        val factor = result.factors.first { it.key == ScoreWeights.KEY_TEMPERATURE }
        assertEquals("气温", factor.label)
    }

    @Test
    fun `开启水温估算时 因子名为水温`() {
        val result = FishingScoreEngine.evaluate(snapshot(), FishSpecies.ANY)
        val factor = result.factors.first { it.key == ScoreWeights.KEY_TEMPERATURE }
        assertEquals("水温", factor.label)
    }

    @Test
    fun `关闭风向方位评分后 风向项为中性分`() {
        // 西南风 225° 按方位评分只有 40 分，关闭后应回到中性分
        val s = snapshot { _, point -> point.copy(windDirectionDeg = 225) }
        val options = EngineOptions(useWindDirectionScoring = false)
        val result = FishingScoreEngine.evaluate(s, FishSpecies.ANY, options = options)
        val factor = result.factors.first { it.key == ScoreWeights.KEY_WIND_DIRECTION }
        assertEquals(EngineOptions.NEUTRAL_WIND_DIRECTION_SCORE, factor.rawScore)
    }

    @Test
    fun `开启风向方位评分时 西南风得分低于中性分`() {
        val s = snapshot { _, point -> point.copy(windDirectionDeg = 225) }
        val result = FishingScoreEngine.evaluate(s, FishSpecies.ANY)
        val factor = result.factors.first { it.key == ScoreWeights.KEY_WIND_DIRECTION }
        assertTrue(
            "西南风方位分应低于中性分 ${EngineOptions.NEUTRAL_WIND_DIRECTION_SCORE}，实际 ${factor.rawScore}",
            factor.rawScore < EngineOptions.NEUTRAL_WIND_DIRECTION_SCORE
        )
    }

    @Test
    fun `关闭耐低氧修正后 鲢鳙在低气压下得分回升`() {
        val lowPressure = snapshot(pressureHpa = 990.0)
        val withAdjust = FishingScoreEngine.evaluate(
            lowPressure,
            FishSpecies.BIGHEAD,
            options = EngineOptions(useOxygenToleranceAdjust = true)
        ).totalScore
        val withoutAdjust = FishingScoreEngine.evaluate(
            lowPressure,
            FishSpecies.BIGHEAD,
            options = EngineOptions(useOxygenToleranceAdjust = false)
        ).totalScore
        assertTrue(
            "关闭修正后极不耐低氧鱼种应少扣分：修正 $withAdjust vs 不修正 $withoutAdjust",
            withoutAdjust > withAdjust
        )
    }

    @Test
    fun `算法说明中的权重与传入权重一致`() {
        val weights = ScoreWeights(30, 30, 10, 5, 10, 10, 5)
        val spec = FishingScoreEngine.algorithmSpec(FishSpecies.ANY, weights)
        val pressure = spec.factors.first { it.key == ScoreWeights.KEY_PRESSURE }
        assertEquals(30.0, pressure.weight, 0.001)
        assertEquals(100.0, spec.factors.sumOf { it.weight }, 0.001)
    }
}
