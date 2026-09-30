package com.pcinfo.fishing.domain

import com.pcinfo.fishing.domain.engine.FishAdviceEngine
import com.pcinfo.fishing.domain.engine.AdviceContext
import com.pcinfo.fishing.domain.engine.DayPhase
import com.pcinfo.fishing.domain.engine.FishingScoreEngine
import com.pcinfo.fishing.domain.engine.WaterTempEstimator
import com.pcinfo.fishing.domain.model.FishSpecies
import com.pcinfo.fishing.domain.model.HourlyWeather
import com.pcinfo.fishing.domain.model.OxygenTolerance
import com.pcinfo.fishing.domain.model.SpotTag
import com.pcinfo.fishing.domain.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 鱼种化模型测试：水温估算、鱼种温度曲线、耐低氧修正、
 * 活动节律与钓位/水深规则。
 */
class FishSpeciesTest {

    private val baseHour = 1_700_000_000_000L
    private val hourMs = 3_600_000L

    /** 构造 55 个小时点，观测点在第 24 位，保证历史样本充足（水温估算需 ≥12 点） */
    private fun hourly(
        tempC: Double = 20.0,
        pressureHpa: Double = 1015.0,
        windSpeedKmh: Double = 12.0,
        windDirectionDeg: Int = 90,
        precipitationMm: Double = 0.0,
        observedIndex: Int = 24,
        count: Int = 55,
        temperatureAt: ((index: Int) -> Double)? = null
    ): List<HourlyWeather> = (0 until count).map { index ->
        HourlyWeather(
            epochMs = baseHour + (index - observedIndex) * hourMs,
            temperatureC = temperatureAt?.invoke(index) ?: tempC,
            pressureHpa = pressureHpa,
            windSpeedKmh = windSpeedKmh,
            windDirectionDeg = windDirectionDeg,
            precipitationMm = precipitationMm,
            precipitationProbabilityPercent = 0,
            cloudCoverPercent = 20
        )
    }

    private fun snapshot(
        tempC: Double = 20.0,
        pressureHpa: Double = 1015.0,
        windSpeedKmh: Double = 12.0,
        windDirectionDeg: Int = 90,
        precipitationMm: Double = 0.0,
        temperatureAt: ((index: Int) -> Double)? = null
    ): WeatherSnapshot {
        val observedIndex = 24
        val points = hourly(
            tempC = tempC,
            pressureHpa = pressureHpa,
            windSpeedKmh = windSpeedKmh,
            windDirectionDeg = windDirectionDeg,
            precipitationMm = precipitationMm,
            observedIndex = observedIndex,
            temperatureAt = temperatureAt
        )
        val observedAt = points[observedIndex].epochMs
        return WeatherSnapshot(
            observedAtEpochMs = observedAt,
            temperatureC = tempC,
            apparentTemperatureC = tempC,
            humidityPercent = 60,
            precipitationMm = precipitationMm,
            weatherCode = 0,
            pressureHpa = pressureHpa,
            windSpeedKmh = windSpeedKmh,
            windGustKmh = windSpeedKmh,
            windDirectionDeg = windDirectionDeg,
            cloudCoverPercent = 20,
            elevationMeters = null,
            timeZoneId = "Asia/Shanghai",
            utcOffsetSeconds = 8 * 3600,
            sunriseEpochMs = observedAt - 60 * 60 * 1000L,
            sunsetEpochMs = observedAt + 8 * 60 * 60 * 1000L,
            hourly = points
        )
    }

    private fun advice(
        species: FishSpecies = FishSpecies.CRUCIAN,
        waterTempC: Double = 22.0,
        airTempC: Double = 24.0,
        delta3h: Double = 0.0,
        windSpeedKmh: Double = 12.0,
        dayPhase: DayPhase = DayPhase.DAWN_DUSK,
        localHour: Int = 6
    ) = FishAdviceEngine.build(
        AdviceContext(
            species = species,
            waterTempC = waterTempC,
            waterTempReliable = true,
            airTempC = airTempC,
            pressureHpa = 1015.0,
            pressureDelta3h = delta3h,
            windSpeedKmh = windSpeedKmh,
            windDirectionDeg = 90,
            precipitationMm = 0.0,
            cloudCoverPercent = 20,
            localHour = localHour,
            dayPhase = dayPhase
        )
    )

    // ------------------------------------------------------------ 水温估算

