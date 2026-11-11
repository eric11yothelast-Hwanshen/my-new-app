package com.hmoney.app.ui.screens.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.common.IconMapper
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 記帳 Bottom Sheet：數字鍵盤為主的輸入流程，記完即存。
 * 編輯模式傳入 editingId。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    editingId: Long? = null,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val form = state.form

    LaunchedEffect(editingId) {
        if (editingId != null) {
            viewModel.loadForEdit(editingId)
        } else {
            viewModel.reset()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (editingId == null) R.string.add_title else R.string.add_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            // 交易類型：支出／收入／轉帳
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TransactionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = form.type == type,
                        onClick = { viewModel.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TransactionType.entries.size,
                        ),
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

            // 金額顯示（數字鍵盤輸入）
            Text(
                text = if (form.amountText.isEmpty()) {
                    stringResource(R.string.add_amount_hint)
                } else {
                    form.amountText
                },
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                color = if (form.amountText.isEmpty()) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // 帳戶選擇
            AccountChipRow(
                label = stringResource(R.string.add_account),
                selectedId = form.accountId,
                accounts = state.accounts.map { it.id to it.name },
                onSelect = viewModel::setAccount,
            )

            // 轉入帳戶（僅轉帳）
            if (form.type == TransactionType.TRANSFER) {
                AccountChipRow(
                    label = stringResource(R.string.add_to_account),
                    selectedId = form.toAccountId,
                    accounts = state.accounts
                        .filter { it.id != form.accountId }
                        .map { it.id to it.name },
                    onSelect = viewModel::setToAccount,
                )
                OutlinedTextField(
                    value = form.feeText,
                    onValueChange = viewModel::setFee,
                    label = { Text(stringResource(R.string.add_fee)) },
                    placeholder = { Text(stringResource(R.string.add_fee_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 分類（支出／收入）
            if (form.type != TransactionType.TRANSFER) {
                Text(
                    text = stringResource(R.string.add_category),
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    userScrollEnabled = false,
                ) {
                    items(state.categories, key = { it.id }) { category ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setCategory(category.id) }
                                .padding(8.dp),
                        ) {
                            Icon(
                                imageVector = IconMapper.iconOf(category.iconKey),
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(category.colorArgb),
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // 日期與時間
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DateFieldButton(
                    date = form.date,
                    onPick = viewModel::setDate,
                    modifier = Modifier.weight(1f),
                )
                TimeFieldButton(
                    time = form.time,
                    onPick = viewModel::setTime,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = form.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.add_note)) },
                placeholder = { Text(stringResource(R.string.add_note_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.tagsText,
                onValueChange = viewModel::setTags,
                label = { Text(stringResource(R.string.add_tags)) },
                placeholder = { Text(stringResource(R.string.add_tags_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 驗證錯誤回饋
            form.errorRes?.let { errorRes ->
                Text(
                    text = stringResource(errorRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // 數字鍵盤
            Keypad(
                onInput = viewModel::onKeypadInput,
                onBackspace = viewModel::onKeypadBackspace,
            )

            Button(
                onClick = { viewModel.save(onSaved = onDismiss) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_save))
            }
        }
    }
}

/** 帳戶選擇列（chip 形式） */
@Composable
private fun AccountChipRow(
    label: String,
    selectedId: Long?,
    accounts: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp),
        ) {
            accounts.take(4).forEach { (id, name) ->
                FilterChip(
                    selected = selectedId == id,
                    onClick = { onSelect(id) },
                    label = { Text(name, maxLines = 1) },
                )
            }
        }
    }
}

/** 日期選擇按鈕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFieldButton(
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onPick(
                                Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.systemDefault()).toLocalDate(),
                            )
                        }
                        showDialog = false
                    },
                ) { Text(stringResource(R.string.add_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.add_dismiss))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
    AssistChip(
        onClick = { showDialog = true },
        label = { Text(Formats.date(date)) },
        leadingIcon = {
            Icon(Icons.Outlined.Event, contentDescription = stringResource(R.string.add_date))
        },
        modifier = modifier,
    )
}

/** 時間選擇按鈕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeFieldButton(
    time: LocalTime,
    onPick: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        val pickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPick(LocalTime.of(pickerState.hour, pickerState.minute))
                        showDialog = false
                    },
                ) { Text(stringResource(R.string.add_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.add_dismiss))
                }
            },
        ) {
            TimePicker(state = pickerState)
        }
    }
    AssistChip(
        onClick = { showDialog = true },
        label = { Text(Formats.time(time)) },
        leadingIcon = {
            Icon(Icons.Outlined.Schedule, contentDescription = stringResource(R.string.add_time))
        },
        modifier = modifier,
    )
}

/** 數字鍵盤：1–9、0、小數點、退格 */
@Composable
private fun Keypad(
    onInput: (Char) -> Unit,
    onBackspace: () -> Unit,
) {
    val keys = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('.', '0'),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { rowKeys ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowKeys.forEach { key ->
                    KeypadKey(
                        text = key.toString(),
                        onClick = { onInput(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowKeys.size < 3) {
                    KeypadKey(
                        text = "",
                        icon = Icons.AutoMirrored.Outlined.Backspace,
                        onClick = onBackspace,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
