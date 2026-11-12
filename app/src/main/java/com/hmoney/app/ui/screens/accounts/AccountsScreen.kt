package com.hmoney.app.ui.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.domain.model.Account
import com.hmoney.app.ui.common.AmountParser
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.common.IconMapper
import com.hmoney.app.ui.components.AiFloatingButton
import com.hmoney.app.ui.components.AutoHideFabColumn
import com.hmoney.app.ui.components.AiResultDialog
import com.hmoney.app.ui.components.EmptyState
import com.hmoney.app.ui.components.GlassCard
import com.hmoney.app.ui.components.glassSource
import com.hmoney.app.ui.theme.AccountPalette
import com.hmoney.app.ui.screens.ai.AiAssistantSheet
import com.hmoney.app.ui.screens.ai.FeatureAiViewModel
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/**
 * 帳戶頁：帳戶玻璃卡片（含即時餘額，以帳戶色主題化）＋管理（新增／編輯／隱藏／刪除）。
 */
@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    onGoSettings: () -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel(),
    aiViewModel: FeatureAiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val aiState by aiViewModel.uiState.collectAsState()
    val glassState = rememberHazeState()

    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var hideTarget by remember { mutableStateOf<Account?>(null) }
    var showAiSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val deleteBlockedMessage = stringResource(R.string.accounts_delete_fail)
    val savedMessage = stringResource(R.string.accounts_saved)
    val deletedMessage = stringResource(R.string.accounts_deleted)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // 滾動離開頂端時自動隱藏
            AutoHideFabColumn(
                listState = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                AiFloatingButton(onClick = { showAiSheet = true })
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.accounts_add)) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .glassSource(glassState),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.accounts_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                IconButton(onClick = onGoSettings) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.tab_settings),
                    )
                }
            }

            // AI 解讀帳戶：餘額變化與現金流建議
            val analyzeTitle = stringResource(R.string.ai_analyze_accounts)
            AssistChip(
                onClick = {
                    val data = state.accountsWithBalance.joinToString("\n") { (account, balance) ->
                        "- ${account.name}（${account.currency}）：" +
                            "${Formats.moneyWithCurrency(balance, account.currency)}"
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
                label = { Text(stringResource(R.string.ai_analyze_accounts)) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            if (state.accountsWithBalance.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    messageRes = R.string.accounts_empty,
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 160.dp),
                ) {
                    items(state.accountsWithBalance, key = { it.first.id }) { (account, balance) ->
                        val accentColor = Color(account.colorArgb)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            GlassCard(
                                glassState = glassState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingAccount = account },
                            ) {
                                Row(
                                    modifier = Modifier.height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .background(accentColor, RoundedCornerShape(2.dp)),
                                    )
                                    Icon(
                                        imageVector = IconMapper.iconOf(account.iconKey),
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(32.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = account.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            if (account.isHidden) {
                                                Text(
                                                    text = stringResource(R.string.accounts_hidden_tag),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Text(
                                            text = stringResource(R.string.accounts_currency_tag, account.currency),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = Formats.moneyWithCurrency(balance, account.currency),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        accentColor.copy(alpha = 0.12f),
                                        RoundedCornerShape(24.dp),
                                    ),
                            )
                        }
                    }
                    item {
                        if (state.hiddenAccountCount > 0) {
                            TextButton(
                                onClick = viewModel::toggleShowHidden,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            ) {
                                Text(stringResource(R.string.accounts_show_hidden))
                            }
                        }
                    }
                }
            }
        }
    }

    // 全域 AI 助手
    if (showAiSheet) {
        AiAssistantSheet(
            onDismiss = { showAiSheet = false },
            onGoSettings = {
                showAiSheet = false
                onGoSettings()
            },
        )
    }

    if (aiState !is FeatureAiViewModel.UiState.Idle) {
        AiResultDialog(
            state = aiState,
            onCancel = aiViewModel::cancel,
            onGoSettings = onGoSettings,
            onDismiss = aiViewModel::reset,
        )
    }

    // 新增帳戶
    if (showAddDialog) {
        AccountDialog(
            title = stringResource(R.string.accounts_add),
            initialAccount = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, iconKey, color, initialCents ->
                viewModel.addAccount(name, iconKey, color, initialCents)
                showAddDialog = false
                scope.launch { snackbarHostState.showSnackbar(savedMessage) }
            },
        )
    }

    // 編輯帳戶
    editingAccount?.let { account ->
        AccountDialog(
            title = stringResource(R.string.accounts_edit),
            initialAccount = account,
            onDismiss = { editingAccount = null },
            onConfirm = { name, iconKey, color, _ ->
                viewModel.updateAccount(account, name, iconKey, color)
                editingAccount = null
                scope.launch { snackbarHostState.showSnackbar(savedMessage) }
            },
            onHide = {
                hideTarget = account
                editingAccount = null
            },
            onDelete = {
                scope.launch {
                    // 僅無交易的帳戶可刪；有交易則提示僅能隱藏
                    val deleted = viewModel.deleteAccount(account.id)
                    if (deleted) {
                        snackbarHostState.showSnackbar(deletedMessage)
                    } else {
                        snackbarHostState.showSnackbar(deleteBlockedMessage)
                    }
                }
                editingAccount = null
            },
        )
    }

    // 隱藏確認
    hideTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { hideTarget = null },
            title = { Text(stringResource(R.string.accounts_hide)) },
            text = {
                Text(stringResource(R.string.accounts_hide_confirm, account.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.hideAccount(account.id)
                        hideTarget = null
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { hideTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

}

/**
 * 帳戶新增／編輯對話框：名稱、icon、顏色、期初餘額（僅新增時可填）。
 */
@Composable
private fun AccountDialog(
    title: String,
    initialAccount: Account?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconKey: String, colorArgb: Long, initialBalanceCents: Long) -> Unit,
    onHide: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialAccount?.name ?: "") }
    var iconKey by remember { mutableStateOf(initialAccount?.iconKey ?: "payments") }
    var color by remember { mutableStateOf(initialAccount?.colorArgb ?: AccountPalette.colorArgbs[0]) }
    var balanceText by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.accounts_name)) },
                    isError = nameError,
                    supportingText = {
                        if (nameError) {
                            Text(stringResource(R.string.accounts_name_required))
                        }
                    },
                    singleLine = true,
                )

                Text(stringResource(R.string.accounts_icon), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(IconMapper.accountIconKeys) { key ->
                        Icon(
                            imageVector = IconMapper.iconOf(key),
                            contentDescription = null,
                            tint = if (iconKey == key) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { iconKey = key }
                                .padding(4.dp),
                        )
                    }
                }

                Text(stringResource(R.string.accounts_color), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AccountPalette.colorArgbs) { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(c), CircleShape)
                                .border(
                                    width = if (color == c) 3.dp else 1.dp,
                                    color = if (color == c) {
                                        MaterialTheme.colorScheme.primary
                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }

                if (initialAccount == null) {
                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { balanceText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.accounts_initial_balance)) },
                        placeholder = { Text(stringResource(R.string.accounts_initial_balance_hint)) },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        onConfirm(
                            name.trim(),
                            iconKey,
                            color,
                            AmountParser.toCents(balanceText) ?: 0L,
                        )
                    }
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            stringResource(R.string.common_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (onHide != null) {
                    TextButton(onClick = onHide) {
                        Text(stringResource(R.string.accounts_hide))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}
