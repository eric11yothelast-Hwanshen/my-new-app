package com.hmoney.app.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.logic.CategoryRules
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 分類管理頁：支出／收入兩個 tab，列表含隱藏分類；
 * 隱藏／刪除規則見 CategoryRules（fallback 分類不可隱藏或刪除）。
 */
@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    data class UiState(
        val type: CategoryType = CategoryType.EXPENSE,
        val categories: List<Category> = emptyList(),
    )

    private val type = MutableStateFlow(CategoryType.EXPENSE)

    val uiState: StateFlow<UiState> = combine(
        type,
        categoryRepository.observeAll(CategoryType.EXPENSE),
        categoryRepository.observeAll(CategoryType.INCOME),
    ) { t, expense, income ->
        val list = if (t == CategoryType.EXPENSE) expense else income
        UiState(type = t, categories = list.sortedBy { it.sortOrder })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    fun setType(type: CategoryType) {
        this.type.value = type
    }

    /** 新增分類：名稱同型別內不可重複；sortOrder 排最後。回傳是否成功。 */
    fun addCategory(name: String, iconKey: String, colorArgb: Long, onDone: (Boolean) -> Unit) {
        val trimmed = name.trim()
        viewModelScope.launch {
            val current = uiState.value.categories
            if (trimmed.isEmpty() || current.any { it.name.equals(trimmed, ignoreCase = true) }) {
                onDone(false)
                return@launch
            }
            val maxSort = current.maxOfOrNull { it.sortOrder } ?: 0
            categoryRepository.addCategory(
                Category(
                    name = trimmed,
                    type = uiState.value.type,
                    iconKey = iconKey,
                    colorArgb = colorArgb,
                    sortOrder = maxSort + 1,
                ),
            )
            onDone(true)
        }
    }

    /** 編輯既有分類（名稱／圖示／顏色），名稱不可空白、同型別內不可重複。 */
    fun updateCategory(
        category: Category,
        name: String,
        iconKey: String,
        colorArgb: Long,
        onDone: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val trimmed = name.trim()
            val duplicate = uiState.value.categories.any {
                it.id != category.id && it.name.equals(trimmed, ignoreCase = true)
            }
            if (trimmed.isEmpty() || duplicate) {
                onDone(false)
                return@launch
            }
            categoryRepository.updateCategory(
                category.copy(name = trimmed, iconKey = iconKey, colorArgb = colorArgb),
            )
            onDone(true)
        }
    }

    /** 隱藏／恢復顯示（fallback 保護分類不可隱藏）。 */
    fun setHidden(id: Long, hidden: Boolean) {
        viewModelScope.launch {
            val category = categoryRepository.getCategory(id) ?: return@launch
            if (!CategoryRules.canHide(category.name)) return@launch
            categoryRepository.updateCategory(category.copy(isHidden = hidden))
        }
    }

    /** 刪除（僅限無關聯分類）；回傳是否真的刪除。 */
    fun delete(id: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(categoryRepository.deleteCategoryIfUnused(id))
        }
    }

    /** 排序：與相鄰分類互換 sortOrder（列表依 sortOrder、id 排序）。 */
    fun move(id: Long, up: Boolean) {
        val list = uiState.value.categories
        val index = list.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target < 0 || target >= list.size) return
        val a = list[index]
        val b = list[target]
        viewModelScope.launch {
            categoryRepository.updateCategory(a.copy(sortOrder = b.sortOrder))
            categoryRepository.updateCategory(b.copy(sortOrder = a.sortOrder))
        }
    }
}
