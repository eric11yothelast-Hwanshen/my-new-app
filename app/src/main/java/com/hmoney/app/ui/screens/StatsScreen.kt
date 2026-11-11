package com.hmoney.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hmoney.app.R

/**
 * 統計頁：後續階段將加入圖表儀表板（占比、趨勢、排行榜）。
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    SectionScaffold(
        modifier = modifier,
        titleRes = R.string.stats_title,
        descriptionRes = R.string.stats_description,
    )
}
