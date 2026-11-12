package com.hmoney.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.hmoney.app.domain.model.DarkMode
import com.hmoney.app.domain.model.RecurringFrequency
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.ui.common.AmountParser
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.domain.logic.BudgetEvaluator
import com.hmoney.app.ui.components.FluidSwitch
import com.hmoney.app.ui.components.GlassCard
import com.hmoney.app.ui.components.glassSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/**
 * 設定頁：外觀（深色模式／動態色彩）、預算（月總＋分類）、
 * 週期交易（固定收支）。AI 連線、通知、備份將於後續階段加入。
 */
@Composable
fun SettingsScreen(
    onOpenWizard: () -> Unit,
    onOpenAi: () -> Unit,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    budgetViewModel: BudgetViewModel = hiltViewModel(),
    recurringViewModel: RecurringViewModel = hiltViewModel(),
    aiViewModel: com.hmoney.app.ui.screens.ai.AiViewModel = hiltViewModel(),
) {
    val appearance by settingsViewModel.appearance.collectAsState()
    val glassState = rememberHazeState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val budgetSavedMessage = stringResource(R.string.budget_saved)

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .glassSource(glassState)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 12.dp),
        )

        // ===== 外觀 =====
        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.titleMedium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_dark_mode),
                style = MaterialTheme.typography.bodyLarge,
            )
            val modes = DarkMode.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = appearance.darkMode == mode,
                        onClick = { settingsViewModel.setDarkMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size,
                        ),
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        DarkMode.SYSTEM -> R.string.settings_dark_mode_system
                                        DarkMode.LIGHT -> R.string.settings_dark_mode_light
                                        DarkMode.DARK -> R.string.settings_dark_mode_dark
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_dynamic_color),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_dynamic_color_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FluidSwitch(
                checked = appearance.dynamicColor,
                onCheckedChange = settingsViewModel::setDynamicColor,
            )
        }

        // ===== 通知 =====
        NotificationSection(onOpenWizard = onOpenWizard)

        // ===== 預算 =====
        BudgetSection(budgetViewModel, onSaved = {
            scope.launch { snackbarHostState.showSnackbar(budgetSavedMessage) }
        })

        // ===== 週期交易 =====
        RecurringSection(recurringViewModel)

        // ===== AI 連線 =====
        AiSection(aiViewModel, onOpenAi)

        // ===== 備份與還原 =====
        BackupSection()
    }

    // 預算儲存回饋（Snackbar）
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    }
}

