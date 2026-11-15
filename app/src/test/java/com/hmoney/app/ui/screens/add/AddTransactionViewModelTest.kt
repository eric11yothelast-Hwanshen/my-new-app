package com.hmoney.app.ui.screens.add

import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.AiAnalysis
import com.hmoney.app.domain.model.AiConfig
import com.hmoney.app.domain.model.AiFilterSuggestion
import com.hmoney.app.domain.model.AiSuggestion
import com.hmoney.app.domain.model.AiTextResult
import com.hmoney.app.domain.model.TransactionQuery
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionSource
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.AiRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.TransactionRepository
import com.hmoney.app.domain.scanner.BarcodeScanError
import com.hmoney.app.domain.scanner.BarcodeScanResult
import com.hmoney.app.domain.scanner.BarcodeScannerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * AddTransactionViewModel 條碼欄測試（S43）：
 * 掃碼幀→scannedBarcode 設置；使用條碼→表單填入；存檔→fake repo 收到 barcode 且 source==manual
 * （鐵律：條碼是資料欄位不是來源，手動記帳 source 恆為 manual）。
 */
class AddTransactionViewModelTest {

    private lateinit var txRepo: FakeTransactionRepository
    private lateinit var scanner: FakeScannerSource
    private val collectScope = CoroutineScope(UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        txRepo = FakeTransactionRepository()
        scanner = FakeScannerSource()
    }

    @After
    fun tearDown() {
        collectScope.cancel()
        Dispatchers.resetMain()
    }

    /** uiState 為 WhileSubscribed stateIn——測試需先訂閱才會反映 scanState 更新 */
    private fun subscribeState(vm: AddTransactionViewModel) {
        collectScope.launch { vm.uiState.collect {} }
    }

    private fun createViewModel(): AddTransactionViewModel = AddTransactionViewModel(
        transactionRepository = txRepo,
        aiRepository = dagger.Lazy { FakeAiRepository() },
        accountRepository = FakeAccountRepository(),
        categoryRepository = FakeCategoryRepository(),
        barcodeScannerSource = scanner,
    )

    private fun fillValidForm(vm: AddTransactionViewModel) {
        vm.onKeypadInput('1')
        vm.onKeypadInput('2')
        vm.onKeypadInput('0')
        vm.setAccount(3L)
        vm.setCategory(1L)
    }

    @Test
    fun `掃碼幀後 scannedBarcode 設置`() {
        scanner.result = BarcodeScanResult.Success("4711234567890")
        val vm = createViewModel()
        subscribeState(vm)
        vm.onScanFrame(anyBitmap(), 0)
        assertEquals("4711234567890", vm.uiState.value.scannedBarcode)
    }

    @Test
    fun `使用條碼後填入表單條碼欄`() {
        scanner.result = BarcodeScanResult.Success("4711234567890")
        val vm = createViewModel()
        subscribeState(vm)
        vm.onScanFrame(anyBitmap(), 0)
        vm.useScannedBarcode()
        assertEquals("4711234567890", vm.uiState.value.form.barcode)
        assertNull(vm.uiState.value.scannedBarcode)
    }

    @Test
    fun `存檔後交易帶條碼且來源為 manual`() {
        scanner.result = BarcodeScanResult.Success("4711234567890")
        val vm = createViewModel()
        fillValidForm(vm)
        vm.onScanFrame(anyBitmap(), 0)
        vm.useScannedBarcode()
        vm.save(onSaved = {})
        val tx = txRepo.added.single()
        assertEquals("4711234567890", tx.barcode)
        assertEquals(TransactionSource.MANUAL, tx.source)
    }

    @Test
    fun `未填條碼存檔後 barcode 為 null`() {
        val vm = createViewModel()
        fillValidForm(vm)
        vm.save(onSaved = {})
        assertNull(txRepo.added.single().barcode)
        assertEquals(TransactionSource.MANUAL, txRepo.added.single().source)
    }
}

/** 單元測試環境無 Android framework：以 Unsafe 配置 Bitmap 實例（VM 不觸碰 bitmap 內容） */
@Suppress("DiscouragedPrivateApi")
private fun anyBitmap(): android.graphics.Bitmap {
    val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
    field.isAccessible = true
    val unsafe = field.get(null) as sun.misc.Unsafe
    return unsafe.allocateInstance(android.graphics.Bitmap::class.java) as android.graphics.Bitmap
}

private class FakeScannerSource : BarcodeScannerSource {
    var result: BarcodeScanResult = BarcodeScanResult.Failure(BarcodeScanError.NoBarcodeFound)

    override suspend fun scan(bitmap: android.graphics.Bitmap): BarcodeScanResult = result
    override suspend fun scan(bitmap: android.graphics.Bitmap, rotationDegrees: Int): BarcodeScanResult = result
}

private class FakeAiRepository : AiRepository {
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
    override suspend fun parseTransactionsFromText(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ) = throw UnsupportedOperationException()
    override suspend fun parseTransactionsFromImage(
        imageBase64: String,
        mimeType: String,
        hint: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ) = throw UnsupportedOperationException()
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
        return added.size.toLong()
    }

    override suspend fun updateTransaction(transaction: MoneyTransaction) = Unit
    override suspend fun deleteTransaction(id: Long) = Unit
}

private class FakeCategoryRepository : CategoryRepository {
    private val categories = listOf(
        Category(id = 1, name = "餐飲", type = CategoryType.EXPENSE, iconKey = "restaurant", colorArgb = 0L),
    )

    override fun observeActive(type: CategoryType): Flow<List<Category>> =
        MutableStateFlow(categories.filter { it.type == type })
    override fun observeAll(type: CategoryType): Flow<List<Category>> =
        MutableStateFlow(categories.filter { it.type == type })
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
