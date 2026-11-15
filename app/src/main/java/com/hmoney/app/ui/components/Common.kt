package com.hmoney.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hmoney.app.R
import com.hmoney.app.ui.common.Formats

/** 金額文字（本地化格式，含貨幣代號） */
@Composable
fun MoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    currency: String = "TWD",
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = Formats.moneyWithCurrency(cents, currency),
        modifier = modifier,
        style = style,
        color = color,
    )
}

/**
 * 數字滾動：餘額／金額變動時以 tween 動畫滾動至新值。
 */
@Composable
fun AnimatedAmountText(
    cents: Long,
    modifier: Modifier = Modifier,
    currency: String = "TWD",
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
) {
    // NaN 代表尚未收到第一個值：首次組合（含切回頁面）直接以當前值為起始，不重播動畫
    val animated = remember { Animatable(Float.NaN) }
    LaunchedEffect(cents) {
        if (animated.value.isNaN()) {
            animated.snapTo(cents.toFloat())
        } else {
            animated.animateTo(
                targetValue = cents.toFloat(),
                animationSpec = tween(durationMillis = 600),
            )
        }
    }
    val displayCents = if (animated.value.isNaN()) cents else animated.value.toLong()
    Text(
        text = Formats.moneyWithCurrency(displayCents, currency),
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        color = color,
    )
}

/** 空狀態：無資料時的引導插圖（icon）與文案 */
@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    messageRes: Int,
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
