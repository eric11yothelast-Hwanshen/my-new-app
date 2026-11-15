package com.hmoney.app.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.BudgetType
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.AiRepository
import com.hmoney.app.domain.repository.BudgetRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.SettingsRepository
import com.hmoney.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 總覽 ViewModel：本月收支、本月預算進度、總餘額、帳戶餘額列與近期明細。
 */
@HiltViewModel
class OverviewViewModel @Inject constructor(
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository,
    aiRepository: AiRepository,
) : ViewModel() {

    data class UiState(
        val monthExpenseCents: Long = 0L,
        val monthIncomeCents: Long = 0L,
        val totalBalanceCents: Long = 0L,
        val budgetCents: Long = 0L,
        val daysRemainingInMonth: Int = 1,
        val accountsWithBalance: List<Pair<Account, Long>> = emptyList(),
        val categories: Map<Long, Category> = emptyMap(),
        val recentTransactions: List<MoneyTransaction> = emptyList(),
    )

    val uiState: StateFlow<UiState> = combine(
        accountRepository.observeVisibleWithBalance(),
        categoryRepository.observeAll(com.hmoney.app.domain.model.CategoryType.EXPENSE),
        categoryRepository.observeAll(com.hmoney.app.domain.model.CategoryType.INCOME),
        transactionRepository.observeTransactions(),
        budgetRepository.observeAll(),
    ) { accountsWithBalance, expenseCategories, incomeCategories, transactions, budgets ->
        val categoryMap = (expenseCategories + incomeCategories).associateBy { it.id }
        // 本月收支（轉帳不計入收支統計）
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val monthEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val monthTx = transactions.filter { it.dateEpochMillis in monthStart until monthEnd }
        UiState(
            monthExpenseCents = monthTx
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amountCents },
            monthIncomeCents = monthTx
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amountCents },
            totalBalanceCents = accountsWithBalance.sumOf { it.second },
            budgetCents = budgets
                .firstOrNull { it.type == BudgetType.TOTAL }?.amountCents ?: 0L,
            daysRemainingInMonth = today.lengthOfMonth() - today.dayOfMonth + 1,
            accountsWithBalance = accountsWithBalance,
            categories = categoryMap,
            recentTransactions = transactions.take(5),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    /**
     * ✨ 洞察圓點＝「有尚未查看的洞察」：最新洞察的時間晚於最後查看時間才顯示；
     * 已讀時間存 DataStore，重啟 App 不會無故重現，重新分析產生新洞察才會再出現。
     */
    val insightUnread: StateFlow<Boolean> = combine(
        settingsRepository.observeLastSeenInsight(),
        aiRepository.observeHistory(),
    ) { lastSeen, history ->
        val latest = history.firstOrNull()?.createdAtEpochMillis ?: 0L
        latest > lastSeen
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    /**
     * S49 新手引導：未完成（預設 false）→ 總覽頁顯示引導；完成／跳過寫入旗標後永不再現。
     * initialValue = true 避免首幀誤閃（真實值由 flow 首發決定）。
     */
    val onboardingCompleted: StateFlow<Boolean> = settingsRepository.observeFeatureFlags()
        .map { it.onboardingCompleted }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    /** 完成引導（含跳過）→ 寫入 DataStore 旗標 */
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    /** S50 實機點擊引導步驟（sheet 完成＝step 1） */
    val onboardingCoachStep: StateFlow<Int> = settingsRepository.observeFeatureFlags()
        .map { it.onboardingCoachStep }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /** sheet「開始使用」→ 進入實機點擊引導（step 1，持久化，中斷後續步） */
    fun startCoach() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCoachStep(1)
        }
    }

    /** 查看過洞察（AiResultDialog 顯示過即算，含直接關閉）→ 標記已讀、圓點消失 */
    fun markInsightSeen() {
        viewModelScope.launch {
            settingsRepository.setLastSeenInsightEpochMillis(System.currentTimeMillis())
        }
    }
}
