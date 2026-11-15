package com.hmoney.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 帳戶資料表：餘額以「分」為單位儲存 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val initialBalanceCents: Long = 0L,
    val currency: String = "TWD",
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
)
