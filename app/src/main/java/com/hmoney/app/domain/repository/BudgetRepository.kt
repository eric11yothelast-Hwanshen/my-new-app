package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.Budget
import kotlinx.coroutines.flow.Flow

/** 預算倉儲介面：月總預算＋各分類預算 */
interface BudgetRepository {

    fun observeAll(): Flow<List<Budget>>

    /** 設定月總預算（0 表示移除） */
    suspend fun setTotalBudget(amountCents: Long)

    /** 設定分類預算（0 表示移除） */
    suspend fun setCategoryBudget(categoryId: Long, amountCents: Long)

    suspend fun getBudgets(): List<Budget>
}
