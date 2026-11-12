package com.hmoney.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.components.AiFloatingButton
import com.hmoney.app.ui.components.AutoHideFabColumn
import com.hmoney.app.ui.components.AiResultDialog
import com.hmoney.app.ui.components.DonutChart
import com.hmoney.app.ui.components.GlassCard
import com.hmoney.app.ui.components.RankRow
import com.hmoney.app.ui.components.TrendBarChart
import com.hmoney.app.ui.components.glassSource
import com.hmoney.app.domain.logic.StatsAggregator
import com.hmoney.app.ui.screens.ai.AiAssistantSheet
import com.hmoney.app.ui.screens.ai.FeatureAiViewModel
import com.hmoney.app.ui.screens.add.AddTransactionSheet
import com.hmoney.app.ui.screens.stats.StatsViewModel
import com.hmoney.app.ui.theme.CategoryPalette
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/**
 * 統計頁：圖表儀表板——支出分類占比（環圈）、收支趨勢（長條）、
 * 分類支出排行榜（Top 10，含前期增減），可切換日／週／月／年與帳戶視角。
 */
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    onGoSettings: () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
    aiViewModel: FeatureAiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val aiState by aiViewModel.uiState.collectAsState()
    val glassState = rememberHazeState()
    var showAiSheet by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

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
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.stats_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )

                // 時間維度切換：日／週／月／年
                val periods = StatsAggregator.Period.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    periods.forEachIndexed { index, p ->
                        SegmentedButton(
                            selected = state.period == p,
                            onClick = { viewModel.setPeriod(p) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = periods.size,
                            ),
                            label = {
                                Text(
                                    stringResource(
                                        when (p) {
                                            StatsAggregator.Period.DAY -> R.string.stats_period_day
                                            StatsAggregator.Period.WEEK -> R.string.stats_period_week
                                            StatsAggregator.Period.MONTH -> R.string.stats_period_month
                                            StatsAggregator.Period.YEAR -> R.string.stats_period_year
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }

                // 帳戶視角：全部／單一帳戶
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.accountId == null,
                            onClick = { viewModel.setAccount(null) },
                            label = { Text(stringResource(R.string.stats_all_accounts)) },
                        )
                    }
                    items(state.accounts, key = { it.id }) { account ->
                        FilterChip(
                            selected = state.accountId == account.id,
                            onClick = { viewModel.setAccount(account.id) },
                            label = { Text(account.name) },
                        )
                    }
                }

                // 期間收支合計
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.stats_period_expense),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Formats.moneyWithCurrency(state.totalExpenseCents),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.stats_period_income),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Formats.moneyWithCurrency(state.totalIncomeCents),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                // AI 分析本期：將該期間的分類統計送給 AI 解讀
                val periodLabel = stringResource(
                    when (state.period) {
                        StatsAggregator.Period.DAY -> R.string.stats_period_day
                        StatsAggregator.Period.WEEK -> R.string.stats_period_week
                        StatsAggregator.Period.MONTH -> R.string.stats_period_month
                        StatsAggregator.Period.YEAR -> R.string.stats_period_year
                    },
                )
                val analyzeTitle = stringResource(R.string.ai_analyze_period)
                AssistChip(
                    onClick = {
                        val slices = state.categorySlices.joinToString("\n") { slice ->
                            "- ${slice.category.name}：${Formats.money(slice.cents)}" +
                                "（占 ${(slice.ratio * 100).toInt()}%）"
                        }
                        val ranking = state.ranking.joinToString("\n") { entry ->
                            "- ${entry.category.name}：${Formats.money(entry.cents)}" +
                                "（前期 ${Formats.money(entry.previousCents)}）"
                        }
                        val data = "期間：$periodLabel\n" +
                            "總支出：${Formats.money(state.totalExpenseCents)}\n" +
                            "總收入：${Formats.money(state.totalIncomeCents)}\n" +
                            "支出分類占比：\n$slices\n分類排行（含前期比較）：\n$ranking"
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
                    label = { Text(stringResource(R.string.ai_analyze_period)) },
                )
            }
        }

        // 支出分類占比
        item {
            GlassCard(
                glassState = glassState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = stringResource(R.string.stats_category_share),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.categorySlices.isEmpty()) {
                    StatsChartEmptyState(
                        onAddTransaction = { showAddSheet = true },
                    )
                } else {
                    val sliceDetails = state.categorySlices.associateBy({ it.category.id }) { slice ->
                        stringResource(
                            R.string.stats_slice_detail,
                            slice.category.name,
                            Formats.moneyWithCurrency(slice.cents),
                            (slice.ratio * 100).toInt(),
                        )
                    }
                    DonutChart(
                        slices = state.categorySlices,
                        onSliceClick = { slice ->
                            val detail = sliceDetails[slice.category.id].orEmpty()
                            scope.launch { snackbarHostState.showSnackbar(detail) }
                        },
                    )
                    state.categorySlices.forEach { slice ->
                        val sliceColor = CategoryPalette.colorOf(
                            slice.category.name,
                            slice.category.colorArgb,
                        )
                        val percent = (slice.ratio * 100).toInt()
                        val detail = stringResource(
                            R.string.stats_slice_detail,
                            slice.category.name,
                            Formats.moneyWithCurrency(slice.cents),
                            percent,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch { snackbarHostState.showSnackbar(detail) }
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(sliceColor, CircleShape),
                                )
                                Text(
                                    text = slice.category.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = Formats.moneyWithCurrency(slice.cents),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 收支趨勢
        item {
            GlassCard(
                glassState = glassState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = stringResource(R.string.stats_trend),
                    style = MaterialTheme.typography.titleMedium,
                )
                TrendBarChart(buckets = state.trend)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                        )
                        Text(
                            text = stringResource(R.string.add_type_income),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape),
                        )
                        Text(
                            text = stringResource(R.string.add_type_expense),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        // 分類支出排行榜（Top 10，含前期增減）
        item {
            GlassCard(
                glassState = glassState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = stringResource(R.string.stats_ranking),
                    style = MaterialTheme.typography.titleMedium,
                )
                val maxCents = state.ranking.maxOfOrNull { it.cents } ?: 0L
                state.ranking.forEachIndexed { index, entry ->
                    RankRow(
                        rank = index + 1,
                        entry = entry,
                        maxCents = maxCents,
                    )
                }
            }
        }
        }

        // 浮動 AI 按鈕（全域 AI 助手，滾動離開頂端時自動隱藏）
        AutoHideFabColumn(
            listState = listState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            AiFloatingButton(onClick = { showAiSheet = true })
        }

        // 圖例／圓餅分區點擊後的分類金額詳細
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }

    if (showAddSheet) {
        AddTransactionSheet(onDismiss = { showAddSheet = false })
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
        )
    }
}

/**
 * 統計頁圖表空狀態：大圖示＋主標題＋引導文字＋「去記一筆」按鈕。
 */
@Composable
private fun StatsChartEmptyState(
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.PieChart,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = stringResource(R.string.stats_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.stats_empty_guide),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddTransaction) {
            Text(text = stringResource(R.string.stats_empty_cta))
        }
    }
}
