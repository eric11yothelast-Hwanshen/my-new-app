package com.hmoney.app.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.AiTextResult
import com.hmoney.app.domain.model.ChatTitle
import com.hmoney.app.domain.repository.AiRepository
import com.hmoney.app.domain.repository.ChatRepository
import com.hmoney.app.domain.repository.SettingsRepository
import android.graphics.Bitmap
import android.net.Uri
import com.hmoney.app.data.repository.AiCallException
import com.hmoney.app.data.repository.ReceiptImageStore
import com.hmoney.app.data.scanner.isSameBarcode
import com.hmoney.app.data.scanner.shouldScanAt
import com.hmoney.app.domain.logic.ConfirmDraftsUseCase
import com.hmoney.app.domain.logic.resolveDraftToTransaction
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.Item
import com.hmoney.app.domain.model.ItemActionIntent
import com.hmoney.app.domain.logic.ItemNameMatch
import com.hmoney.app.domain.logic.matchItemByName
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionDraft
import com.hmoney.app.domain.model.TransactionSource
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.BarcodeRepository
import com.hmoney.app.domain.repository.ItemRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.TransactionRepository
import com.hmoney.app.domain.model.TransactionQuery
import com.hmoney.app.domain.model.TransactionActionIntent
import com.hmoney.app.domain.model.TransactionActionType
import com.hmoney.app.domain.model.TransactionUpdates
import com.hmoney.app.domain.scanner.BarcodeScanError
import com.hmoney.app.domain.scanner.BarcodeScanResult
import com.hmoney.app.domain.scanner.BarcodeScannerSource
import com.hmoney.app.ui.common.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 對話訊息（全域 AI 助手 bottom sheet 用；id 為 DB id，純記憶體訊息為負數） */
data class AiChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
)

/** 草稿組狀態（S44）：PENDING 可編輯；CONFIRMED／CANCELLED 為唯讀終態，不得再寫入或復原 */
enum class ChatDraftStatus { PENDING, CONFIRMED, CANCELLED }

/** 草稿組單筆項：已解析為交易（含 source／barcode／receiptImagePath），可編輯與剔除 */
data class ChatDraftItem(
    val transaction: MoneyTransaction,
    val removed: Boolean = false,
    /** S49 穩定 key：建立時配發單調遞增 Long，編輯 copy 時保留——LazyColumn key 與 remember 以此為據（identityHashCode 教訓） */
    val key: Long = 0L,
)

/**
 * 聊天記帳草稿組（S44）：一次記帳意圖＝一組草稿；僅存在 VM UI state——
 * 不落 DB、不寫 chat_messages（切 session／重啟即消失，取捨見 PROGRESS）。
 */
data class ChatDraftGroup(
    val id: Long,
    val source: TransactionSource,
    val items: List<ChatDraftItem>,
    val status: ChatDraftStatus = ChatDraftStatus.PENDING,
    val writtenCount: Int = 0,
    val barcode: String? = null,
    val receiptImagePath: String? = null,
)

/**
 * S47 聊天室物品操作確認卡：意圖解析後的待確認狀態，僅存在 VM UI state（不落 DB）。
 * 確認前嚴禁寫入 ItemRepository；CONFIRMED／CANCELLED 為唯讀終態，不得再寫入。
 */
data class ChatItemAction(
    val id: Long,
    val intent: ItemActionIntent,
    val targetItem: Item? = null,
    val status: ChatDraftStatus = ChatDraftStatus.PENDING,
)

/**
 * S48 聊天室交易刪改確認卡：意圖解析＋候選匹配後的待確認狀態，僅存在 VM UI state（不落 DB）。
 * target 為使用者選定（或唯一匹配）的交易；working 為 UPDATE 併入 updates 後的可編輯版本。
 * 確認前嚴禁寫入；CONFIRMED／CANCELLED 為唯讀終態，不得再寫入。一次僅一筆（嚴禁批次刪除）。
 */
data class ChatTransactionAction(
    val id: Long,
    val intent: TransactionActionIntent,
    val status: ChatDraftStatus = ChatDraftStatus.PENDING,
    val target: MoneyTransaction? = null,
    val candidates: List<MoneyTransaction> = emptyList(),
    val working: MoneyTransaction? = null,
)

/**
 * 全域 AI 助手 ViewModel：訊息由 ChatRepository 的 DB flow 驅動——各頁的
 * AiAssistantSheet 共用同一份資料，天然跨頁同步；目前 session id 記於 DataStore。
 *
 * Session 延遲建立（請勿誤改）：currentSessionId == 0 代表「無進行中對話」，
 * 不建立任何 DB 記錄；只有送出第一則訊息時才建立 session。DataStore 的
 * currentSessionId 是唯一事實來源，多個 VM 實例不會各自建立 session。
 */
