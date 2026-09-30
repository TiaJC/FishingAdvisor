package com.pcinfo.fishing.core

/**
 * 集中配置：所有可调参数集中在此，避免散落在代码里的魔法数字。
 * Open-Meteo 为免密钥公开 API，因此无需配置任何敏感信息。
 */
object AppConfig {

    /** 天气服务地址 */
    const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/v1/forecast"

    /** 逆地理编码服务（免密钥，支持中文） */
    const val REVERSE_GEOCODE_URL = "https://api.bigdatacloud.net/data/reverse-geocode-client"

    /** 地址解析结果缓存时长（毫秒） */
    const val PLACE_CACHE_TTL_MS = 10 * 60 * 1000L

    /** HTTP 连接/读取超时（秒） */
    const val HTTP_TIMEOUT_SECONDS = 15L

    /**
     * 用于计算气压趋势与水温估算的历史小时数。
     * 取 24 是因为水温估算需要一整天的气温均值作为季节基准，
     * 仅取 6 小时会被当日昼夜温差带偏。
     */
    const val PAST_HOURS = 24

    /** 请求的未来小时数（覆盖评分所需的 24 小时窗口） */
    const val FORECAST_HOURS = 30

    /** 单次定位总等待超时（毫秒） */
    const val LOCATION_TIMEOUT_MS = 20_000L

    /** GPS 通道等待上限（毫秒）：精度最高，优先尝试，室内可能拿不到 */
    const val LOCATION_GPS_TIMEOUT_MS = 8_000L

    /** 网络定位通道等待上限（毫秒）：室内可用，精度较低 */
    const val LOCATION_NETWORK_TIMEOUT_MS = 5_000L

    /** Google Play 服务融合定位通道等待上限（毫秒）：作为最后的兜底通道 */
    const val LOCATION_FUSED_TIMEOUT_MS = 5_000L

    /** 系统缓存位置被视为可用的最大「年龄」（毫秒），超过则主动请求一次新位置 */
    const val LOCATION_FRESHNESS_MS = 60_000L

    /** 主动定位时的更新间隔（毫秒） */
    const val LOCATION_UPDATE_INTERVAL_MS = 2_000L

    /** 天气结果在内存中的缓存时长（毫秒），避免频繁刷新打接口 */
    const val WEATHER_CACHE_TTL_MS = 5 * 60 * 1000L

    /** 合法经纬度范围，用于边界校验 */
    const val MIN_LATITUDE = -90.0
    const val MAX_LATITUDE = 90.0
    const val MIN_LONGITUDE = -180.0
    const val MAX_LONGITUDE = 180.0
}
