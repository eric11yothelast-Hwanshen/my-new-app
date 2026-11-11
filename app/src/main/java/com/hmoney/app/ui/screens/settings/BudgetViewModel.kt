package com.hmoney.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.logic.BudgetEvaluator
import com.hmoney.app.domain.model.BudgetType
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.BudgetRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 預算 ViewModel：月總預算＋各分類預算，含本月支出進度
 * （80% 轉警示色、100% 轉紅）。判斷邏輯委派 BudgetEvaluator（可單元測試）。
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    private val evaluator: BudgetEvaluator,
) : ViewModel() {

    data class UiState(
        val totalBudget: BudgetEvaluator.BudgetProgress =
            BudgetEvaluator.BudgetProgress(0L, 0L, 0f, BudgetEvaluator.Level.NORMAL),
        val categoryProgress: List<Pair<Category, BudgetEvaluator.BudgetProgress>> = emptyList(),
        val categories: List<Category> = emptyList(),
    )

    val uiState: StateFlow<UiState> = combine(
        budgetRepository.observeAll(),
        categoryRepository.observeAll(CategoryType.EXPENSE),
        transactionRepository.observeTransactions(),
    ) { budgets, categories, transactions ->
        // 本月支出（轉帳不計入）
        val zone = ZoneId.systemDefault()
        val monthStart = LocalDate.now(zone).withDayOfMonth(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = LocalDate.now(zone).plusMonths(1).withDayOfMonth(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val monthExpenses = transactions.filter {
            it.type == TransactionType.EXPENSE &&
                it.dateEpochMillis >= monthStart &&
                it.dateEpochMillis < monthEnd
        }
        val spentByCategory = monthExpenses.groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }
        val totalSpent = monthExpenses.sumOf { it.amountCents }

        val categoryBudgets = budgets.filter { it.type == BudgetType.CATEGORY }
            .associateBy { it.categoryId }

        UiState(
            totalBudget = evaluator.evaluate(
                budgets.firstOrNull { it.type == BudgetType.TOTAL }?.amountCents ?: 0L,
                totalSpent,
            ),
            categoryProgress = categories.map { category ->
                val budget = categoryBudgets[category.id]?.amountCents ?: 0L
                category to evaluator.evaluate(budget, spentByCategory[category.id] ?: 0L)
            },
            categories = categories,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    fun setTotalBudget(amountCents: Long) {
        viewModelScope.launch { budgetRepository.setTotalBudget(amountCents) }
    }

    fun setCategoryBudget(categoryId: Long, amountCents: Long) {
        viewModelScope.launch { budgetRepository.setCategoryBudget(categoryId, amountCents) }
    }
}
