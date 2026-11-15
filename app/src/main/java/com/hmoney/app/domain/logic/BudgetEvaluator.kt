package com.hmoney.app.domain.logic

import javax.inject.Inject

/**
 * 預算判斷（純邏輯，可單元測試）：
 * 進度條 80% 轉警示色、100% 轉紅。
 */
class BudgetEvaluator @Inject constructor() {

    enum class Level { NORMAL, WARNING, OVER }

    data class BudgetProgress(
        val budgetCents: Long,
        val spentCents: Long,
        val ratio: Float,
        val level: Level,
    )

    fun evaluate(budgetCents: Long, spentCents: Long): BudgetProgress {
        val ratio = if (budgetCents == 0L) 0f else spentCents.toFloat() / budgetCents
        val level = when {
            budgetCents == 0L -> Level.NORMAL
            ratio >= 1f -> Level.OVER
            ratio >= 0.8f -> Level.WARNING
            else -> Level.NORMAL
        }
        return BudgetProgress(budgetCents, spentCents, ratio, level)
    }
}
