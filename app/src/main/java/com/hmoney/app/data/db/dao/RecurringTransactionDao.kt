package com.hmoney.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hmoney.app.data.db.entity.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow

/** 週期交易 DAO */
@Dao
interface RecurringTransactionDao {

    @Query("SELECT * FROM recurring_transactions ORDER BY nextDueEpochMillis")
    fun observeAll(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE isEnabled = 1 AND nextDueEpochMillis <= :now")
    suspend fun getDue(now: Long): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: Long): RecurringTransactionEntity?

    @Insert
    suspend fun insert(recurring: RecurringTransactionEntity): Long

    @Update
    suspend fun update(recurring: RecurringTransactionEntity)

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(items: List<RecurringTransactionEntity>)

    @Query("DELETE FROM recurring_transactions")
    suspend fun deleteAll()
}
