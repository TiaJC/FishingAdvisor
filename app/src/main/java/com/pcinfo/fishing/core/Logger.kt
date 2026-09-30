package com.pcinfo.fishing.core

import android.util.Log

/**
 * 统一日志出口。
 *
 * 约定：绝不记录经纬度、设备标识等隐私数据；位置相关只记录精度量级。
 */
object Logger {

    private const val TAG = "FishingAdvisor"

    fun d(message: String) = Log.d(TAG, message)

    fun i(message: String) = Log.i(TAG, message)

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable == null) Log.w(TAG, message) else Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable == null) Log.e(TAG, message) else Log.e(TAG, message, throwable)
    }

    /**
     * 位置日志脱敏：只输出精度与来源，不输出坐标。
     */
    fun locationAcquired(provider: String, accuracyMeters: Float?) {
        val accuracy = accuracyMeters?.let { "%.0f".format(it) } ?: "unknown"
        d("location acquired: provider=$provider, accuracy=${accuracy}m")
    }
}
