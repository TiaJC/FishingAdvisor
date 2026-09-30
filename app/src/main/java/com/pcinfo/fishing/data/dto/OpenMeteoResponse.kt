package com.pcinfo.fishing.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Open-Meteo 响应结构（列式数组）。
 *
 * 仅声明本应用用到的字段，其余一律忽略；数值字段使用可空类型，
 * 关键字段缺失时在数据层直接判定为 DataFormat 错误，避免拿 0 当真值。
 */
@Serializable
data class OpenMeteoResponse(
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("elevation") val elevation: Double? = null,
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    @SerialName("current") val current: CurrentBlock? = null,
    @SerialName("hourly") val hourly: HourlyBlock? = null,
    @SerialName("daily") val daily: DailyBlock? = null
)

@Serializable
data class CurrentBlock(
    @SerialName("time") val time: String? = null,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("precipitation") val precipitation: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("surface_pressure") val surfacePressure: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Int? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null
)

@Serializable
data class HourlyBlock(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("surface_pressure") val surfacePressure: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Int?> = emptyList(),
    @SerialName("precipitation") val precipitation: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList()
)

@Serializable
data class DailyBlock(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("sunrise") val sunrise: List<String?> = emptyList(),
    @SerialName("sunset") val sunset: List<String?> = emptyList()
)
