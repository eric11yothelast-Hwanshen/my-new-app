package com.hmoney.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.logic.StatsAggregator
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.CategoryType
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
import java.time.ZoneId
import javax.inject.Inject

/**
 * 統計 ViewModel：日／週／月／年切換、支出分類占比、收支趨勢、
 * 分類支出排行榜（含前期增減），可切換單一帳戶／全部帳戶。
 * 彙總計算委派 StatsAggregator（可單元測試）。
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val aggregator: StatsAggregator,
) : ViewModel() {

    data class UiState(
        val period: StatsAggregator.Period = StatsAggregator.Period.MONTH,
        val accountId: Long? = null,
        val accounts: List<Account> = emptyList(),
        val totalExpenseCents: Long = 0L,
        val totalIncomeCents: Long = 0L,
        val categorySlices: List<StatsAggregator.CategorySlice> = emptyList(),
        val trend: List<StatsAggregator.TrendBucket> = emptyList(),
        val ranking: List<StatsAggregator.RankEntry> = emptyList(),
    )

    private val period = MutableStateFlow(StatsAggregator.Period.MONTH)
    private val accountId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<UiState> = combine(
        period,
        accountId,
        accountRepository.observeAll(),
        combine(
            categoryRepository.observeAll(CategoryType.EXPENSE),
            categoryRepository.observeAll(CategoryType.INCOME),
        ) { expense, income -> expense + income },
        transactionRepository.observeTransactions(),
    ) { p, accId, accounts, categories, transactions ->
        // 帳戶視角過濾（轉帳亦計入轉入帳戶）
        val filtered = transactions.filter { tx ->
            accId == null || tx.accountId == accId || tx.toAccountId == accId
        }
        val aggregation = aggregator.aggregate(
            transactions = filtered,
            categories = categories,
            period = p,
            zone = ZoneId.systemDefault(),
        )
        UiState(
            period = p,
            accountId = accId,
            accounts = accounts,
            totalExpenseCents = aggregation.totalExpenseCents,
            totalIncomeCents = aggregation.totalIncomeCents,
            categorySlices = aggregation.categorySlices,
            trend = aggregation.trend,
            ranking = aggregation.ranking,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    fun setPeriod(p: StatsAggregator.Period) = period.update { p }

    fun setAccount(id: Long?) = accountId.update { id }
}
