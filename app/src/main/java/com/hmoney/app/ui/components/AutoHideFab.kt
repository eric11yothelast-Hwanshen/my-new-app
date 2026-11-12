package com.hmoney.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val FabHideDistance = 200.dp

/**
 * 自動隱藏 FAB 容器：列表在頂端（canScrollBackward == false，未捲動）時顯示，
 * 離開頂端時以「滑出畫面＋淡出」動畫隱藏，滑回頂端恢復。
 * 請勿誤改：顯示條件為 !canScrollBackward（還能向上捲回＝已離開頂端）；
 * 勿改成 canScrollForward——內容比畫面長時，停在頂端 canScrollForward 也是 true，
 * 會導致 FAB 在頂端就被隱藏。隱藏時同時位移出畫面（200dp），確保不可誤觸。
 */
@Composable
fun AutoHideFabColumn(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visible = !listState.canScrollBackward
    val progress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(220),
        label = "fabHideProgress",
    )
    Column(
        modifier = modifier.graphicsLayer {
            alpha = 1f - progress
            translationY = progress * FabHideDistance.toPx()
        },
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}
