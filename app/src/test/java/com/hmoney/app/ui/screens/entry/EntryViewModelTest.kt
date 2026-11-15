package com.hmoney.app.ui.screens.entry

import com.hmoney.app.data.repository.ReceiptImageStore
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.AiAnalysis
import com.hmoney.app.domain.model.AiConfig
import com.hmoney.app.domain.model.AiFilterSuggestion
import com.hmoney.app.domain.model.AiSuggestion
import com.hmoney.app.domain.model.AiTextResult
import com.hmoney.app.domain.model.TransactionQuery
import com.hmoney.app.domain.model.AppearanceSettings
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
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
import com.hmoney.app.domain.scanner.BarcodeScanResult
import com.hmoney.app.domain.scanner.BarcodeScannerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * EntryViewModel 測試（S41）：確認寫入只發生在使用者按「全部確認」之後——
 * 解析完成（PREVIEW）時 fake repository 零呼叫；confirmAll 後呼叫次數與每筆 source/barcode 正確。
 */
class EntryViewModelTest {

    private lateinit var aiRepo: FakeAiRepository
    private lateinit var txRepo: FakeTransactionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        aiRepo = FakeAiRepository()
        txRepo = FakeTransactionRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): EntryViewModel = EntryViewModel(
        aiRepository = aiRepo,
        transactionRepository = txRepo,
        categoryRepository = FakeCategoryRepository(),
        accountRepository = FakeAccountRepository(),
        barcodeRepository = FakeBarcodeRepository(),
        barcodeScannerSource = FakeScannerSource(),
        receiptImageStore = FakeReceiptImageStore(),
        settingsRepository = FakeEntrySettingsRepository(),
    )

    @Test
    fun `解析完成進入 PREVIEW 且尚未寫入任何交易`() {
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "午餐"),
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 3000L, note = "捷運"),
        )
        val vm = createViewModel()
        vm.parseFromText("昨天午餐 120、捷運 30")
        assertEquals(EntryViewModel.Phase.PREVIEW, vm.uiState.value.phase)
        assertEquals(2, vm.uiState.value.drafts.size)
        // 鐵律：確認前嚴禁寫入
        assertEquals(0, txRepo.addCount)
    }

    @Test
    fun `確認後批次寫入且每筆帶 ai_text 來源`() {
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "午餐"),
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 3000L, note = "捷運"),
        )
        val vm = createViewModel()
        vm.parseFromText("昨天午餐 120、捷運 30")
        vm.confirmAll()
        assertEquals(EntryViewModel.Phase.DONE, vm.uiState.value.phase)
        assertEquals(2, txRepo.addCount)
        assertEquals(2, vm.uiState.value.writtenCount)
        txRepo.added.forEach { tx ->
            assertEquals(TransactionSource.AI_TEXT, tx.source)
        }
    }

    @Test
    fun `剔除的草稿不會寫入`() {
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "午餐"),
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 3000L, note = "捷運"),
        )
        val vm = createViewModel()
        vm.parseFromText("昨天午餐 120、捷運 30")
        vm.removeDraft(vm.uiState.value.drafts.first().transaction)
        vm.confirmAll()
        assertEquals(1, txRepo.addCount)
        assertEquals("捷運", txRepo.added.first().note)
    }

    @Test
    fun `掃碼草稿帶 barcode 來源與條碼欄位`() {
        aiRepo.textDrafts = emptyList()
        val vm = createViewModel()
        vm.prepareScanDraft("4711234567890")
        assertEquals(EntryViewModel.Phase.PREVIEW, vm.uiState.value.phase)
        vm.confirmAll()
        assertEquals(1, txRepo.addCount)
        val tx = txRepo.added.first()
        assertEquals(TransactionSource.BARCODE, tx.source)
        assertEquals("4711234567890", tx.barcode)
    }

    @Test
    fun `解析時傳入使用者既有分類與帳戶清單`() {
        aiRepo.textDrafts = emptyList()
        val vm = createViewModel()
        vm.parseFromText("漢堡120 悠遊卡")
        assertTrue(aiRepo.lastExpenseCategories.isNotEmpty())
        assertTrue(aiRepo.lastIncomeCategories.isNotEmpty())
        assertTrue(aiRepo.lastAccountNames.isNotEmpty())
        assertTrue(aiRepo.lastExpenseCategories.contains("餐飲"))
        assertTrue(aiRepo.lastAccountNames.contains("現金"))
    }

    @Test
    fun `掃碼草稿預覽期間不吃幀`() {
        val vm = createViewModel()
        vm.prepareScanDraft("4711234567890")
        assertEquals(EntryViewModel.Phase.PREVIEW, vm.uiState.value.phase)
        // S42 相機幀 phase 白名單：PREVIEW 期間幀不得把 phase 蓋回 SCANNING
        vm.onCameraFrame(anyBitmap(), 0)
        assertEquals(EntryViewModel.Phase.PREVIEW, vm.uiState.value.phase)
        assertEquals(0, txRepo.addCount)
    }

    @Test
    fun `無購買紀錄條碼記一筆金額為零可編輯`() {
        val vm = createViewModel()
        vm.prepareScanDraft("4711234567899")
        assertEquals(EntryViewModel.Phase.PREVIEW, vm.uiState.value.phase)
        val draft = vm.uiState.value.drafts.single()
        assertEquals(0L, draft.transaction.amountCents)
        vm.updateDraftAmount(draft.transaction, 9900L)
        assertEquals(9900L, vm.uiState.value.drafts.single().transaction.amountCents)
    }

    @Test
    fun `草稿穩定 key 各筆相異且不因編輯改變`() {
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "午餐"),
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 3000L, note = "捷運"),
        )
        val vm = createViewModel()
        vm.parseFromText("昨天午餐 120、捷運 30")
        val keys = vm.uiState.value.drafts.map { it.key }
        assertEquals(2, keys.distinct().size)
        val first = vm.uiState.value.drafts.first()
        vm.updateDraftAmount(first.transaction, 5500L)
        // 編輯後 copy 出新物件，第二次編輯需以最新 state 的 transaction 為據（與 UI 行為一致）
        val refreshed = vm.uiState.value.drafts.first()
        vm.updateDraftNote(refreshed.transaction, "改備註")
        // S49：編輯 copy 保留穩定 key（LazyColumn key 不變 → 不重組失焦）
        assertEquals(keys, vm.uiState.value.drafts.map { it.key })
        assertEquals(5500L, vm.uiState.value.drafts.first().transaction.amountCents)
        assertEquals("改備註", vm.uiState.value.drafts.first().transaction.note)
    }

    @Test
    fun `解析失敗回 IDLE 且不寫入`() {
        aiRepo.textDrafts = emptyList()
        val vm = createViewModel()
        vm.parseFromText("亂碼")
        assertEquals(EntryViewModel.Phase.IDLE, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.errorRes != null)
        assertEquals(0, txRepo.addCount)
    }
}

