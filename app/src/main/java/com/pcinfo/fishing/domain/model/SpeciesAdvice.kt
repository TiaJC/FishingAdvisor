package com.pcinfo.fishing.domain.model

/** 单个钓位建议 */
data class SpotAdvice(
    /** 结构类型 */
    val tag: SpotTag,
    /** 优先度 0-100，用于排序与展示 */
    val priority: Int,
    /** 为什么推荐这里 */
    val reason: String
)

/** 面向某个鱼种的作钓建议 */
data class SpeciesAdvice(
    val species: FishSpecies,
    /** 估算水温（℃）。由气温序列推算，非实测值 */
    val waterTempC: Double,
    /** 水温估算是否可靠（历史数据是否充足） */
    val waterTempReliable: Boolean,
    /** 建议作钓水深下限（米） */
    val depthMinM: Double,
    /** 建议作钓水深上限（米） */
    val depthMaxM: Double,
    /** 深度调整的理由 */
    val depthReason: String,
    /** 是否建议改为钓浮 / 钓离底 */
    val fishOffBottom: Boolean,
    /** 排序后的钓位建议，最多 3 条 */
    val spots: List<SpotAdvice>,
    /** 建议饵料 */
    val baits: List<String>,
    /** 饵料调整理由 */
    val baitReason: String,
    /** 钓组 */
    val rig: String,
    /** 钓法 */
    val method: String,
    /** 额外注意事项 */
    val cautions: List<String>
) {
    /** 水深展示文案 */
    val depthText: String get() =
        if (depthMaxM - depthMinM < 0.3) {
            "约 %.1f 米".format(depthMinM)
        } else {
            "%.1f ~ %.1f 米".format(depthMinM, depthMaxM)
        }
}
