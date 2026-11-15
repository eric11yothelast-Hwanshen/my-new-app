package com.hmoney.app.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.hmoney.app.domain.model.ItemActionType
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.components.FullWidthDatePickerDialog

/**
 * S47 聊天室物品操作確認卡：模式照 ChatDraftCard／ChatDraftGroup——
 * CREATE 顯示將建立的欄位（可編輯）、UPDATE 顯示前後對照（可編輯）、DELETE 唯讀＋紅色警示；
 * PENDING 可確認／取消，確認→ItemRepository 對應操作→卡轉「已確認」，取消→「已取消」。
 * 終態（CONFIRMED／CANCELLED）不得再寫入（VM 端防護）。
 */
@Composable
fun ChatItemActionCard(
    action: ChatItemAction,
    onNameChange: (String) -> Unit,
    onStorageChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onExpiryChange: (Long?) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (action.status) {
                ChatDraftStatus.CONFIRMED -> {
                    Text(
                        text = stringResource(R.string.ai_item_action_confirmed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                ChatDraftStatus.CANCELLED -> {
                    Text(
                        text = stringResource(R.string.ai_item_action_cancelled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ChatDraftStatus.PENDING -> {
                    val intent = action.intent
                    Text(
                        text = when (intent.action) {
                            ItemActionType.CREATE -> stringResource(R.string.ai_item_action_create_title)
                            ItemActionType.UPDATE -> stringResource(R.string.ai_item_action_update_title)
                            ItemActionType.DELETE -> stringResource(R.string.ai_item_action_delete_title)
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (intent.action == ItemActionType.DELETE) {
                        ItemActionReadonlyFields(action = action)
                        Text(
                            text = stringResource(R.string.ai_item_action_delete_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        action.targetItem?.takeIf { intent.action == ItemActionType.UPDATE }?.let {
                            ItemActionOriginalRow(it)
                            // S49 相對期限前後對照：原到期日 → 新到期日
                            if (intent.expiryDateEpochMillis != null && it.expiryDateEpochMillis != null) {
                                Text(
                                    text = stringResource(
                                        R.string.ai_item_action_expiry_change,
                                        Formats.date(it.expiryDateEpochMillis!!),
                                        Formats.date(intent.expiryDateEpochMillis!!),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        ItemActionEditableFields(
                            action = action,
                            onNameChange = onNameChange,
                            onStorageChange = onStorageChange,
                            onQuantityChange = onQuantityChange,
                            onExpiryChange = onExpiryChange,
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

/** DELETE 唯讀欄位：名稱／數量／存放位置／到期日 */
@Composable
private fun ItemActionReadonlyFields(action: ChatItemAction) {
    val intent = action.intent
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.items_name) + "：" + intent.name)
        Text(text = stringResource(R.string.items_quantity) + "：" + (intent.quantity ?: 1))
        intent.storageLocation?.let {
            Text(text = stringResource(R.string.items_storage_location) + "：" + it)
        }
        intent.expiryDateEpochMillis?.let {
            Text(text = stringResource(R.string.items_expiry_date) + "：" + Formats.date(it))
        }
    }
}

/** UPDATE 前後對照：原值列（唯讀，灰色小字） */
@Composable
private fun ItemActionOriginalRow(item: com.hmoney.app.domain.model.Item) {
    val original = listOfNotNull(
        stringResource(R.string.items_name) + "：" + item.name,
        stringResource(R.string.items_quantity) + "：" + item.quantity,
        item.storageLocation?.let { stringResource(R.string.items_storage_location) + "：" + it },
        item.expiryDateEpochMillis?.let { stringResource(R.string.items_expiry_date) + "：" + Formats.date(it) },
    ).joinToString("｜")
    Text(
        text = stringResource(R.string.ai_item_action_original, original),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** CREATE／UPDATE 可編輯欄位：名稱／數量／存放位置／到期日（到期日走全寬日期對話框） */
@Composable
private fun ItemActionEditableFields(
    action: ChatItemAction,
    onNameChange: (String) -> Unit,
    onStorageChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onExpiryChange: (Long?) -> Unit,
) {
    val intent = action.intent
    val target = action.targetItem
    var quantityText by remember(intent) {
        mutableStateOf((intent.quantity ?: target?.quantity ?: 1).toString())
    }
    var storageText by remember(intent) {
        mutableStateOf(intent.storageLocation ?: target?.storageLocation.orEmpty())
    }
    var pickDate by remember { mutableStateOf(false) }
    val expiryMillis = intent.expiryDateEpochMillis ?: target?.expiryDateEpochMillis

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = intent.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.items_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = quantityText,
                onValueChange = { text ->
                    quantityText = text
                    onQuantityChange(text)
                },
                label = { Text(stringResource(R.string.items_quantity)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = storageText,
                onValueChange = { text ->
                    storageText = text
                    onStorageChange(text)
                },
                label = { Text(stringResource(R.string.items_storage_location)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(
            onClick = { pickDate = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = expiryMillis?.let { Formats.date(it) }
                    ?: stringResource(R.string.items_expiry_date),
                maxLines = 1,
            )
        }
    }

    if (pickDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = expiryMillis)
        FullWidthDatePickerDialog(
            state = pickerState,
            title = stringResource(R.string.items_expiry_date),
            confirmText = stringResource(R.string.common_confirm),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = {
                onExpiryChange(pickerState.selectedDateMillis)
                pickDate = false
            },
            onDismiss = { pickDate = false },
        )
    }
}
