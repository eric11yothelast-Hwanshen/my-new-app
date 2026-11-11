package com.hmoney.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.components.DonutChart
import com.hmoney.app.ui.components.EmptyState
import com.hmoney.app.ui.components.GlassCard
import com.hmoney.app.ui.components.RankRow
import com.hmoney.app.ui.components.TrendBarChart
import com.hmoney.app.ui.components.glassSource
import com.hmoney.app.ui.screens.stats.StatsViewModel
import dev.chrisbanes.haze.rememberHazeState

/**
 * 統計頁：圖表儀表板——支出分類占比（環圈）、收支趨勢（長條）、
 * 分類支出排行榜（Top 10，含前期增減），可切換日／週／月／年與帳戶視角。
 */
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val glassState = rememberHazeState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .glassSource(glassState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                val periods = StatsViewModel.Period.entries
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
                                            StatsViewModel.Period.DAY -> R.string.stats_period_day
                                            StatsViewModel.Period.WEEK -> R.string.stats_period_week
                                            StatsViewModel.Period.MONTH -> R.string.stats_period_month
                                            StatsViewModel.Period.YEAR -> R.string.stats_period_year
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
                    EmptyState(
                        icon = Icons.Outlined.PieChart,
                        messageRes = R.string.stats_empty,
                    )
                } else {
                    DonutChart(slices = state.categorySlices)
                    state.categorySlices.take(6).forEach { slice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(slice.category.colorArgb), CircleShape),
                                )
                                Text(
                                    text = slice.category.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Text(
                                text = "${(slice.ratio * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
}
