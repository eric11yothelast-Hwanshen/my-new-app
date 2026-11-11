package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.RecurringTransaction
import kotlinx.coroutines.flow.Flow

/** 週期交易倉儲介面 */
interface RecurringRepository {

    fun observeAll(): Flow<List<RecurringTransaction>>

    suspend fun getById(id: Long): RecurringTransaction?

    /** 新增週期規則（自動計算首次到期時間） */
    suspend fun addRecurring(recurring: RecurringTransaction): Long

    suspend fun updateRecurring(recurring: RecurringTransaction)

    suspend fun deleteRecurring(id: Long)

    suspend fun setEnabled(id: Long, enabled: Boolean)

    /**
     * 產生所有已到期的交易記錄（補產過去到期者），
     * 並將規則的下次到期時間推進到未來。
     * @return 本次產生的筆數
     */
    suspend fun generateDue(): Int
}
