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

    /** 新增交易（含完整防呆驗證） */
    suspend fun addTransaction(transaction: MoneyTransaction): Long

    /** 更新交易 */
    suspend fun updateTransaction(transaction: MoneyTransaction)

    /** 刪除交易 */
    suspend fun deleteTransaction(id: Long)
}
