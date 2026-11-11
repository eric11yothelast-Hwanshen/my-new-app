package com.hmoney.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.model.Budget
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
 * （80% 轉警示色、100% 轉紅）。
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    data class BudgetProgress(
        val budgetCents: Long,
        val spentCents: Long,
        val ratio: Float,
        val level: Level,
    ) {
        enum class Level { NORMAL, WARNING, OVER }
    }

    data class UiState(
        val totalBudget: BudgetProgress = BudgetProgress(0L, 0L, 0f, BudgetProgress.Level.NORMAL),
        val categoryProgress: List<Pair<Category, BudgetProgress>> = emptyList(),
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

        fun progressOf(budgetCents: Long, spentCents: Long): BudgetProgress {
            val ratio = if (budgetCents == 0L) 0f
            else spentCents.toFloat() / budgetCents
            val level = when {
                budgetCents == 0L -> BudgetProgress.Level.NORMAL
                ratio >= 1f -> BudgetProgress.Level.OVER
                ratio >= 0.8f -> BudgetProgress.Level.WARNING
                else -> BudgetProgress.Level.NORMAL
            }
            return BudgetProgress(budgetCents, spentCents, ratio, level)
        }

        val categoryBudgets = budgets.filter { it.type == com.hmoney.app.domain.model.BudgetType.CATEGORY }
            .associateBy { it.categoryId }

        UiState(
            totalBudget = progressOf(
                budgets.firstOrNull { it.type == com.hmoney.app.domain.model.BudgetType.TOTAL }
                    ?.amountCents ?: 0L,
                totalSpent,
            ),
            categoryProgress = categories.map { category ->
                val budget = categoryBudgets[category.id]?.amountCents ?: 0L
                category to progressOf(budget, spentByCategory[category.id] ?: 0L)
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
