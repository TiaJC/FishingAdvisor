package com.pcinfo.fishing.domain.engine

import kotlin.math.roundToInt

/**
 * 风力/风向换算工具。
 *
 * 风速使用蒲福风级（Beaufort）判断，风向按 16 方位给中文名与钓鱼经验分。
 */

/** 蒲福风级：0-12 级，返回中文描述 */
fun beaufortLevel(windSpeedKmh: Double): Int = when {
    windSpeedKmh < 1.0 -> 0
    windSpeedKmh < 6.0 -> 1
    windSpeedKmh < 12.0 -> 2
    windSpeedKmh < 20.0 -> 3
    windSpeedKmh < 29.0 -> 4
    windSpeedKmh < 39.0 -> 5
    windSpeedKmh < 50.0 -> 6
    windSpeedKmh < 62.0 -> 7
    windSpeedKmh < 75.0 -> 8
    windSpeedKmh < 89.0 -> 9
    windSpeedKmh < 103.0 -> 10
    windSpeedKmh < 118.0 -> 11
    else -> 12
}

private val BEAUFORT_LABEL = arrayOf(
    "无风", "软风", "轻风", "微风", "和风", "清风",
    "强风", "疾风", "大风", "烈风", "狂风", "暴风", "飓风"
)

fun beaufortLabel(windSpeedKmh: Double): String =
    "${BEAUFORT_LABEL[beaufortLevel(windSpeedKmh)]}（${beaufortLevel(windSpeedKmh)}级）"

private val COMPASS_16 = arrayOf(
    "北", "北东北", "东北", "东东北", "东", "东东南",
    "东南", "南东南", "南", "南西南", "西南", "西西南",
    "西", "西西北", "西北", "北西北"
)

/**
 * 16 方位扇区索引。
 *
 * 扇区 k 的中心为 k×22.5°，覆盖区间 [k×22.5−11.25, k×22.5+11.25)，
 * 因此索引 = round(角度 / 22.5) % 16（正南 180° → 8 → 南）。
 */
private fun sectorIndex(degrees: Int): Int {
    val normalized = ((degrees % 360) + 360) % 360
    return (normalized / 22.5).roundToInt() % 16
}

/** 气象角度（风的来向，0°=正北）转 16 方位中文名 */
fun windDirectionLabel(degrees: Int): String = COMPASS_16[sectorIndex(degrees)] + "风"

/**
 * 风向钓鱼评分（0-100）。
 *
 * 经验依据：东风/东南风多伴随水汽与溶氧改善，鱼口较好；
 * 南风与西南风常带来闷热低压，鱼口差；北风降温后通常转好但当下偏冷。
 */
fun scoreWindDirection(degrees: Int): Int = DIRECTION_SCORES[sectorIndex(degrees)]

private val DIRECTION_SCORES = intArrayOf(
    70,  // 北
    78,  // 北东北
    88,  // 东北
    88,  // 东东北
    95,  // 东
    92,  // 东东南
    85,  // 东南
    70,  // 南东南
    55,  // 南
    45,  // 南西南
    40,  // 西南
    45,  // 西西南
    55,  // 西
    65,  // 西西北
    72,  // 西北
    68   // 北西北
)

/**
 * 风力评分（0-100）。
 *
 * 2-3 级风最佳：既能搅动水面增加溶氧，又不影响抛竿与观漂；
 * 6 级以上抛竿困难且存在安全隐患。
 */
fun scoreWindSpeed(windSpeedKmh: Double): Int = when (beaufortLevel(windSpeedKmh)) {
    2, 3 -> 100
    1, 4 -> 78
    0, 5 -> 55
    6 -> 30
    7 -> 15
    else -> 5
}
