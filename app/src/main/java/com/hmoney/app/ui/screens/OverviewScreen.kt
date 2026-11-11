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

/**
 * 總覽頁：後續階段將加入本月收支玻璃卡、帳戶餘額列、近期明細與快速記帳 FAB。
 */
@Composable
fun OverviewScreen(modifier: Modifier = Modifier) {
    SectionScaffold(
        modifier = modifier,
        titleRes = R.string.overview_title,
        descriptionRes = R.string.overview_description,
    )
}

/**
 * 骨架階段共用版面：標題＋說明，字串全部來自 strings.xml。
 */
@Composable
internal fun SectionScaffold(
    titleRes: Int,
    descriptionRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
