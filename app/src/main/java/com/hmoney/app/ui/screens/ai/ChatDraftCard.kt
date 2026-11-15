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
import com.hmoney.app.ui.components.FullWidthDatePickerDialog
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
import com.hmoney.app.ui.common.AmountParser
import com.hmoney.app.ui.common.Formats

/**
 * 聊天流草稿組卡（S44）：與 DraftPreviewSheet.DraftCard 平行實作（該卡為私有且與
 * ModalBottomSheet 流程耦合，抽共用會動到 S41 鐵律檔案，故平行實作——取捨見 PROGRESS）。
 * PENDING 可逐筆編輯／剔除＋全部確認／取消；CONFIRMED／CANCELLED 為唯讀終態。
 */
@Composable
fun ChatDraftGroupCard(
    group: ChatDraftGroup,
    categories: List<Category>,
    accounts: List<Account>,
    onAmountChange: (MoneyTransaction, Long) -> Unit,
    onNoteChange: (MoneyTransaction, String) -> Unit,
    onCategoryChange: (MoneyTransaction, Long) -> Unit,
    onAccountChange: (MoneyTransaction, Long) -> Unit,
    onDateChange: (MoneyTransaction, Long) -> Unit,
    onRemove: (MoneyTransaction) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (group.status) {
                ChatDraftStatus.CONFIRMED -> {
                    Text(
                        text = stringResource(R.string.ai_draft_confirmed, group.writtenCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                ChatDraftStatus.CANCELLED -> {
                    Text(
                        text = stringResource(R.string.ai_draft_cancelled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ChatDraftStatus.PENDING -> {
                    Text(
                        text = stringResource(
                            R.string.ai_draft_group_title,
                            group.items.count { !it.removed },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    group.items.forEach { item ->
                        if (!item.removed) {
                            ChatDraftItemCard(
                                draftKey = item.key,
                                transaction = item.transaction,
                                categories = categories,
                                accounts = accounts,
                                onAmountChange = onAmountChange,
                                onNoteChange = onNoteChange,
                                onCategoryChange = onCategoryChange,
                                onAccountChange = onAccountChange,
                                onDateChange = onDateChange,
                                onRemove = onRemove,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.entry_cancel))
                        }
                        Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.ai_draft_confirm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatDraftItemCard(
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

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        // S45 排版修復：分類＋帳戶同一列各 weight(1f)（maxLines=1），日期獨立全寬一列——
        // 帳戶名再長（如「Redmi Watch 6 (悠遊卡)」）日期也永遠完整顯示（模式照 AddTransactionSheet 日期欄）
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
        // S45：條碼列（唯讀，僅有值時顯示）——掃碼草稿一掃完即在卡上看得到號碼（照 DraftPreviewSheet S43 模式）
        if (transaction.barcode != null) {
            Text(
                text = stringResource(R.string.entry_draft_barcode, transaction.barcode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickCategory) {
        ChatDraftOptionsDialog(
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
        ChatDraftOptionsDialog(
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

/** 有限高度選擇對話（單一 verticalScroll 容器鐵律） */
@Composable
private fun ChatDraftOptionsDialog(
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
