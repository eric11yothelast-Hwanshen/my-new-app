package com.hmoney.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 預算資料表：type = TOTAL（月總預算）／CATEGORY（分類預算，categoryId 對應分類） */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val categoryId: Long? = null,
    val amountCents: Long,
)
