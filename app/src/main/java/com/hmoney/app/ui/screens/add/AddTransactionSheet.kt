package com.hmoney.app.ui.screens.add

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.common.IconMapper
import com.hmoney.app.ui.screens.ai.toMessageRes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 記帳 Bottom Sheet：數字鍵盤為主的輸入流程，記完即存。
 * 內容可捲動（小螢幕不裁切鍵盤）、含返回鍵（未儲存確認）與鍵盤列「完成 ✓」送出鍵、
 * 驗證與 AI 回饋走 Snackbar。
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.add_saved)

    LaunchedEffect(editingId) {
        if (editingId != null) {
            viewModel.loadForEdit(editingId)
        } else {
            viewModel.reset()
        }
    }

    // 提示字串於組合期解析（設定變更時自動更新，避免 LocalContext 取到過期資源）
    val errorText = form.errorRes?.let { stringResource(it) }
    val aiAppliedMessage = stringResource(R.string.ai_applied)
    val aiNeedNoteMessage = stringResource(R.string.ai_need_note)
    val aiErrorMessage = (state.aiState as? AddTransactionViewModel.AiState.Error)
        ?.let { stringResource(it.error.toMessageRes()) }

    // 驗證失敗提示（Snackbar，取代佔空間的行內錯誤）
    LaunchedEffect(form.errorRes) {
        errorText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // AI 建議回饋
    LaunchedEffect(state.aiState) {
        when (state.aiState) {
            AddTransactionViewModel.AiState.Done -> {
                snackbarHostState.showSnackbar(aiAppliedMessage)
                viewModel.clearAiState()
            }

            AddTransactionViewModel.AiState.NeedNote -> {
                snackbarHostState.showSnackbar(aiNeedNoteMessage)
                viewModel.clearAiState()
            }

            is AddTransactionViewModel.AiState.Error -> {
                aiErrorMessage?.let { snackbarHostState.showSnackbar(it) }
                viewModel.clearAiState()
            }

            else -> Unit
        }
    }

    // 是否已有未儲存的輸入（返回時需確認）
    fun hasUnsavedInput(): Boolean = form.amountText.isNotEmpty() ||
        form.note.isNotBlank() ||
        form.tagsText.isNotBlank()

    fun attemptClose() {
        if (hasUnsavedInput()) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = { attemptClose() }) {
        // 單一捲動結構：外層 Column 不捲動；中段 weight(1f) 可捲動；鍵盤與完成鍵固定底部，永遠完整顯示。
        // 注意：不得在中段捲動區內再放 LazyVerticalGrid／LazyColumn 等捲動元件（實機會拋無限高度例外）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            // 標題與返回鍵
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { attemptClose() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.wizard_back),
                    )
                }
                Text(
                    text = stringResource(
                        if (editingId == null) R.string.add_title else R.string.add_edit_title,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

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
                CategoryGrid(
                    categories = state.categories,
                    onSelect = viewModel::setCategory,
                )
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

            // 備註＋AI 建議按鈕（潤飾備註／自動分類／智慧標籤）
            OutlinedTextField(
                value = form.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.add_note)) },
                placeholder = { Text(stringResource(R.string.add_note_hint)) },
                singleLine = true,
                trailingIcon = {
                    if (state.aiState is AddTransactionViewModel.AiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = viewModel::aiSuggest) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = stringResource(R.string.ai_suggest_note),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.aiState is AddTransactionViewModel.AiState.Loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ai_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = viewModel::cancelAi) {
                        Text(stringResource(R.string.ai_cancel))
                    }
                }
            }

            OutlinedTextField(
                value = form.tagsText,
                onValueChange = viewModel::setTags,
                label = { Text(stringResource(R.string.add_tags)) },
                placeholder = { Text(stringResource(R.string.add_tags_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            }

            // 數字鍵盤（固定底部，不隨內容捲動，任何螢幕都完整顯示 0、.、刪除鍵）
            Keypad(
                onInput = viewModel::onKeypadInput,
                onBackspace = viewModel::onKeypadBackspace,
            )

            // 鍵盤列送出鍵：完成 ✓（三個分頁一致）
            Button(
                onClick = {
                    viewModel.save(
                        onSaved = {
                            Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null)
                Text(
                    text = " " + stringResource(R.string.add_done),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            SnackbarHost(hostState = snackbarHostState)
        }
    }

    // 返回（放棄）確認對話框
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.add_discard_title)) },
            text = { Text(stringResource(R.string.add_discard_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        viewModel.reset()
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.add_discard_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.add_cancel))
                }
            },
        )
    }
}

/**
 * 分類格：非捲動的固定 4 欄格
 * 原本使用 LazyVerticalGrid，在可捲動父層內會被以無限高度測量而拋例外（實機閃退根因），
 * 改為純 Column／Row 組合，分類數量有限（內建種子＋使用者自訂）不影響效能。
 */
@Composable
private fun CategoryGrid(
    categories: List<com.hmoney.app.domain.model.Category>,
    onSelect: (Long) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        categories.chunked(4).forEach { rowCategories ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowCategories.forEach { category ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(category.id) }
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
                repeat(4 - rowCategories.size) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
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

/** 數字鍵盤：1–9、0、小數點、退格（weight 版面自適應，不會被裁切） */
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
