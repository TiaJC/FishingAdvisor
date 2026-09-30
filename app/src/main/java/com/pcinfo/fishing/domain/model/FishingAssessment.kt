package com.pcinfo.fishing.domain.model

/** 推荐等级 */
enum class AdviceLevel(val label: String, val emoji: String) {
    EXCELLENT("极佳", "🎣"),
    GOOD("良好", "👍"),
    FAIR("一般", "🤔"),
    POOR("较差", "👎"),
    BAD("不宜出钓", "⛔");

    companion object {
        fun fromScore(score: Int): AdviceLevel = when {
            score >= 80 -> EXCELLENT
            score >= 65 -> GOOD
            score >= 50 -> FAIR
            score >= 35 -> POOR
            else -> BAD
        }
    }
}

/** 气压趋势方向 */
enum class PressureDirection(val label: String) {
    RISING_FAST("快速上升"),
    RISING("缓慢上升"),
    STEADY("平稳"),
    FALLING("缓慢下降"),
    FALLING_FAST("快速下降")
}

/** 气压趋势：近 3 小时变化量与方向 */
data class PressureTrend(
    val delta3hHpa: Double,
    val direction: PressureDirection,
    val currentHpa: Double,
    val minHpa: Double,
    val maxHpa: Double
)

/**
 * 单项评分因子：UI 直接据此渲染「评分构成」，保证结论可解释。
 *
 * @param key 因子标识
 * @param label 展示名称
 * @param weight 归一化后的权重百分比（所有因子之和为 100）
 * @param rawScore 该因子得分 0-100
 * @param displayValue 实测值展示文案
 * @param comment 面向用户的解释
 */
data class ScoreFactor(
    val key: String,
    val label: String,
    val weight: Double,
    val rawScore: Int,
    val displayValue: String,
    val comment: String
) {
    /** 该因子对总分的实际贡献 */
    val contribution: Double get() = rawScore * weight / 100.0

    /** 权重展示文案：整数不显示小数，避免「20.0%」这种噪音 */
    val weightText: String
        get() = if (weight % 1.0 == 0.0) "${weight.toInt()}%" else "%.1f%%".format(weight)
}

/** 推荐出钓时段 */
data class TimeWindow(
    val startEpochMs: Long,
    val endEpochMs: Long,
    val score: Int,
    val reason: String
)

/** 一次完整的钓鱼指数评估 */
data class FishingAssessment(
    val totalScore: Int,
    val level: AdviceLevel,
    val pressureTrend: PressureTrend,
    val factors: List<ScoreFactor>,
    val bestWindow: TimeWindow?,
    val tips: List<String>,
    /** 本次评估所对应的时刻（可能是当前，也可能是用户在时间轴上选中的未来/过去时刻） */
    val evaluatedAtEpochMs: Long = 0L,
    /** 本次评估针对的目标鱼种 */
    val species: FishSpecies = FishSpecies.ANY,
    /** 估算水温（℃）；由气温序列推算，非实测 */
    val waterTempC: Double? = null,
    /** 针对该鱼种的钓位、水深、饵料建议 */
    val speciesAdvice: SpeciesAdvice? = null
)
