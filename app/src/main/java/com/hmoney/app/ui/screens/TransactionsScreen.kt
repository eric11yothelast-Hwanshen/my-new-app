package com.hmoney.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hmoney.app.R

/**
 * 明細頁：後續階段將加入按日分組的交易列表、搜尋與篩選。
 */
@Composable
fun TransactionsScreen(modifier: Modifier = Modifier) {
    SectionScaffold(
        modifier = modifier,
        titleRes = R.string.transactions_title,
        descriptionRes = R.string.transactions_description,
    )
}
