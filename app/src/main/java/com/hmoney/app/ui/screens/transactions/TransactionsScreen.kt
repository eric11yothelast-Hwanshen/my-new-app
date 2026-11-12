package com.hmoney.app.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.components.AiFloatingButton
import com.hmoney.app.ui.components.AiResultDialog
import com.hmoney.app.ui.components.EmptyState
import com.hmoney.app.ui.components.TransactionRow
import kotlinx.coroutines.launch
import com.hmoney.app.ui.components.glassSource
import com.hmoney.app.ui.screens.add.AddTransactionSheet
import com.hmoney.app.ui.screens.ai.AiAssistantSheet
import com.hmoney.app.ui.screens.ai.FeatureAiViewModel
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
    onGoSettings: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
    aiViewModel: FeatureAiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val aiState by aiViewModel.uiState.collectAsState()
    val glassState = rememberHazeState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editingId by remember { mutableStateOf<Long?>(null) }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    var showDateRange by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var aiQuery by remember { mutableStateOf("") }
    val aiFilterAppliedMessage = stringResource(R.string.ai_filter_applied)
    val deletedMessage = stringResource(R.string.transactions_deleted)

    // AI 篩選結果：轉成資料庫篩選條件並套用到清單
    LaunchedEffect(aiState) {
        when (val s = aiState) {
            is FeatureAiViewModel.UiState.Filter -> {
                s.suggestion.categoryName?.let { name ->
                    state.categories.values
                        .firstOrNull { it.name == name }
                        ?.let { viewModel.setCategory(it.id) }
                }
                s.suggestion.accountName?.let { name ->
                    state.accounts.firstOrNull { it.name == name }
                        ?.let { viewModel.setAccount(it.id) }
                }
                viewModel.setKeyword(s.suggestion.keyword ?: "")
                viewModel.setDateRange(
                    s.suggestion.startDateEpochMillis,
                    s.suggestion.endDateEpochMillis,
                )
                scope.launch { snackbarHostState.showSnackbar(aiFilterAppliedMessage) }
                aiViewModel.reset()
            }

            else -> Unit
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .glassSource(glassState),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
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

            // AI 自然語言搜尋／篩選
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = aiQuery,
                    onValueChange = { aiQuery = it },
                    placeholder = { Text(stringResource(R.string.ai_filter_hint)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        aiViewModel.interpretFilter(
                            query = aiQuery,
                            categoryNames = state.categories.values.map { it.name },
                            accountNames = state.accounts.map { it.name },
                        )
                    },
                    enabled = aiQuery.isNotBlank() &&
                        aiState !is FeatureAiViewModel.UiState.Loading,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(R.string.ai_filter_apply),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

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

            // 期間篩選與批次分析
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
                val expenseLabel = stringResource(R.string.add_type_expense)
                val incomeLabel = stringResource(R.string.add_type_income)
                val transferLabel = stringResource(R.string.add_type_transfer)
                val analyzeTitle = stringResource(R.string.ai_analyze_batch)
                AssistChip(
                    onClick = {
                        val data = state.groups
                            .flatMap { it.transactions }
                            .take(50)
                            .joinToString("\n") { tx ->
                                val typeLabel = when (tx.type) {
                                    TransactionType.EXPENSE -> expenseLabel
                                    TransactionType.INCOME -> incomeLabel
                                    TransactionType.TRANSFER -> transferLabel
                                }
                                val accountName = state.accounts
                                    .firstOrNull { it.id == tx.accountId }?.name ?: ""
                                val categoryName = state.categories[tx.categoryId]?.name ?: ""
                                "- ${Formats.date(tx.dateEpochMillis)} $typeLabel " +
                                    "$accountName $categoryName ${Formats.money(tx.amountCents)} ${tx.note}"
                            }
                        aiViewModel.runText(
                            title = analyzeTitle,
                            data = data,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = { Text(stringResource(R.string.ai_analyze_batch)) },
                )
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

        // 浮動 AI 按鈕（全域 AI 助手）
        AiFloatingButton(
            onClick = { showAiSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )

        // 刪除／AI 篩選回饋（Snackbar）
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }

    // 編輯
    editingId?.let { id ->
        AddTransactionSheet(
            onDismiss = { editingId = null },
            editingId = id,
            snackbarHostState = snackbarHostState,
        )
    }

    if (showAiSheet) {
        AiAssistantSheet(
            onDismiss = { showAiSheet = false },
            onGoSettings = {
                showAiSheet = false
                onGoSettings()
            },
        )
    }

    if (aiState !is FeatureAiViewModel.UiState.Idle &&
        aiState !is FeatureAiViewModel.UiState.Filter
    ) {
        AiResultDialog(
            state = aiState,
            onCancel = aiViewModel::cancel,
            onGoSettings = onGoSettings,
            onDismiss = aiViewModel::reset,
        )
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
                        scope.launch { snackbarHostState.showSnackbar(deletedMessage) }
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
            initialSelectedStartDateMillis = state.filters.startDate,
            initialSelectedEndDateMillis = state.filters.endDate,
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
                        // 期間以「日」為單位：起始日 00:00 ～結束日 23:59
                        val startMillis = pickerState.selectedStartDateMillis
                        val endMillis = pickerState.selectedEndDateMillis
                            ?.let { Formats.toLocalDate(it).plusDays(1)
                                .atStartOfDay(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli() - 1 }
                        viewModel.setDateRange(startMillis, endMillis)
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