    @Test
    fun `恒温序列下水温应约为气温减 1 度`() {
        val temp = WaterTempEstimator.estimate(hourly(tempC = 20.0), 20.0, baseHour)
        assertEquals(19.0, temp, 0.01)
    }

    @Test
    fun `水温估算应受季节基准约束而非只看当前气温`() {
        // 过去 24 小时平均约 10℃，当前气温骤升至 30℃
        val points = hourly(temperatureAt = { index -> if (index <= 24) 10.0 else 30.0 })
        val temp = WaterTempEstimator.estimate(points, 30.0, baseHour)
        assertTrue("水温应明显低于当前气温（水体升温滞后），实际 $temp", temp < 25.0)
        assertTrue("水温应高于历史均值（已受当日升温拉动），实际 $temp", temp > 12.0)
    }

    @Test
    fun `历史样本不足时应标记为不可靠`() {
        val few = hourly(count = 5, observedIndex = 4)
        assertEquals(false, WaterTempEstimator.isReliable(few, baseHour))
        assertEquals(true, WaterTempEstimator.isReliable(hourly(), baseHour))
    }

    // ------------------------------------------------------------ 鱼种温度模型

    @Test
    fun `同一水温下不同鱼种评分应不同`() {
        val waterTemp = 19.0
        val crucian = FishingScoreEngine.scoreTemperature(waterTemp, FishSpecies.CRUCIAN)
        val tilapia = FishingScoreEngine.scoreTemperature(waterTemp, FishSpecies.TILAPIA)
        assertEquals("19℃ 在鲫鱼最适区间内", 100, crucian)
        assertTrue("19℃ 对罗非偏低，评分应更低，实际 $tilapia", tilapia < crucian)
    }

    @Test
    fun `水温超出可摄食区间应得最低分`() {
        assertEquals(12, FishingScoreEngine.scoreTemperature(12.0, FishSpecies.TILAPIA))
        assertEquals(100, FishingScoreEngine.scoreTemperature(28.0, FishSpecies.TILAPIA))
    }

    @Test
    fun `切换鱼种会改变综合评分`() {
        val s = snapshot(tempC = 20.0)
        val generic = FishingScoreEngine.evaluate(s, FishSpecies.ANY).totalScore
        val tilapia = FishingScoreEngine.evaluate(s, FishSpecies.TILAPIA).totalScore
        assertTrue("20℃ 气温下罗非评分应低于通用模型", tilapia < generic)
    }

    // ------------------------------------------------------------ 耐低氧修正

    @Test
    fun `耐低氧鱼种在低气压下惩罚更小`() {
        val lowPressureScore = FishingScoreEngine.scorePressure(990.0)
        val bighead = FishingScoreEngine.adjustForOxygenTolerance(
            lowPressureScore, OxygenTolerance.VERY_LOW
        )
        val snakehead = FishingScoreEngine.adjustForOxygenTolerance(
            lowPressureScore, OxygenTolerance.VERY_HIGH
        )
        assertTrue("鲢鳙应比黑鱼受更大惩罚：$bighead vs $snakehead", bighead < snakehead)
        assertEquals(lowPressureScore, FishingScoreEngine.adjustForOxygenTolerance(
            lowPressureScore, OxygenTolerance.MEDIUM
        ))
    }

    // ------------------------------------------------------------ 活动节律

    @Test
    fun `夜行鱼种夜间评分高于白天`() {
        val night = FishingScoreEngine.scoreDayTime(22 * 3600, 6 * 3600, 18 * 3600, FishSpecies.CATFISH)
        val day = FishingScoreEngine.scoreDayTime(12 * 3600, 6 * 3600, 18 * 3600, FishSpecies.CATFISH)
        assertEquals(100, night)
        assertTrue("鲶鱼白天口应更稀：$day", day < night)
    }

    @Test
    fun `昼行鱼种白天评分高于夜间`() {
        val day = FishingScoreEngine.scoreDayTime(12 * 3600, 6 * 3600, 18 * 3600, FishSpecies.GRASS)
        val night = FishingScoreEngine.scoreDayTime(22 * 3600, 6 * 3600, 18 * 3600, FishSpecies.GRASS)
        assertTrue("草鱼白天应优于夜间：$day vs $night", day > night)
    }

    @Test
    fun `通用鱼种时段规则保持既有分档`() {
        assertEquals(
            100,
            FishingScoreEngine.scoreDayTime(6 * 3600, 6 * 3600, 18 * 3600, FishSpecies.ANY)
        )
        assertEquals(
            65,
            FishingScoreEngine.scoreDayTime(12 * 3600, 6 * 3600, 18 * 3600, FishSpecies.ANY)
        )
        assertEquals(
            35,
            FishingScoreEngine.scoreDayTime(1 * 3600, 6 * 3600, 18 * 3600, FishSpecies.ANY)
        )
    }

