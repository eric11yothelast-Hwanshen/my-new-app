package com.hmoney.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 交易資料表：type = EXPENSE / INCOME / TRANSFER；tags 以半形逗號分隔多個標籤 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val amountCents: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val feeCents: Long = 0L,
    val dateEpochMillis: Long,
    val note: String = "",
    val tags: String = "",
)
