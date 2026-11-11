package com.hmoney.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hmoney.app.R

/** 各主分頁的浮動 AI 按鈕（開啟全域 AI 助手） */
@Composable
fun AiFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = stringResource(R.string.ai_fab),
        )
    }
}
