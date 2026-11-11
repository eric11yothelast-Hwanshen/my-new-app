package com.hmoney.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hmoney.app.R

/**
 * 設定頁：後續階段將加入預算、AI 連線、通知、備份與外觀設定。
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    SectionScaffold(
        modifier = modifier,
        titleRes = R.string.settings_title,
        descriptionRes = R.string.settings_description,
    )
}
