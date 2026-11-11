package com.hmoney.app.domain.model

/** 分類類型：支出／收入分類獨立管理 */
enum class CategoryType {
    EXPENSE,
    INCOME,
}

/**
 * 分類：可新增／編輯／隱藏，每個分類有顏色與 icon。
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val type: CategoryType,
    val iconKey: String,
    val colorArgb: Long,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
)
