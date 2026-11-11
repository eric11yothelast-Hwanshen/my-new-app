package com.hmoney.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 分類資料表：支出／收入分類獨立管理（type = EXPENSE / INCOME） */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String,
    val iconKey: String,
    val colorArgb: Long,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
)
