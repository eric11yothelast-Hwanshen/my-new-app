package com.hmoney.app.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 帳戶 ViewModel：帳戶卡片與管理（新增／編輯／隱藏／刪除）。
 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    data class UiState(
        val accountsWithBalance: List<Pair<Account, Long>> = emptyList(),
        val hiddenAccountCount: Int = 0,
        val showHidden: Boolean = false,
    )

    private val showHidden = MutableStateFlow(false)

    val uiState: StateFlow<UiState> = combine(
        accountRepository.observeAllWithBalance(),
        showHidden,
    ) { all, showHiddenFlag ->
        UiState(
            accountsWithBalance = if (showHiddenFlag) all else all.filter { !it.first.isHidden },
            hiddenAccountCount = all.count { it.first.isHidden },
            showHidden = showHiddenFlag,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    fun toggleShowHidden() {
        showHidden.value = !showHidden.value
    }

    fun addAccount(name: String, iconKey: String, colorArgb: Long, initialBalanceCents: Long) {
        viewModelScope.launch {
            accountRepository.addAccount(
                Account(
                    name = name,
                    iconKey = iconKey,
                    colorArgb = colorArgb,
                    initialBalanceCents = initialBalanceCents,
                ),
            )
        }
    }

    fun updateAccount(account: Account, newName: String, newIconKey: String, newColor: Long) {
        viewModelScope.launch {
            accountRepository.updateAccount(
                account.copy(name = newName, iconKey = newIconKey, colorArgb = newColor),
            )
        }
    }

    fun hideAccount(id: Long) {
        viewModelScope.launch { accountRepository.hideAccount(id) }
    }

    /** 僅無交易的帳戶可刪；回傳 false 表示有交易（應改為隱藏） */
    suspend fun deleteAccount(id: Long): Boolean = accountRepository.deleteAccount(id)
}