/** 單元測試環境無 Android framework：以 Unsafe 配置 Bitmap 實例（phase guard 在任何 bitmap 存取前返回，不會觸碰 stub） */
@Suppress("DiscouragedPrivateApi")
private fun anyBitmap(): android.graphics.Bitmap {
    val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
    field.isAccessible = true
    val unsafe = field.get(null) as sun.misc.Unsafe
    return unsafe.allocateInstance(android.graphics.Bitmap::class.java) as android.graphics.Bitmap
}

private class FakeAiRepository : AiRepository {
    var textDrafts: List<TransactionDraft> = emptyList()

    override fun observeConfig(): Flow<AiConfig> = emptyFlow()
    override suspend fun getConfig(): AiConfig = AiConfig()
    override suspend fun saveConfig(baseUrl: String, apiKey: String, model: String) = Unit
    override suspend fun analyzeWeek() = throw UnsupportedOperationException()
    override suspend fun askQuestion(question: String) = throw UnsupportedOperationException()
    override suspend fun chat(question: String, includeContext: Boolean): AiTextResult =
        throw UnsupportedOperationException()
    override suspend fun polishNote(note: String): AiTextResult = throw UnsupportedOperationException()
    override suspend fun suggestForNote(note: String, categoryNames: List<String>): Result<AiSuggestion> =
        throw UnsupportedOperationException()
    override suspend fun interpretFilter(
        query: String,
        categoryNames: List<String>,
        accountNames: List<String>,
    ): Result<AiFilterSuggestion> = throw UnsupportedOperationException()
    override suspend fun analyzeData(title: String, data: String): AiTextResult =
        throw UnsupportedOperationException()
    var lastExpenseCategories: List<String> = emptyList()
    var lastIncomeCategories: List<String> = emptyList()
    var lastAccountNames: List<String> = emptyList()

