package com.pcinfo.fishing.core

/**
 * 类型化错误体系：所有跨层失败都收敛到这里，UI 只负责把 message 展示给用户，
 * 不向上泄漏堆栈、HTTP 细节或第三方 SDK 原始异常。
 */
sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** 用户拒绝或未授予定位权限 */
    data object PermissionDenied : AppError("未获得定位权限，无法分析当地气象")

    /** 系统定位关闭、超时或 provider 不可用 */
    data class LocationUnavailable(val detail: String) : AppError("无法获取当前位置（$detail）")

    /** 无网络、DNS 失败、超时 */
    data class NetworkUnavailable(val detail: String) : AppError("网络不可用，请检查连接后重试（$detail）")

    /** 天气服务返回非 2xx */
    data class ServerError(val httpCode: Int) : AppError("天气服务暂时不可用（HTTP $httpCode）")

    /** 返回体结构与预期不符 */
    data class DataFormat(val detail: String) : AppError("天气数据解析失败（$detail）")

    /** 兜底错误 */
    data class Unknown(val detail: String) : AppError("出现未知错误，请重试（$detail）")

    companion object {
        /** 把任意 Throwable 归一化为 AppError，便于在边界处统一兜底 */
        fun from(throwable: Throwable): AppError = when (throwable) {
            is AppError -> throwable
            is java.net.SocketTimeoutException -> NetworkUnavailable("请求超时")
            is java.net.UnknownHostException -> NetworkUnavailable("域名解析失败")
            is java.io.IOException -> NetworkUnavailable(throwable.message ?: "网络异常")
            else -> Unknown(throwable.message ?: throwable::class.java.simpleName)
        }
    }
}

/**
 * 轻量结果封装：避免使用标准库 Result 时丢失错误类型。
 */
sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Fail(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Fail -> this
}

inline fun <T> runOutcome(block: () -> T): Outcome<T> = try {
    Outcome.Ok(block())
} catch (t: Throwable) {
    Outcome.Fail(AppError.from(t))
}
