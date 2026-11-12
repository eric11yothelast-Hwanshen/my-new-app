package com.hmoney.app.ui.screens.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.common.IconMapper
import com.hmoney.app.ui.components.AiFloatingButton
import com.hmoney.app.ui.components.AutoHideFabColumn
import com.hmoney.app.ui.components.AiResultDialog
import com.hmoney.app.ui.components.AnimatedAmountText
import com.hmoney.app.ui.components.EmptyState
import com.hmoney.app.ui.components.GlassCard
import com.hmoney.app.ui.components.TransactionRow
import com.hmoney.app.ui.components.glassSource
import com.hmoney.app.ui.screens.add.AddTransactionSheet
import com.hmoney.app.ui.screens.ai.AiAssistantSheet
import com.hmoney.app.ui.screens.ai.FeatureAiViewModel
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/**
 * 總覽頁：本月收支玻璃卡、總餘額（數字滾動）、帳戶餘額列、近期明細與快速記帳 FAB。
 */
@Composable
fun OverviewScreen(
    modifier: Modifier = Modifier,
    onGoSettings: () -> Unit = {},
    viewModel: OverviewViewModel = hiltViewModel(),
    aiViewModel: FeatureAiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val aiState by aiViewModel.uiState.collectAsState()
    var insightUnread by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    val glassState = rememberHazeState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    // 有新的本週洞察時，✨ 入口顯示小圓點提示；點開後歸零
    LaunchedEffect(aiState) {
        if (aiState is FeatureAiViewModel.UiState.Insight) insightUnread = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .glassSource(glassState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 160.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.overview_title),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // 本週 AI 洞察小入口：點擊後以 AiResultDialog 顯示（未設定 AI 時由對話框引導前往設定）
                            Box {
                                IconButton(
                                    onClick = {
                                        insightUnread = false
                                        aiViewModel.weekInsight()
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        contentDescription = stringResource(R.string.ai_insight_week),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (insightUnread) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 8.dp, end = 8.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }
                            IconButton(onClick = onGoSettings) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.tab_settings),
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.overview_total_balance),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedAmountText(cents = state.totalBalanceCents)
                }
            }

            // 本月收支玻璃卡
            item {
                GlassCard(
                    glassState = glassState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.overview_month_summary),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MonthAmountBlock(
                            label = stringResource(R.string.overview_month_expense),
                            cents = state.monthExpenseCents,
                            icon = Icons.AutoMirrored.Outlined.CallMade,
                            accentColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        MonthAmountBlock(
                            label = stringResource(R.string.overview_month_income),
                            cents = state.monthIncomeCents,
                            icon = Icons.AutoMirrored.Outlined.CallReceived,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // 本月預算進度卡
            item {
                GlassCard(
                    glassState = glassState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.overview_budget_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.budgetCents == 0L) {
                            TextButton(onClick = onGoSettings) {
                                Text(text = stringResource(R.string.overview_budget_go_set))
                            }
                        }
                    }
                    if (state.budgetCents == 0L) {
                        Text(
                            text = stringResource(R.string.overview_budget_not_set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val ratio = state.monthExpenseCents.toFloat() / state.budgetCents
                        val remainingCents = state.budgetCents - state.monthExpenseCents
                        val dailyAvailableCents = remainingCents.coerceAtLeast(0L) /
                            state.daysRemainingInMonth
                        val statusColor = when {
                            ratio > 0.9f -> MaterialTheme.colorScheme.error
                            ratio >= 0.6f -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        LinearProgressIndicator(
                            progress = { ratio.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = statusColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.overview_budget_spent,
                                Formats.moneyWithCurrency(state.monthExpenseCents),
                                Formats.moneyWithCurrency(state.budgetCents),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (ratio > 0.9f) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        // 剩餘與每日可用壓縮為同一列，提高首屏資訊密度
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.overview_budget_remaining_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                AnimatedAmountText(
                                    cents = remainingCents,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.overview_budget_daily_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                AnimatedAmountText(
                                    cents = dailyAvailableCents,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // 帳戶餘額列
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.overview_accounts_section),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.accountsWithBalance, key = { it.first.id }) { (account, balance) ->
                            GlassCard(
                                glassState = glassState,
                                contentPadding = 12.dp,
                                cornerRadius = 16.dp,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = IconMapper.iconOf(account.iconKey),
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color(account.colorArgb),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column {
                                        Text(
                                            text = account.name,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        Text(
                                            text = Formats.moneyWithCurrency(
                                                balance,
                                                account.currency,
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 近期明細
            item {
                Text(
                    text = stringResource(R.string.overview_recent_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            if (state.recentTransactions.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        messageRes = R.string.overview_empty,
                    )
                }
            } else {
                items(state.recentTransactions, key = { it.id }) { tx ->
                    TransactionRow(
                        transaction = tx,
                        category = state.categories[tx.categoryId],
                        accountName = state.accountsWithBalance
                            .firstOrNull { it.first.id == tx.accountId }?.first?.name,
                        toAccountName = state.accountsWithBalance
                            .firstOrNull { it.first.id == tx.toAccountId }?.first?.name,
                    )
                }
            }
        }

        // 浮動按鈕：AI 助手＋快速記帳（滾動離開頂端時自動隱藏）
        AutoHideFabColumn(
            listState = listState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            AiFloatingButton(onClick = { showAiSheet = true })
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.overview_add)) },
            )
        }

        // 儲存成功回饋（Snackbar）
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }

    if (showAddSheet) {
        AddTransactionSheet(
            onDismiss = { showAddSheet = false },
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

    if (aiState !is FeatureAiViewModel.UiState.Idle) {
        AiResultDialog(
            state = aiState,
            onCancel = aiViewModel::cancel,
            onGoSettings = onGoSettings,
            onDismiss = aiViewModel::reset,
            onRetry = aiViewModel::weekInsight,
        )
    }
}

/**
 * 本月收支小卡：有資料（> 0）用強調色邊框＋圖示＋粗體金額；
 * 0 元整體降飽和為灰色調，一眼分辨本月有沒有記帳。
 */
@Composable
private fun MonthAmountBlock(
    label: String,
    cents: Long,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val hasData = cents > 0
    val contentColor = if (hasData) {
        accentColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (hasData) {
        accentColor
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedAmountText(
                cents = cents,
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
                fontWeight = if (hasData) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
