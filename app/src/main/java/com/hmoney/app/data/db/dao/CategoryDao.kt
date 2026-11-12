package com.hmoney.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hmoney.app.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/** 分類 DAO */
@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE type = :type AND isHidden = 0 ORDER BY sortOrder, id")
    fun observeActive(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder, id")
    fun observeAllOfType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id")
    suspend fun countTransactionsUsing(id: Long): Int

    @Query("SELECT COUNT(*) FROM budgets WHERE categoryId = :id")
    suspend fun countBudgetsUsing(id: Long): Int

    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE categoryId = :id")
    suspend fun countRecurringUsing(id: Long): Int
}
