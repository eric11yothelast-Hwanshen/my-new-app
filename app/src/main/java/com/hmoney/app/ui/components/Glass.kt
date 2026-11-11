package com.hmoney.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource

/**
 * 玻璃態元件：API 33+ 使用 Haze 背景模糊＋高光描邊；
 * 低版本自動降級為半透明壓色（不呼叫進階 API，保證 API 26+ 不崩潰）。
 */

/** 標記為模糊來源（放在可捲動內容的最上層容器） */
fun Modifier.glassSource(state: HazeState): Modifier = this.hazeSource(state)

/**
 * 玻璃表面修飾詞：API 33+ 背景模糊；低版本半透明壓色＋描邊。
 */
fun Modifier.glassSurface(
    state: HazeState,
    fallbackColor: Color,
    outlineColor: Color = Color.White.copy(alpha = 0.25f),
    cornerRadius: Dp = 24.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip(shape)
            .hazeBlur(
                input = HazeInput.Sources(state),
                style = HazeMaterials.thin(),
            )
            .border(width = 1.dp, color = outlineColor, shape = shape)
    } else {
        clip(shape)
            .background(color = fallbackColor)
            .border(width = 1.dp, color = outlineColor, shape = shape)
    }
}

/**
 * 玻璃卡片容器：內容需先以 Modifier.glassSource(state) 標記為模糊來源。
 */
@Composable
fun GlassCard(
    glassState: HazeState,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.glassSurface(
            state = glassState,
            fallbackColor = androidx.compose.material3.MaterialTheme.colorScheme
                .surface.copy(alpha = 0.88f),
            cornerRadius = cornerRadius,
        ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}
