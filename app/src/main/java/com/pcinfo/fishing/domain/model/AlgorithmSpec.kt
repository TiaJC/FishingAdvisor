package com.pcinfo.fishing.domain.model

/**
 * 算法规格：把评分模型「自描述」出来供界面展示。
 *
 * 关键点：档位说明直接由评分引擎生成，与真实打分逻辑同源，
 * 避免文档与实现各写一份后出现不一致。
 */

/** 单档评分区间 */
data class ScoreBand(
    val condition: String,
    val score: Int
)

/** 因子规格 */
data class FactorSpec(
    val key: String,
    val label: String,
    /** 归一化后的权重百分比 */
    val weight: Double,
    /** 该因子的经验依据 */
    val basis: String,
    /** 分档规则：满足条件 → 得分 */
    val bands: List<ScoreBand>
) {
    /** 权重展示文案：整数不显示小数 */
    val weightText: String
        get() = if (weight % 1.0 == 0.0) "${weight.toInt()}%" else "%.1f%%".format(weight)
}

/** 等级规格 */
data class LevelSpec(
    val range: String,
    val level: AdviceLevel
)

/** 完整算法说明 */
data class AlgorithmSpec(
    val formula: String,
    val factors: List<FactorSpec>,
    val levels: List<LevelSpec>,
    val notes: List<String>
)
