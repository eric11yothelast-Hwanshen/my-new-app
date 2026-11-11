package com.hmoney.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmoney.app.R
import com.hmoney.app.ui.components.glassSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 統計頁：圖表儀表板將於「統計、預算、週期交易」階段實作，
 * 目前先建立畫面骨架與說明。
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val glassState = rememberHazeState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .glassSource(glassState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.stats_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
