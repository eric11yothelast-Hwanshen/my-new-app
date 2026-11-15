package com.hmoney.app.domain.model

/** 深色模式選項 */
enum class DarkMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** 外觀設定：深色模式與 Material You 動態色彩 */
data class AppearanceSettings(
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val dynamicColor: Boolean = true,
)
