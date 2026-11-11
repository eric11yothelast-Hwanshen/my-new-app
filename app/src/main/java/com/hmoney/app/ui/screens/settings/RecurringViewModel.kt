package com.hmoney.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.RecurringTransaction
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 週期交易 ViewModel：規則列表（含帳戶／分類名稱）與新增／編輯／刪除／啟停。
 */
@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val recurringRepository: RecurringRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    data class UiState(
        val items: List<RecurringTransaction> = emptyList(),
        val accounts: List<Account> = emptyList(),
        val expenseCategories: List<Category> = emptyList(),
        val incomeCategories: List<Category> = emptyList(),
    )

    val uiState: StateFlow<UiState> = combine(
        recurringRepository.observeAll(),
        accountRepository.observeAll(),
        categoryRepository.observeAll(CategoryType.EXPENSE),
        categoryRepository.observeAll(CategoryType.INCOME),
    ) { items, accounts, expenseCategories, incomeCategories ->
        UiState(
            items = items,
            accounts = accounts,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    fun add(
        type: TransactionType,
        amountCents: Long,
        accountId: Long,
        categoryId: Long,
        note: String,
        frequencyName: String,
        dayOfWeek: Int?,
        dayOfMonth: Int?,
        onDone: (success: Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val frequency = com.hmoney.app.domain.model.RecurringFrequency.valueOf(frequencyName)
                val firstDue = com.hmoney.app.domain.logic.RecurringScheduler()
                    .firstDue(frequency, dayOfWeek, dayOfMonth)
                recurringRepository.addRecurring(
                    RecurringTransaction(
                        type = type,
                        amountCents = amountCents,
                        accountId = accountId,
                        categoryId = categoryId,
                        note = note,
                        frequency = frequency,
                        dayOfWeek = dayOfWeek,
                        dayOfMonth = dayOfMonth,
                        nextDueEpochMillis = firstDue,
                    ),
                )
                onDone(true)
            } catch (e: IllegalArgumentException) {
                onDone(false)
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { recurringRepository.deleteRecurring(id) }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { recurringRepository.setEnabled(id, enabled) }
    }
}
