package com.hmoney.app.data.db.entity

import androidx.room.ColumnInfo
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
    /** 記帳來源標記：manual / ai_text / ai_image / barcode（語意勿擴充） */
    @ColumnInfo(defaultValue = "'manual'") val source: String = "manual",
    /** 商品條碼（掃描記帳時填入） */
    val barcode: String? = null,
    /** 憑證圖片路徑（圖片記帳時填入） */
    val receiptImagePath: String? = null,
)
