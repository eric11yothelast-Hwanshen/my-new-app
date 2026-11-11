package com.hmoney.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hmoney.app.R

/**
 * 帳戶頁：後續階段將加入帳戶卡片、即時餘額與帳戶管理。
 */
@Composable
fun AccountsScreen(modifier: Modifier = Modifier) {
    SectionScaffold(
        modifier = modifier,
        titleRes = R.string.accounts_title,
        descriptionRes = R.string.accounts_description,
    )
}
