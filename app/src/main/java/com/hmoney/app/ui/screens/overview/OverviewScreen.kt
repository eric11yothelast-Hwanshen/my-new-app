package com.hmoney.app.ui.screens.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.common.IconMapper
import com.hmoney.app.ui.components.AnimatedAmountText
import com.hmoney.app.ui.components.EmptyState
import com.hmoney.app.ui.components.GlassCard
import com.hmoney.app.ui.components.TransactionRow
import com.hmoney.app.ui.components.glassSource
import com.hmoney.app.ui.screens.add.AddTransactionSheet
import dev.chrisbanes.haze.rememberHazeState

/**
 * 總覽頁：本月收支玻璃卡、總餘額（數字滾動）、帳戶餘額列、近期明細與快速記帳 FAB。
 */
@Composable
fun OverviewScreen(
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    val glassState = rememberHazeState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .glassSource(glassState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.overview_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(R.string.overview_total_balance),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedAmountText(cents = state.totalBalanceCents)
                }
            }

            // 本月收支玻璃卡
            item {
                GlassCard(
                    glassState = glassState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.overview_month_summary),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.overview_month_expense),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = Formats.moneyWithCurrency(state.monthExpenseCents),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.overview_month_income),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = Formats.moneyWithCurrency(state.monthIncomeCents),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }

            // 帳戶餘額列
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.overview_accounts_section),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.accountsWithBalance, key = { it.first.id }) { (account, balance) ->
                            GlassCard(
                                glassState = glassState,
                                contentPadding = 12.dp,
                                cornerRadius = 16.dp,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = IconMapper.iconOf(account.iconKey),
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color(account.colorArgb),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column {
                                        Text(
                                            text = account.name,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        Text(
                                            text = Formats.moneyWithCurrency(
                                                balance,
                                                account.currency,
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 近期明細
            item {
                Text(
                    text = stringResource(R.string.overview_recent_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            if (state.recentTransactions.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        messageRes = R.string.overview_empty,
                    )
                }
            } else {
                items(state.recentTransactions, key = { it.id }) { tx ->
                    TransactionRow(
                        transaction = tx,
                        category = state.categories[tx.categoryId],
                        accountName = state.accountsWithBalance
                            .firstOrNull { it.first.id == tx.accountId }?.first?.name,
                        toAccountName = state.accountsWithBalance
                            .firstOrNull { it.first.id == tx.toAccountId }?.first?.name,
                    )
                }
            }
        }

        // 快速記帳 FAB
        ExtendedFloatingActionButton(
            onClick = { showAddSheet = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.overview_add)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }

    if (showAddSheet) {
        AddTransactionSheet(onDismiss = { showAddSheet = false })
    }
}
