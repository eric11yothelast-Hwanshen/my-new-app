package com.hmoney.app.ui.screens.items

import com.hmoney.app.data.repository.AiCallException
import com.hmoney.app.domain.model.AiConfig
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.AiTextResult
import com.hmoney.app.domain.model.Item
import com.hmoney.app.domain.model.ItemSuggestion
import com.hmoney.app.domain.repository.AiRepository
import com.hmoney.app.domain.repository.ItemRepository
import com.hmoney.app.domain.scanner.BarcodeScanError
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S46 ItemsViewModel 測試：分節列表、儲存驗證（名稱空 → 錯誤）、刪除、AI 填選建議預覽。
 * StateFlow（WhileSubscribed 無關，這裡為 MutableStateFlow 直推）以 UnconfinedTestDispatcher 收集。
 */
class ItemsViewModelTest {

    private lateinit var itemRepo: FakeItemRepository
    private lateinit var aiRepo: FakeItemsAiRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        itemRepo = FakeItemRepository()
        aiRepo = FakeItemsAiRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ItemsViewModel =
        ItemsViewModel(itemRepo, aiRepo, FakeScannerSource())

    @Test
    fun `清單依到期狀態分節`() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        fun epoch(date: java.time.LocalDate) =
            date.atStartOfDay(zone).toInstant().toEpochMilli()
        itemRepo.seed(
            listOf(
                Item(id = 1, name = "過期", expiryDateEpochMillis = epoch(today.minusDays(1)), createdAtEpochMillis = 0, updatedAtEpochMillis = 0),
                Item(id = 2, name = "即將", expiryDateEpochMillis = epoch(today.plusDays(3)), createdAtEpochMillis = 0, updatedAtEpochMillis = 0),
                Item(id = 3, name = "無到期日", createdAtEpochMillis = 0, updatedAtEpochMillis = 0),
            ),
        )
        val vm = createViewModel()
        val sections = vm.uiState.value.sections
        assertEquals(3, sections.size)
        assertEquals(listOf("過期"), sections[0].items.map { it.name })
        assertEquals(listOf("即將"), sections[1].items.map { it.name })
        assertEquals(listOf("無到期日"), sections[2].items.map { it.name })
    }

    @Test
    fun `名稱空白儲存顯示錯誤且不寫入`() {
        val vm = createViewModel()
        vm.saveItem(
            editing = null,
            name = "  ",
            barcode = null,
            storageLocation = null,
            quantity = 1,
            expiryDateEpochMillis = null,
            onSaved = { },
        )
        assertTrue(vm.uiState.value.nameError)
        assertTrue(itemRepo.added.isEmpty())
    }

    @Test
    fun `合法儲存寫入並重置錯誤`() {
        val vm = createViewModel()
        var saved = false
        vm.saveItem(
            editing = null,
            name = "鮮奶",
            barcode = "4711234567890",
            storageLocation = "冰箱",
            quantity = 2,
            expiryDateEpochMillis = 123L,
            onSaved = { saved = true },
        )
        assertTrue(saved)
        assertFalse(vm.uiState.value.nameError)
        assertEquals(1, itemRepo.added.size)
        assertEquals("鮮奶", itemRepo.added.single().name)
        assertEquals(2, itemRepo.added.single().quantity)
    }

    @Test
    fun `編輯儲存走更新`() {
        itemRepo.seed(
            listOf(
                Item(id = 9, name = "舊名", quantity = 1, createdAtEpochMillis = 0, updatedAtEpochMillis = 0),
            ),
        )
        val vm = createViewModel()
        vm.saveItem(
            editing = itemRepo.items.single(),
            name = "新名",
            barcode = null,
            storageLocation = "冷藏",
            quantity = 5,
            expiryDateEpochMillis = null,
            onSaved = { },
        )
        assertTrue(itemRepo.added.isEmpty())
        assertEquals("新名", itemRepo.items.single().name)
        assertEquals(5, itemRepo.items.single().quantity)
    }

    @Test
    fun `刪除物品`() {
        itemRepo.seed(
            listOf(
                Item(id = 4, name = "果汁", createdAtEpochMillis = 0, updatedAtEpochMillis = 0),
            ),
        )
        val vm = createViewModel()
        vm.deleteItem(4)
        assertTrue(itemRepo.items.isEmpty())
    }

    @Test
    fun `AI 填選產生建議預覽且不自動寫入`() {
        aiRepo.suggestion = ItemSuggestion(
            storageLocation = "冰箱",
            expiryDateEpochMillis = 456L,
            quantity = 3,
        )
        val vm = createViewModel()
        vm.suggestItemFields("鮮奶", null)
        val suggestion = vm.uiState.value.suggestion
        assertNotNull(suggestion)
        assertEquals("冰箱", suggestion!!.storageLocation)
        assertEquals(3, suggestion.quantity)
        // 建議僅預覽：不寫入任何物品
        assertTrue(itemRepo.added.isEmpty())
    }

    @Test
    fun `AI 填選全空建議不顯示`() {
        aiRepo.suggestion = ItemSuggestion()
        val vm = createViewModel()
        vm.suggestItemFields("未知物", null)
        assertEquals(null, vm.uiState.value.suggestion)
    }

    @Test
    fun `AI 未設定顯示引導`() {
        aiRepo.error = AiError.NotConfigured
        val vm = createViewModel()
        vm.suggestItemFields("鮮奶", null)
        assertTrue(vm.uiState.value.aiNotConfigured)
        assertFalse(vm.uiState.value.aiFailed)
    }
}

