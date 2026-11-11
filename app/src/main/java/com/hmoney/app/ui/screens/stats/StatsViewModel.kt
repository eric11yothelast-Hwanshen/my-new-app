package com.hmoney.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * 統計 ViewModel：日／週／月／年切換、支出分類占比、收支趨勢、
 * 分類支出排行榜（含前期增減），可切換單一帳戶／全部帳戶。
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    /** 統計時間維度 */
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

    data class UiState(
        val period: Period = Period.MONTH,
        val accountId: Long? = null,
        val accounts: List<Account> = emptyList(),
        val totalExpenseCents: Long = 0L,
        val totalIncomeCents: Long = 0L,
        val categorySlices: List<CategorySlice> = emptyList(),
        val trend: List<TrendBucket> = emptyList(),
        val ranking: List<RankEntry> = emptyList(),
    )

    private val period = MutableStateFlow(Period.MONTH)
    private val accountId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<UiState> = combine(
        period,
        accountId,
        accountRepository.observeAll(),
        combine(
            categoryRepository.observeAll(com.hmoney.app.domain.model.CategoryType.EXPENSE),
            categoryRepository.observeAll(com.hmoney.app.domain.model.CategoryType.INCOME),
        ) { expense, income -> expense + income },
        transactionRepository.observeTransactions(),
    ) { p, accId, accounts, categories, transactions ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        // 依維度計算當前期間與前期範圍
        val range = when (p) {
            Period.DAY -> today.atStartOfDay(zone) to today.plusDays(1).atStartOfDay(zone)
            Period.WEEK -> {
                val start = today.with(DayOfWeek.MONDAY)
                start.atStartOfDay(zone) to start.plusWeeks(1).atStartOfDay(zone)
            }
            Period.MONTH -> {
                val start = today.withDayOfMonth(1)
                start.atStartOfDay(zone) to start.plusMonths(1).atStartOfDay(zone)
            }
            Period.YEAR -> {
                val start = today.withDayOfYear(1)
                start.atStartOfDay(zone) to start.plusYears(1).atStartOfDay(zone)
            }
        }
        val (rangeStart, rangeEnd) = range
        val durationMillis = rangeEnd.toInstant().toEpochMilli() -
            rangeStart.toInstant().toEpochMilli()
        val prevStart = rangeStart.toInstant().toEpochMilli() - durationMillis

        val inAccount = { txAccountId: Long, toAccountId: Long? ->
            accId == null || txAccountId == accId || toAccountId == accId
        }
        val current = transactions.filter {
            it.dateEpochMillis >= rangeStart.toInstant().toEpochMilli() &&
                it.dateEpochMillis < rangeEnd.toInstant().toEpochMilli() &&
                inAccount(it.accountId, it.toAccountId)
        }
        val previous = transactions.filter {
            it.dateEpochMillis >= prevStart &&
                it.dateEpochMillis < rangeStart.toInstant().toEpochMilli() &&
                inAccount(it.accountId, it.toAccountId)
        }

        // 支出分類占比（轉帳不計入）
        val expenseByCategory = current
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }
        val totalExpense = expenseByCategory.values.sum()
        val categoryMap = categories.associateBy { it.id }
        val slices = expenseByCategory.entries
            .mapNotNull { (id, cents) ->
                categoryMap[id]?.let { cat ->
                    CategorySlice(cat, cents, if (totalExpense == 0L) 0f else cents.toFloat() / totalExpense)
                }
            }
            .sortedByDescending { it.cents }

        // 收支趨勢：依維度分桶
        val buckets = buildTrend(p, current, zone, today)

        // 排行榜 Top 10（含前期增減）
        val prevByCategory = previous
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }
        val ranking = slices.take(10).map { slice ->
            RankEntry(slice.category, slice.cents, prevByCategory[slice.category.id] ?: 0L)
        }

        UiState(
            period = p,
            accountId = accId,
            accounts = accounts,
            totalExpenseCents = totalExpense,
            totalIncomeCents = current
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amountCents },
            categorySlices = slices,
            trend = buckets,
            ranking = ranking,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    // 依維度建立趨勢分桶
    private fun buildTrend(
        period: Period,
        transactions: List<com.hmoney.app.domain.model.MoneyTransaction>,
        zone: ZoneId,
        today: LocalDate,
    ): List<TrendBucket> = when (period) {
        Period.DAY -> (0..23).map { hour ->
            val cents = transactions.filter {
                it.dateEpochMillis / 3_600_000 == today.atStartOfDay(zone)
                    .toInstant().toEpochMilli() / 3_600_000 + hour
            }
            TrendBucket(
                label = "$hour",
                incomeCents = cents.filter { it.type == TransactionType.INCOME }
                    .sumOf { it.amountCents },
                expenseCents = cents.filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amountCents },
            )
        }
        Period.WEEK -> (0..6).map { offset ->
            val day = today.with(DayOfWeek.MONDAY).plusDays(offset.toLong())
            val cents = transactions.filter {
                val d = java.time.Instant.ofEpochMilli(it.dateEpochMillis).atZone(zone).toLocalDate()
                d == day
            }
            TrendBucket(
                label = day.dayOfWeek.name.take(3),
                incomeCents = cents.filter { it.type == TransactionType.INCOME }
                    .sumOf { it.amountCents },
                expenseCents = cents.filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amountCents },
            )
        }
        Period.MONTH -> {
            val days = today.lengthOfMonth()
            (1..days).map { day ->
                val cents = transactions.filter {
                    val d = java.time.Instant.ofEpochMilli(it.dateEpochMillis).atZone(zone).toLocalDate()
                    d == today.withDayOfMonth(day)
                }
                TrendBucket(
                    label = day.toString(),
                    incomeCents = cents.filter { it.type == TransactionType.INCOME }
                        .sumOf { it.amountCents },
                    expenseCents = cents.filter { it.type == TransactionType.EXPENSE }
                        .sumOf { it.amountCents },
                )
            }
        }
        Period.YEAR -> (1..12).map { month ->
            val ym = YearMonth.of(today.year, month)
            val cents = transactions.filter {
                val d = java.time.Instant.ofEpochMilli(it.dateEpochMillis).atZone(zone).toLocalDate()
                YearMonth.from(d) == ym
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

    fun setPeriod(p: Period) = period.update { p }

    fun setAccount(id: Long?) = accountId.update { id }
}
