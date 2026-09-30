package com.pcinfo.fishing.ui.util

import android.location.LocationManager
import androidx.compose.ui.graphics.Color
import com.pcinfo.fishing.domain.model.FavoriteSpot
import java.util.Calendar
import java.util.Locale

/** 时间格式化 */
fun formatClock(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    return String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

fun formatDayLabel(epochMs: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = epochMs }
    val today = Calendar.getInstance()
    val isSameDay = target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val isTomorrow = target.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
        target.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)
    val prefix = when {
        isSameDay -> "今天"
        isTomorrow -> "明天"
        else -> "后天及以后"
    }
    return "$prefix ${formatClock(epochMs)}"
}

fun formatUpdatedAt(epochMs: Long): String = "更新于 ${formatClock(epochMs)}"

/** WMO 天气代码 → 中文描述 */
fun weatherCodeLabel(code: Int): String = when (code) {
    0 -> "晴"
    1 -> "晴间少云"
    2 -> "多云"
    3 -> "阴"
    45, 48 -> "雾"
    51, 53, 55 -> "毛毛雨"
    56, 57 -> "冻毛毛雨"
    61, 63, 65 -> "降雨"
    66, 67 -> "冻雨"
    71, 73, 75, 77 -> "降雪"
    80, 81, 82 -> "阵雨"
    85, 86 -> "阵雪"
    95 -> "雷阵雨"
    96, 99 -> "雷阵雨伴冰雹"
    else -> "未知天气"
}

/** 天气现象的归类色：用于时间轴上的天气色带 */
fun weatherColor(code: Int): Color = when (code) {
    0 -> Color(0xFFFFB300)                      // 晴
    1, 2 -> Color(0xFF90A4AE)                   // 少云 / 多云
    3 -> Color(0xFF607D8B)                      // 阴
    45, 48 -> Color(0xFFB0BEC5)                 // 雾
    51, 53, 55, 56, 57 -> Color(0xFF4FC3F7)     // 毛毛雨 / 冻雨
    61, 63, 65, 66, 67 -> Color(0xFF1E88E5)     // 雨
    71, 73, 75, 77, 85, 86 -> Color(0xFF81D4FA) // 雪
    80, 81, 82 -> Color(0xFF1565C0)             // 阵雨
    95, 96, 99 -> Color(0xFF8E24AA)             // 雷雨
    else -> Color(0xFF90A4AE)
}

/** 天气现象的简短归类名，用于图例等窄空间 */
fun weatherCategoryLabel(code: Int): String = when (code) {
    0 -> "晴"
    in 1..2 -> "多云"
    3 -> "阴"
    45, 48 -> "雾"
    in 51..67 -> "雨"
    in 71..77 -> "雪"
    in 80..82 -> "阵雨"
    in 85..86 -> "阵雪"
    95, 96, 99 -> "雷雨"
    else -> "未知"
}

/** 定位来源中文说明，便于判断结果可信度 */
fun providerLabel(provider: String): String = when (provider.lowercase()) {
    LocationManager.GPS_PROVIDER -> "GPS 卫星定位"
    LocationManager.NETWORK_PROVIDER -> "网络基站定位"
    "fused" -> "GMS 融合定位"
    FavoriteSpot.PROVIDER_FAVORITE -> "收藏钓点"
    else -> provider
}

/** 位置精度文案 */
fun accuracyLabel(meters: Float?): String = when {
    meters == null -> "精度未知"
    meters <= 30f -> "精度 %.0f 米（GPS 级）".format(meters)
    meters <= 500f -> "精度 %.0f 米".format(meters)
    else -> "精度约 %.1f 公里（建议开启 GPS）".format(meters / 1000.0)
}
