package com.hmoney.app.ui.screens.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import com.hmoney.app.ui.components.FullWidthDatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmoney.app.R
import com.hmoney.app.domain.model.Item
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.screens.add.SimpleScanDialog

/**
 * S46 物品新增／編輯共用 Sheet（ModalBottomSheet＋skipHalfExpanded）：
 * 名稱必填、數量（預設 1）、存放位置、到期日（DatePicker＋共用短格式 formatter）、
 * 條碼欄（唯讀＋掃碼鈕，重用 SimpleScanDialog 模式）。
 * AI 填選為「建議預覽 → 逐欄採用」——採用前不覆寫任何使用者已填欄位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditSheet(
    editing: Item?,
    onDismiss: () -> Unit,
    onGoSettings: () -> Unit,
    viewModel: ItemsViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var quantityText by remember(editing) { mutableStateOf((editing?.quantity ?: 1).toString()) }
    var storage by remember(editing) { mutableStateOf(editing?.storageLocation ?: "") }
    var expiryMillis by remember(editing) { mutableStateOf(editing?.expiryDateEpochMillis) }
    var barcode by remember(editing) { mutableStateOf(editing?.barcode ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (editing == null) R.string.items_add else R.string.items_edit,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    viewModel.clearNameError()
                },
                label = { Text(stringResource(R.string.items_name)) },
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text(stringResource(R.string.items_name_required)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.items_quantity)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = storage,
                onValueChange = { storage = it },
                label = { Text(stringResource(R.string.items_storage_location)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = {
                        Text(
                            expiryMillis?.let { Formats.date(it) }
                                ?: stringResource(R.string.items_expiry_none),
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Event, contentDescription = null)
                    },
                    modifier = Modifier.weight(1f),
                )
                if (expiryMillis != null) {
                    TextButton(onClick = { expiryMillis = null }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(stringResource(R.string.items_barcode)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showScanner = true }) {
                    Icon(
                        Icons.Outlined.QrCodeScanner,
                        contentDescription = stringResource(R.string.items_scan),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // AI 填選：未設定時沿用既有引導（前往設定）
            AssistChip(
                onClick = { viewModel.suggestItemFields(name, barcode) },
                enabled = !state.suggesting && name.isNotBlank(),
                leadingIcon = {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                },
                label = {
                    Text(
                        if (state.suggesting) {
                            stringResource(R.string.ai_loading)
                        } else {
                            stringResource(R.string.items_ai_suggest)
                        },
                    )
                },
            )
            if (state.aiNotConfigured) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.items_ai_not_configured),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onGoSettings) {
                        Text(stringResource(R.string.items_go_settings))
                    }
                }
            }
            if (state.aiFailed) {
                Text(
                    text = stringResource(R.string.ai_error_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // 建議預覽：逐欄採用，不合用可不採；不自動覆寫使用者輸入
            state.suggestion?.let { suggestion ->
                Text(
                    text = stringResource(R.string.items_ai_suggestion_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                suggestion.storageLocation?.let { value ->
                    SuggestionRow(
                        label = stringResource(R.string.items_storage_location),
                        value = value,
                        onApply = { storage = value },
                    )
                }
                suggestion.expiryDateEpochMillis?.let { value ->
                    SuggestionRow(
                        label = stringResource(R.string.items_expiry_date),
                        value = Formats.date(value),
                        onApply = { expiryMillis = value },
                    )
                }
                suggestion.quantity?.let { value ->
                    SuggestionRow(
                        label = stringResource(R.string.items_quantity),
                        value = value.toString(),
                        onApply = { quantityText = value.toString() },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_cancel)) }
                Button(
                    onClick = {
                        viewModel.saveItem(
                            editing = editing,
                            name = name,
                            barcode = barcode,
                            storageLocation = storage,
                            quantity = quantityText.toIntOrNull() ?: 1,
                            expiryDateEpochMillis = expiryMillis,
                            onSaved = onDismiss,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_confirm)) }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = expiryMillis)
        FullWidthDatePickerDialog(
            state = pickerState,
            title = stringResource(R.string.items_expiry_date),
            confirmText = stringResource(R.string.common_confirm),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = {
                expiryMillis = pickerState.selectedDateMillis ?: expiryMillis
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showScanner) {
        SimpleScanDialog(
            scannedBarcode = state.scannedBarcode,
            scanError = state.scanError,
            onFrame = viewModel::onScanFrame,
            onUse = {
                state.scannedBarcode?.let { barcode = it }
                viewModel.clearScanState()
                showScanner = false
            },
            onClose = {
                viewModel.clearScanState()
                showScanner = false
            },
        )
    }
}

/** 建議預覽單列：欄位名＋建議值＋採用鈕（採用才寫入表單） */
@Composable
private fun SuggestionRow(label: String, value: String, onApply: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$label：$value",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onApply) {
            Text(stringResource(R.string.items_ai_apply))
        }
    }
}
