package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

/** 分類的領域介面：收入／支出分類獨立管理 */
interface CategoryRepository {

    fun observeActive(type: CategoryType): Flow<List<Category>>

    fun observeAll(type: CategoryType): Flow<List<Category>>

    suspend fun getCategory(id: Long): Category?

    /** 新增分類 */
    suspend fun addCategory(category: Category): Long

    /** 更新分類（名稱、顏色、icon） */
    suspend fun updateCategory(category: Category)

    /** 隱藏分類（保留歷史資料關聯） */
    suspend fun hideCategory(id: Long)
}
