package com.pcinfo.fishing.data.weather

import com.pcinfo.fishing.core.AppConfig
import com.pcinfo.fishing.core.AppError
import com.pcinfo.fishing.core.Logger
import com.pcinfo.fishing.core.Outcome
import com.pcinfo.fishing.core.runOutcome
import com.pcinfo.fishing.data.dto.OpenMeteoResponse
import com.pcinfo.fishing.domain.model.HourlyWeather
import com.pcinfo.fishing.domain.model.WeatherSnapshot
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * Open-Meteo 数据源：负责发请求、校验响应、把 DTO 映射为领域模型。
 *
 * 该服务为免密钥公开 API，因此无需任何敏感配置。
 */
class OpenMeteoApi(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson()
) {

    /** 拉取「当前 + 历史 6 小时 + 未来 30 小时 + 当日日出日落」 */
    fun fetch(latitude: Double, longitude: Double): Outcome<WeatherSnapshot> {
        if (!isCoordinateValid(latitude, longitude)) {
            return Outcome.Fail(AppError.DataFormat("经纬度超出合法范围"))
        }

        val url = runOutcome {
            AppConfig.OPEN_METEO_BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("latitude", latitude.toString())
                .addQueryParameter("longitude", longitude.toString())
                .addQueryParameter("current", CURRENT_FIELDS)
                .addQueryParameter("hourly", HOURLY_FIELDS)
                .addQueryParameter("daily", DAILY_FIELDS)
                .addQueryParameter("past_hours", AppConfig.PAST_HOURS.toString())
                .addQueryParameter("forecast_hours", AppConfig.FORECAST_HOURS.toString())
                .addQueryParameter("timezone", "auto")
                .build()
        }.getOrElse { return Outcome.Fail(it) }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        val response = runOutcome { client.newCall(request).execute() }
            .getOrElse { return Outcome.Fail(it) }

        return response.use { resp ->
            if (!resp.isSuccessful) {
                Logger.w("open-meteo http ${resp.code}")
                return Outcome.Fail(AppError.ServerError(resp.code))
            }
            val body = runOutcome { resp.body?.string() }
                .getOrElse { return Outcome.Fail(it) }
            if (body.isNullOrBlank()) {
                return Outcome.Fail(AppError.DataFormat("响应为空"))
            }
            parse(body)
        }
    }

    /** 解析并映射为领域模型 */
    internal fun parse(raw: String): Outcome<WeatherSnapshot> {
        val dto = runOutcome { json.decodeFromString<OpenMeteoResponse>(raw) }
            .getOrElse { return Outcome.Fail(it) }

        val current = dto.current
            ?: return Outcome.Fail(AppError.DataFormat("缺少 current 字段"))
        val hourly = dto.hourly
            ?: return Outcome.Fail(AppError.DataFormat("缺少 hourly 字段"))
        val offset = dto.utcOffsetSeconds ?: 0

        val pressure = current.surfacePressure
            ?: return Outcome.Fail(AppError.DataFormat("缺少气压数据"))
        val windSpeed = current.windSpeed ?: 0.0
        val observedAt = current.time?.let { parseLocalToEpoch(it, offset) }
            ?: return Outcome.Fail(AppError.DataFormat("缺少观测时间"))

        val sunrise = dto.daily?.sunrise?.firstOrNull { !it.isNullOrBlank() }
            ?.let { parseLocalToEpoch(it, offset) }
        val sunset = dto.daily?.sunset?.firstOrNull { !it.isNullOrBlank() }
            ?.let { parseLocalToEpoch(it, offset) }

        val points = mutableListOf<HourlyWeather>()
        hourly.time.forEachIndexed { index, timeText ->
            val epoch = parseLocalToEpoch(timeText, offset) ?: return@forEachIndexed
            val pointPressure = hourly.surfacePressure.getOrNull(index) ?: return@forEachIndexed
            points += HourlyWeather(
                epochMs = epoch,
                temperatureC = hourly.temperature.getOrNull(index) ?: current.temperature ?: 0.0,
                pressureHpa = pointPressure,
                windSpeedKmh = hourly.windSpeed.getOrNull(index) ?: windSpeed,
                windDirectionDeg = hourly.windDirection.getOrNull(index) ?: current.windDirection ?: 0,
                precipitationMm = hourly.precipitation.getOrNull(index) ?: 0.0,
                precipitationProbabilityPercent = hourly.precipitationProbability.getOrNull(index) ?: 0,
                cloudCoverPercent = hourly.cloudCover.getOrNull(index) ?: 0,
                weatherCode = hourly.weatherCode.getOrNull(index) ?: 0
            )
        }
        if (points.isEmpty()) {
            return Outcome.Fail(AppError.DataFormat("hourly 序列为空"))
        }

        return Outcome.Ok(
            WeatherSnapshot(
                observedAtEpochMs = observedAt,
                temperatureC = current.temperature ?: points.first().temperatureC,
                apparentTemperatureC = current.apparentTemperature ?: current.temperature ?: 0.0,
                humidityPercent = current.humidity ?: 0,
                precipitationMm = current.precipitation ?: 0.0,
                weatherCode = current.weatherCode ?: 0,
                pressureHpa = pressure,
                windSpeedKmh = windSpeed,
                windGustKmh = current.windGusts ?: windSpeed,
                windDirectionDeg = current.windDirection ?: 0,
                cloudCoverPercent = current.cloudCover ?: 0,
                elevationMeters = dto.elevation,
                timeZoneId = dto.timezone ?: "UTC",
                utcOffsetSeconds = offset,
                sunriseEpochMs = sunrise,
                sunsetEpochMs = sunset,
                hourly = points.sortedBy { it.epochMs }
            )
        )
    }

    /**
     * Open-Meteo 返回的是「当地时间」字符串（无时区后缀），
     * 需结合 utc_offset_seconds 换算为 epoch 毫秒。
     */
    private fun parseLocalToEpoch(text: String, utcOffsetSeconds: Int): Long? = try {
        val parsed = LocalDateTime.parse(text)
        (parsed.toEpochSecond(ZoneOffset.UTC) - utcOffsetSeconds) * 1000L
    } catch (t: Throwable) {
        Logger.w("unparsable time: $text", t)
        null
    }

    private fun isCoordinateValid(latitude: Double, longitude: Double): Boolean =
        latitude in AppConfig.MIN_LATITUDE..AppConfig.MAX_LATITUDE &&
            longitude in AppConfig.MIN_LONGITUDE..AppConfig.MAX_LONGITUDE

    private inline fun <T> Outcome<T>.getOrElse(onFail: (AppError) -> Nothing): T = when (this) {
        is Outcome.Ok -> value
        is Outcome.Fail -> onFail(error)
    }

    companion object {
        private const val CURRENT_FIELDS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation," +
                "weather_code,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover"
        private const val HOURLY_FIELDS =
            "temperature_2m,surface_pressure,wind_speed_10m,wind_direction_10m," +
                "precipitation,precipitation_probability,cloud_cover,weather_code"
        private const val DAILY_FIELDS = "sunrise,sunset"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(AppConfig.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(AppConfig.HTTP_TIMEOUT_SECONDS + 5L, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
