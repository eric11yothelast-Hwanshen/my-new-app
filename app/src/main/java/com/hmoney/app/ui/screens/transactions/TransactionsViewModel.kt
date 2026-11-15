package com.hmoney.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * 明細 ViewModel：關鍵字搜尋＋篩選（期間／帳戶／分類／類型），按日分組與當日小計。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    /** 篩選條件 */
    data class Filters(
        val keyword: String = "",
        val type: TransactionType? = null,
        val accountId: Long? = null,
        val categoryId: Long? = null,
        val startDate: Long? = null,
        val endDate: Long? = null,
    )

    data class DayGroup(
        val epochDay: Long,
        val transactions: List<MoneyTransaction>,
        val expenseCents: Long,
        val incomeCents: Long,
    )

    data class UiState(
        val filters: Filters = Filters(),
        val groups: List<DayGroup> = emptyList(),
        val accounts: List<Account> = emptyList(),
        val categories: Map<Long, Category> = emptyMap(),
        val filterCategories: List<Category> = emptyList(),
    )

    private val filters = MutableStateFlow(Filters())

    // 依篩選條件查詢交易（條件變動時自動重查）
    private val filteredTransactions = filters.flatMapLatest { f ->
        transactionRepository.observeTransactions(
            keyword = f.keyword,
            startDate = f.startDate,
            endDate = f.endDate,
            accountId = f.accountId,
            categoryId = f.categoryId,
            type = f.type,
        )
    }

    val uiState: StateFlow<UiState> = combine(
        filters,
        accountRepository.observeAll(),
        combine(
            categoryRepository.observeAll(CategoryType.EXPENSE),
            categoryRepository.observeAll(CategoryType.INCOME),
        ) { expense, income -> expense + income },
        combine(
            categoryRepository.observeActive(CategoryType.EXPENSE),
            categoryRepository.observeActive(CategoryType.INCOME),
        ) { expense, income -> expense + income },
        filteredTransactions,
    ) { f, accounts, categories, activeCategories, transactions ->
        UiState(
            filters = f,
            accounts = accounts,
            categories = categories.associateBy { it.id },
            filterCategories = activeCategories.sortedBy { it.sortOrder },
            groups = groupByDay(transactions),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    // 按日分組（新→舊），各自計算支出／收入小計
    private fun groupByDay(transactions: List<MoneyTransaction>): List<DayGroup> =
        transactions
            .groupBy { tx ->
                Instant.ofEpochMilli(tx.dateEpochMillis).atZone(ZoneId.systemDefault())
                    .toLocalDate().toEpochDay()
            }
            .map { (epochDay, dayTx) ->
                DayGroup(
                    epochDay = epochDay,
                    transactions = dayTx,
                    expenseCents = dayTx
                        .filter { it.type == TransactionType.EXPENSE }
                        .sumOf { it.amountCents },
                    incomeCents = dayTx
                        .filter { it.type == TransactionType.INCOME }
                        .sumOf { it.amountCents },
                )
            }
            .sortedByDescending { it.epochDay }

    fun setKeyword(keyword: String) = filters.update { it.copy(keyword = keyword) }

    fun setType(type: TransactionType?) = filters.update { it.copy(type = type) }

    fun setAccount(accountId: Long?) = filters.update { it.copy(accountId = accountId) }

    fun setCategory(categoryId: Long?) = filters.update { it.copy(categoryId = categoryId) }

    fun setDateRange(start: Long?, end: Long?) =
        filters.update { it.copy(startDate = start, endDate = end) }

    fun clearFilters() = filters.update { Filters() }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { transactionRepository.deleteTransaction(id) }
    }
}
