package com.pcinfo.fishing.domain.model

/**
 * 七个评分因子的权重配置。
 *
 * 设计要点：
 * - 存的是**相对权重**（整数滑块值），不要求和为 100；
 *   实际计分时按 `weight / total` 归一化，因此用户怎么拖都不会把总分撑爆或压扁。
 * - 默认权重沿用垂钓界通行经验，其中气压类合计 45 是最高的一项，
 *   但这属于经验权重而非严格实验结论，「百科」页已标注证据强度。
 */
data class ScoreWeights(
    val pressure: Int = 20,
    val pressureTrend: Int = 25,
    val windSpeed: Int = 15,
    val windDirection: Int = 10,
    val temperature: Int = 10,
    val precipitation: Int = 10,
    val dayTime: Int = 10
) {

    /** 相对权重之和，归一化用它做分母 */
    val total: Int get() =
        pressure + pressureTrend + windSpeed + windDirection + temperature + precipitation + dayTime

    fun get(key: String): Int = when (key) {
        KEY_PRESSURE -> pressure
        KEY_PRESSURE_TREND -> pressureTrend
        KEY_WIND_SPEED -> windSpeed
        KEY_WIND_DIRECTION -> windDirection
        KEY_TEMPERATURE -> temperature
        KEY_PRECIPITATION -> precipitation
        KEY_DAY_TIME -> dayTime
        else -> 0
    }

    /** 按 key 覆盖单项，供设置页滑块使用 */
    fun with(key: String, value: Int): ScoreWeights = when (key) {
        KEY_PRESSURE -> copy(pressure = value)
        KEY_PRESSURE_TREND -> copy(pressureTrend = value)
        KEY_WIND_SPEED -> copy(windSpeed = value)
        KEY_WIND_DIRECTION -> copy(windDirection = value)
        KEY_TEMPERATURE -> copy(temperature = value)
        KEY_PRECIPITATION -> copy(precipitation = value)
        KEY_DAY_TIME -> copy(dayTime = value)
        else -> this
    }

    /** 归一化后的百分比，合计恒为 100 */
    fun percentages(): Map<String, Double> {
        val t = total
        if (t <= 0) return DEFAULT.percentagesForFallback()
        return linkedMapOf(
            KEY_PRESSURE to pressure * 100.0 / t,
            KEY_PRESSURE_TREND to pressureTrend * 100.0 / t,
            KEY_WIND_SPEED to windSpeed * 100.0 / t,
            KEY_WIND_DIRECTION to windDirection * 100.0 / t,
            KEY_TEMPERATURE to temperature * 100.0 / t,
            KEY_PRECIPITATION to precipitation * 100.0 / t,
            KEY_DAY_TIME to dayTime * 100.0 / t
        )
    }

    fun percentOf(key: String): Double = percentages()[key] ?: 0.0

    /** 全部权重被拖到 0 时的兜底（避免除零） */
    private fun percentagesForFallback(): Map<String, Double> = KEYS.associateWith { 100.0 / KEYS.size }

    companion object {
        const val KEY_PRESSURE = "pressure"
        const val KEY_PRESSURE_TREND = "pressureTrend"
        const val KEY_WIND_SPEED = "windSpeed"
        const val KEY_WIND_DIRECTION = "windDirection"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_PRECIPITATION = "precipitation"
        const val KEY_DAY_TIME = "dayTime"

        val KEYS: List<String> = listOf(
            KEY_PRESSURE,
            KEY_PRESSURE_TREND,
            KEY_WIND_SPEED,
            KEY_WIND_DIRECTION,
            KEY_TEMPERATURE,
            KEY_PRECIPITATION,
            KEY_DAY_TIME
        )

        val LABELS: Map<String, String> = mapOf(
            KEY_PRESSURE to "气压",
            KEY_PRESSURE_TREND to "气压趋势",
            KEY_WIND_SPEED to "风力",
            KEY_WIND_DIRECTION to "风向",
            KEY_TEMPERATURE to "水温",
            KEY_PRECIPITATION to "降水",
            KEY_DAY_TIME to "时段"
        )

        /** 单个因子的可调上限，避免某一项被拖到独占全部权重 */
        const val MAX_SINGLE = 50
        const val MIN_SINGLE = 0

        val DEFAULT = ScoreWeights()

        /**
         * 预设方案。每项合计均为 100，便于用户直观比较。
         * 提供预设是因为「自己拖七个滑块」对多数用户来说负担太重，
         * 而几个典型取向能覆盖大部分真实场景。
         */
        val PRESETS: List<WeightPreset> = listOf(
            WeightPreset(
                name = "均衡默认",
                summary = "综合七项因子，适合日常参考",
                weights = ScoreWeights(20, 25, 15, 10, 10, 10, 10)
            ),
            WeightPreset(
                name = "重气压",
                summary = "坚信「气压定鱼口」，对骤降最敏感",
                weights = ScoreWeights(30, 30, 10, 5, 10, 10, 5)
            ),
            WeightPreset(
                name = "重水温",
                summary = "水温是证据最强的因子，适合季节交替期",
                weights = ScoreWeights(10, 15, 10, 5, 35, 10, 15)
            ),
            WeightPreset(
                name = "重时段",
                summary = "只看早晚窗口，适合只能抽空作钓的人",
                weights = ScoreWeights(10, 15, 10, 10, 10, 10, 35)
            ),
            WeightPreset(
                name = "重风浪",
                summary = "风浪决定溶氧与饵鱼走向，适合大水面",
                weights = ScoreWeights(10, 15, 25, 25, 10, 10, 5)
            )
        )
    }
}

/** 一组权重预设 */
data class WeightPreset(
    val name: String,
    val summary: String,
    val weights: ScoreWeights
)
