package com.hmoney.app.data.repository

import com.hmoney.app.data.db.dao.BudgetDao
import com.hmoney.app.data.db.entity.BudgetEntity
import com.hmoney.app.domain.model.Budget
import com.hmoney.app.domain.model.BudgetType
import com.hmoney.app.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 預算倉儲實作：同一分類僅一份預算（重設即覆寫） */
@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
) : BudgetRepository {

    private fun BudgetEntity.toDomain(): Budget = Budget(
        id = id,
        type = runCatching { BudgetType.valueOf(type) }.getOrDefault(BudgetType.TOTAL),
        categoryId = categoryId,
        amountCents = amountCents,
    )

    override fun observeAll(): Flow<List<Budget>> =
        budgetDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun setTotalBudget(amountCents: Long) {
        require(amountCents >= 0L) { "預算不可為負" }
        val existing = budgetDao.getTotalBudget()
        if (amountCents == 0L) {
            existing?.let { budgetDao.deleteById(it.id) }
            return
        }
        budgetDao.upsert(
            BudgetEntity(
                id = existing?.id ?: 0L,
                type = BudgetType.TOTAL.name,
                categoryId = null,
                amountCents = amountCents,
            ),
        )
    }

    override suspend fun setCategoryBudget(categoryId: Long, amountCents: Long) {
        require(amountCents >= 0L) { "預算不可為負" }
        val existing = budgetDao.getCategoryBudget(categoryId)
        if (amountCents == 0L) {
            existing?.let { budgetDao.deleteById(it.id) }
            return
        }
        budgetDao.upsert(
            BudgetEntity(
                id = existing?.id ?: 0L,
                type = BudgetType.CATEGORY.name,
                categoryId = categoryId,
                amountCents = amountCents,
            ),
        )
    }

    override suspend fun getBudgets(): List<Budget> =
        budgetDao.observeAll().first().map { it.toDomain() }
}
