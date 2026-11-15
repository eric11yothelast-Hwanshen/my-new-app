package com.hmoney.app.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmoney.app.R
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionActionType
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.ui.common.AmountParser
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.components.FullWidthDatePickerDialog

/**
 * S48 聊天室交易刪改確認卡（模式照 ChatItemActionCard／ChatDraftCard）：
 * DELETE＝完整交易資訊唯讀＋紅色不可逆警示；UPDATE＝可編輯欄位（金額／備註／分類／帳戶／日期，
 * 分類帳戶限定清單選取＋既有 fallback 慣例）。候選階段（target == null）列出每筆附「選這筆」按鈕。
 * PENDING 可確認／取消；確認→既有 updateTransaction／deleteTransaction（VM 唯一寫入點）→卡轉「已完成」；
 * 取消→「已取消」。終態（CONFIRMED／CANCELLED）唯讀，不得再寫入。一次僅一筆（嚴禁批次刪除）。
 */
@Composable
fun ChatTransactionActionCard(
    action: ChatTransactionAction,
    categories: List<Category>,
    accounts: List<Account>,
    onAmountChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onAccountChange: (Long) -> Unit,
    onDateChange: (Long) -> Unit,
    onSelectCandidate: (Long) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (action.status) {
                ChatDraftStatus.CONFIRMED -> {
                    Text(
                        text = stringResource(R.string.ai_tx_action_confirmed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                ChatDraftStatus.CANCELLED -> {
                    Text(
                        text = stringResource(R.string.ai_tx_action_cancelled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ChatDraftStatus.PENDING -> {
                    Text(
                        text = when (action.intent.action) {
                            TransactionActionType.DELETE -> stringResource(R.string.ai_tx_action_delete_title)
                            TransactionActionType.UPDATE -> stringResource(R.string.ai_tx_action_update_title)
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val target = action.target
                    if (target == null) {
                        Text(
                            text = stringResource(R.string.ai_tx_action_candidates_title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        action.candidates.forEach { candidate ->
                            TransactionCandidateRow(
                                transaction = candidate,
                                categories = categories,
                                accounts = accounts,
                                onSelect = { onSelectCandidate(candidate.id) },
                            )
                        }
                    } else {
                        if (action.intent.action == TransactionActionType.DELETE) {
                            TransactionReadonlyFields(
                                transaction = target,
                                categories = categories,
                                accounts = accounts,
                            )
                            Text(
                                text = stringResource(R.string.ai_tx_action_delete_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            TransactionEditableFields(
                                transaction = action.working ?: target,
                                categories = categories,
                                accounts = accounts,
                                onAmountChange = onAmountChange,
                                onNoteChange = onNoteChange,
                                onCategoryChange = onCategoryChange,
                                onAccountChange = onAccountChange,
                                onDateChange = onDateChange,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.entry_cancel))
                            }
                            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.common_confirm))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 候選列：日期／金額／分類／備註＋「選這筆」 */
@Composable
private fun TransactionCandidateRow(
    transaction: MoneyTransaction,
    categories: List<Category>,
    accounts: List<Account>,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Formats.date(transaction.dateEpochMillis) + "｜" +
                    Formats.money(transaction.amountCents),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = listOfNotNull(
                    categories.firstOrNull { it.id == transaction.categoryId }?.name,
                    accounts.firstOrNull { it.id == transaction.accountId }?.name,
                    transaction.note.takeIf { it.isNotBlank() },
                ).joinToString("｜").ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TextButton(onClick = onSelect) {
            Text(stringResource(R.string.ai_tx_action_select))
        }
    }
}

/** DELETE 完整交易資訊（唯讀）：日期／類型／金額／分類／帳戶／備註 */
@Composable
private fun TransactionReadonlyFields(
    transaction: MoneyTransaction,
    categories: List<Category>,
    accounts: List<Account>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.entry_draft_date) + "：" + Formats.date(transaction.dateEpochMillis))
        Text(text = stringResource(R.string.ai_tx_action_type) + "：" + transactionTypeLabel(transaction.type))
        Text(text = stringResource(R.string.entry_draft_amount) + "：" + Formats.money(transaction.amountCents))
        Text(
            text = stringResource(R.string.entry_draft_category) + "：" +
                (categories.firstOrNull { it.id == transaction.categoryId }?.name ?: "-"),
        )
        Text(
            text = stringResource(R.string.entry_draft_account) + "：" +
                (accounts.firstOrNull { it.id == transaction.accountId }?.name ?: "-"),
        )
        Text(text = stringResource(R.string.entry_draft_note) + "：" + transaction.note.ifBlank { "-" })
    }
}

/** UPDATE 可編輯欄位（模式照 ChatDraftItemCard；分類帳戶限定清單選取；日期走全寬日期對話框） */
@Composable
private fun TransactionEditableFields(
    transaction: MoneyTransaction,
    categories: List<Category>,
    accounts: List<Account>,
    onAmountChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onAccountChange: (Long) -> Unit,
    onDateChange: (Long) -> Unit,
) {
    // S49 編輯的是已存交易，id 即穩定 key（嚴禁 identityHashCode／物件身分當 key）
    var amountText by remember(transaction.id) {
        mutableStateOf(
            if (transaction.amountCents == 0L) "" else Formats.money(transaction.amountCents),
        )
    }
    var pickCategory by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = amountText,
            onValueChange = { text ->
                amountText = text
                AmountParser.toCents(text)?.let { cents -> onAmountChange(cents) }
            },
            label = { Text(stringResource(R.string.entry_draft_amount)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = transaction.note,
            onValueChange = onNoteChange,
            label = { Text(stringResource(R.string.entry_draft_note)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { pickCategory = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = categories.firstOrNull { it.id == transaction.categoryId }?.name ?: "",
                    maxLines = 1,
                )
            }
            TextButton(
                onClick = { pickAccount = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = accounts.firstOrNull { it.id == transaction.accountId }?.name ?: "",
                    maxLines = 1,
                )
            }
        }
        TextButton(
            onClick = { pickDate = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = Formats.date(transaction.dateEpochMillis),
                maxLines = 1,
            )
        }
    }

    if (pickCategory) {
        TransactionActionOptionsDialog(
            title = stringResource(R.string.entry_draft_category),
            options = categories
                .filter { it.type.name == transaction.type.name }
                .map { it.id to it.name },
            onSelect = { id ->
                onCategoryChange(id)
                pickCategory = false
            },
            onDismiss = { pickCategory = false },
        )
    }
    if (pickAccount) {
        TransactionActionOptionsDialog(
            title = stringResource(R.string.entry_draft_account),
            options = accounts.map { it.id to it.name },
            onSelect = { id ->
                onAccountChange(id)
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
                onDateChange(millis)
                pickDate = false
            },
            onDismiss = { pickDate = false },
        )
    }
}

@Composable
private fun transactionTypeLabel(type: TransactionType): String = when (type) {
    TransactionType.EXPENSE -> stringResource(R.string.add_type_expense)
    TransactionType.INCOME -> stringResource(R.string.add_type_income)
    TransactionType.TRANSFER -> stringResource(R.string.add_type_transfer)
}

/** 有限高度選擇對話框（單一 verticalScroll 容器鐵律） */
@Composable
private fun TransactionActionOptionsDialog(
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