/** AI 連線區：Base URL／API Key／模型名稱（加密保存）與開啟 AI 助手 */
@Composable
private fun AiSection(
    viewModel: com.hmoney.app.ui.screens.ai.AiViewModel,
    onOpenAi: () -> Unit,
) {
    val config by viewModel.config.collectAsState()
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var model by remember { mutableStateOf(config.model) }
    var saved by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.ai_config_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.ai_config_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it; saved = false },
            label = { Text(stringResource(R.string.ai_base_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; saved = false },
            label = { Text(stringResource(R.string.ai_api_key)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it; saved = false },
            label = { Text(stringResource(R.string.ai_model)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                viewModel.saveConfig(baseUrl, apiKey, model) { saved = true }
            }) {
                Text(stringResource(R.string.ai_config_save))
            }
            TextButton(onClick = onOpenAi) {
                Text(stringResource(R.string.ai_open_assistant))
            }
        }
        if (saved) {
            Text(
                text = stringResource(R.string.ai_config_saved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 預算進度條顏色：80% 警示色、100% 轉紅 */
@Composable
private fun progressColor(level: BudgetEvaluator.Level): androidx.compose.ui.graphics.Color =
    when (level) {
        BudgetEvaluator.Level.NORMAL -> MaterialTheme.colorScheme.primary
        BudgetEvaluator.Level.WARNING -> MaterialTheme.colorScheme.tertiary
        BudgetEvaluator.Level.OVER -> MaterialTheme.colorScheme.error
    }

@Composable
private fun BudgetSection(
    viewModel: BudgetViewModel,
    onSaved: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    var editingTotal by remember { mutableStateOf(false) }
    var editingCategoryId by remember { mutableStateOf<Long?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.budget_title),
            style = MaterialTheme.typography.titleMedium,
        )

        // 月總預算
        GlassCard(glassState = rememberHazeState(), contentPadding = 12.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.budget_total),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = { editingTotal = true }) {
                        Text(
                            text = if (state.totalBudget.budgetCents == 0L) {
                                stringResource(R.string.budget_set)
                            } else {
                                stringResource(R.string.budget_edit)
                            },
                        )
                    }
                }
                if (state.totalBudget.budgetCents > 0L) {
                    LinearProgressIndicator(
                        progress = { state.totalBudget.ratio.coerceIn(0f, 1f) },
                        color = progressColor(state.totalBudget.level),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.budget_spent_of,
                            Formats.money(state.totalBudget.spentCents),
                            Formats.money(state.totalBudget.budgetCents),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.totalBudget.level == BudgetEvaluator.Level.OVER) {
                        Text(
                            text = stringResource(R.string.budget_over),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        // 分類預算
        state.categoryProgress
            .filter { it.second.budgetCents > 0L }
            .forEach { (category, progress) ->
                GlassCard(glassState = rememberHazeState(), contentPadding = 12.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            TextButton(onClick = { editingCategoryId = category.id }) {
                                Text(stringResource(R.string.budget_edit))
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progress.ratio.coerceIn(0f, 1f) },
                            color = progressColor(progress.level),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(
                                R.string.budget_spent_of,
                                Formats.money(progress.spentCents),
                                Formats.money(progress.budgetCents),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

        // 未設定預算的分類：一鍵設定
        Text(
            text = stringResource(R.string.budget_category_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                state.categoryProgress.filter { it.second.budgetCents == 0L }.map { it.first },
                key = { it.id },
            ) { category ->
                FilterChip(
                    selected = false,
                    onClick = { editingCategoryId = category.id },
                    label = { Text(category.name) },
                )
            }
        }
    }

    if (editingTotal) {
        BudgetAmountDialog(
            title = stringResource(R.string.budget_total),
            onDismiss = { editingTotal = false },
            onSave = { cents ->
                viewModel.setTotalBudget(cents)
                editingTotal = false
                onSaved()
            },
        )
    }
    editingCategoryId?.let { categoryId ->
        BudgetAmountDialog(
            title = stringResource(R.string.budget_set),
            onDismiss = { editingCategoryId = null },
            onSave = { cents ->
                viewModel.setCategoryBudget(categoryId, cents)
                editingCategoryId = null
                onSaved()
            },
        )
    }
}


/** 預算金額對話框：留空或 0 表示移除 */
@Composable
private fun BudgetAmountDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(stringResource(R.string.budget_amount_hint)) },
                supportingText = { Text(stringResource(R.string.budget_remove)) },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onSave(AmountParser.toCents(text) ?: 0L) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

/** 週期交易區塊 */
@Composable
private fun RecurringSection(viewModel: RecurringViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<Long?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recurring_title),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { showAddDialog = true }) {
                Text(stringResource(R.string.recurring_add))
            }
        }

        if (state.items.isEmpty()) {
            Text(
                text = stringResource(R.string.recurring_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.items.forEach { item ->
            val accountName = state.accounts.firstOrNull { it.id == item.accountId }?.name ?: ""
            val categoryName = (
                state.expenseCategories + state.incomeCategories
                ).firstOrNull { it.id == item.categoryId }?.name ?: ""
            GlassCard(glassState = rememberHazeState(), contentPadding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                when (item.type) {
                                    TransactionType.EXPENSE -> R.string.add_type_expense
                                    else -> R.string.add_type_income
                                },
                            ) + " · " + categoryName + " · " + Formats.money(item.amountCents),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = accountName + " · " + frequencyLabel(item.frequency) +
                                describeRepeat(item) + " · " +
                                stringResource(
                                    R.string.recurring_next_due,
                                    Formats.date(item.nextDueEpochMillis),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FluidSwitch(
                        checked = item.isEnabled,
                        onCheckedChange = { viewModel.setEnabled(item.id, it) },
                    )
                    IconButton(onClick = { deletingId = item.id }) {
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

    if (showAddDialog) {
        RecurringAddDialog(
            state = state,
            onDismiss = { showAddDialog = false },
            onAdd = { type, amountCents, accountId, categoryId, note, frequency, dayOfWeek, dayOfMonth ->
                viewModel.add(
                    type, amountCents, accountId, categoryId, note,
                    frequency, dayOfWeek, dayOfMonth,
                ) { showAddDialog = false }
            },
        )
    }

    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = { Text(stringResource(R.string.transactions_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(id)
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
}

@Composable
private fun frequencyLabel(frequency: RecurringFrequency): String = stringResource(
    when (frequency) {
        RecurringFrequency.WEEKLY -> R.string.recurring_frequency_weekly
        RecurringFrequency.MONTHLY -> R.string.recurring_frequency_monthly
    },
)

@Composable
private fun describeRepeat(item: com.hmoney.app.domain.model.RecurringTransaction): String {
    return if (item.frequency == RecurringFrequency.WEEKLY) {
        stringResource(
            when (item.dayOfWeek ?: 1) {
                1 -> R.string.recurring_weekday_1
                2 -> R.string.recurring_weekday_2
                3 -> R.string.recurring_weekday_3
                4 -> R.string.recurring_weekday_4
                5 -> R.string.recurring_weekday_5
                6 -> R.string.recurring_weekday_6
                else -> R.string.recurring_weekday_7
            },
        )
    } else {
        "${item.dayOfMonth ?: 1}"
    }
}

/** 新增週期交易對話框 */
@Composable
private fun RecurringAddDialog(
    state: RecurringViewModel.UiState,
    onDismiss: () -> Unit,
    onAdd: (
        type: TransactionType,
        amountCents: Long,
        accountId: Long,
        categoryId: Long,
        note: String,
        frequency: String,
        dayOfWeek: Int?,
        dayOfMonth: Int?,
    ) -> Unit,
) {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<Long?>(state.accounts.firstOrNull()?.id) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(RecurringFrequency.MONTHLY) }
    var dayOfWeek by remember { mutableStateOf(1) }
    var dayOfMonthText by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurring_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 類型
                val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = {
                                type = t
                                categoryId = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            label = {
                                Text(
                                    stringResource(
                                        if (t == TransactionType.EXPENSE) {
                                            R.string.add_type_expense
                                        } else {
                                            R.string.add_type_income
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.add_amount_hint)) },
                    singleLine = true,
                )

                // 帳戶
                Text(stringResource(R.string.add_account), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.accounts, key = { it.id }) { account ->
                        FilterChip(
                            selected = accountId == account.id,
                            onClick = { accountId = account.id },
                            label = { Text(account.name) },
                        )
                    }
                }

                // 分類
                Text(stringResource(R.string.add_category), style = MaterialTheme.typography.titleSmall)
                val categories = if (type == TransactionType.EXPENSE) {
                    state.expenseCategories
                } else {
                    state.incomeCategories
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) },
                        )
                    }
                }

                // 頻率
                val frequencies = RecurringFrequency.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    frequencies.forEachIndexed { index, f ->
                        SegmentedButton(
                            selected = frequency == f,
                            onClick = { frequency = f },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = frequencies.size,
                            ),
                            label = {
                                Text(
                                    stringResource(
                                        if (f == RecurringFrequency.WEEKLY) {
                                            R.string.recurring_frequency_weekly
                                        } else {
                                            R.string.recurring_frequency_monthly
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }

                if (frequency == RecurringFrequency.WEEKLY) {
                    Text(
                        stringResource(R.string.recurring_day_of_week),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((1..7).toList()) { dow ->
                            FilterChip(
                                selected = dayOfWeek == dow,
                                onClick = { dayOfWeek = dow },
                                label = {
                                    Text(
                                        stringResource(
                                            when (dow) {
                                                1 -> R.string.recurring_weekday_1
                                                2 -> R.string.recurring_weekday_2
                                                3 -> R.string.recurring_weekday_3
                                                4 -> R.string.recurring_weekday_4
                                                5 -> R.string.recurring_weekday_5
                                                6 -> R.string.recurring_weekday_6
                                                else -> R.string.recurring_weekday_7
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = dayOfMonthText,
                        onValueChange = { dayOfMonthText = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.recurring_day_of_month)) },
                        supportingText = {
                            Text(stringResource(R.string.recurring_day_of_month_hint))
                        },
                        singleLine = true,
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.add_note)) },
                    placeholder = { Text(stringResource(R.string.add_note_hint)) },
                    singleLine = true,
                )

                if (error) {
                    Text(
                        text = stringResource(R.string.recurring_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountCents = AmountParser.toCents(amountText) ?: -1L
                    val dayOfMonth = dayOfMonthText.toIntOrNull()?.coerceIn(1, 31)
                    if (amountCents <= 0L || accountId == null || categoryId == null ||
                        (frequency == RecurringFrequency.MONTHLY && dayOfMonth == null)
                    ) {
                        error = true
                    } else {
                        onAdd(
                            type,
                            amountCents,
                            accountId!!,
                            categoryId!!,
                            note.trim(),
                            frequency.name,
                            if (frequency == RecurringFrequency.WEEKLY) dayOfWeek else null,
                            if (frequency == RecurringFrequency.MONTHLY) dayOfMonth else null,
                        )
                    }
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
