package com.hmoney.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hmoney.app.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** 同條碼歷史交易摘要列（S40 唯讀查詢，欄位名與 SQL 別名對應） */
data class PurchaseHistoryRow(
    val amountCents: Long,
    val dateEpochMillis: Long,
    val note: String,
    val accountName: String?,
)

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

    /** 以條碼查歷史交易（依日期新→舊排序），供掃描記帳時參考 */
    @Query("SELECT * FROM transactions WHERE barcode = :barcode ORDER BY dateEpochMillis DESC, id DESC")
    fun observeByBarcode(barcode: String): Flow<List<TransactionEntity>>

    /** S40 唯讀：同條碼歷史交易摘要（含帳戶名，日期新→舊）——嚴禁作為寫入依據 */
    @Query(
        "SELECT t.amountCents AS amountCents, t.dateEpochMillis AS dateEpochMillis, " +
            "t.note AS note, a.name AS accountName " +
            "FROM transactions t LEFT JOIN accounts a ON t.accountId = a.id " +
            "WHERE t.barcode = :barcode " +
            "ORDER BY t.dateEpochMillis DESC, t.id DESC",
    )
    fun observePurchaseHistory(barcode: String): Flow<List<PurchaseHistoryRow>>

    /** S45 唯讀：AI 查詢記錄用檢索（note／tags 關鍵字 LIKE＋欄位過濾＋日期新→舊＋筆數上限）——嚴禁作為寫入依據 */
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:keyword = '' OR note LIKE '%' || :keyword || '%' OR tags LIKE '%' || :keyword || '%')
          AND (:startDate IS NULL OR dateEpochMillis >= :startDate)
          AND (:endDate IS NULL OR dateEpochMillis <= :endDate)
          AND (:accountId IS NULL OR accountId = :accountId OR toAccountId = :accountId)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:type IS NULL OR type = :type)
          AND (:minAmountCents IS NULL OR amountCents >= :minAmountCents)
          AND (:maxAmountCents IS NULL OR amountCents <= :maxAmountCents)
        ORDER BY dateEpochMillis DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun searchTransactions(
        keyword: String = "",
        startDate: Long? = null,
        endDate: Long? = null,
        accountId: Long? = null,
        categoryId: Long? = null,
        type: String? = null,
        minAmountCents: Long? = null,
        maxAmountCents: Long? = null,
        limit: Int = 50,
    ): List<TransactionEntity>

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
