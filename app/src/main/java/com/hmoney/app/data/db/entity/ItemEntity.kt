package com.hmoney.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 物品管理資料表（1.3.0）：barcode 與到期日各建索引；
 * purchaseTransactionId 連動交易，交易刪除時保留物品（FK 設為 NULL）。
 */
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseTransactionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("barcode"), Index("expiryDateEpochMillis")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val barcode: String? = null,
    val storageLocation: String? = null,
    @ColumnInfo(defaultValue = "1") val quantity: Int = 1,
    val expiryDateEpochMillis: Long? = null,
    val purchaseTransactionId: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
