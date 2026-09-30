package com.pcinfo.fishing.ui.navigation

/** 底部导航的三个一级页面 */
enum class AppTab(
    val label: String,
    /** 图标占位字形：工程未引入 material-icons-extended，用 emoji 避免额外依赖 */
    val glyph: String,
    val description: String
) {
    HOME("首页", "🏠", "定位、气象与钓鱼指数"),
    KNOWLEDGE("百科", "📖", "算法、鱼种档案与数据来源"),
    SETTINGS("设置", "⚙️", "权重、开关与显示偏好")
}
