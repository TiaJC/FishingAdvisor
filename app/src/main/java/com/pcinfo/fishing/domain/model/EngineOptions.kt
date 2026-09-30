package com.pcinfo.fishing.domain.model

/**
 * 评分引擎的可切换行为。
 *
 * 之所以把这些做成开关而不是写死，是因为每一项都存在「经验 vs 证据」的张力：
 * 有人坚信方位口诀，有人认为方位无效；有人有水温计，有人只能靠估算。
 * 与其替用户做决定，不如让他按自己的装备与钓场习惯选择。
 *
 * @param useEstimatedWaterTemp 用「气温推算的水温」还是直接用气温评分。
 *   关闭后因子按气温评分，适合已知水温与实际气温接近的小水体。
 * @param useWindDirectionScoring 是否按风向方位（东/南/西南）给分。
 *   关闭后风向项固定记中性分，只保留风力的影响。
 *   方位口诀带有明显地域性，换个气候区可能不成立，故提供关闭入口。
 * @param useOxygenToleranceAdjust 是否按鱼种耐低氧能力缩放气压类得分。
 */
data class EngineOptions(
    val useEstimatedWaterTemp: Boolean = true,
    val useWindDirectionScoring: Boolean = true,
    val useOxygenToleranceAdjust: Boolean = true
) {
    companion object {
        val DEFAULT = EngineOptions()

        /** 关闭方位评分时风向项取的中性分 */
        const val NEUTRAL_WIND_DIRECTION_SCORE = 70
    }
}
