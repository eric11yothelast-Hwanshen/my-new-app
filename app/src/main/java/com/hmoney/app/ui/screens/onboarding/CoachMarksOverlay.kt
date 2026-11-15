package com.hmoney.app.ui.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hmoney.app.R
import kotlin.math.roundToInt

/**
 * S51：四條遮罩條 bounds（純函式，供單元測試）——
 * ①上條：(0,0) 起、寬＝螢幕寬、高＝hole.top；②下條：(0,hole.bottom) 起、寬＝螢幕寬、高＝螢幕高−hole.bottom；
 * ③左條：(0,hole.top) 起、寬＝hole.left、高＝hole.height；④右條：(hole.right,hole.top) 起、寬＝螢幕寬−hole.right、高＝hole.height。
 * bounds 一律夾在螢幕內（避免洞貼邊時出現負尺寸），洞內區域零覆蓋。
 */
internal fun coachMaskBarRects(screenWidth: Float, screenHeight: Float, hole: Rect): List<Rect> {
    val holeLeft = hole.left.coerceIn(0f, screenWidth)
    val holeRight = hole.right.coerceIn(0f, screenWidth)
    val holeTop = hole.top.coerceIn(0f, screenHeight)
    val holeBottom = hole.bottom.coerceIn(0f, screenHeight)
    return listOf(
        Rect(0f, 0f, screenWidth, holeTop),
        Rect(0f, holeBottom, screenWidth, screenHeight),
        Rect(0f, holeTop, holeLeft, holeBottom),
        Rect(holeRight, holeTop, screenWidth, holeBottom),
    ).filter { it.width > 0f && it.height > 0f }
}

/**
 * S50 實機點擊引導覆蓋層：半透明遮罩＋BlendMode.Clear 在目標 bounds（外擴 12dp）挖洞；
 * S51 修復：攔截層由「全畫面 Box＋洞內不消費」改為四條遮罩條——Compose 命中測試對重疊兄弟層
 * 預設獨占，全畫面攔截層被命中後下方 FAB／分頁項不在命中路徑上（不消費也不會收到事件），
 * 洞內必須零 pointerInput 節點才能讓底層真實元件成為最上命中者（真的可點、可滑）。
 * 指示文字卡＋「跳過引導」鈕放洞旁（目標在下半部時放洞上方）。
 */
@Composable
fun CoachMarksOverlay(
    step: Int,
    targetRect: Rect?,
    hint: String,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (targetRect == null) return
    val density = LocalDensity.current
    val holePadding = with(density) { 12.dp.toPx() }
    val hole = Rect(
        left = targetRect.left - holePadding,
        top = targetRect.top - holePadding,
        right = targetRect.right + holePadding,
        bottom = targetRect.bottom + holePadding,
    )
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()
        val scrimColor = Color.Black.copy(alpha = 0.6f)
        val holeLeft = hole.left
        val holeTop = hole.top
        val holeWidth = hole.width
        val holeHeight = hole.height
        val maskBars = remember(hole, screenWidthPx, screenHeightPx) {
            coachMaskBarRects(screenWidthPx, screenHeightPx, hole)
        }

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(scrimColor)
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(holeLeft, holeTop),
                size = Size(holeWidth, holeHeight),
                cornerRadius = CornerRadius(28f),
                blendMode = BlendMode.Clear,
            )
        }

        // 洞外攔截（四條遮罩條，洞內零覆蓋→底層 FAB／分頁項可直接被點）；點洞外顯示「點擊高亮處」效果不變
        maskBars.forEach { bar ->
            Box(
                modifier = Modifier
                    .absoluteOffset { IntOffset(bar.left.roundToInt(), bar.top.roundToInt()) }
                    .size(
                        width = with(density) { bar.width.toDp() },
                        height = with(density) { bar.height.toDp() },
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    },
            )
        }

        // 指示文字卡＋跳過鈕：放在洞上方（目標皆在畫面下半部）
        val cardWidth = 280.dp
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val hintX = (hole.center.x - cardWidthPx / 2f)
            .coerceIn(16f, (screenWidthPx - cardWidthPx - 16f).coerceAtLeast(16f))
        val hintY = (hole.top - with(density) { 132.dp.toPx() })
            .coerceAtLeast(with(density) { 24f })
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .align(Alignment.TopStart)
                .absoluteOffset { IntOffset(hintX.roundToInt(), hintY.roundToInt()) }
                .width(cardWidth),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.coach_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.coach_skip))
                    }
                }
            }
        }
    }
}