private class FakeItemRepository : ItemRepository {
    val items = mutableListOf<Item>()
    val added = mutableListOf<Item>()
    private val flow = MutableStateFlow<List<Item>>(emptyList())

    fun seed(list: List<Item>) {
        items += list
        flow.value = items.toList()
    }

    override fun observeAll(): Flow<List<Item>> = flow

    override suspend fun findByBarcode(barcode: String): List<Item> =
        items.filter { it.barcode == barcode }

    override suspend fun findWithExpiryBefore(endEpochMillis: Long): List<Item> =
        items.filter { it.expiryDateEpochMillis != null && it.expiryDateEpochMillis <= endEpochMillis }

    override suspend fun addItem(item: Item): Long {
        added += item
        items += item
        flow.value = items.toList()
        return item.id
    }

    override suspend fun updateItem(item: Item) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) items[index] = item
        flow.value = items.toList()
    }

    override suspend fun deleteItem(id: Long) {
        items.removeAll { it.id == id }
        flow.value = items.toList()
    }
}

private class FakeItemsAiRepository : AiRepository {
    var suggestion = ItemSuggestion()
    var error: AiError? = null

    override fun observeConfig(): Flow<AiConfig> = MutableStateFlow(AiConfig())
    override suspend fun getConfig(): AiConfig = AiConfig()
    override suspend fun saveConfig(baseUrl: String, apiKey: String, model: String) = Unit
    override suspend fun analyzeWeek() = throw UnsupportedOperationException()
    override suspend fun askQuestion(question: String) = throw UnsupportedOperationException()
    override suspend fun chat(question: String, includeContext: Boolean): AiTextResult =
        throw UnsupportedOperationException()
    override suspend fun polishNote(note: String) = throw UnsupportedOperationException()
    override suspend fun suggestForNote(note: String, categoryNames: List<String>) =
        throw UnsupportedOperationException()
    override suspend fun interpretFilter(
        query: String,
        categoryNames: List<String>,
        accountNames: List<String>,
    ) = throw UnsupportedOperationException()
    override suspend fun analyzeData(title: String, data: String) =
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
    ) = throw UnsupportedOperationException()
    override suspend fun summarizeQueryResult(question: String, resultsText: String): AiTextResult =
        throw UnsupportedOperationException()
    override suspend fun suggestItemFields(name: String, barcode: String?): Result<ItemSuggestion> =
        error?.let { Result.failure(AiCallException(it)) } ?: Result.success(suggestion)
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

override fun observeHistory() =
        emptyFlow<List<com.hmoney.app.domain.model.AiAnalysis>>()
    override suspend fun deleteHistory(id: Long) = Unit
}

private class FakeScannerSource : BarcodeScannerSource {
    override suspend fun scan(bitmap: android.graphics.Bitmap): BarcodeScanResult =
        BarcodeScanResult.Failure(BarcodeScanError.NoBarcodeFound)

    override suspend fun scan(bitmap: android.graphics.Bitmap, rotationDegrees: Int): BarcodeScanResult =
        BarcodeScanResult.Failure(BarcodeScanError.NoBarcodeFound)
}
