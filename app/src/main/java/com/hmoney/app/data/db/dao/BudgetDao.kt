package com.hmoney.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hmoney.app.data.db.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/** 預算 DAO */
@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE type = 'CATEGORY' AND categoryId = :categoryId")
    suspend fun getCategoryBudget(categoryId: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE type = 'TOTAL'")
    suspend fun getTotalBudget(): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
