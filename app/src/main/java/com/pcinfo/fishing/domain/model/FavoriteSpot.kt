package com.pcinfo.fishing.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 收藏的钓点。
 *
 * 只存「坐标 + 名称」，不存天气与评分：每次切换都重新拉取气象，
 * 这样收藏夹不会因为数据过期而失真，也不需要维护本地时序库。
 *
 * @param id 唯一标识，取创建时刻的时间戳字符串
 * @param name 展示名称，默认用逆地理编码得到的地址
 * @param createdAtMs 收藏时间
 */
@Serializable
data class FavoriteSpot(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val createdAtMs: Long = 0L
) {

    /** 转成领域层坐标。收藏点没有定位精度概念，provider 标记为 favorite */
    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude, null, PROVIDER_FAVORITE)

    /** 与给定坐标的球面距离（米），用于判断「当前位置是否已在收藏夹里」 */
    fun distanceMetersTo(lat: Double, lon: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat - latitude)
        val dLon = Math.toRadians(lon - longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(lat)) * sin(dLon / 2).pow(2)
        return 2 * earthRadius * atan2(sqrt(a), sqrt(1 - a))
    }

    companion object {
        const val PROVIDER_FAVORITE = "favorite"

        /**
         * 判定「同一个钓点」的距离阈值。
         * 取 500 米是因为 Open-Meteo 的缓存键按 2 位小数（约 1km）聚合，
         * 500 米内重复收藏只会得到同一份天气数据，没有意义。
         */
        const val SAME_SPOT_METERS = 500.0
    }
}
