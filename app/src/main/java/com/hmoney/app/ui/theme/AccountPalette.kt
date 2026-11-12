package com.hmoney.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 帳戶固定色票（單一共用常數檔）：新增／編輯帳戶時可挑選的顏色，
 * 採 Material 400／600 色階，深色與淺色模式皆可讀；
 * 帳戶色以 colorArgb（Long）存於資料庫既有欄位，UI 端以 Color(colorArgb) 呈現。
 */
object AccountPalette {
    val colorArgbs: List<Long> = listOf(
        0xFF43A047,
        0xFF42A5F5,
        0xFF7E57C2,
        0xFFEF5350,
        0xFFFFB74D,
        0xFF26A69A,
        0xFFEC407A,
        0xFF5C6BC0,
        0xFFFFCA28,
        0xFF78909C,
    )

    val colors: List<Color> = colorArgbs.map { Color(it) }
}
