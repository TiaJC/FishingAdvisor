package com.pcinfo.fishing.data.settings

import android.content.Context
import com.pcinfo.fishing.domain.model.AppSettings
import com.pcinfo.fishing.domain.model.EngineOptions
import com.pcinfo.fishing.domain.model.FishSpecies
import com.pcinfo.fishing.domain.model.ScoreWeights
import com.pcinfo.fishing.domain.model.ThemeMode

/**
 * 设置持久化。
 *
 * 用 SharedPreferences 而非 DataStore：设置项只有十几个、读写频率极低，
 * 引入 DataStore 会多一个依赖与一层协程包装，收益不成比例。
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val weights = ScoreWeights(
            pressure = readInt(K_W_PRESSURE, ScoreWeights.DEFAULT.pressure),
            pressureTrend = readInt(K_W_TREND, ScoreWeights.DEFAULT.pressureTrend),
            windSpeed = readInt(K_W_WIND_SPEED, ScoreWeights.DEFAULT.windSpeed),
            windDirection = readInt(K_W_WIND_DIR, ScoreWeights.DEFAULT.windDirection),
            temperature = readInt(K_W_TEMP, ScoreWeights.DEFAULT.temperature),
            precipitation = readInt(K_W_PRECIP, ScoreWeights.DEFAULT.precipitation),
            dayTime = readInt(K_W_DAY_TIME, ScoreWeights.DEFAULT.dayTime)
        )
        val options = EngineOptions(
            useEstimatedWaterTemp = prefs.getBoolean(K_OPT_WATER_TEMP, true),
            useWindDirectionScoring = prefs.getBoolean(K_OPT_WIND_DIR, true),
            useOxygenToleranceAdjust = prefs.getBoolean(K_OPT_OXYGEN, true)
        )
        return AppSettings(
            weights = weights,
            options = options,
            species = FishSpecies.fromId(prefs.getString(K_SPECIES, null) ?: FishSpecies.ANY.name),
            themeMode = ThemeMode.valueOf(prefs.getString(K_THEME, null) ?: ThemeMode.SYSTEM.name),
            autoRefreshOnStart = prefs.getBoolean(K_AUTO_REFRESH, true),
            timelineSpanHours = readInt(K_SPAN, 24),
            showRawLocation = prefs.getBoolean(K_RAW_LOCATION, true),
            showHourlyTable = prefs.getBoolean(K_HOURLY_TABLE, true)
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putInt(K_W_PRESSURE, settings.weights.pressure)
            .putInt(K_W_TREND, settings.weights.pressureTrend)
            .putInt(K_W_WIND_SPEED, settings.weights.windSpeed)
            .putInt(K_W_WIND_DIR, settings.weights.windDirection)
            .putInt(K_W_TEMP, settings.weights.temperature)
            .putInt(K_W_PRECIP, settings.weights.precipitation)
            .putInt(K_W_DAY_TIME, settings.weights.dayTime)
            .putBoolean(K_OPT_WATER_TEMP, settings.options.useEstimatedWaterTemp)
            .putBoolean(K_OPT_WIND_DIR, settings.options.useWindDirectionScoring)
            .putBoolean(K_OPT_OXYGEN, settings.options.useOxygenToleranceAdjust)
            .putString(K_SPECIES, settings.species.name)
            .putString(K_THEME, settings.themeMode.name)
            .putBoolean(K_AUTO_REFRESH, settings.autoRefreshOnStart)
            .putInt(K_SPAN, settings.timelineSpanHours)
            .putBoolean(K_RAW_LOCATION, settings.showRawLocation)
            .putBoolean(K_HOURLY_TABLE, settings.showHourlyTable)
            .apply()
    }

    private fun readInt(key: String, fallback: Int): Int =
        if (prefs.contains(key)) prefs.getInt(key, fallback) else fallback

    private companion object {
        const val PREF_NAME = "fishing_settings"
        const val K_W_PRESSURE = "w_pressure"
        const val K_W_TREND = "w_pressure_trend"
        const val K_W_WIND_SPEED = "w_wind_speed"
        const val K_W_WIND_DIR = "w_wind_direction"
        const val K_W_TEMP = "w_temperature"
        const val K_W_PRECIP = "w_precipitation"
        const val K_W_DAY_TIME = "w_day_time"
        const val K_OPT_WATER_TEMP = "opt_water_temp"
        const val K_OPT_WIND_DIR = "opt_wind_direction"
        const val K_OPT_OXYGEN = "opt_oxygen"
        const val K_SPECIES = "species"
        const val K_THEME = "theme"
        const val K_AUTO_REFRESH = "auto_refresh"
        const val K_SPAN = "timeline_span"
        const val K_RAW_LOCATION = "raw_location"
        const val K_HOURLY_TABLE = "hourly_table"
    }
}
