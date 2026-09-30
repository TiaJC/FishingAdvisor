package com.pcinfo.fishing.domain.engine

import com.pcinfo.fishing.domain.model.HourlyWeather
import kotlin.math.roundToInt

/**
 * 水温估算。
 *
 * 为什么必须估算：鱼是变温动物，其行为由**水温**决定，而我们拿到的气象数据只有**气温**。
 * 水的比热容约为空气的 4 倍，且存在蒸发降温，因此日间水温通常低于气温、
 * 夜间高于气温，且变化幅度只有气温的 40%~60%、相位滞后数小时。
 *
 * 估算式（工程近似，非物理模型）：
 *   水温 ≈ 0.6 × 过去 24 小时平均气温 + 0.4 × 当前气温 − 1.0
 *
 * 前项承担「季节基准」（水温对气温的滞后与平滑），后项让水温跟随当日冷暖趋势，
 * 常数项粗略补偿蒸发降温。
 *
 * 局限（界面已标注为估算值）：
 * - 无法反映水深分层：夏季表层与 3 米以下可差 3-6℃。
 * - 无法反映水体类型：浅塘升温快、水库深水升温慢、流水与静水差异明显。
 * - 冰期、融雪、水库泄洪等特殊情形误差很大。
 * 若要精确，请用水温计或探鱼器实测，并以此为参考做本地校准。
 */
object WaterTempEstimator {

    private const val LOOKBACK_HOURS = 24
    private const val HOUR_MS = 3_600_000L

    /** 估算充分所需的最少历史样本数 */
    private const val MIN_SAMPLES = 12

    /**
     * @param hourly 升序的逐小时序列
     * @param nowEpochMs 目标时刻
     * @return 估算水温（℃）
     */
    fun estimate(hourly: List<HourlyWeather>, nowEpochMs: Long): Double {
        if (hourly.isEmpty()) return 15.0
        val current = hourly.minByOrNull { kotlin.math.abs(it.epochMs - nowEpochMs) } ?: hourly.last()
        return estimate(hourly, current.temperatureC, nowEpochMs)
    }

    /**
     * @param hourly 升序的逐小时序列
     * @param currentAirTempC 当前气温
     * @param nowEpochMs 目标时刻
     */
    fun estimate(
        hourly: List<HourlyWeather>,
        currentAirTempC: Double,
        nowEpochMs: Long
    ): Double {
        val history = hourly.filter { it.epochMs <= nowEpochMs + HOUR_MS / 2 }
            .takeLast(LOOKBACK_HOURS)
        if (history.isEmpty()) {
            // 完全没有历史数据时退化为气温减 2℃，是相当粗的近似
            return (currentAirTempC - 2.0).coerceIn(0.5, 40.0)
        }
        val mean = history.map { it.temperatureC }.average()
        return (mean * 0.6 + currentAirTempC * 0.4 - 1.0).coerceIn(0.5, 40.0)
    }

    /** 历史样本是否足够支撑估算 */
    fun isReliable(hourly: List<HourlyWeather>, nowEpochMs: Long): Boolean =
        hourly.count { it.epochMs <= nowEpochMs + HOUR_MS / 2 } >= MIN_SAMPLES

    /** 展示用：保留一位小数 */
    fun display(celsius: Double): String = "%.1f℃".format(celsius)

    /** 展示用：整数 */
    fun displayInt(celsius: Double): Int = celsius.roundToInt()
}
