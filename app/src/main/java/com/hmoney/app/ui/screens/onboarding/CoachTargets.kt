package com.hmoney.app.ui.screens.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * S50 實機點擊引導目標座標容器：真實元件以 onGloballyPositioned（boundsInRoot）回報位置，
 * 畫面旋轉／字級變化時自動更新。僅供觀察掛鉤寫入、覆蓋層讀取——
 * 嚴禁把 coach 步驟邏輯塞進 FAB 或分頁列內部。
 */
class CoachTargets {
    var fabBounds: Rect? by mutableStateOf(null)
    var tabBarBounds: Rect? by mutableStateOf(null)
}
