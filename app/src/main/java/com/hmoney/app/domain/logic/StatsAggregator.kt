package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * 統計彙總（純邏輯，可單元測試）：
 * 依日／週／月／年計算期間範圍、支出分類占比、收支趨勢分桶與排行榜（含前期增減）。
 */
class StatsAggregator @Inject constructor() {

    enum class Period { DAY, WEEK, MONTH, YEAR }

    data class CategorySlice(
        val category: Category,
        val cents: Long,
        val ratio: Float,
    )

    data class TrendBucket(
        val label: String,
        val incomeCents: Long,
        val expenseCents: Long,
    )

    data class RankEntry(
        val category: Category,
        val cents: Long,
        val previousCents: Long,
    )

    data class Aggregation(
        val totalExpenseCents: Long,
        val totalIncomeCents: Long,
        val categorySlices: List<CategorySlice>,
        val trend: List<TrendBucket>,
        val ranking: List<RankEntry>,
    )

    /** 依維度計算期間範圍（起點、終點） */
    fun rangeOf(period: Period, today: LocalDate, zone: ZoneId): Pair<Long, Long> {
        val (start, end) = when (period) {
            Period.DAY -> today to today.plusDays(1)
            Period.WEEK -> {
                val start = today.with(DayOfWeek.MONDAY)
                start to start.plusWeeks(1)
            }
            Period.MONTH -> {
                val start = today.withDayOfMonth(1)
                start to start.plusMonths(1)
            }
            Period.YEAR -> {
                val start = today.withDayOfYear(1)
                start to start.plusYears(1)
            }
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to
            end.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** 主彙總：期間內收支、分類占比、趨勢分桶、Top 10 排行（含前期增減） */
    fun aggregate(
        transactions: List<MoneyTransaction>,
        categories: List<Category>,
        period: Period,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Aggregation {
        val (rangeStart, rangeEnd) = rangeOf(period, today, zone)
        val duration = rangeEnd - rangeStart
        val prevStart = rangeStart - duration

        val inAccount: (Long, Long?) -> Boolean = { _, _ -> true }
        val current = transactions.filter {
            it.dateEpochMillis >= rangeStart &&
                it.dateEpochMillis < rangeEnd &&
                inAccount(it.accountId, it.toAccountId)
        }
        val previous = transactions.filter {
            it.dateEpochMillis >= prevStart &&
                it.dateEpochMillis < rangeStart &&
                inAccount(it.accountId, it.toAccountId)
        }

        val categoryMap = categories.associateBy { it.id }
        val expenseByCategory = current
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }
        val totalExpense = expenseByCategory.values.sum()

        val slices = expenseByCategory.entries
            .mapNotNull { (id, cents) ->
                categoryMap[id]?.let { cat ->
                    CategorySlice(
                        category = cat,
                        cents = cents,
                        ratio = if (totalExpense == 0L) 0f
                        else cents.toFloat() / totalExpense,
                    )
                }
            }
            .sortedByDescending { it.cents }

        val prevByCategory = previous
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }
        val ranking = slices.take(10).map { slice ->
            RankEntry(slice.category, slice.cents, prevByCategory[slice.category.id] ?: 0L)
        }

        return Aggregation(
            totalExpenseCents = totalExpense,
            totalIncomeCents = current
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amountCents },
            categorySlices = slices,
            trend = buildTrend(period, current, zone, today),
            ranking = ranking,
        )
    }

    /** 依維度建立趨勢分桶 */
    internal fun buildTrend(
        period: Period,
        transactions: List<MoneyTransaction>,
        zone: ZoneId,
        today: LocalDate,
    ): List<TrendBucket> = when (period) {
        Period.DAY -> {
            val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            (0..23).map { hour ->
                val cents = transactions.filter {
                    it.dateEpochMillis / 3_600_000 == dayStart / 3_600_000 + hour
                }
                TrendBucket(
                    label = hour.toString(),
                    incomeCents = cents.filter { it.type == TransactionType.INCOME }
                        .sumOf { it.amountCents },
                    expenseCents = cents.filter { it.type == TransactionType.EXPENSE }
                        .sumOf { it.amountCents },
                )
            }
        }
        Period.WEEK -> (0..6).map { offset ->
            val day = today.with(DayOfWeek.MONDAY).plusDays(offset.toLong())
            val cents = transactions.filter {
                LocalDate.ofInstant(Instant.ofEpochMilli(it.dateEpochMillis), zone) == day
            }
            TrendBucket(
                label = day.dayOfWeek.name.take(3),
                incomeCents = cents.filter { it.type == TransactionType.INCOME }
                    .sumOf { it.amountCents },
                expenseCents = cents.filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amountCents },
            )
        }
        Period.MONTH -> (1..today.lengthOfMonth()).map { day ->
            // 以日期比對（同年同月同日）
            val dayTx = transactions.filter {
                val d = LocalDate.ofInstant(Instant.ofEpochMilli(it.dateEpochMillis), zone)
                d.year == today.year && d.month == today.month && d.dayOfMonth == day
            }
            TrendBucket(
                label = day.toString(),
                incomeCents = dayTx.filter { it.type == TransactionType.INCOME }
                    .sumOf { it.amountCents },
                expenseCents = dayTx.filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amountCents },
            )
        }
        Period.YEAR -> (1..12).map { month ->
            val cents = transactions.filter {
                val d = LocalDate.ofInstant(Instant.ofEpochMilli(it.dateEpochMillis), zone)
                YearMonth.from(d) == YearMonth.of(today.year, month)
            }
            TrendBucket(
                label = month.toString(),
                incomeCents = cents.filter { it.type == TransactionType.INCOME }
                    .sumOf { it.amountCents },
                expenseCents = cents.filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amountCents },
            )
        }
    }
}
