package com.hmoney.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 流體切換開關（FluidSwitch）：
 * - spring 物理動畫位移
 * - 按壓時 thumb 變形（squish 壓扁）
 * - 拖曳超過一半閾值才切換，放開回彈
 */
@Composable
fun FluidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackWidth = 56.dp
    val trackHeight = 32.dp
    val thumbSize = 24.dp
    val travel = trackWidth - thumbSize - 8.dp

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()

    // thumb 位置：spring 物理動畫
    val thumbOffset: Dp by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "thumbOffset",
    )

    // 拖曳進度（0f~1f），放開後以 spring 回彈
    val dragProgress = remember { Animatable(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }

    // 按壓 squish：壓力越大越扁
    val thumbWidth by animateDpAsState(
        targetValue = if (pressed) thumbSize + 4.dp else thumbSize,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "thumbWidth",
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (pressed) thumbSize - 3.dp else thumbSize,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "thumbHeight",
    )

    fun commitDrag(finalChecked: Boolean) {
        scope.launch {
            dragProgress.animateTo(
                if (finalChecked) 1f else 0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
            )
        }
        if (finalChecked != checked) onCheckedChange(finalChecked)
    }

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .background(
                color = if (checked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.4f,
                    )
                },
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                if (enabled) onCheckedChange(!checked)
            }
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
            .pointerInput(checked, enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        scope.launch {
                            dragProgress.snapTo(if (checked) 1f else 0f)
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val next = (dragProgress.value + dragAmount / travel.toPx())
                                .coerceIn(0f, 1f)
                            dragProgress.snapTo(next)
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        // 拖曳超過閾值（一半）才切換
                        commitDrag(dragProgress.value > 0.5f)
                    },
                    onDragCancel = {
                        dragging = false
                        commitDrag(checked)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .graphicsLayer {
                    val progress = if (dragging) dragProgress.value
                    else (thumbOffset / travel).coerceIn(0f, 1f)
                    translationX = travel.toPx() * progress
                }
                .size(thumbSize)
                .graphicsLayer {
                    // squish 變形：按壓時橫向拉長、縱向壓扁
                    scaleX = thumbWidth / thumbSize
                    scaleY = thumbHeight / thumbSize
                }
                .background(
                    color = if (checked) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape,
                ),
        )
    }
}
