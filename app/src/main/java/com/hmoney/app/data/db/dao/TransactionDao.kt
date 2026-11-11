package com.hmoney.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hmoney.app.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** 交易 DAO：支援關鍵字搜尋與期間／帳戶／分類／類型篩選（參數為 null 表示不篩選） */
@Dao
interface TransactionDao {

    @Query(
        """
        SELECT * FROM transactions
        WHERE (:keyword = '' OR note LIKE '%' || :keyword || '%' OR tags LIKE '%' || :keyword || '%')
          AND (:startDate IS NULL OR dateEpochMillis >= :startDate)
          AND (:endDate IS NULL OR dateEpochMillis <= :endDate)
          AND (:accountId IS NULL OR accountId = :accountId OR toAccountId = :accountId)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:type IS NULL OR type = :type)
        ORDER BY dateEpochMillis DESC, id DESC
        """,
    )
    fun observeFiltered(
        keyword: String = "",
        startDate: Long? = null,
        endDate: Long? = null,
        accountId: Long? = null,
        categoryId: Long? = null,
        type: String? = null,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun countByAccount(accountId: Long): Int

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
