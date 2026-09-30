package com.pcinfo.fishing.domain

import com.pcinfo.fishing.domain.engine.FishingScoreEngine
import com.pcinfo.fishing.domain.engine.beaufortLevel
import com.pcinfo.fishing.domain.engine.scoreWindDirection
import com.pcinfo.fishing.domain.engine.windDirectionLabel
import com.pcinfo.fishing.domain.model.AdviceLevel
import com.pcinfo.fishing.domain.model.HourlyWeather
import com.pcinfo.fishing.domain.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评分引擎单元测试：覆盖理想条件、劣化条件与关键边界。
 * 引擎为纯 Kotlin 实现，无需 Robolectric。
 */
class FishingScoreEngineTest {

    private val baseHour = 1_700_000_000_000L
    private val hourMs = 3_600_000L

    /** 构造逐小时序列：默认气压恒定，便于隔离单一变量 */
    private fun snapshot(
        pressureHpa: Double = 1015.0,
        windSpeedKmh: Double = 12.0,
        windDirectionDeg: Int = 90,
        temperatureC: Double = 20.0,
        precipitationMm: Double = 0.0,
        observedIndex: Int = 6,
        sunriseOffsetSeconds: Int? = null,
        sunsetOffsetSeconds: Int? = null,
        // 函数类型参数放在最后，既支持尾随 lambda，也不会与后续参数产生匹配歧义
        pressureCurve: (index: Int) -> Double = { pressureHpa }
    ): WeatherSnapshot {
        val hourly = (0..30).map { index ->
            HourlyWeather(
                epochMs = baseHour + (index - 6) * hourMs,
                temperatureC = temperatureC,
                pressureHpa = pressureCurve(index),
                windSpeedKmh = windSpeedKmh,
                windDirectionDeg = windDirectionDeg,
                precipitationMm = precipitationMm,
                precipitationProbabilityPercent = 0,
                cloudCoverPercent = 20
            )
        }
        // 观测时刻落在日出窗口内（当地时间 06:00 附近）
        val utcOffset = 8 * 3600
        val observedAt = hourly[observedIndex].epochMs
        return WeatherSnapshot(
            observedAtEpochMs = observedAt,
            temperatureC = temperatureC,
            apparentTemperatureC = temperatureC,
            humidityPercent = 60,
            precipitationMm = precipitationMm,
            weatherCode = 0,
            pressureHpa = pressureCurve(observedIndex),
            windSpeedKmh = windSpeedKmh,
            windGustKmh = windSpeedKmh,
            windDirectionDeg = windDirectionDeg,
            cloudCoverPercent = 20,
            elevationMeters = null,
            timeZoneId = "Asia/Shanghai",
            utcOffsetSeconds = utcOffset,
            sunriseEpochMs = sunriseOffsetSeconds?.let { observedAt - 60 * 60 * 1000L },
            sunsetEpochMs = sunsetOffsetSeconds?.let { observedAt + 8 * 60 * 60 * 1000L },
            hourly = hourly
        )
    }

    @Test
    fun `理想条件应得到高分区间`() {
        val result = FishingScoreEngine.evaluate(
            snapshot(
                pressureHpa = 1015.0,
                windSpeedKmh = 12.0,
                windDirectionDeg = 90,
                temperatureC = 20.0
            )
        )
        assertTrue("理想条件分数应不低于 80，实际 ${result.totalScore}", result.totalScore >= 80)
        assertEquals(AdviceLevel.EXCELLENT, result.level)
        assertEquals(7, result.factors.size)
        assertEquals(100.0, result.factors.sumOf { it.weight }, 0.001)
    }

    @Test
    fun `气压骤降应显著拉低得分`() {
        // 每 3 小时下降约 3.6 hPa，属于「骤降」档
        val falling = snapshot(pressureCurve = { index -> 1018.0 - index * 1.2 })
        val result = FishingScoreEngine.evaluate(falling)
        val trendFactor = result.factors.first { it.key == "pressureTrend" }
        assertEquals(15, trendFactor.rawScore)
        // 其余条件理想时总分约 78，骤降让趋势项（权重 25）几乎失分
        assertTrue("骤降场景总分应偏低，实际 ${result.totalScore}", result.totalScore <= 80)
    }

    @Test
    fun `大风天气得分应明显偏低`() {
        val stormy = snapshot(windSpeedKmh = 55.0)
        val result = FishingScoreEngine.evaluate(stormy)
        val windFactor = result.factors.first { it.key == "windSpeed" }
        assertTrue("大风评分应很低，实际 ${windFactor.rawScore}", windFactor.rawScore <= 30)
        assertTrue(result.tips.any { it.contains("抛竿") })
    }

    @Test
    fun `南风与西南风评分应低于东风`() {
        assertTrue(scoreWindDirection(90) > scoreWindDirection(180))
        assertTrue(scoreWindDirection(90) > scoreWindDirection(225))
        assertEquals("东风", windDirectionLabel(90))
        assertEquals("南风", windDirectionLabel(180))
    }

    @Test
    fun `风力等级换算正确`() {
        assertEquals(0, beaufortLevel(0.5))
        assertEquals(2, beaufortLevel(10.0))
        assertEquals(3, beaufortLevel(15.0))
        assertEquals(6, beaufortLevel(45.0))
    }

    @Test
    fun `极端温度得分低`() {
        val cold = FishingScoreEngine.evaluate(snapshot(temperatureC = 2.0))
        val mild = FishingScoreEngine.evaluate(snapshot(temperatureC = 20.0))
        assertTrue(cold.totalScore < mild.totalScore)
        assertTrue(cold.factors.first { it.key == "temperature" }.rawScore <= 30)
    }

    @Test
    fun `时段评分：早晚窗口高于深夜`() {
        val morningScore = FishingScoreEngine.scoreDayTime(6 * 3600, 6 * 3600, 18 * 3600)
        val midnightScore = FishingScoreEngine.scoreDayTime(1 * 3600, 6 * 3600, 18 * 3600)
        val noonScore = FishingScoreEngine.scoreDayTime(12 * 3600, 6 * 3600, 18 * 3600)
        assertEquals(100, morningScore)
        assertEquals(65, noonScore)
        assertEquals(35, midnightScore)
    }

    @Test
    fun `缺少日出日落数据时退化为固定时段规则`() {
        assertEquals(100, FishingScoreEngine.scoreDayTime(6 * 3600, null, null))
        assertEquals(35, FishingScoreEngine.scoreDayTime(23 * 3600, null, null))
    }

    @Test
    fun `最佳窗口在夜间低分场景下应给出白天时段`() {
        // 当前处于深夜，未来白天条件更好
        val night = snapshot(
            sunriseOffsetSeconds = 0,
            sunsetOffsetSeconds = 0
        )
        val result = FishingScoreEngine.evaluate(night)
        assertTrue("应给出推荐窗口", result.bestWindow != null)
        assertTrue(result.bestWindow!!.score >= result.totalScore)
    }

    @Test
    fun `评分区间始终落在 0 到 100`() {
        val worst = FishingScoreEngine.evaluate(
            snapshot(
                pressureHpa = 985.0,
                windSpeedKmh = 90.0,
                windDirectionDeg = 225,
                temperatureC = 38.0,
                precipitationMm = 12.0,
                pressureCurve = { 1010.0 - it * 2.0 }
            )
        )
        assertTrue(worst.totalScore in 0..100)
        assertEquals(AdviceLevel.BAD, worst.level)
    }
}
