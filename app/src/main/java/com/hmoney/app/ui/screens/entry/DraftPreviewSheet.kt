@file:OptIn(ExperimentalMaterial3Api::class)

package com.hmoney.app.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import com.hmoney.app.ui.components.FullWidthDatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmoney.app.R
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.ui.common.AmountParser
import com.hmoney.app.ui.common.Formats

/**
 * 草稿預覽 Sheet（S41 獨立新元件，與 AddTransactionSheet 無結構耦合）：
 * 列出草稿（金額／備註／分類／帳戶／日期），逐筆可編輯與剔除；
 * 「全部確認」批次寫入——寫入唯一入口＝此按鈕（鐵律：確認前嚴禁寫入 DB）。
 * skipHalfExpanded；草稿清單以 heightIn 有限高度約束（單一捲動容器鐵律）。
 */
@Composable
fun DraftPreviewSheet(
    state: EntryViewModel.UiState,
    onAmountChange: (MoneyTransaction, Long) -> Unit,
    onNoteChange: (MoneyTransaction, String) -> Unit,
    onCategoryChange: (MoneyTransaction, Long) -> Unit,
    onAccountChange: (MoneyTransaction, Long) -> Unit,
    onDateChange: (MoneyTransaction, Long) -> Unit,
    onRemove: (MoneyTransaction) -> Unit,
    onConfirmAll: () -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.entry_preview_title, state.drafts.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            when (state.phase) {
                EntryViewModel.Phase.WRITING -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 24.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.entry_writing))
                }

                EntryViewModel.Phase.DONE -> Column(modifier = Modifier.padding(vertical = 24.dp)) {
                    Text(
                        text = stringResource(R.string.entry_done, state.writtenCount),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.entry_close))
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = state.drafts.filter { !it.removed },
                            key = { it.key },
                        ) { draft ->
                            DraftCard(
                                draftKey = draft.key,
                                transaction = draft.transaction,
                                categories = state.categories,
                                accounts = state.accounts,
                                onAmountChange = onAmountChange,
                                onNoteChange = onNoteChange,
                                onCategoryChange = onCategoryChange,
                                onAccountChange = onAccountChange,
                                onDateChange = onDateChange,
                                onRemove = onRemove,
                            )
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                    ) {
                        OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.entry_cancel))
                        }
                        Button(onClick = onConfirmAll, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.entry_preview_confirm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draftKey: Long,
    transaction: MoneyTransaction,
    categories: List<Category>,
    accounts: List<Account>,
    onAmountChange: (MoneyTransaction, Long) -> Unit,
    onNoteChange: (MoneyTransaction, String) -> Unit,
    onCategoryChange: (MoneyTransaction, Long) -> Unit,
    onAccountChange: (MoneyTransaction, Long) -> Unit,
    onDateChange: (MoneyTransaction, Long) -> Unit,
    onRemove: (MoneyTransaction) -> Unit,
) {
    var amountText by remember(draftKey) {
        mutableStateOf(
            if (transaction.amountCents == 0L) "" else Formats.money(transaction.amountCents),
        )
    }
    var pickCategory by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { text ->
                        amountText = text
                        AmountParser.toCents(text)?.let { cents -> onAmountChange(transaction, cents) }
                    },
                    label = { Text(stringResource(R.string.entry_draft_amount)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onRemove(transaction) }) {
                    Text(stringResource(R.string.entry_draft_remove))
                }
            }
            OutlinedTextField(
                value = transaction.note,
                onValueChange = { onNoteChange(transaction, it) },
                label = { Text(stringResource(R.string.entry_draft_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // S43：條碼（唯讀，僅有值時顯示）——掃碼記帳一掃完即在此看得到
            if (transaction.barcode != null) {
                Text(
                    text = stringResource(R.string.entry_draft_barcode, transaction.barcode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { pickCategory = true }) {
                    Text(categoryName(categories, transaction))
                }
                TextButton(onClick = { pickAccount = true }) {
                    Text(accountName(accounts, transaction))
                }
                TextButton(onClick = { pickDate = true }) {
                    Text(Formats.date(transaction.dateEpochMillis))
                }
            }
        }
    }

    if (pickCategory) {
        OptionsDialog(
            title = stringResource(R.string.entry_draft_category),
            options = categories
                .filter { it.type.name == transaction.type.name }
                .map { it.id to it.name },
            onSelect = { id ->
                onCategoryChange(transaction, id)
                pickCategory = false
            },
            onDismiss = { pickCategory = false },
        )
    }
    if (pickAccount) {
        OptionsDialog(
            title = stringResource(R.string.entry_draft_account),
            options = accounts.map { it.id to it.name },
            onSelect = { id ->
                onAccountChange(transaction, id)
                pickAccount = false
            },
            onDismiss = { pickAccount = false },
        )
    }
    if (pickDate) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = transaction.dateEpochMillis,
        )
        FullWidthDatePickerDialog(
            state = dateState,
            title = stringResource(R.string.entry_draft_date),
            confirmText = stringResource(R.string.entry_ok),
            dismissText = stringResource(R.string.entry_cancel),
            onConfirm = {
                val millis = dateState.selectedDateMillis ?: transaction.dateEpochMillis
                onDateChange(transaction, millis)
                pickDate = false
            },
            onDismiss = { pickDate = false },
        )
    }
}

/** 有限高度選擇對話（單一 verticalScroll 容器，鐵律） */
@Composable
private fun OptionsDialog(
    title: String,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.entry_cancel)) }
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { (id, name) ->
                    TextButton(
                        onClick = { onSelect(id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(name) }
                }
            }
        },
    )
}

private fun categoryName(categories: List<Category>, transaction: MoneyTransaction): String =
    categories.firstOrNull { it.id == transaction.categoryId }?.name ?: ""

private fun accountName(accounts: List<Account>, transaction: MoneyTransaction): String =
    accounts.firstOrNull { it.id == transaction.accountId }?.name ?: ""
