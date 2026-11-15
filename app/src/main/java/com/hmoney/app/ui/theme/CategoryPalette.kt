package com.hmoney.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 分類固定色盤（單一共用常數檔）：以分類名稱對應固定色，
 * 採 Material 400 色階，深色與淺色模式皆可讀；
 * 未收錄的自訂分類沿用資料庫既有的 colorArgb。
 */
object CategoryPalette {
    private val byName = mapOf(
        "餐飲" to Color(0xFFEF5350),
        "交通" to Color(0xFF5C6BC0),
        "購物" to Color(0xFFFFB74D),
        "娛樂" to Color(0xFFAB47BC),
        "居家" to Color(0xFF26A69A),
        "醫療" to Color(0xFFEC407A),
        "其他" to Color(0xFF78909C),
    )

    fun colorOf(name: String, fallbackArgb: Long): Color = byName[name] ?: Color(fallbackArgb)

    /** 以分類名稱查固定色；未收錄（自訂分類）回傳 null，由呼叫端決定 fallback */
    fun colorOfNameOrNull(name: String): Color? = byName[name]
}
