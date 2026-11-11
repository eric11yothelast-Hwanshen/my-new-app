package com.hmoney.app.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.components.EmptyState
import com.hmoney.app.ui.components.TransactionRow
import com.hmoney.app.ui.components.glassSource
import com.hmoney.app.ui.screens.add.AddTransactionSheet
import dev.chrisbanes.haze.rememberHazeState
import java.time.Instant
import java.time.ZoneId

/**
 * 明細頁：完整交易列表＋搜尋（關鍵字）＋篩選（期間、帳戶、分類、類型），
 * 按日分組顯示、當日小計，單筆可編輯／刪除（刪除需確認對話框）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val glassState = rememberHazeState()

    var editingId by remember { mutableStateOf<Long?>(null) }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    var showDateRange by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .glassSource(glassState),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.transactions_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            OutlinedTextField(
                value = state.filters.keyword,
                onValueChange = viewModel::setKeyword,
                placeholder = { Text(stringResource(R.string.transactions_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 類型篩選
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                FilterChip(
                    selected = state.filters.type == null,
                    onClick = { viewModel.setType(null) },
                    label = { Text(stringResource(R.string.transactions_filter_all)) },
                )
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = state.filters.type == type,
                        onClick = { viewModel.setType(type) },
                        label = {
                            Text(
                                stringResource(
                                    when (type) {
                                        TransactionType.EXPENSE -> R.string.add_type_expense
                                        TransactionType.INCOME -> R.string.add_type_income
                                        TransactionType.TRANSFER -> R.string.add_type_transfer
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            // 帳戶篩選
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.filters.accountId == null,
                        onClick = { viewModel.setAccount(null) },
                        label = {
                            Text(
                                stringResource(R.string.transactions_filter_account) +
                                    "：" + stringResource(R.string.transactions_filter_all),
                            )
                        },
                    )
                }
                items(state.accounts, key = { it.id }) { account ->
                    FilterChip(
                        selected = state.filters.accountId == account.id,
                        onClick = { viewModel.setAccount(account.id) },
                        label = { Text(account.name) },
                    )
                }
            }

            // 分類篩選
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.filters.categoryId == null,
                        onClick = { viewModel.setCategory(null) },
                        label = {
                            Text(
                                stringResource(R.string.transactions_filter_category) +
                                    "：" + stringResource(R.string.transactions_filter_all),
                            )
                        },
                    )
                }
                items(state.categories.values.sortedBy { it.id }, key = { it.id }) { category ->
                    FilterChip(
                        selected = state.filters.categoryId == category.id,
                        onClick = { viewModel.setCategory(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }

            // 期間篩選
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filters.startDate != null || state.filters.endDate != null,
                    onClick = { showDateRange = true },
                    label = {
                        val start = state.filters.startDate
                        val end = state.filters.endDate
                        Text(
                            if (start == null && end == null) {
                                stringResource(R.string.transactions_filter_date)
                            } else {
                                (start?.let { Formats.date(it) } ?: "—") +
                                    " ~ " +
                                    (end?.let { Formats.date(it) } ?: "—")
                            },
                        )
                    },
                )
                if (state.filters != TransactionsViewModel.Filters()) {
                    TextButton(onClick = viewModel::clearFilters) {
                        Text(stringResource(R.string.transactions_filter_all))
                    }
                }
            }
        }

        if (state.groups.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.ReceiptLong,
                messageRes = R.string.transactions_empty,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.groups.forEach { group ->
                    item(key = "header-${group.epochDay}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = Formats.date(
                                    java.time.LocalDate.ofEpochDay(group.epochDay),
                                ),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.transactions_day_total) +
                                    "：" +
                                    Formats.money(group.incomeCents - group.expenseCents),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(group.transactions, key = { it.id }) { tx ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TransactionRow(
                                transaction = tx,
                                category = state.categories[tx.categoryId],
                                accountName = state.accounts
                                    .firstOrNull { it.id == tx.accountId }?.name,
                                toAccountName = state.accounts
                                    .firstOrNull { it.id == tx.toAccountId }?.name,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { editingId = tx.id }) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.accounts_edit),
                                )
                            }
                            IconButton(onClick = { deletingId = tx.id }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.common_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 編輯
    editingId?.let { id ->
        AddTransactionSheet(onDismiss = { editingId = null }, editingId = id)
    }

    // 刪除確認
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.transactions_delete_title)) },
            text = { Text(stringResource(R.string.transactions_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransaction(id)
                        deletingId = null
                    },
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // 期間篩選對話框
    if (showDateRange) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDate = state.filters.startDate,
            initialSelectedEndDate = state.filters.endDate,
        )
        AlertDialog(
            onDismissRequest = { showDateRange = false },
            title = { Text(stringResource(R.string.transactions_filter_date)) },
            text = {
                DateRangePicker(
                    state = pickerState,
                    showModeToggle = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setDateRange(
                            pickerState.selectedStartDateMillis,
                            pickerState.selectedEndDateMillis,
                        )
                        showDateRange = false
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDateRange = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