@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val barcodeRepository: BarcodeRepository,
    private val itemRepository: ItemRepository,
    private val transactionRepository: TransactionRepository,
    private val barcodeScannerSource: BarcodeScannerSource,
    private val receiptImageStore: ReceiptImageStore,
    private val confirmDraftsUseCase: ConfirmDraftsUseCase,
) : ViewModel() {

    data class UiState(
        val messages: List<AiChatMessage> = emptyList(),
        val sessions: List<com.hmoney.app.domain.model.ChatSession> = emptyList(),
        val currentSessionId: Long = 0L,
        val settings: SettingsRepository.AiAssistantSettings =
            SettingsRepository.AiAssistantSettings(),
        val loading: Boolean = false,
        val error: AiError? = null,
        // S44 聊天智慧記帳：草稿組（同時僅一組未決）、編輯用分類／帳戶清單、掃碼狀態、未決提示
        val draftGroups: List<ChatDraftGroup> = emptyList(),
        val draftCategories: List<Category> = emptyList(),
        val draftAccounts: List<Account> = emptyList(),
        val scannedBarcode: String? = null,
        val scanError: BarcodeScanError? = null,
        val draftBlockedHint: Boolean = false,
        // S47 聊天室物品操作：確認卡（同時僅一個未決）與未決提示；開關跟隨設定頁「物品管理」
        val inventoryEnabled: Boolean = true,
        val itemActions: List<ChatItemAction> = emptyList(),
        val itemActionBlockedHint: Boolean = false,
        // S48 聊天室交易刪改：確認卡（同時僅一一個未決）與未決提示
        val transactionActions: List<ChatTransactionAction> = emptyList(),
        val transactionActionBlockedHint: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var job: Job? = null
    private var messagesJob: Job? = null
    private var activeSessionId = 0L
    private var ephemeralCounter = 0L
    private var draftGroupIdCounter = 0L
    private var draftItemKeyCounter = 0L
    private var itemActionIdCounter = 0L
    private var transactionActionIdCounter = 0L

    // 掃碼節流與 in-flight 序列化（ScanThrottle 模式第三處使用點，照抄 AddTransactionViewModel.onScanFrame）
    private var scanInFlight = false
    private var lastScanAt = 0L
    private var lastBarcode: String? = null

    init {
        viewModelScope.launch {
            // 一次性清理：刪除延遲建立之前產生的零訊息空 session（之後不會再出現）
            runCatching { chatRepository.deleteEmptySessions() }
        }
        viewModelScope.launch {
            settingsRepository.observeAiAssistant().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                syncActiveSession(settings)
            }
        }
        viewModelScope.launch {
            chatRepository.observeSessions().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
        // S47：物品管理開關（FeatureFlagSettings.inventoryEnabled）——關閉時物品操作解析整段跳過
        viewModelScope.launch {
            settingsRepository.observeFeatureFlags().collect { flags ->
                _uiState.update { it.copy(inventoryEnabled = flags.inventoryEnabled) }
            }
        }
        // S44：草稿解析與編輯用的分類／帳戶清單
        viewModelScope.launch {
            val expense = categoryRepository.observeAll(CategoryType.EXPENSE).first()
            val income = categoryRepository.observeAll(CategoryType.INCOME).first()
            _uiState.update { it.copy(draftCategories = expense + income) }
        }
        viewModelScope.launch {
            val accounts = accountRepository.observeAll().first()
            _uiState.update { it.copy(draftAccounts = accounts) }
        }
    }

    fun send(question: String) {
        val q = question.trim()
        if (q.isBlank() || _uiState.value.loading) return
        val settings = _uiState.value.settings
        val now = System.currentTimeMillis()
        _uiState.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                if (settings.keepHistory) {
                    // 延遲建立：第一則訊息送出時才建立 session
                    val sessionId = ensureSessionForSend()
                    chatRepository.addMessage(
                        sessionId,
                        isUser = true,
                        text = q,
                        nowEpochMillis = now,
                    )
                    maybeSetNameFromQuestion(sessionId, q)
                } else {
                    appendEphemeral(isUser = true, text = q)
                }
                // S48 意圖分流閘門（本地關鍵字在前，省 AI 呼叫且杜絕誤判；順序為刻意設計，請勿改動）：
                // 含刪改動詞 → 交易操作 parse；含「物品」或刪改動詞 → 物品操作 parse；
                // 含「物品」→ 不觸發記帳 parse；其餘照既有：記帳 parse → 物品操作 parse → 查詢 → chat。
                val mentionsItem = q.contains(KEYWORD_ITEM)
                val hasTransactionActionVerb = TRANSACTION_ACTION_VERBS.any { q.contains(it) }
                if (hasTransactionActionVerb) {
                    val txAction = tryParseTransactionAction(q)
                    if (txAction != null) {
                        handleTransactionAction(txAction)
                        return@launch
                    }
                }
                if (_uiState.value.inventoryEnabled && (mentionsItem || hasTransactionActionVerb)) {
                    val itemAction = tryParseItemAction(q)
                    if (itemAction != null) {
                        handleItemAction(itemAction)
                        return@launch
                    }
                }
                if (!mentionsItem) {
                    // S44 記帳意圖：先嘗試草稿解析；有草稿→彈出草稿組卡，不進一般對話
                    val drafts = tryParseDraftsFromText(q)
                    if (drafts != null) {
                        if (hasPendingDraftGroup()) {
                            _uiState.update { it.copy(loading = false, draftBlockedHint = true) }
                            return@launch
                        }
                        createDraftGroup(drafts, TransactionSource.AI_TEXT)
                        _uiState.update { it.copy(loading = false) }
                        return@launch
                    }
                    // S47 物品操作意圖：記帳意圖落空後，若「物品管理」開啟則嘗試解析物品操作；
                    // 開關關閉時整段跳過（省 AI 呼叫）。確認前嚴禁寫入 DB。
                    if (_uiState.value.inventoryEnabled) {
                        val itemAction = tryParseItemAction(q)
                        if (itemAction != null) {
                            handleItemAction(itemAction)
                            return@launch
                        }
                    }
                }
                // S45 查詢意圖：記帳／物品意圖落空後，若「AI 查詢記錄」開啟則嘗試解析查詢條件；
                // 開關關閉時整段跳過（省 AI 呼叫），直接進一般對話
                if (settings.aiQueryEnabled) {
                    val query = tryParseQuery(q)
                    if (query != null) {
                        answerQuery(q, query)
                        return@launch
                    }
                }
                when (val result = aiRepository.chat(q, settings.includeFinanceContext)) {
                    is AiTextResult.Success -> {
                        if (settings.keepHistory) {
                            val sessionId = activeSessionId
                            chatRepository.addMessage(
                                sessionId,
                                isUser = false,
                                text = result.text,
                                nowEpochMillis = System.currentTimeMillis(),
                            )
                        } else {
                            appendEphemeral(isUser = false, text = result.text)
                        }
                        _uiState.update { it.copy(loading = false) }
                    }

                    is AiTextResult.Failure -> _uiState.update { it.copy(
                        loading = false,
                        error = result.error,
                    ) }
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "AI 對話失敗", t)
                _uiState.update { it.copy(
                    loading = false,
                    error = AiError.Network,
                ) }
            }
        }
    }

    // ===== S44 聊天智慧記帳：草稿組 =====

    /** 文字記帳意圖解析：帶使用者支出／收入分類與帳戶清單（模式照抄 EntryViewModel.parseFromText）；空或失敗回 null（接續一般對話） */
    private suspend fun tryParseDraftsFromText(q: String): List<TransactionDraft>? {
        val expense = categoryRepository.observeAll(CategoryType.EXPENSE).first().map { it.name }
        val income = categoryRepository.observeAll(CategoryType.INCOME).first().map { it.name }
        val accounts = accountRepository.observeAll().first().map { it.name }
        return runCatching {
            aiRepository.parseTransactionsFromText(q, expense, income, accounts).getOrNull()
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    // ===== S47 聊天室物品操作：解析 → 名稱匹配 → 確認卡 =====

    /**
     * 物品操作意圖解析：帶現有物品清單（UPDATE／DELETE 名稱匹配用）；失敗或非物品操作回 null
     * （續走查詢／一般 chat，體感不變）。模式照 tryParseDraftsFromText，嚴禁經 extractAiContent。
     */
    private suspend fun tryParseItemAction(q: String): ItemActionIntent? {
        val existingNames = itemRepository.observeAll().first().map { it.name }
        return runCatching {
            aiRepository.parseItemAction(q, existingNames).getOrNull()
        }.getOrNull()
    }

    /**
     * 物品意圖處理：CREATE 直接產確認卡；UPDATE／DELETE 先名稱匹配——
     * 無匹配→助理訊息「找不到物品」；多筆→列出候選請使用者講明；單筆→確認卡。
     * 同時僅一個未決物品操作，有未決再觸發只提示不產生新卡。確認前嚴禁寫入 DB。
     */
    private suspend fun handleItemAction(intent: ItemActionIntent) {
        if (hasPendingItemAction()) {
            _uiState.update { it.copy(loading = false, itemActionBlockedHint = true) }
            return
        }
        when (intent.action) {
            com.hmoney.app.domain.model.ItemActionType.CREATE -> {
                appendItemAction(intent, targetItem = null)
                _uiState.update { it.copy(loading = false) }
            }

            else -> {
                val items = itemRepository.observeAll().first()
                when (val match = matchItemByName(items, intent.name)) {
                    is ItemNameMatch.Unique -> {
                        appendItemAction(resolveExpiryDelta(intent, match.item), targetItem = match.item)
                        _uiState.update { it.copy(loading = false) }
                    }

                    is ItemNameMatch.NotFound -> {
                        deliverReply("找不到名為「${intent.name}」的物品，請確認名稱後再試一次。")
                        _uiState.update { it.copy(loading = false) }
                    }

                    is ItemNameMatch.Ambiguous -> {
                        val candidates = match.items.joinToString("、") { it.name }
                        deliverReply("找到多筆符合「${intent.name}」的物品：$candidates。請說明要操作哪一個。")
                        _uiState.update { it.copy(loading = false) }
                    }
                }
            }
        }
    }

    // ===== S48 聊天室交易刪改：解析 → 唯讀匹配 → 候選／確認卡 =====

    /**
     * 交易刪改意圖解析：模式照 tryParseItemAction（嚴禁經 extractAiContent）；
     * 失敗或非交易操作回 null（續走物品操作／記帳／查詢／一般 chat）。
     */
    private suspend fun tryParseTransactionAction(q: String): TransactionActionIntent? {
        val expense = categoryRepository.observeAll(CategoryType.EXPENSE).first().map { it.name }
        val income = categoryRepository.observeAll(CategoryType.INCOME).first().map { it.name }
        val accounts = accountRepository.observeAll().first().map { it.name }
        return runCatching {
            aiRepository.parseTransactionAction(q, expense, income, accounts).getOrNull()
        }.getOrNull()
    }

    /**
     * 交易刪改意圖處理：以 target 查既有唯讀 searchTransactions（上限 5 筆）——
     * 0 筆→助理回覆找不到；1 筆→直接確認卡；2~5 筆→候選卡（選定後進確認卡）。
     * 同時僅一一個未決交易操作，有未決再觸發只提示不產生新卡。確認前嚴禁寫入 DB。
     */
    private suspend fun handleTransactionAction(intent: TransactionActionIntent) {
        if (hasPendingTransactionAction()) {
            _uiState.update { it.copy(loading = false, transactionActionBlockedHint = true) }
            return
        }
        val state = _uiState.value
        val target = intent.target
        val categoryId = target.categoryName
            ?.let { name -> state.draftCategories.firstOrNull { it.name == name }?.id }
        val accountId = target.accountName
            ?.let { name -> state.draftAccounts.firstOrNull { it.name == name }?.id }
        val matches = if (intent.recent) {
            // S49 空泛刪除：取最近 5 筆（時間倒序，必要時再排序確保新→舊）
            transactionRepository.searchTransactions(limit = 5)
                .sortedByDescending { it.dateEpochMillis }
        } else {
            transactionRepository.searchTransactions(
                keyword = target.keyword.orEmpty(),
            startDate = target.startDateEpochMillis,
            endDate = target.endDateEpochMillis,
            accountId = accountId,
            categoryId = categoryId,
            type = target.type,
                minAmountCents = target.minAmountCents,
                maxAmountCents = target.maxAmountCents,
                limit = 5,
            )
        }
        when (matches.size) {
            0 -> {
                deliverReply("找不到符合的交易，請換個描述再試一次。")
                _uiState.update { it.copy(loading = false) }
            }

            1 -> appendTransactionAction(intent, target = matches.single(), candidates = matches)

            else -> appendTransactionAction(intent, target = null, candidates = matches)
        }
    }

    private fun hasPendingTransactionAction(): Boolean =
        _uiState.value.transactionActions.any { it.status == ChatDraftStatus.PENDING }

    private fun appendTransactionAction(
        intent: TransactionActionIntent,
        target: MoneyTransaction?,
        candidates: List<MoneyTransaction>,
    ) {
        val state = _uiState.value
        _uiState.update { s ->
            s.copy(
                transactionActions = s.transactionActions + ChatTransactionAction(
                    id = ++transactionActionIdCounter,
                    intent = intent,
                    target = target,
                    candidates = candidates,
                    working = target?.let {
                        mergeTransactionUpdates(it, intent.updates, state.draftCategories, state.draftAccounts)
                    },
                ),
                loading = false,
            )
        }
    }

    /** UPDATE 欄位合併（既有 fallback 慣例）：分類／帳戶以名稱對清單比對，對不上保留原值 */
    private fun mergeTransactionUpdates(
        target: MoneyTransaction,
        updates: TransactionUpdates?,
        categories: List<Category>,
        accounts: List<Account>,
    ): MoneyTransaction {
        val u = updates ?: return target
        return target.copy(
            amountCents = u.amountCents ?: target.amountCents,
            note = u.note ?: target.note,
            categoryId = u.categoryName
                ?.let { name -> categories.firstOrNull { it.name == name }?.id }
                ?: target.categoryId,
            accountId = u.accountName
                ?.let { name -> accounts.firstOrNull { it.name == name }?.id }
                ?: target.accountId,
            dateEpochMillis = u.dateEpochMillis ?: target.dateEpochMillis,
        )
    }

    private fun updateTransactionAction(id: Long, transform: (ChatTransactionAction) -> ChatTransactionAction) {
        _uiState.update { state ->
            state.copy(
                transactionActions = state.transactionActions.map { if (it.id == id) transform(it) else it },
            )
        }
    }

    /** 候選卡「選這筆」：選定後進確認卡（仍為 PENDING，確認前嚴禁寫入） */
    fun selectTransactionActionCandidate(id: Long, transactionId: Long) {
        updateTransactionAction(id) { action ->
            if (action.status != ChatDraftStatus.PENDING) return@updateTransactionAction action
            val picked = action.candidates.firstOrNull { it.id == transactionId } ?: return@updateTransactionAction action
            val state = _uiState.value
            action.copy(
                target = picked,
                working = mergeTransactionUpdates(picked, action.intent.updates, state.draftCategories, state.draftAccounts),
            )
        }
    }

    fun updateTransactionActionAmount(id: Long, amountCents: Long) =
        updateTransactionAction(id) { action ->
            if (action.status == ChatDraftStatus.PENDING && action.working != null) {
                action.copy(working = action.working.copy(amountCents = amountCents))
            } else {
                action
            }
        }

    fun updateTransactionActionNote(id: Long, note: String) =
        updateTransactionAction(id) { action ->
            if (action.status == ChatDraftStatus.PENDING && action.working != null) {
                action.copy(working = action.working.copy(note = note))
            } else {
                action
            }
        }

    fun updateTransactionActionCategory(id: Long, categoryId: Long) =
        updateTransactionAction(id) { action ->
            if (action.status == ChatDraftStatus.PENDING && action.working != null) {
                action.copy(working = action.working.copy(categoryId = categoryId))
            } else {
                action
            }
        }

    fun updateTransactionActionAccount(id: Long, accountId: Long) =
        updateTransactionAction(id) { action ->
            if (action.status == ChatDraftStatus.PENDING && action.working != null) {
                action.copy(working = action.working.copy(accountId = accountId))
            } else {
                action
            }
        }

    fun updateTransactionActionDate(id: Long, dateEpochMillis: Long) =
        updateTransactionAction(id) { action ->
            if (action.status == ChatDraftStatus.PENDING && action.working != null) {
                action.copy(working = action.working.copy(dateEpochMillis = dateEpochMillis))
            } else {
                action
            }
        }

    /**
     * 確認交易操作：唯一寫入點——只呼叫既有 updateTransaction／deleteTransaction（嚴禁新增寫入路徑）。
     * 一次僅一筆；終態（CONFIRMED／CANCELLED）不得再寫入。
     */
    fun confirmTransactionAction(id: Long) {
        val action = _uiState.value.transactionActions.firstOrNull { it.id == id } ?: return
        if (action.status != ChatDraftStatus.PENDING) return
        val target = action.target ?: return
        viewModelScope.launch {
            when (action.intent.action) {
                TransactionActionType.DELETE -> transactionRepository.deleteTransaction(target.id)
                TransactionActionType.UPDATE -> transactionRepository.updateTransaction(action.working ?: target)
            }
            updateTransactionAction(id) { it.copy(status = ChatDraftStatus.CONFIRMED) }
        }
    }

    /** 取消：不寫入／不復原 DB；終態唯讀 */
    fun cancelTransactionAction(id: Long) {
        updateTransactionAction(id) { action ->
            if (action.status == ChatDraftStatus.PENDING) action.copy(status = ChatDraftStatus.CANCELLED) else action
        }
    }

    fun clearTransactionActionBlockedHint() {
        _uiState.update { it.copy(transactionActionBlockedHint = false) }
    }

    /**
     * S49 相對期限換算：「增加 N 天」＝新到期日＝matched（名稱匹配到的既有物品）到期日＋delta；
     * 無 delta 或 matched 無到期日時保持原意圖（絕對日期語意相容）。
     */
    private fun resolveExpiryDelta(intent: ItemActionIntent, matched: Item): ItemActionIntent {
        val delta = intent.expiryDeltaDays ?: return intent
        val base = matched.expiryDateEpochMillis ?: return intent
        val zone = java.time.ZoneId.systemDefault()
        val newExpiry = java.time.Instant.ofEpochMilli(base).atZone(zone).toLocalDate()
            .plusDays(delta.toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return intent.copy(expiryDateEpochMillis = newExpiry, expiryDeltaDays = null)
    }

    private fun hasPendingItemAction(): Boolean =
        _uiState.value.itemActions.any { it.status == ChatDraftStatus.PENDING }

    private fun appendItemAction(intent: ItemActionIntent, targetItem: Item?) {
        _uiState.update { state ->
            state.copy(
                itemActions = state.itemActions + ChatItemAction(
                    id = ++itemActionIdCounter,
                    intent = intent,
                    targetItem = targetItem,
                ),
            )
        }
    }

    private fun updateItemAction(id: Long, transform: (ChatItemAction) -> ChatItemAction) {
        _uiState.update { state ->
            state.copy(
                itemActions = state.itemActions.map { if (it.id == id) transform(it) else it },
            )
        }
    }

    /** 確認卡欄位編輯（僅 PENDING 可編輯；名稱不可為空） */
    fun updateItemActionName(id: Long, name: String) =
        updateItemAction(id) { action ->
            if (action.status == ChatDraftStatus.PENDING && name.isNotBlank()) {
                action.copy(intent = action.intent.copy(name = name.trim()))
            } else {
                action
            }
        }

    fun updateItemActionStorage(id: Long, storage: String) =
        updateItemAction(id) { action ->
            if (action.status == ChatDraftStatus.PENDING) {
                action.copy(
                    intent = action.intent.copy(
                        storageLocation = storage.trim().takeIf { it.isNotEmpty() },
                    ),
                )
            } else {
                action
            }
        }

    fun updateItemActionQuantity(id: Long, quantity: Int?) =
        updateItemAction(id) { action ->
            if (action.status == ChatDraftStatus.PENDING) {
                action.copy(intent = action.intent.copy(quantity = quantity?.takeIf { it > 0 }))
            } else {
                action
            }
        }

    fun updateItemActionExpiry(id: Long, expiryEpochMillis: Long?) =
        updateItemAction(id) { action ->
            if (action.status == ChatDraftStatus.PENDING) {
                action.copy(intent = action.intent.copy(expiryDateEpochMillis = expiryEpochMillis))
            } else {
                action
            }
        }

    /**
     * 確認物品操作：唯一寫入點——走 ItemRepository（物品非交易，嚴禁經 ConfirmDraftsUseCase）。
     * 終態（CONFIRMED／CANCELLED）不得再寫入。
     */
    fun confirmItemAction(id: Long) {
        val action = _uiState.value.itemActions.firstOrNull { it.id == id } ?: return
        if (action.status != ChatDraftStatus.PENDING) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val intent = action.intent
            when (intent.action) {
                com.hmoney.app.domain.model.ItemActionType.CREATE -> {
                    itemRepository.addItem(
                        Item(
                            name = intent.name,
                            storageLocation = intent.storageLocation,
                            quantity = intent.quantity ?: 1,
                            expiryDateEpochMillis = intent.expiryDateEpochMillis,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                        ),
                    )
                }

                com.hmoney.app.domain.model.ItemActionType.UPDATE -> {
                    val target = action.targetItem ?: return@launch
                    itemRepository.updateItem(
                        target.copy(
                            storageLocation = intent.storageLocation ?: target.storageLocation,
                            quantity = intent.quantity ?: target.quantity,
                            expiryDateEpochMillis = intent.expiryDateEpochMillis
                                ?: target.expiryDateEpochMillis,
                            updatedAtEpochMillis = now,
                        ),
                    )
                }

                com.hmoney.app.domain.model.ItemActionType.DELETE -> {
                    val target = action.targetItem ?: return@launch
                    itemRepository.deleteItem(target.id)
                }
            }
            updateItemAction(id) { it.copy(status = ChatDraftStatus.CONFIRMED) }
        }
    }

    /** 取消：零寫入、不復原 DB，直接轉唯讀終態 */
    fun cancelItemAction(id: Long) {
        val action = _uiState.value.itemActions.firstOrNull { it.id == id } ?: return
        if (action.status != ChatDraftStatus.PENDING) return
        updateItemAction(id) { it.copy(status = ChatDraftStatus.CANCELLED) }
    }

    fun clearItemActionBlockedHint() {
        _uiState.update { it.copy(itemActionBlockedHint = false) }
    }

    // ===== S45 AI 查詢記錄：解析 → 唯讀檢索 → AI 綜整回答 =====

    /**
     * 查詢意圖解析：失敗或非查詢語意回 null（接續一般 chat 回覆，體感不變）。
     * 模式照 tryParseDraftsFromText，帶使用者分類／帳戶清單。
     */
    private suspend fun tryParseQuery(q: String): TransactionQuery? {
        val expense = categoryRepository.observeAll(CategoryType.EXPENSE).first().map { it.name }
        val income = categoryRepository.observeAll(CategoryType.INCOME).first().map { it.name }
        val accounts = accountRepository.observeAll().first().map { it.name }
        return runCatching {
            aiRepository.parseTransactionQuery(q, expense, income, accounts).getOrNull()
        }.getOrNull()
    }

    /** 唯讀檢索＋格式化＋AI 綜整回答（零結果由彙整端回「沒有符合的記錄」語意）；嚴禁任何寫入 */
    private suspend fun answerQuery(q: String, query: TransactionQuery) {
        val state = _uiState.value
        val results = transactionRepository.searchTransactions(
            keyword = query.keyword.orEmpty(),
            startDate = query.startDateEpochMillis,
            endDate = query.endDateEpochMillis,
            accountId = query.accountName?.let { name ->
                state.draftAccounts.firstOrNull { it.name == name }?.id
            },
            categoryId = query.categoryName?.let { name ->
                state.draftCategories.firstOrNull { it.name == name }?.id
            },
            type = query.type,
            minAmountCents = query.minAmountCents,
            maxAmountCents = query.maxAmountCents,
            limit = query.limit,
        )
        val resultsText = results.joinToString("\n") { tx -> formatQueryResultRow(tx) }
        when (val reply = aiRepository.summarizeQueryResult(q, resultsText)) {
            is AiTextResult.Success -> {
                deliverReply(reply.text)
                _uiState.update { it.copy(loading = false) }
            }

            is AiTextResult.Failure -> _uiState.update { it.copy(
                loading = false,
                error = reply.error,
            ) }
        }
    }

    /** 查詢結果列格式化：日期／金額／類型／分類／帳戶／備註／標籤 */
    private fun formatQueryResultRow(tx: MoneyTransaction): String {
        val state = _uiState.value
        val typeLabel = when (tx.type) {
            TransactionType.EXPENSE -> "支出"
            TransactionType.INCOME -> "收入"
            TransactionType.TRANSFER -> "轉帳"
        }
        val categoryName = state.draftCategories.firstOrNull { it.id == tx.categoryId }?.name ?: "未分類"
        val accountName = state.draftAccounts.firstOrNull { it.id == tx.accountId }?.name ?: ""
        val tags = if (tx.tags.isEmpty()) "" else tx.tags.joinToString("、")
        return "- 日期 ${Formats.date(tx.dateEpochMillis)}｜$typeLabel｜金額 ${Formats.money(tx.amountCents)}" +
            "｜分類 $categoryName｜帳戶 $accountName｜備註 ${tx.note}｜標籤 $tags"
    }

    /** 回覆訊息交付：沿用 chat 的保留對話／記憶體雙路徑 */
    private suspend fun deliverReply(text: String) {
        val settings = _uiState.value.settings
        if (settings.keepHistory) {
            val sessionId = ensureSessionForSend()
            chatRepository.addMessage(
                sessionId,
                isUser = false,
                text = text,
                nowEpochMillis = System.currentTimeMillis(),
            )
        } else {
            appendEphemeral(isUser = false, text = text)
        }
    }

    private fun hasPendingDraftGroup(): Boolean =
        _uiState.value.draftGroups.any { it.status == ChatDraftStatus.PENDING }

    private suspend fun createDraftGroup(
        drafts: List<TransactionDraft>,
        source: TransactionSource,
        barcode: String? = null,
        receiptImagePath: String? = null,
    ) {
        val state = _uiState.value
        val transactions = drafts.map { draft ->
            resolveDraftToTransaction(
                draft = draft,
                source = source,
                categories = state.draftCategories,
                accounts = state.draftAccounts,
                nowEpochMillis = System.currentTimeMillis(),
                barcode = barcode,
                receiptImagePath = receiptImagePath?.takeIf { source == TransactionSource.AI_IMAGE },
            )
        }
        val group = ChatDraftGroup(
            id = ++draftGroupIdCounter,
            source = source,
            items = transactions.map { ChatDraftItem(it, key = ++draftItemKeyCounter) },
            barcode = barcode,
            receiptImagePath = receiptImagePath,
        )
        _uiState.update { it.copy(draftGroups = it.draftGroups + group) }
    }

    /** 圖片記帳：選圖後走 parseTransactionsFromImage（附暫存縮圖，模式照抄 EntryViewModel 拍照入口） */
    fun sendImage(uri: Uri) {
        startImageEntry { withContext(Dispatchers.IO) { receiptImageStore.decodeAndStore(uri) } }
    }

    /** 圖片記帳內部入口（internal 供單元測試接縫——測試環境無法建構 Uri） */
    internal fun startImageEntry(decode: suspend () -> Pair<String, String>?) {
        if (_uiState.value.loading) return
        if (hasPendingDraftGroup()) {
            _uiState.update { it.copy(draftBlockedHint = true) }
            return
        }
        _uiState.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val decoded = decode()
                if (decoded == null) {
                    _uiState.update { it.copy(loading = false, error = AiError.Parse) }
                    return@launch
                }
                val (base64, path) = decoded
                val expense = categoryRepository.observeAll(CategoryType.EXPENSE).first().map { it.name }
                val income = categoryRepository.observeAll(CategoryType.INCOME).first().map { it.name }
                val accounts = accountRepository.observeAll().first().map { it.name }
                val result = runCatching {
                    aiRepository.parseTransactionsFromImage(
                        base64, "image/jpeg", "", expense, income, accounts,
                    )
                }.getOrNull()
                when {
                    result == null ->
                        _uiState.update { it.copy(loading = false, error = AiError.Network) }
                    result.isFailure -> {
                        // 暫存縮圖刪除，嚴禁孤兒檔案；沿用既有錯誤分類（含 VisionUnsupported）
                        receiptImageStore.delete(path)
                        val error = (result.exceptionOrNull() as? AiCallException)?.error ?: AiError.Parse
                        _uiState.update { it.copy(loading = false, error = error) }
                    }
                    result.getOrNull().isNullOrEmpty() -> {
                        receiptImageStore.delete(path)
                        _uiState.update { it.copy(loading = false, error = AiError.Parse) }
                    }
                    else -> {
                        createDraftGroup(result.getOrNull()!!, TransactionSource.AI_IMAGE, receiptImagePath = path)
                        _uiState.update { it.copy(loading = false) }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "圖片記帳解析失敗", t)
                _uiState.update { it.copy(loading = false, error = AiError.Network) }
            }
        }
    }

    /** 掃碼 Dialog 幀入口：ScanThrottle 純函式＋in-flight 序列化（第三處使用點） */
    fun onScanFrame(bitmap: Bitmap, rotationDegrees: Int) {
        if (scanInFlight) return
        val now = System.currentTimeMillis()
        if (!shouldScanAt(lastScanAt, now)) return
        lastScanAt = now
        scanInFlight = true
        viewModelScope.launch {
            try {
                when (val result = barcodeScannerSource.scan(bitmap, rotationDegrees)) {
                    is BarcodeScanResult.Success -> {
                        val barcode = result.barcode
                        if (isSameBarcode(lastBarcode, barcode)) return@launch
                        lastBarcode = barcode
                        _uiState.update { it.copy(scannedBarcode = barcode, scanError = null) }
                    }
                    is BarcodeScanResult.Failure ->
                        _uiState.update { it.copy(scanError = result.error) }
                }
            } finally {
                scanInFlight = false
            }
        }
    }

    /** 「使用此條碼」：查價格趨勢後產生草稿組（金額預填上次價、無紀錄為 0，source=barcode） */
    fun useScannedBarcode() {
        val code = _uiState.value.scannedBarcode ?: return
        clearScanState()
        if (hasPendingDraftGroup()) {
            _uiState.update { it.copy(draftBlockedHint = true) }
            return
        }
        viewModelScope.launch {
            val trend = runCatching { barcodeRepository.getPriceTrend(code).first() }.getOrNull()
            val draft = TransactionDraft(
                type = TransactionType.EXPENSE,
                amountCents = trend?.lastPriceCents ?: 0L,
                note = "",
                dateEpochMillis = System.currentTimeMillis(),
            )
            createDraftGroup(listOf(draft), TransactionSource.BARCODE, barcode = code)
        }
    }

    fun clearScanState() {
        lastBarcode = null
        lastScanAt = 0L
        scanInFlight = false
        _uiState.update { it.copy(scannedBarcode = null, scanError = null) }
    }

    fun clearDraftBlockedHint() {
        _uiState.update { it.copy(draftBlockedHint = false) }
    }

    private fun updateDraftGroup(groupId: Long, transform: (ChatDraftGroup) -> ChatDraftGroup) {
        _uiState.update { state ->
            state.copy(draftGroups = state.draftGroups.map { if (it.id == groupId) transform(it) else it })
        }
    }

    private fun updateDraft(
        groupId: Long,
        key: MoneyTransaction,
        transform: (MoneyTransaction) -> MoneyTransaction,
    ) {
        updateDraftGroup(groupId) { group ->
            group.copy(
                items = group.items.map {
                    if (it.transaction === key) it.copy(transaction = transform(it.transaction)) else it
                },
            )
        }
    }

    fun updateDraftAmount(groupId: Long, key: MoneyTransaction, amountCents: Long) =
        updateDraft(groupId, key) { it.copy(amountCents = amountCents) }

    fun updateDraftNote(groupId: Long, key: MoneyTransaction, note: String) =
        updateDraft(groupId, key) { it.copy(note = note) }

    fun updateDraftCategory(groupId: Long, key: MoneyTransaction, categoryId: Long) =
        updateDraft(groupId, key) { it.copy(categoryId = categoryId) }

    fun updateDraftAccount(groupId: Long, key: MoneyTransaction, accountId: Long) =
        updateDraft(groupId, key) { it.copy(accountId = accountId) }

    fun updateDraftDate(groupId: Long, key: MoneyTransaction, dateEpochMillis: Long) =
        updateDraft(groupId, key) { it.copy(dateEpochMillis = dateEpochMillis) }

    fun removeDraft(groupId: Long, key: MoneyTransaction) {
        updateDraftGroup(groupId) { group ->
            group.copy(
                items = group.items.map { if (it.transaction === key) it.copy(removed = true) else it },
            )
        }
    }

    /** 全部確認：走 ConfirmDraftsUseCase（草稿唯一寫入路徑）；全剔除視同取消 */
    fun confirmDraftGroup(groupId: Long) {
        val group = _uiState.value.draftGroups.firstOrNull { it.id == groupId } ?: return
        if (group.status != ChatDraftStatus.PENDING) return
        val toWrite = group.items.filter { !it.removed }
        if (toWrite.isEmpty()) {
            cancelDraftGroup(groupId)
            return
        }
        viewModelScope.launch {
            val written = confirmDraftsUseCase.execute(toWrite.map { it.transaction })
            updateDraftGroup(groupId) { it.copy(status = ChatDraftStatus.CONFIRMED, writtenCount = written) }
        }
    }

    /** 取消：不寫入／不復原 DB；刪除暫存縮圖（嚴禁孤兒檔案） */
    fun cancelDraftGroup(groupId: Long) {
        val group = _uiState.value.draftGroups.firstOrNull { it.id == groupId } ?: return
        if (group.status != ChatDraftStatus.PENDING) return
        group.receiptImagePath?.let { receiptImageStore.delete(it) }
        updateDraftGroup(groupId) { it.copy(status = ChatDraftStatus.CANCELLED) }
    }

    /** 丟棄所有未決草稿組（切 session／清空時）：刪暫存縮圖 */
    private fun discardPendingDraftGroups() {
        _uiState.value.draftGroups
            .filter { it.status == ChatDraftStatus.PENDING }
            .forEach { group -> group.receiptImagePath?.let { receiptImageStore.delete(it) } }
        _uiState.update { state ->
            state.copy(
                draftGroups = state.draftGroups.filter { it.status != ChatDraftStatus.PENDING },
                itemActions = state.itemActions.filter { it.status != ChatDraftStatus.PENDING },
                itemActionBlockedHint = false,
                transactionActions = state.transactionActions.filter { it.status != ChatDraftStatus.PENDING },
                transactionActionBlockedHint = false,
                scannedBarcode = null,
                scanError = null,
            )
        }
    }

    override fun onCleared() {
        discardPendingDraftGroups()
        super.onCleared()
    }

    fun cancel() {
        job?.cancel()
        _uiState.update { it.copy(loading = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** 新對話：只把 currentSessionId 歸零（不建立任何 DB 記錄），下次送訊息才建立 */
    fun newSession() {
        discardPendingDraftGroups()
        viewModelScope.launch {
            if (_uiState.value.settings.keepHistory) {
                settingsRepository.setAiCurrentSessionId(0L)
            } else {
                ephemeralCounter = 0L
                _uiState.update { it.copy(messages = emptyList(), error = null) }
            }
        }
    }

    /** 切換到既有對話（僅保留開啟時有意義） */
    fun switchTo(sessionId: Long) {
        viewModelScope.launch {
            settingsRepository.setAiCurrentSessionId(sessionId)
        }
    }

    /** 刪除單一對話；若刪的是目前對話則回到「無進行中對話」狀態，不自動補建 */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (sessionId == activeSessionId) {
                settingsRepository.setAiCurrentSessionId(0L)
            }
        }
    }

    /** 清空所有對話（保留關閉時僅清空記憶體）；清完回到「無進行中對話」，不補建 */
    fun clearAll() {
        discardPendingDraftGroups()
        viewModelScope.launch {
            if (_uiState.value.settings.keepHistory) {
                chatRepository.clearAll()
                settingsRepository.setAiCurrentSessionId(0L)
            } else {
                ephemeralCounter = 0L
                _uiState.update { it.copy(messages = emptyList(), error = null) }
            }
        }
    }

    /** 刪除單則訊息 */
    fun deleteMessage(id: Long) {
        if (id > 0) {
            viewModelScope.launch { chatRepository.deleteMessage(id) }
        } else {
            _uiState.update { state ->
                state.copy(messages = state.messages.filterNot { it.id == id })
            }
        }
    }

    /**
     * 以 DataStore 的 currentSessionId 為單一事實來源同步進行中對話：
     * id 有效且存在 → 觀察其訊息；否則維持 0（延遲建立，絕不在此建立 session）。
     */
    private suspend fun syncActiveSession(settings: SettingsRepository.AiAssistantSettings) {
        if (!settings.keepHistory) {
            if (activeSessionId != 0L) {
                activeSessionId = 0L
                messagesJob?.cancel()
                _uiState.update { it.copy(currentSessionId = 0L) }
            }
            return
        }
        val stored = settings.currentSessionId
        val existing = if (stored > 0) chatRepository.getSession(stored) else null
        if (existing != null) {
            if (activeSessionId != existing.id) {
                activeSessionId = existing.id
                observeMessages(existing.id)
            }
            _uiState.update { it.copy(currentSessionId = existing.id) }
        } else {
            activeSessionId = 0L
            messagesJob?.cancel()
            if (stored != 0L) settingsRepository.setAiCurrentSessionId(0L)
            _uiState.update { it.copy(currentSessionId = 0L, messages = emptyList()) }
        }
    }

    /**
     * 送訊息路徑的 session 保證：已有進行中對話則沿用；否則建立一次並寫回
     * DataStore（唯一事實來源），讓其他頁的 VM 實例跟著同步。
     */
    private suspend fun ensureSessionForSend(): Long {
        activeSessionId.takeIf { it > 0 }
            ?.let { id -> chatRepository.getSession(id) }
            ?.let { return it.id }
        val id = chatRepository.createSession(System.currentTimeMillis())
        settingsRepository.setAiCurrentSessionId(id)
        activeSessionId = id
        observeMessages(id)
        _uiState.update { it.copy(currentSessionId = id) }
        return id
    }

    private fun observeMessages(sessionId: Long) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.observeMessages(sessionId).collect { list ->
                _uiState.update { state ->
                    state.copy(
                        messages = list.map { AiChatMessage(it.id, it.isUser, it.text) },
                    )
                }
            }
        }
    }

    /**
     * 自動命名：送出第一則 USER 訊息時，以「使用者問題首行截 20 字」命名；
     * 直接查 DB 判斷 title 是否為空（不讀 UI state，避免競態）。
     */
    private suspend fun maybeSetNameFromQuestion(sessionId: Long, question: String) {
        val session = chatRepository.getSession(sessionId) ?: return
        if (session.title.isBlank()) {
            val title = ChatTitle.from(question)
            if (title.isNotBlank()) {
                chatRepository.setSessionTitle(sessionId, title)
            }
        }
    }

    /** 純記憶體訊息（「保留對話」關閉時）：id 使用負數避免與 DB id 衝突 */
    private fun appendEphemeral(isUser: Boolean, text: String) {
        ephemeralCounter -= 1
        val id = ephemeralCounter
        _uiState.update { state ->
            state.copy(messages = state.messages + AiChatMessage(id, isUser, text))
        }
    }

    private companion object {
        private const val TAG = "AiChatVM"

        /** S48 意圖閘門關鍵字（刻意設計，請勿改動順序與內容） */
        private const val KEYWORD_ITEM = "物品"
        private val TRANSACTION_ACTION_VERBS = listOf("刪除", "移除", "改成", "修改", "編輯")
    }
}
