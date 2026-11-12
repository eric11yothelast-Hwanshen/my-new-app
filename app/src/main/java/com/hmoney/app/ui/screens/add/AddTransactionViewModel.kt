package com.hmoney.app.ui.screens.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.R
import com.hmoney.app.data.repository.AiCallException
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.AiSuggestion
import com.hmoney.app.domain.model.AiTextResult
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.AiRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.TransactionRepository
import com.hmoney.app.ui.common.AmountParser
import com.hmoney.app.ui.common.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * 新增／編輯交易 ViewModel：數字鍵盤為主的輸入流程，含完整防呆驗證。
 */
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val aiRepository: dagger.Lazy<AiRepository>,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    /** 記帳 AI 輔助狀態：智慧備註潤飾／自動分類／智慧標籤 */
    sealed class AiState {
        data object Idle : AiState()
        data object Loading : AiState()
        data object NeedNote : AiState()
        data object Done : AiState()
        data class Error(val error: AiError) : AiState()
    }

    data class FormState(
        val editingId: Long? = null,
        val type: TransactionType = TransactionType.EXPENSE,
        val amountText: String = "",
        val feeText: String = "",
        val accountId: Long? = null,
        val toAccountId: Long? = null,
        val categoryId: Long? = null,
        val date: LocalDate = LocalDate.now(),
        val time: LocalTime = LocalTime.now(),
        val note: String = "",
        val tagsText: String = "",
        val errorRes: Int? = null,
    )

    data class UiState(
        val form: FormState = FormState(),
        val accounts: List<Account> = emptyList(),
        val categories: List<Category> = emptyList(),
        val aiState: AiState = AiState.Idle,
    )

    private val form = MutableStateFlow(FormState())
    private val aiState = MutableStateFlow<AiState>(AiState.Idle)
    private var aiJob: Job? = null

    val uiState: StateFlow<UiState> = combine(
        form,
        aiState,
        accountRepository.observeVisibleWithBalance(),
        categoryRepository.observeActive(CategoryType.EXPENSE),
        categoryRepository.observeActive(CategoryType.INCOME),
    ) { f, ai, accounts, expenseCategories, incomeCategories ->
        UiState(
            form = f,
            accounts = accounts.map { it.first },
            // 依目前類型顯示對應分類
            categories = when (f.type) {
                TransactionType.EXPENSE -> expenseCategories
                TransactionType.INCOME -> incomeCategories
                TransactionType.TRANSFER -> emptyList()
            },
            aiState = ai,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    init {
        // 預設帳戶：第一個可見帳戶
        viewModelScope.launch {
            accountRepository.observeVisibleWithBalance().collect { accounts ->
                if (form.value.accountId == null && accounts.isNotEmpty()) {
                    form.update { it.copy(accountId = accounts.first().first.id) }
                }
            }
        }
    }

    fun loadForEdit(transactionId: Long) {
        if (form.value.editingId == transactionId) return
        viewModelScope.launch {
            val tx = transactionRepository.getTransaction(transactionId) ?: return@launch
            val dateTime = java.time.Instant.ofEpochMilli(tx.dateEpochMillis)
                .atZone(ZoneId.systemDefault())
            form.value = FormState(
                editingId = tx.id,
                type = tx.type,
                amountText = Formats.plain(tx.amountCents),
                feeText = if (tx.feeCents > 0) Formats.plain(tx.feeCents) else "",
                accountId = tx.accountId,
                toAccountId = tx.toAccountId,
                categoryId = tx.categoryId,
                date = dateTime.toLocalDate(),
                time = dateTime.toLocalTime(),
                note = tx.note,
                tagsText = tx.tags.joinToString(","),
            )
        }
    }

    fun reset() {
        form.value = FormState()
    }

    /** 連記模式：保留帳戶與分類，清空金額、手續費、備註與標籤，日期時間重設為現在 */
    fun resetForNext() = form.update { f ->
        f.copy(
            editingId = null,
            amountText = "",
            feeText = "",
            note = "",
            tagsText = "",
            errorRes = null,
            date = LocalDate.now(),
            time = LocalTime.now(),
        )
    }

    fun setType(type: TransactionType) = form.update { it.copy(type = type, categoryId = null) }

    fun onKeypadInput(digit: Char) = form.update { f ->
        when {
            digit == '.' && f.amountText.contains('.') -> f
            digit == '.' -> f.copy(amountText = f.amountText.ifEmpty { "0" } + ".")
            f.amountText.contains('.') &&
                f.amountText.substringAfter('.').length >= 2 -> f
            f.amountText == "0" && digit != '.' -> f.copy(amountText = digit.toString())
            else -> f.copy(amountText = f.amountText + digit)
        }
    }

    fun onKeypadBackspace() = form.update { f ->
        f.copy(amountText = f.amountText.dropLast(1))
    }

    fun setFee(fee: String) = form.update {
        it.copy(feeText = fee.filter { c -> c.isDigit() || c == '.' })
    }

    fun setAccount(id: Long) = form.update { it.copy(accountId = id) }

    fun setToAccount(id: Long) = form.update { it.copy(toAccountId = id) }

    fun setCategory(id: Long) = form.update { it.copy(categoryId = id) }

    fun setDate(date: LocalDate) = form.update { it.copy(date = date) }

    fun setTime(time: LocalTime) = form.update { it.copy(time = time) }

    fun setNote(note: String) = form.update { it.copy(note = note) }

    fun setTags(tags: String) = form.update { it.copy(tagsText = tags) }

    /** 儲存：驗證失敗回傳錯誤字串資源，成功回傳 null 並關閉表單；onContinue 非 null 時為連記模式 */
    fun save(onSaved: () -> Unit, onContinue: (() -> Unit)? = null) {
        val f = form.value
        val amountCents = AmountParser.toCents(f.amountText)
        when {
            amountCents == null || amountCents < 0 ->
                form.update { it.copy(errorRes = R.string.add_error_amount) }
            f.accountId == null ->
                form.update { it.copy(errorRes = R.string.add_error_account) }
            f.type != TransactionType.TRANSFER && f.categoryId == null ->
                form.update { it.copy(errorRes = R.string.add_error_category) }
            f.type == TransactionType.TRANSFER && f.toAccountId == null ->
                form.update { it.copy(errorRes = R.string.add_error_account) }
            f.type == TransactionType.TRANSFER && f.toAccountId == f.accountId ->
                form.update { it.copy(errorRes = R.string.add_error_same_account) }
            else -> {
                val feeCents = if (f.feeText.isBlank()) 0L else AmountParser.toCents(f.feeText) ?: 0L
                val dateMillis = f.date.atTime(f.time)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val tags = f.tagsText.split(',', '，')
                    .map { it.trim() }.filter { it.isNotEmpty() }
                // 前面 when 已驗證非空
                val accountId = f.accountId!!
                val transaction = MoneyTransaction(
                    id = f.editingId ?: 0L,
                    type = f.type,
                    amountCents = amountCents,
                    accountId = accountId,
                    toAccountId = if (f.type == TransactionType.TRANSFER) f.toAccountId else null,
                    categoryId = if (f.type == TransactionType.TRANSFER) null else f.categoryId,
                    feeCents = feeCents,
                    dateEpochMillis = dateMillis,
                    note = f.note.trim(),
                    tags = tags,
                )
                viewModelScope.launch {
                    try {
                        if (f.editingId == null) {
                            transactionRepository.addTransaction(transaction)
                        } else {
                            transactionRepository.updateTransaction(transaction)
                        }
                        if (onContinue != null) {
                            resetForNext()
                            onContinue()
                        } else {
                            reset()
                            onSaved()
                        }
                    } catch (e: IllegalArgumentException) {
                        form.update { it.copy(errorRes = R.string.add_error_generic) }
                    }
                }
            }
        }
    }

    fun clearError() = form.update { it.copy(errorRes = null) }

    fun clearAiState() {
        aiState.value = AiState.Idle
    }

    fun cancelAi() {
        aiJob?.cancel()
        aiState.value = AiState.Idle
    }

    /**
     * AI 建議：依備註自動建議分類與 1–3 個標籤並潤飾備註。
     * 分類僅在符合目前類型的分類清單中比對，使用者仍可手動改選。
     */
    fun aiSuggest() {
        val note = form.value.note.trim()
        if (note.isBlank()) {
            aiState.value = AiState.NeedNote
            return
        }
        if (aiState.value is AiState.Loading) return
        aiState.value = AiState.Loading
        val repository = try {
            aiRepository.get()
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "AI 服務初始化失敗", t)
            aiState.value = AiState.Error(AiError.Network)
            return
        }
        val categories = uiState.value.categories
        aiJob = viewModelScope.launch {
            try {
                repository.suggestForNote(note, categories.map { it.name })
                    .onSuccess { suggestion -> applySuggestion(repository, suggestion, note) }
                    .onFailure { e ->
                        aiState.value = AiState.Error(
                            (e as? AiCallException)?.error ?: AiError.Parse,
                        )
                    }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "AI 建議失敗", t)
                aiState.value = AiState.Error(AiError.Network)
            }
        }
    }

    private suspend fun applySuggestion(
        repository: AiRepository,
        suggestion: AiSuggestion,
        originalNote: String,
    ) {
        suggestion.categoryName?.let { name ->
            uiState.value.categories.firstOrNull { it.name == name }?.let { matched ->
                form.update { it.copy(categoryId = matched.id) }
            }
        }
        if (suggestion.tags.isNotEmpty()) {
            form.update { it.copy(tagsText = suggestion.tags.take(3).joinToString(",")) }
        }
        try {
            when (val polished = repository.polishNote(originalNote)) {
                is AiTextResult.Success ->
                    form.update { it.copy(note = polished.text.ifBlank { originalNote }) }
                is AiTextResult.Failure -> Unit
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "備註潤飾失敗", t)
        }
        aiState.value = AiState.Done
    }

    /** 常用備註：從既有交易備註做記憶體去重（新到舊），不新增 DAO 查詢與資料表欄位 */
    val recentNotes: StateFlow<List<String>> = transactionRepository.observeTransactions()
        .map { transactions ->
            transactions.mapNotNull { tx -> tx.note.trim().takeIf(String::isNotEmpty) }
                .distinct()
                .take(RECENT_NOTE_LIMIT)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private companion object {
        private const val TAG = "AddTransactionVM"
        private const val RECENT_NOTE_LIMIT = 5
    }
}