    override suspend fun parseTransactionsFromText(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<List<TransactionDraft>> {
        lastExpenseCategories = expenseCategories
        lastIncomeCategories = incomeCategories
        lastAccountNames = accountNames
        return Result.success(textDrafts)
    }

    override suspend fun parseTransactionsFromImage(
        imageBase64: String,
        mimeType: String,
        hint: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<List<TransactionDraft>> = throw UnsupportedOperationException()
    override suspend fun parseTransactionQuery(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<TransactionQuery?> = Result.success(null)

    override suspend fun summarizeQueryResult(question: String, resultsText: String): AiTextResult =
        AiTextResult.Success("")

    override suspend fun suggestItemFields(
        name: String,
        barcode: String?,
    ): Result<com.hmoney.app.domain.model.ItemSuggestion> = Result.success(com.hmoney.app.domain.model.ItemSuggestion())

        override suspend fun parseItemAction(
        text: String,
        existingItemNames: List<String>,
    ): Result<com.hmoney.app.domain.model.ItemActionIntent?> = Result.success(null)

    override suspend fun parseTransactionAction(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<com.hmoney.app.domain.model.TransactionActionIntent?> =
        Result.success(null)

override fun observeHistory(): Flow<List<AiAnalysis>> = emptyFlow()
    override suspend fun deleteHistory(id: Long) = Unit
}

private class FakeTransactionRepository : TransactionRepository {
    val added = mutableListOf<MoneyTransaction>()
    var addCount = 0

    override fun observeTransactions(
        keyword: String,
        startDate: Long?,
        endDate: Long?,
        accountId: Long?,
        categoryId: Long?,
        type: TransactionType?,
    ): Flow<List<MoneyTransaction>> = emptyFlow()

    override suspend fun getTransaction(id: Long): MoneyTransaction? = null
    override suspend fun searchTransactions(
            keyword: String,
            startDate: Long?,
            endDate: Long?,
            accountId: Long?,
            categoryId: Long?,
            type: TransactionType?,
            minAmountCents: Long?,
            maxAmountCents: Long?,
            limit: Int,
        ): List<MoneyTransaction> = emptyList()
    override fun observeTransactionsByBarcode(barcode: String): Flow<List<MoneyTransaction>> = emptyFlow()
    override suspend fun addTransaction(transaction: MoneyTransaction): Long {
        added += transaction
        addCount++
        return added.size.toLong()
    }

    override suspend fun updateTransaction(transaction: MoneyTransaction) = Unit
    override suspend fun deleteTransaction(id: Long) = Unit
}

private class FakeCategoryRepository : CategoryRepository {
    private val categories = listOf(
        Category(id = 1, name = "餐飲", type = CategoryType.EXPENSE, iconKey = "restaurant", colorArgb = 0L),
        Category(id = 7, name = "其他", type = CategoryType.EXPENSE, iconKey = "more_horiz", colorArgb = 0L),
        Category(id = 8, name = "其他收入", type = CategoryType.INCOME, iconKey = "add_circle", colorArgb = 0L),
    )

    override fun observeActive(type: CategoryType): Flow<List<Category>> = MutableStateFlow(
        categories.filter { it.type == type },
    )
    override fun observeAll(type: CategoryType): Flow<List<Category>> = MutableStateFlow(
        categories.filter { it.type == type },
    )
    override suspend fun getCategory(id: Long): Category? = categories.firstOrNull { it.id == id }
    override suspend fun addCategory(category: Category): Long = 0L
    override suspend fun updateCategory(category: Category) = Unit
    override suspend fun hideCategory(id: Long) = Unit
    override suspend fun deleteCategoryIfUnused(id: Long): Boolean = false
}

private class FakeAccountRepository : AccountRepository {
    private val accounts = listOf(Account(id = 3, name = "現金", iconKey = "payments", colorArgb = 0L))

    override fun observeVisibleWithBalance(): Flow<List<Pair<Account, Long>>> =
        MutableStateFlow(accounts.map { it to 0L })
    override fun observeAllWithBalance(): Flow<List<Pair<Account, Long>>> =
        MutableStateFlow(accounts.map { it to 0L })
    override fun observeAll(): Flow<List<Account>> = MutableStateFlow(accounts)
    override suspend fun getAccount(id: Long): Account? = accounts.firstOrNull { it.id == id }
    override suspend fun addAccount(account: Account): Long = 0L
    override suspend fun updateAccount(account: Account) = Unit
    override suspend fun hideAccount(id: Long) = Unit
    override suspend fun deleteAccount(id: Long): Boolean = false
    override fun observeTotalBalance(): Flow<Long> = MutableStateFlow(0L)
}

private class FakeBarcodeRepository : BarcodeRepository {
    override fun getPurchaseHistory(barcode: String): Flow<List<PurchaseSummary>> = emptyFlow()
    override fun getPriceTrend(barcode: String): Flow<PriceTrend> = MutableStateFlow(PriceTrend())
}

private class FakeScannerSource : BarcodeScannerSource {
    override suspend fun scan(bitmap: android.graphics.Bitmap): BarcodeScanResult =
        throw UnsupportedOperationException()
    override suspend fun scan(bitmap: android.graphics.Bitmap, rotationDegrees: Int): BarcodeScanResult =
        throw UnsupportedOperationException()
}

private class FakeReceiptImageStore : ReceiptImageStore {
    override fun decodeAndStore(uri: android.net.Uri): Pair<String, String>? = null
}

private class FakeEntrySettingsRepository : SettingsRepository {
    override fun observeAppearance(): Flow<AppearanceSettings> = emptyFlow()
    override suspend fun setDarkMode(modeName: String) = Unit
    override suspend fun setDynamicColor(enabled: Boolean) = Unit
    override fun observeReminder(): Flow<SettingsRepository.ReminderSettings> = emptyFlow()
    override suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) = Unit
    override suspend fun getFlag(key: String): Boolean = false
    override suspend fun setFlag(key: String, value: Boolean) = Unit
    override fun observeAiAssistant(): Flow<SettingsRepository.AiAssistantSettings> = emptyFlow()
    override suspend fun setAiAssistantIncludeContext(enabled: Boolean) = Unit
    override suspend fun setAiAssistantKeepHistory(enabled: Boolean) = Unit
    override suspend fun setAiAssistantAutoCleanup(enabled: Boolean) = Unit
    override suspend fun setAiAssistantShowQuickPrompts(enabled: Boolean) = Unit
    override suspend fun setAiAssistantQueryEnabled(enabled: Boolean) = Unit
    override suspend fun setAiCurrentSessionId(sessionId: Long) = Unit
    override fun observeFeatureFlags(): Flow<SettingsRepository.FeatureFlagSettings> =
        MutableStateFlow(SettingsRepository.FeatureFlagSettings())
    override suspend fun setAiNlEntryEnabled(enabled: Boolean) = Unit
    override suspend fun setAiImageEntryEnabled(enabled: Boolean) = Unit
    override suspend fun setBarcodeEnabled(enabled: Boolean) = Unit
    override suspend fun setInventoryEnabled(enabled: Boolean) = Unit
    override suspend fun setOnboardingCompleted(enabled: Boolean) = Unit
    override suspend fun setOnboardingCoachStep(step: Int) = Unit
    override fun observeLastSeenInsight(): Flow<Long> = emptyFlow()
    override suspend fun setLastSeenInsightEpochMillis(epochMillis: Long) = Unit
}
