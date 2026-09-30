package com.pcinfo.fishing.domain.model

/** 主题模式 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

/** 时间轴默认跨度（小时） */
enum class TimelineSpan(val hours: Int, val label: String) {
    H12(12, "12 小时"),
    H24(24, "24 小时"),
    H36(36, "36 小时"),
    H48(48, "48 小时");

    companion object {
        fun fromHours(hours: Int): TimelineSpan =
            entries.firstOrNull { it.hours == hours } ?: H24
    }
}

/**
 * 应用级设置：评分权重、引擎开关与界面偏好。
 * 由 SettingsStore 持久化到 SharedPreferences。
 */
data class AppSettings(
    val weights: ScoreWeights = ScoreWeights(),
    val options: EngineOptions = EngineOptions(),
    /** 上次选择的目标鱼种 */
    val species: FishSpecies = FishSpecies.ANY,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 启动时自动定位并刷新 */
    val autoRefreshOnStart: Boolean = true,
    /** 首页时间轴默认显示的小时跨度 */
    val timelineSpanHours: Int = TimelineSpan.H24.hours,
    /** 是否展示经纬度等原始定位信息 */
    val showRawLocation: Boolean = true,
    /** 是否显示 24 小时逐小时明细表（关闭后只保留组合时间轴） */
    val showHourlyTable: Boolean = true
)
