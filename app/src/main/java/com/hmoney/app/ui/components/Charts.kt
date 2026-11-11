package com.hmoney.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.domain.logic.StatsAggregator
import com.hmoney.app.ui.screens.stats.StatsViewModel

/**
 * 統計圖表（純 Compose Canvas 實作，配合整體玻璃態設計）。
 */

/** 支出分類占比：環圈圖 */
@Composable
fun DonutChart(
    slices: List<StatsAggregator.CategorySlice>,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.height(200.dp).fillMaxWidth()) {
            val stroke = Stroke(width = size.minDimension / 4f)
            val radius = (size.minDimension - stroke.width) / 2f
            val topLeft = Offset(
                (size.width - radius * 2) / 2f,
                (size.height - radius * 2) / 2f,
            )
            val arcSize = Size(radius * 2, radius * 2)
            var startAngle = -90f
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            slices.forEach { slice ->
                val sweep = slice.ratio * 360f
                drawArc(
                    color = Color(slice.category.colorArgb),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                startAngle += sweep
            }
        }
    }
}

/** 收支趨勢：成對長條圖（收入／支出） */
@Composable
fun TrendBarChart(
    buckets: List<StatsAggregator.TrendBucket>,
    modifier: Modifier = Modifier,
    incomeColor: Color = MaterialTheme.colorScheme.tertiary,
    expenseColor: Color = MaterialTheme.colorScheme.error,
) {
    val max = buckets.maxOfOrNull { maxOf(it.incomeCents, it.expenseCents) } ?: 0L
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(vertical = 8.dp),
    ) {
        if (buckets.isEmpty() || max == 0L) return@Canvas
        val gap = 4.dp.toPx()
        val slotWidth = size.width / buckets.size
        val barWidth = (slotWidth - gap * 3) / 2f
        buckets.forEachIndexed { index, bucket ->
            val slotLeft = index * slotWidth
            val incomeHeight = (bucket.incomeCents.toFloat() / max) * size.height
            val expenseHeight = (bucket.expenseCents.toFloat() / max) * size.height
            drawRect(
                color = incomeColor,
                topLeft = Offset(slotLeft + gap, size.height - incomeHeight),
                size = Size(barWidth, incomeHeight),
            )
            drawRect(
                color = expenseColor,
                topLeft = Offset(slotLeft + gap * 2 + barWidth, size.height - expenseHeight),
                size = Size(barWidth, expenseHeight),
            )
        }
    }
}

/** 排行榜列：分類名、金額、占比條與前期增減 */
@Composable
fun RankRow(
    rank: Int,
    entry: StatsAggregator.RankEntry,
    maxCents: Long,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$rank. ${entry.category.name}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = Formats.money(entry.cents),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            val delta = entry.cents - entry.previousCents
            val deltaText = when {
                entry.previousCents == 0L -> ""
                delta > 0 -> "+${delta * 100 / entry.previousCents}%"
                else -> "${delta * 100 / entry.previousCents}%"
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(6.dp)
                    .padding(top = 4.dp),
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val ratio = if (maxCents == 0L) 0f
                    else (entry.cents.toFloat() / maxCents).coerceIn(0f, 1f)
                    drawRect(
                        color = Color(entry.category.colorArgb).copy(alpha = 0.3f),
                        size = Size(size.width, size.height),
                    )
                    drawRect(
                        color = Color(entry.category.colorArgb),
                        size = Size(size.width * ratio, size.height),
                    )
                }
            }
            Text(
                text = deltaText,
                style = MaterialTheme.typography.labelSmall,
                color = if (delta > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
