package com.pcinfo.fishing.domain.model

/**
 * 领域层数据模型：与具体 API 结构解耦，数据层负责把 Open-Meteo 的响应映射到这些模型。
 */

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val provider: String
) {
    /** 边界校验：拒绝非法坐标，避免把脏数据传给下游服务 */
    fun isValid(): Boolean = com.pcinfo.fishing.core.AppConfig.let { cfg ->
        latitude in cfg.MIN_LATITUDE..cfg.MAX_LATITUDE &&
            longitude in cfg.MIN_LONGITUDE..cfg.MAX_LONGITUDE
    }
}

/** 逐小时气象点，用于气压趋势与最佳窗口计算 */
data class HourlyWeather(
    val epochMs: Long,
    val temperatureC: Double,
    val pressureHpa: Double,
    val windSpeedKmh: Double,
    val windDirectionDeg: Int,
    val precipitationMm: Double,
    val precipitationProbabilityPercent: Int,
    val cloudCoverPercent: Int,
    /** WMO 天气代码，用于展示逐小时天气现象 */
    val weatherCode: Int = 0
)

/** 某个时刻的完整气象快照 */
data class WeatherSnapshot(
    val observedAtEpochMs: Long,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val humidityPercent: Int,
    val precipitationMm: Double,
    val weatherCode: Int,
    val pressureHpa: Double,
    val windSpeedKmh: Double,
    val windGustKmh: Double,
    val windDirectionDeg: Int,
    val cloudCoverPercent: Int,
    val elevationMeters: Double?,
    val timeZoneId: String,
    val utcOffsetSeconds: Int,
    val sunriseEpochMs: Long?,
    val sunsetEpochMs: Long?,
    val hourly: List<HourlyWeather>
)
