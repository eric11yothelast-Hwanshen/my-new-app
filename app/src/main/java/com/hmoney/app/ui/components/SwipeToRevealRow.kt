package com.hmoney.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 滑出動作列的兩個錨點狀態 */
enum class RevealValue { Settled, Revealed }

private val RevealDistance = 160.dp
private val ActionAreaHorizontalPadding = 16.dp
private val ActionSpacing = 12.dp

/**
 * 滑出動作列：內容可向左滑到 Revealed 錨點停住，露出右側動作鈕（不會消失、不會自動彈回）。
 * 錨點設計：Settled = 0f、Revealed = -160dp（兩顆動作鈕＋間距＋左右邊距總寬）。
 * 單一展開由外部 revealed／onReveal／onCollapse 控制（同一時間只允許一列 Revealed）。
 * 請勿誤改：AnchoredDraggable 為水平手勢（Orientation.Horizontal），與垂直 LazyColumn 捲動不衝突；
 * 錨點僅兩個，勿加入「放開自動彈回」或「滑過門檻即消失」的邏輯（前者破壞停留操作、後者即 SwipeToDismissBox 的舊 bug）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeToRevealRow(
    revealed: Boolean,
    onReveal: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = remember { AnchoredDraggableState(RevealValue.Settled) }
    val revealPx = with(LocalDensity.current) { RevealDistance.toPx() }

    SideEffect {
        state.updateAnchors(
            DraggableAnchors {
                RevealValue.Settled at 0f
                RevealValue.Revealed at -revealPx
            },
        )
    }

    // 停在 Revealed 時回報外部（單一展開狀態由外部 state 管理）
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == RevealValue.Revealed) onReveal()
    }

    // 外部收回：點擊內容／列表空白處，或另一列展開時
    LaunchedEffect(revealed) {
        if (!revealed && state.currentValue == RevealValue.Revealed) {
            state.animateTo(RevealValue.Settled)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = ActionAreaHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(ActionSpacing, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
        }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        if (state.offset.isNaN()) 0 else state.offset.roundToInt(),
                        0,
                    )
                }
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = revealed,
                ) { onCollapse() }
                .anchoredDraggable(state, Orientation.Horizontal),
        ) {
            content()
        }
    }
}
