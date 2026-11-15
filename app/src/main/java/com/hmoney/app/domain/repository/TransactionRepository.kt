package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/** 交易的領域介面：記帳 CRUD 與搜尋篩選 */
interface TransactionRepository {

    /**
     * 觀察交易列表：支援關鍵字（備註／標籤）與期間、帳戶、分類、類型篩選，
     * 參數為 null／空表示不篩選。
     */
    fun observeTransactions(
        keyword: String = "",
        startDate: Long? = null,
        endDate: Long? = null,
        accountId: Long? = null,
        categoryId: Long? = null,
        type: TransactionType? = null,
    ): Flow<List<MoneyTransaction>>

    suspend fun getTransaction(id: Long): MoneyTransaction?

    /** S45 唯讀：AI 查詢記錄用檢索（關鍵字／欄位過濾／日期新→舊／筆數上限）——嚴禁接入任何寫入流程 */
    suspend fun searchTransactions(
        keyword: String = "",
        startDate: Long? = null,
        endDate: Long? = null,
        accountId: Long? = null,
        categoryId: Long? = null,
        type: TransactionType? = null,
        minAmountCents: Long? = null,
        maxAmountCents: Long? = null,
        limit: Int = 50,
    ): List<MoneyTransaction>

    /** 以條碼觀察歷史交易（依日期新→舊排序），供條碼記帳時參考 */
    fun observeTransactionsByBarcode(barcode: String): Flow<List<MoneyTransaction>>

    /** 新增交易（含完整防呆驗證） */
    suspend fun addTransaction(transaction: MoneyTransaction): Long

    /** 更新交易 */
    suspend fun updateTransaction(transaction: MoneyTransaction)

    /** 刪除交易 */
    suspend fun deleteTransaction(id: Long)
}
