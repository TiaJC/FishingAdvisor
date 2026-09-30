package com.pcinfo.fishing.data.location

import android.content.Context
import android.location.Geocoder
import com.pcinfo.fishing.core.AppConfig
import com.pcinfo.fishing.core.Logger
import com.pcinfo.fishing.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 逆地理编码：把经纬度解析为中文地址（省 / 市 / 区）。
 *
 * 双通道设计的原因：系统 Geocoder 依赖设备内置的地理位置服务，
 * 国内大量 ROM 上会直接返回空列表，因此必须有 HTTP 兜底通道。
 */
interface PlaceNameRepository {
    suspend fun resolve(point: GeoPoint): String?
}

class AndroidPlaceNameRepository(
    private val context: Context,
    private val client: OkHttpClient = defaultClient()
) : PlaceNameRepository {

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedValue: String? = null

    override suspend fun resolve(point: GeoPoint): String? {
        val key = "%.3f_%.3f".format(point.latitude, point.longitude)
        if (cachedKey == key) return cachedValue

        val resolved = withContext(Dispatchers.IO) {
            fromSystemGeocoder(point) ?: fromRemoteService(point)
        }
        if (!resolved.isNullOrBlank()) {
            cachedKey = key
            cachedValue = resolved
        }
        Logger.d("place resolved: $resolved")
        return resolved
    }

    /** 通道一：系统 Geocoder（无需额外网络通道，但国内设备常返回空） */
    private fun fromSystemGeocoder(point: GeoPoint): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.SIMPLIFIED_CHINESE)
                .getFromLocation(point.latitude, point.longitude, 1)
            val address = addresses?.firstOrNull() ?: return null
            listOfNotNull(
                address.adminArea,
                address.locality ?: address.subAdminArea,
                address.subLocality ?: address.thoroughfare
            ).distinct().joinToString(" ").nullIfBlank()
        }.onFailure { Logger.w("system geocoder failed", it) }.getOrNull()
    }

    /** 通道二：BigDataCloud 免密钥逆地理编码，localityLanguage=zh 返回中文 */
    private fun fromRemoteService(point: GeoPoint): String? = runCatching {
        val url = AppConfig.REVERSE_GEOCODE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", point.latitude.toString())
            .addQueryParameter("longitude", point.longitude.toString())
            .addQueryParameter("localityLanguage", "zh")
            .build()

        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.w("reverse geocode http ${response.code}")
                return null
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val json = Json.parseToJsonElement(body).jsonObject
            val parts = listOf(
                json["principalSubdivision"]?.jsonPrimitive?.content,
                json["city"]?.jsonPrimitive?.content,
                json["locality"]?.jsonPrimitive?.content
            ).filter { !it.isNullOrBlank() }.distinct()
            parts.joinToString(" ").nullIfBlank()
        }
    }.onFailure { Logger.w("reverse geocode failed", it) }.getOrNull()

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(AppConfig.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}

private fun String.nullIfBlank(): String? = if (isBlank()) null else this
