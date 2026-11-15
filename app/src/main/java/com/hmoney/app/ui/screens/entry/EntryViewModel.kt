package com.hmoney.app.ui.screens.entry

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.R
import com.hmoney.app.data.repository.AiCallException
import com.hmoney.app.data.scanner.isSameBarcode
import com.hmoney.app.data.scanner.shouldScanAt
import com.hmoney.app.domain.logic.resolveDraftToTransaction
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.PriceTrend
import com.hmoney.app.domain.model.PurchaseSummary
import com.hmoney.app.domain.model.TransactionDraft
import com.hmoney.app.domain.model.TransactionSource
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.AiRepository
import com.hmoney.app.domain.repository.BarcodeRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.SettingsRepository
import com.hmoney.app.domain.repository.TransactionRepository
import com.hmoney.app.domain.scanner.BarcodeScanError
import com.hmoney.app.domain.scanner.BarcodeScanResult
import com.hmoney.app.domain.scanner.BarcodeScannerSource
import com.hmoney.app.ui.screens.ai.toMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * AI 記帳入口流程 ViewModel（S41）：
 * 狀態機 idle → scanning/parsing → preview → writing → done。
 * 鐵律：確認前嚴禁寫入 DB；唯一寫入點＝confirmAll()（草稿預覽的「確認」按鈕），
 * 走既有 TransactionRepository.addTransaction，每筆帶 source 與 barcode（掃碼時）。
 */
