package com.hmoney.app.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.BudgetType
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.BudgetRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 總覽 ViewModel：本月收支、本月預算進度、總餘額、帳戶餘額列與近期明細。
 */
@HiltViewModel
class OverviewViewModel @Inject constructor(
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    budgetRepository: BudgetRepository,
) : ViewModel() {

    data class UiState(
        val monthExpenseCents: Long = 0L,
        val monthIncomeCents: Long = 0L,
        val totalBalanceCents: Long = 0L,
        val budgetCents: Long = 0L,
        val daysRemainingInMonth: Int = 1,
        val accountsWithBalance: List<Pair<Account, Long>> = emptyList(),
        val categories: Map<Long, Category> = emptyMap(),
        val recentTransactions: List<MoneyTransaction> = emptyList(),
    )

    val uiState: StateFlow<UiState> = combine(
        accountRepository.observeVisibleWithBalance(),
        categoryRepository.observeAll(com.hmoney.app.domain.model.CategoryType.EXPENSE),
        categoryRepository.observeAll(com.hmoney.app.domain.model.CategoryType.INCOME),
        transactionRepository.observeTransactions(),
        budgetRepository.observeAll(),
    ) { accountsWithBalance, expenseCategories, incomeCategories, transactions, budgets ->
        val categoryMap = (expenseCategories + incomeCategories).associateBy { it.id }
        // 本月收支（轉帳不計入收支統計）
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val monthEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val monthTx = transactions.filter { it.dateEpochMillis in monthStart until monthEnd }
        UiState(
            monthExpenseCents = monthTx
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amountCents },
            monthIncomeCents = monthTx
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amountCents },
            totalBalanceCents = accountsWithBalance.sumOf { it.second },
            budgetCents = budgets
                .firstOrNull { it.type == BudgetType.TOTAL }?.amountCents ?: 0L,
            daysRemainingInMonth = today.lengthOfMonth() - today.dayOfMonth + 1,
            accountsWithBalance = accountsWithBalance,
            categories = categoryMap,
            recentTransactions = transactions.take(5),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )
}
