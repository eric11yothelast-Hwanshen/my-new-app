package com.hmoney.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 週期交易資料表：每週／每月重複，到期由排程自動產生交易記錄 */
@Entity(tableName = "recurring_transactions")
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val amountCents: Long,
    val accountId: Long,
    val categoryId: Long,
    val note: String = "",
    val frequency: String,
    val dayOfMonth: Int? = null,
    val dayOfWeek: Int? = null,
    val nextDueEpochMillis: Long,
    val isEnabled: Boolean = true,
)
