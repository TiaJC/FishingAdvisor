package com.pcinfo.fishing.ui

import com.pcinfo.fishing.domain.usecase.FishingReport

/** 界面状态：单向数据流，UI 只做渲染 */
sealed interface FishingUiState {

    /** 等待权限或首次触发 */
    data object Idle : FishingUiState

    /** 正在定位或拉取天气 */
    data class Loading(val hint: String) : FishingUiState

    /** 分析成功 */
    data class Success(val report: FishingReport, val updatedAtMs: Long) : FishingUiState

    /**
     * 失败：message 直接来自 AppError，已是可展示文案
     */
    data class Error(val message: String, val reason: ErrorReason) : FishingUiState
}

/** 失败原因，决定 UI 展示「去授权」还是「重试」 */
enum class ErrorReason {
    PERMISSION,
    LOCATION,
    NETWORK,
    UNKNOWN
}