@HiltViewModel
class EntryViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val barcodeRepository: BarcodeRepository,
    private val barcodeScannerSource: BarcodeScannerSource,
    private val receiptImageStore: com.hmoney.app.data.repository.ReceiptImageStore,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    enum class Phase { IDLE, SCANNING, PARSING, PREVIEW, WRITING, DONE }

    /** 草稿預覽項：已解析為交易（含 source／barcode／receiptImagePath），可逐筆編輯與剔除 */
    data class EditableDraft(
        val transaction: MoneyTransaction,
        val removed: Boolean = false,
        /** S49 穩定 key：建立時配發單調遞增 Long，編輯 copy 時保留——LazyColumn key 與 remember 以此為據（identityHashCode 教訓） */
        val key: Long = 0L,
    )

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val source: TransactionSource = TransactionSource.MANUAL,
        val barcode: String? = null,
        val receiptImagePath: String? = null,
        val drafts: List<EditableDraft> = emptyList(),
        val categories: List<Category> = emptyList(),
        val accounts: List<Account> = emptyList(),
        val errorRes: Int? = null,
        val scannedBarcode: String? = null,
        val trend: PriceTrend? = null,
        val history: List<PurchaseSummary> = emptyList(),
        val scanError: BarcodeScanError? = null,
        val writtenCount: Int = 0,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 三個 AI 入口開關（全部關閉時入口選擇不出現，直接開 AddTransactionSheet） */
    val flags: StateFlow<SettingsRepository.FeatureFlagSettings?> =
        settingsRepository.observeFeatureFlags()
            .map { flags -> flags as SettingsRepository.FeatureFlagSettings? }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var lastScanAt = 0L
    private var draftKeyCounter = 0L
    private var lastBarcode: String? = null

    // S41-fix2：in-flight 旗標——前一次 scan 未返回前跳過新幀，明確序列化，避免 ML Kit 併發呼叫
    private var scanInFlight = false

    init {
        viewModelScope.launch {
            val expense = categoryRepository.observeAll(com.hmoney.app.domain.model.CategoryType.EXPENSE).first()
            val income = categoryRepository.observeAll(com.hmoney.app.domain.model.CategoryType.INCOME).first()
            _uiState.update { it.copy(categories = expense + income) }
        }
        viewModelScope.launch {
            val accounts = accountRepository.observeAll().first()
            _uiState.update { it.copy(accounts = accounts) }
        }
    }

    // ===== 打字 AI 解析 =====

    fun parseFromText(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(phase = Phase.PARSING, errorRes = null, source = TransactionSource.AI_TEXT, barcode = null, receiptImagePath = null) }
        viewModelScope.launch {
            val (expense, income, accounts) = currentCategoryAccountNames()
            aiRepository.parseTransactionsFromText(text, expense, income, accounts)
                .fold(
                    onSuccess = { drafts -> onDraftsParsed(drafts) },
                    onFailure = { e -> onParseFailed(e) },
                )
        }
    }

    // ===== 拍照（系統相片挑選器，無需權限）=====

    fun parseFromImage(uri: Uri) {
        _uiState.update { it.copy(phase = Phase.PARSING, errorRes = null, source = TransactionSource.AI_IMAGE, barcode = null) }
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) { receiptImageStore.decodeAndStore(uri) }
            if (decoded == null) {
                _uiState.update { it.copy(phase = Phase.IDLE, errorRes = R.string.entry_image_failed) }
                return@launch
            }
            val (base64, path) = decoded
            _uiState.update { it.copy(receiptImagePath = path) }
            val (expense, income, accounts) = currentCategoryAccountNames()
            aiRepository.parseTransactionsFromImage(base64, "image/jpeg", "", expense, income, accounts)
                .fold(
                    onSuccess = { drafts -> onDraftsParsed(drafts) },
                    onFailure = { e -> onParseFailed(e) },
                )
        }
    }

    /** S41-fix：解析前取得使用者最新的支出／收入分類與帳戶名稱清單，傳入提示詞避免模型發明名稱 */
    private suspend fun currentCategoryAccountNames(): Triple<List<String>, List<String>, List<String>> {
        val expense = categoryRepository.observeAll(com.hmoney.app.domain.model.CategoryType.EXPENSE).first().map { it.name }
        val income = categoryRepository.observeAll(com.hmoney.app.domain.model.CategoryType.INCOME).first().map { it.name }
        val accounts = accountRepository.observeAll().first().map { it.name }
        return Triple(expense, income, accounts)
    }

    // ===== 掃碼 =====

    /** CameraX 分析幀入口：節流（450ms）＋同碼去重；ML Kit 呼叫在協程內，不阻塞 UI thread */
    fun onCameraFrame(bitmap: Bitmap, rotationDegrees: Int) {
        // S42：phase 白名單——PREVIEW/WRITING/DONE 期間一律不吃幀（覆蓋層拆除與相機 unbind 有時間差，in-flight 幀可能在路上）
        val p = _uiState.value.phase
        if (p != Phase.IDLE && p != Phase.SCANNING) return
        if (scanInFlight) return
        val now = System.currentTimeMillis()
        if (!shouldScanAt(lastScanAt, now)) return
        lastScanAt = now
        scanInFlight = true
        _uiState.update { it.copy(phase = Phase.SCANNING, scanError = null) }
        viewModelScope.launch {
            try {
                when (val result = barcodeScannerSource.scan(bitmap, rotationDegrees)) {
                    is BarcodeScanResult.Success -> {
                        val barcode = result.barcode
                        if (isSameBarcode(lastBarcode, barcode)) return@launch
                        lastBarcode = barcode
                        val history = barcodeRepository.getPurchaseHistory(barcode).first()
                        val trend = barcodeRepository.getPriceTrend(barcode).first()
                        _uiState.update {
                            it.copy(scannedBarcode = barcode, trend = trend, history = history)
                        }
                    }
                    is BarcodeScanResult.Failure ->
                        _uiState.update { it.copy(scanError = result.error) }
                }
            } finally {
                scanInFlight = false
            }
        }
    }

    /** 掃到條碼後「此商品記一筆」：以價格趨勢預填單筆草稿（source=barcode、預填 barcode，金額參考上次價格） */
    fun prepareScanDraft(barcode: String? = _uiState.value.scannedBarcode) {
        val code = barcode ?: return
        val trend = _uiState.value.trend
        val draft = TransactionDraft(
            type = TransactionType.EXPENSE,
            amountCents = trend?.lastPriceCents ?: 0L,
            note = "",
            dateEpochMillis = System.currentTimeMillis(),
        )
        showDrafts(listOf(draft), TransactionSource.BARCODE, code)
    }

    // ===== 草稿預覽與確認 =====

    private fun onDraftsParsed(drafts: List<TransactionDraft>) {
        if (drafts.isEmpty()) {
            _uiState.update { it.copy(phase = Phase.IDLE, errorRes = R.string.ai_error_parse) }
            return
        }
        showDrafts(drafts, _uiState.value.source, _uiState.value.barcode)
    }

    private fun onParseFailed(e: Throwable) {
        val res = when {
            e is AiCallException && e.error == AiError.NotConfigured -> R.string.ai_not_configured
            else -> errorMessageRes(e)
        }
        _uiState.update { it.copy(phase = Phase.IDLE, errorRes = res) }
    }

    private fun showDrafts(drafts: List<TransactionDraft>, source: TransactionSource, barcode: String?) {
        val state = _uiState.value
        val transactions = drafts.map { draft ->
            resolveDraftToTransaction(
                draft = draft,
                source = source,
                categories = state.categories,
                accounts = state.accounts,
                nowEpochMillis = System.currentTimeMillis(),
                barcode = barcode,
                receiptImagePath = state.receiptImagePath.takeIf { source == TransactionSource.AI_IMAGE },
            )
        }
        _uiState.update {
            it.copy(
                phase = Phase.PREVIEW,
                source = source,
                barcode = barcode,
                drafts = transactions.map { tx -> EditableDraft(tx, key = ++draftKeyCounter) },
                errorRes = null,
            )
        }
    }

    fun updateDraft(key: MoneyTransaction, updated: MoneyTransaction) {
        _uiState.update { state ->
            state.copy(drafts = state.drafts.map { if (it.transaction === key) it.copy(transaction = updated) else it })
        }
    }

    fun updateDraftAmount(key: MoneyTransaction, amountCents: Long) =
        updateDraft(key, key.copy(amountCents = amountCents))

    fun updateDraftNote(key: MoneyTransaction, note: String) =
        updateDraft(key, key.copy(note = note))

    fun updateDraftCategory(key: MoneyTransaction, categoryId: Long) =
        updateDraft(key, key.copy(categoryId = categoryId))

    fun updateDraftAccount(key: MoneyTransaction, accountId: Long) =
        updateDraft(key, key.copy(accountId = accountId))

    fun updateDraftDate(key: MoneyTransaction, dateEpochMillis: Long) =
        updateDraft(key, key.copy(dateEpochMillis = dateEpochMillis))

    fun removeDraft(key: MoneyTransaction) {
        _uiState.update { state ->
            state.copy(drafts = state.drafts.map { if (it.transaction === key) it.copy(removed = true) else it })
        }
    }

    fun restoreDraft(key: MoneyTransaction) {
        _uiState.update { state ->
            state.copy(drafts = state.drafts.map { if (it.transaction === key) it.copy(removed = false) else it })
        }
    }

    // S44：寫入邏輯抽成共用 ConfirmDraftsUseCase（AiChatViewModel 確認亦走同一路徑；此處以既有 repo 建構，對外行為與測試零變動）
    private val confirmDrafts = com.hmoney.app.domain.logic.ConfirmDraftsUseCase(transactionRepository)

    /** 唯一寫入點：使用者按「全部確認」後批次寫入（每筆帶對應 source 與 barcode） */
    fun confirmAll() {
        val state = _uiState.value
        if (state.phase != Phase.PREVIEW) return
        val toWrite = state.drafts.filter { !it.removed }
        if (toWrite.isEmpty()) {
            _uiState.update { it.copy(errorRes = R.string.entry_preview_empty) }
            return
        }
        _uiState.update { it.copy(phase = Phase.WRITING) }
        viewModelScope.launch {
            val written = confirmDrafts.execute(toWrite.map { it.transaction })
            _uiState.update { it.copy(phase = Phase.DONE, writtenCount = written, drafts = emptyList()) }
        }
    }

    fun reset() {
        lastBarcode = null
        lastScanAt = 0L
        scanInFlight = false
        _uiState.value = UiState(categories = _uiState.value.categories, accounts = _uiState.value.accounts)
    }

    fun clearError() {
        _uiState.update { it.copy(errorRes = null) }
    }

    /** 日期顯示用（趨勢卡） */
    fun dateOf(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun errorMessageRes(e: Throwable): Int =
        if (e is AiCallException) e.error.toMessageRes() else R.string.ai_error_parse

}
