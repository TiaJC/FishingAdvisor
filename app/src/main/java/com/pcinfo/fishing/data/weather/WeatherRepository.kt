package com.pcinfo.fishing.data.weather

import com.pcinfo.fishing.core.AppConfig
import com.pcinfo.fishing.core.Logger
import com.pcinfo.fishing.core.Outcome
import com.pcinfo.fishing.domain.model.GeoPoint
import com.pcinfo.fishing.domain.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 天气仓储：隔离数据源与领域层，并做进程内短缓存，避免频繁刷新打接口。
 */
class WeatherRepository(
    private val api: OpenMeteoApi = OpenMeteoApi()
) {

    @Volatile
    private var cached: WeatherSnapshot? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    @Volatile
    private var cachedKey: String? = null

    /**
     * 获取天气快照。
     *
     * @param forceRefresh 下拉刷新时传 true，绕过缓存
     */
    suspend fun getWeather(point: GeoPoint, forceRefresh: Boolean = false): Outcome<WeatherSnapshot> =
        withContext(Dispatchers.IO) {
            if (!point.isValid()) {
                return@withContext Outcome.Fail(
                    com.pcinfo.fishing.core.AppError.DataFormat("经纬度超出合法范围")
                )
            }

            val key = cacheKey(point)
            val now = System.currentTimeMillis()
            val hit = cached
            if (!forceRefresh && hit != null && cachedKey == key && now - cachedAtMs < AppConfig.WEATHER_CACHE_TTL_MS) {
                Logger.d("weather cache hit")
                return@withContext Outcome.Ok(hit)
            }

            Logger.i("fetching weather from open-meteo")
            when (val result = api.fetch(point.latitude, point.longitude)) {
                is Outcome.Ok -> {
                    cached = result.value
                    cachedAtMs = now
                    cachedKey = key
                    Outcome.Ok(result.value)
                }
                is Outcome.Fail -> {
                    Logger.w("weather fetch failed: ${result.error.message}")
                    Outcome.Fail(result.error)
                }
            }
        }

    /** 缓存键按 2 位小数（约 1km）聚合，避免微小位移造成重复请求 */
    private fun cacheKey(point: GeoPoint): String =
        "%.2f_%.2f".format(point.latitude, point.longitude)
}