    // ------------------------------------------------------------ 水深与钓位

    @Test
    fun `水温高于最适区间时建议水深应加深`() {
        val hot = advice(species = FishSpecies.CARP, waterTempC = 32.0)
        val mild = advice(species = FishSpecies.CARP, waterTempC = 24.0)
        assertTrue("高温应钓更深：${hot.depthMinM} vs ${mild.depthMinM}", hot.depthMinM > mild.depthMinM)
    }

    @Test
    fun `水温低于最适区间时建议水深也应加深`() {
        val cold = advice(species = FishSpecies.CARP, waterTempC = 14.0)
        val mild = advice(species = FishSpecies.CARP, waterTempC = 24.0)
        assertTrue(cold.depthMinM > mild.depthMinM)
    }

    @Test
    fun `气压骤降且水温偏高时应建议钓离底`() {
        val muggy = advice(species = FishSpecies.CRUCIAN, waterTempC = 26.0, delta3h = -4.0)
        assertTrue("应标记钓浮", muggy.fishOffBottom)
        val stable = advice(species = FishSpecies.CRUCIAN, waterTempC = 26.0, delta3h = 0.0)
        assertTrue(stable.fishOffBottom.not())
    }

    @Test
    fun `气压骤降但水温低时不应判定为缺氧上浮`() {
        val coldAndFalling = advice(species = FishSpecies.CRUCIAN, waterTempC = 12.0, delta3h = -4.0)
        assertTrue("低温下溶氧本就充足，不应建议钓浮", coldAndFalling.fishOffBottom.not())
    }

    @Test
    fun `有风时下风口匹配度应显著高于无风时`() {
        val windy = advice(species = FishSpecies.TOPMOUTH, windSpeedKmh = 25.0)
        val calm = advice(species = FishSpecies.TOPMOUTH, windSpeedKmh = 1.0)
        val windyScore = windy.spots.firstOrNull { it.tag == SpotTag.WINDWARD }?.priority
        val calmScore = calm.spots.firstOrNull { it.tag == SpotTag.WINDWARD }?.priority
        assertTrue("有风时下风口应进入推荐列表", windyScore != null)
        assertTrue("无风时下风口不应是首选", calmScore == null || calmScore < windyScore!!)
    }

    @Test
    fun `钓位建议最多三条且按匹配度降序`() {
        val result = advice(species = FishSpecies.CRUCIAN)
        assertTrue(result.spots.size <= 3)
        assertTrue(result.spots.isNotEmpty())
        assertEquals(result.spots.sortedByDescending { it.priority }, result.spots)
    }

    @Test
    fun `低气压时进出水口应获得高匹配度`() {
        val result = advice(species = FishSpecies.CRUCIAN, delta3h = -2.0)
        val inlet = result.spots.firstOrNull { it.tag == SpotTag.INLET }
        assertTrue("低气压时活水入水口应被推荐", inlet != null)
        assertTrue(inlet!!.priority >= 85)
    }

    @Test
    fun `罗非低温天应给出针对性警告`() {
        val result = advice(species = FishSpecies.TILAPIA, waterTempC = 13.0)
        assertTrue(result.cautions.any { it.contains("罗非") })
    }

    // ------------------------------------------------------------ 回归保护

    @Test
    fun `接入鱼种后评分结构保持不变`() {
        val result = FishingScoreEngine.evaluate(snapshot(), FishSpecies.CRUCIAN)
        assertEquals(7, result.factors.size)
        assertEquals(100.0, result.factors.sumOf { it.weight }, 0.001)
        assertTrue(result.totalScore in 0..100)
        assertTrue(result.speciesAdvice != null)
        assertTrue(result.waterTempC != null)
    }

    @Test
    fun `极端条件下评分仍落在 0 到 100`() {
        val worst = FishingScoreEngine.evaluate(
            snapshot(
                tempC = 38.0,
                pressureHpa = 985.0,
                windSpeedKmh = 90.0,
                windDirectionDeg = 225,
                precipitationMm = 12.0
            ),
            FishSpecies.BIGHEAD
        )
        assertTrue(worst.totalScore in 0..100)
        assertTrue(worst.speciesAdvice != null)
    }
}
