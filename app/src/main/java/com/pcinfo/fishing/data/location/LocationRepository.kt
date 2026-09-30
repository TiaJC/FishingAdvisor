package com.pcinfo.fishing.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pcinfo.fishing.core.AppConfig
import com.pcinfo.fishing.core.AppError
import com.pcinfo.fishing.core.Logger
import com.pcinfo.fishing.core.Outcome
import com.pcinfo.fishing.domain.model.GeoPoint
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 定位仓储接口：领域层只依赖接口，便于单测替换。
 */
interface LocationRepository {
    suspend fun currentLocation(): Outcome<GeoPoint>

    /** 是否已授予定位权限（仅做检查，不发起申请） */
    fun hasPermission(): Boolean
}

/**
 * 定位实现，通道优先级：**GPS → 网络 → GMS 融合**。
 *
 * 排序理由：
 * 1. GPS 精度最高，钓点级气压/风场数据值得优先争取；
 * 2. 网络定位在室内与城市环境可用性更好，作为第二选择；
 * 3. GMS 融合定位只在装有 Google Play 服务的设备上可用，放最后兜底，
 *    保证无 GMS 的国产设备同样能拿到位置。
 *
 * 每个通道都先取系统缓存位置（快且省电），缓存缺失或过旧才发起实时定位，
 * 并各自带超时，避免用户在室内长时间等待 GPS 搜星。
 */
class AndroidLocationRepository(
    private val context: Context,
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
) : LocationRepository {

    override fun hasPermission(): Boolean = REQUIRED_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun currentLocation(): Outcome<GeoPoint> {
        if (!hasPermission()) {
            Logger.w("location permission missing")
            return Outcome.Fail(AppError.PermissionDenied)
        }

        withTimeoutOrNull(AppConfig.LOCATION_GPS_TIMEOUT_MS) {
            fetchFromProvider(LocationManager.GPS_PROVIDER)
        }?.let { return Outcome.Ok(it) }

        Logger.d("gps unavailable, fallback to network provider")
        withTimeoutOrNull(AppConfig.LOCATION_NETWORK_TIMEOUT_MS) {
            fetchFromProvider(LocationManager.NETWORK_PROVIDER)
        }?.let { return Outcome.Ok(it) }

        Logger.w("network unavailable, fallback to fused provider")
        withTimeoutOrNull(AppConfig.LOCATION_FUSED_TIMEOUT_MS) { fetchFromFused() }
            ?.let { return Outcome.Ok(it) }

        return Outcome.Fail(AppError.LocationUnavailable("定位服务不可用，请开启 GPS 或网络定位"))
    }

    /** 单个系统 Provider 的取位流程：缓存优先，其次实时请求 */
    @SuppressLint("MissingPermission")
    private suspend fun fetchFromProvider(provider: String): GeoPoint? {
        val manager = locationManager() ?: return null
        if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) return null

        val cached = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        if (cached != null && cached.isFresh()) {
            Logger.locationAcquired("cached:$provider", cached.accuracy)
            return cached.toGeoPoint(provider)
        }
        return requestPlatformUpdate(manager, provider)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestPlatformUpdate(
        manager: LocationManager,
        provider: String
    ): GeoPoint? = suspendCancellableCoroutine { cont ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { manager.removeUpdates(this) }
                Logger.locationAcquired(provider, location.accuracy)
                cont.resumeIfActive(location.toGeoPoint(provider))
            }
        }
        cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }

        try {
            manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
        } catch (security: SecurityException) {
            Logger.w("platform missing permission", security)
            cont.resumeIfActive(null)
        } catch (t: Throwable) {
            Logger.w("platform requestUpdates failed", t)
            cont.resumeIfActive(null)
        }
    }

    /** 兜底通道：GMS 融合定位 */
    private suspend fun fetchFromFused(): GeoPoint? =
        lastKnownFromFused()?.takeIf { it.isFresh() }?.toGeoPoint(PROVIDER_FUSED)
            ?: requestFusedUpdate()?.toGeoPoint(PROVIDER_FUSED)

    private suspend fun lastKnownFromFused(): Location? = suspendCancellableCoroutine { cont ->
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { location -> cont.resumeIfActive(location) }
                .addOnFailureListener { cont.resumeIfActive(null) }
        } catch (t: Throwable) {
            Logger.w("fused lastLocation failed", t)
            cont.resumeIfActive(null)
        }
    }

    private suspend fun requestFusedUpdate(): Location? = suspendCancellableCoroutine { cont ->
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            AppConfig.LOCATION_UPDATE_INTERVAL_MS
        ).setMaxUpdates(1).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                runCatching { fusedClient.removeLocationUpdates(this) }
                Logger.locationAcquired(PROVIDER_FUSED, result.lastLocation?.accuracy)
                cont.resumeIfActive(result.lastLocation)
            }
        }
        cont.invokeOnCancellation { runCatching { fusedClient.removeLocationUpdates(callback) } }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (security: SecurityException) {
            Logger.w("fused missing permission", security)
            cont.resumeIfActive(null)
        } catch (t: Throwable) {
            Logger.w("fused requestUpdates failed", t)
            cont.resumeIfActive(null)
        }
    }

    private fun locationManager(): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private fun Location.isFresh(): Boolean =
        System.currentTimeMillis() - time <= AppConfig.LOCATION_FRESHNESS_MS

    private fun Location.toGeoPoint(providerName: String): GeoPoint = GeoPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        provider = providerName
    )

    private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
        if (isActive) resume(value)
    }

    companion object {
        private val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        const val PROVIDER_FUSED = "fused"
    }
}
