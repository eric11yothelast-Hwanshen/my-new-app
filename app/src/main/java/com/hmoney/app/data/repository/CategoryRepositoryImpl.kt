package com.hmoney.app.data.repository

import com.hmoney.app.data.db.dao.CategoryDao
import com.hmoney.app.data.mapper.toDomain
import com.hmoney.app.data.mapper.toEntity
import com.hmoney.app.domain.logic.CategoryRules
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 分類倉儲實作：收入／支出分類獨立管理 */
@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    override fun observeActive(type: CategoryType): Flow<List<Category>> =
        categoryDao.observeActive(type.name).map { list -> list.map { it.toDomain() } }

    override fun observeAll(type: CategoryType): Flow<List<Category>> =
        categoryDao.observeAllOfType(type.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getCategory(id: Long): Category? = categoryDao.getById(id)?.toDomain()

    override suspend fun addCategory(category: Category): Long {
        require(category.name.isNotBlank()) { "分類名稱不可空白" }
        return categoryDao.insert(category.toEntity())
    }

    override suspend fun updateCategory(category: Category) {
        require(category.name.isNotBlank()) { "分類名稱不可空白" }
        require(categoryDao.getById(category.id) != null) { "分類不存在" }
        categoryDao.update(category.toEntity())
    }

    override suspend fun hideCategory(id: Long) {
        val entity = categoryDao.getById(id) ?: return
        categoryDao.update(entity.copy(isHidden = true))
    }

    override suspend fun deleteCategoryIfUnused(id: Long): Boolean {
        val entity = categoryDao.getById(id) ?: return false
        val inUse = categoryDao.countTransactionsUsing(id) > 0 ||
            categoryDao.countBudgetsUsing(id) > 0 ||
            categoryDao.countRecurringUsing(id) > 0
        if (!CategoryRules.canDelete(entity.name, inUse)) return false
        categoryDao.deleteById(id)
        return true
    }
}
