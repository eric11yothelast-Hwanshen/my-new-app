package com.hmoney.app.ui.screens.ai

import com.hmoney.app.data.repository.ReceiptImageStore
import com.hmoney.app.domain.logic.ConfirmDraftsUseCase
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.AiConfig
import com.hmoney.app.domain.model.AiTextResult
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.Item
import com.hmoney.app.domain.model.ItemActionIntent
import com.hmoney.app.domain.model.ItemActionType
import com.hmoney.app.domain.repository.ItemRepository
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.PriceTrend
import com.hmoney.app.domain.model.TransactionDraft
import com.hmoney.app.domain.model.TransactionQuery
import com.hmoney.app.domain.model.TransactionActionIntent
import com.hmoney.app.domain.model.TransactionActionType
import com.hmoney.app.domain.model.TransactionUpdates
import com.hmoney.app.domain.model.TransactionSource
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.BarcodeRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.TransactionRepository
import com.hmoney.app.domain.scanner.BarcodeScanError
import com.hmoney.app.domain.scanner.BarcodeScanResult
import com.hmoney.app.domain.scanner.BarcodeScannerSource
import com.hmoney.app.domain.model.ChatMessage
import com.hmoney.app.domain.model.ChatSession
import com.hmoney.app.domain.model.ChatTitle
import com.hmoney.app.domain.model.AppearanceSettings
import com.hmoney.app.domain.repository.AiRepository
import com.hmoney.app.domain.repository.ChatRepository
import com.hmoney.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Session 延遲建立生命週期測試：currentSessionId == 0 代表無進行中對話，
 * 只有送出第一則訊息時才建立 session；刪除／清空後不補建。
 */
class AiChatViewModelTest {

    private lateinit var chatRepo: FakeChatRepository
    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var itemRepo: FakeItemRepository
    private lateinit var aiRepo: FakeAiRepository
    private lateinit var txRepo: FakeTransactionRepository
    private lateinit var scanner: FakeScannerSource
    private lateinit var receiptStore: FakeReceiptImageStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        chatRepo = FakeChatRepository()
        settingsRepo = FakeSettingsRepository()
        itemRepo = FakeItemRepository()
        aiRepo = FakeAiRepository()
        txRepo = FakeTransactionRepository()
        scanner = FakeScannerSource()
        receiptStore = FakeReceiptImageStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AiChatViewModel =
        AiChatViewModel(
            aiRepo,
            chatRepo,
            settingsRepo,
            FakeCategoryRepository(),
            FakeAccountRepository(),
            FakeBarcodeRepository(),
            itemRepo,
            txRepo,
            scanner,
            receiptStore,
            ConfirmDraftsUseCase(txRepo),
        )

    @Test
    fun `啟動時清理零訊息空 session 且不建立任何新 session`() {
        chatRepo.seedEmptySession()
        val vm = createViewModel()
        assertEquals(1, chatRepo.emptyCleanupCount)
        assertEquals(0, chatRepo.createCount)
        assertEquals(0L, settingsRepo.aiSettings.value.currentSessionId)
        assertEquals(0L, vm.uiState.value.currentSessionId)
    }

    @Test
    fun `send 延遲建立恰好一個 session 並以使用者問題命名`() {
        val vm = createViewModel()
        vm.send("這個月花最多的是什麼？")
        assertEquals(1, chatRepo.createCount)
        val sessionId = settingsRepo.aiSettings.value.currentSessionId
        assertTrue(sessionId > 0)
        assertEquals(sessionId, vm.uiState.value.currentSessionId)
        val session = runBlocking { chatRepo.getSession(sessionId) }
        assertEquals(ChatTitle.from("這個月花最多的是什麼？"), session?.title)
        assertEquals(2, vm.uiState.value.messages.size)
    }

    @Test
    fun `既有已命名對話送訊息不覆寫標題`() {
        chatRepo.seedTitledSession(title = "既有標題")
        settingsRepo.aiSettings.update { it.copy(currentSessionId = chatRepo.seedId) }
        val vm = createViewModel()
        vm.send("新的問題內容")
        assertEquals("既有標題", runBlocking { chatRepo.getSession(chatRepo.seedId) }?.title)
    }

    @Test
    fun `newSession 只歸零不建立 DB 記錄`() {
        val vm = createViewModel()
        vm.send("第一個問題")
        assertEquals(1, chatRepo.createCount)
        vm.newSession()
        assertEquals(1, chatRepo.createCount)
        assertEquals(0L, settingsRepo.aiSettings.value.currentSessionId)
        assertEquals(0L, vm.uiState.value.currentSessionId)
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test
    fun `反覆 newSession 不產生空 session`() {
        val vm = createViewModel()
        repeat(5) { vm.newSession() }
        assertEquals(0, chatRepo.createCount)
        assertTrue(chatRepo.sessions.value.isEmpty())
    }

    @Test
    fun `刪除目前對話回到空狀態且不補建`() {
        val vm = createViewModel()
        vm.send("問題一")
        val sessionId = settingsRepo.aiSettings.value.currentSessionId
        vm.deleteSession(sessionId)
        assertEquals(1, chatRepo.createCount)
        assertEquals(0L, settingsRepo.aiSettings.value.currentSessionId)
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertTrue(chatRepo.sessions.value.isEmpty())
    }

    // ===== S44 聊天智慧記帳 =====

    @Test
    fun `記帳意圖產生草稿組且不進一般對話`() {
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "漢堡"),
        )
        val vm = createViewModel()
        vm.send("今天早餐漢堡120用悠遊卡付")
        val groups = vm.uiState.value.draftGroups
        assertEquals(1, groups.size)
        assertEquals(ChatDraftStatus.PENDING, groups.single().status)
        assertEquals(TransactionSource.AI_TEXT, groups.single().source)
        // 僅使用者訊息，無 AI 回覆（記帳意圖不進一般對話）
        assertEquals(1, vm.uiState.value.messages.size)
        assertTrue(vm.uiState.value.messages.single().isUser)
    }

    @Test
    fun `一般訊息 fallback 正常回覆`() {
        val vm = createViewModel()
        vm.send("你好")
        assertEquals(0, vm.uiState.value.draftGroups.size)
        assertEquals(2, vm.uiState.value.messages.size)
    }

    @Test
    fun `確認草稿組寫入且來源為 ai_text`() {
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "漢堡"),
        )
        val vm = createViewModel()
        vm.send("今天早餐漢堡120")
        val group = vm.uiState.value.draftGroups.single()
        vm.confirmDraftGroup(group.id)
        assertEquals(1, txRepo.added.size)
        assertEquals(TransactionSource.AI_TEXT, txRepo.added.single().source)
        assertEquals(ChatDraftStatus.CONFIRMED, vm.uiState.value.draftGroups.single().status)
    }

    @Test
    fun `取消草稿組零寫入`() {
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "漢堡"),
        )
        val vm = createViewModel()
        vm.send("今天早餐漢堡120")
        val group = vm.uiState.value.draftGroups.single()
        vm.cancelDraftGroup(group.id)
        assertEquals(0, txRepo.added.size)
        assertEquals(ChatDraftStatus.CANCELLED, vm.uiState.value.draftGroups.single().status)
    }

    @Test
    fun `有未決組時再觸發記帳意圖只提示不產生新組`() {
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "漢堡"),
        )
        val vm = createViewModel()
        vm.send("今天早餐漢堡120")
        vm.send("午餐便當80")
        assertEquals(1, vm.uiState.value.draftGroups.size)
        assertTrue(vm.uiState.value.draftBlockedHint)
    }

    @Test
    fun `圖片記帳產生草稿組確認後帶 receiptImagePath`() {
        aiRepo.imageDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 9900L, note = "便當"),
        )
        receiptStore.decoded = "base64" to "/tmp/receipt_test.jpg"
        val vm = createViewModel()
        vm.startImageEntry { receiptStore.decoded }
        val group = vm.uiState.value.draftGroups.single()
        assertEquals(TransactionSource.AI_IMAGE, group.source)
        assertEquals("/tmp/receipt_test.jpg", group.receiptImagePath)
        vm.confirmDraftGroup(group.id)
        assertEquals(1, txRepo.added.size)
        assertEquals(TransactionSource.AI_IMAGE, txRepo.added.single().source)
        assertEquals("/tmp/receipt_test.jpg", txRepo.added.single().receiptImagePath)
    }

    @Test
    fun `取消圖片草稿刪除暫存檔`() {
        aiRepo.imageDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 9900L, note = "便當"),
        )
        receiptStore.decoded = "base64" to "/tmp/receipt_cancel.jpg"
        val vm = createViewModel()
        vm.startImageEntry { receiptStore.decoded }
        val group = vm.uiState.value.draftGroups.single()
        vm.cancelDraftGroup(group.id)
        assertEquals(0, txRepo.added.size)
        assertEquals(listOf("/tmp/receipt_cancel.jpg"), receiptStore.deleted)
    }

    @Test
    fun `掃碼使用條碼產生草稿組金額預填上次價`() {
        scanner.result = BarcodeScanResult.Success("4711234567890")
        val vm = createViewModel()
        vm.onScanFrame(anyBitmap(), 0)
        assertEquals("4711234567890", vm.uiState.value.scannedBarcode)
        vm.useScannedBarcode()
        val group = vm.uiState.value.draftGroups.single()
        assertEquals(TransactionSource.BARCODE, group.source)
        assertEquals("4711234567890", group.barcode)
        assertEquals(9950L, group.items.single().transaction.amountCents)
        vm.confirmDraftGroup(group.id)
        assertEquals(TransactionSource.BARCODE, txRepo.added.single().source)
        assertEquals("4711234567890", txRepo.added.single().barcode)
    }

    // ===== S45 AI 查詢記錄 =====

    @Test
    fun `查詢意圖檢索結果並以 AI 綜整回答`() {
        aiRepo.query = TransactionQuery(keyword = "螢幕")
        txRepo.searchResults = listOf(
            MoneyTransaction(
                id = 1L,
                type = TransactionType.EXPENSE,
                amountCents = 399000L,
                accountId = 3L,
                categoryId = 1L,
                dateEpochMillis = 1_800_000_000_000L,
                note = "AOC 螢幕",
            ),
        )
        val vm = createViewModel()
        vm.send("我買了什麼螢幕")
        assertEquals(1, aiRepo.parseQueryCount)
        assertEquals(1, aiRepo.summarizeCount)
        assertTrue(aiRepo.lastResultsText!!.contains("AOC 螢幕"))
        assertEquals(0, vm.uiState.value.draftGroups.size)
        assertEquals(2, vm.uiState.value.messages.size)
        assertTrue(vm.uiState.value.messages.last().text.contains("AI 綜整"))
    }

    @Test
    fun `零結果回沒有符合的記錄語意`() {
        aiRepo.query = TransactionQuery(keyword = "不存在的東西")
        txRepo.searchResults = emptyList()
        val vm = createViewModel()
        vm.send("我買過什麼不存在的東西")
        assertEquals(1, aiRepo.summarizeCount)
        assertEquals("", aiRepo.lastResultsText)
        assertEquals(2, vm.uiState.value.messages.size)
    }

    @Test
    fun `非查詢語意 fallback 一般 chat 回覆`() {
        aiRepo.query = null
        val vm = createViewModel()
        vm.send("你好")
        assertEquals(1, aiRepo.parseQueryCount)
        assertEquals(0, aiRepo.summarizeCount)
        assertEquals(2, vm.uiState.value.messages.size)
        assertTrue(vm.uiState.value.messages.last().text.contains("AI 回覆"))
    }

    @Test
    fun `開關關閉時查詢意圖解析整段跳過`() {
        settingsRepo.aiSettings.update { it.copy(aiQueryEnabled = false) }
        aiRepo.query = TransactionQuery(keyword = "螢幕")
        val vm = createViewModel()
        vm.send("我買了什麼螢幕")
        assertEquals(0, aiRepo.parseQueryCount)
        assertEquals(0, aiRepo.summarizeCount)
        assertEquals(2, vm.uiState.value.messages.size)
        assertTrue(vm.uiState.value.messages.last().text.contains("AI 回覆"))
    }

    // ===== S47 聊天室物品操作 =====

    @Test
    fun `意圖分流順序：記帳意圖優先，物品操作解析不被呼叫`() {
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "漢堡"),
        )
        aiRepo.itemAction = ItemActionIntent(action = ItemActionType.CREATE, name = "鮮奶")
        val vm = createViewModel()
        vm.send("今天早餐漢堡120")
        assertEquals(1, vm.uiState.value.draftGroups.size)
        assertEquals(0, aiRepo.parseItemActionCount)
    }

    @Test
    fun `CREATE 物品意圖產生確認卡且確認後寫入 ItemRepository`() {
        aiRepo.itemAction = ItemActionIntent(
            action = ItemActionType.CREATE,
            name = "鮮奶",
            storageLocation = "冰箱",
            quantity = 3,
            expiryDateEpochMillis = 1_800_000_000_000L,
        )
        val vm = createViewModel()
        vm.send("新增物品 鮮奶 放冰箱 3天後到期")
        val card = vm.uiState.value.itemActions.single()
        assertEquals(ChatDraftStatus.PENDING, card.status)
        assertEquals(0, itemRepo.added.size)
        vm.confirmItemAction(card.id)
        assertEquals(1, itemRepo.added.size)
        assertEquals("鮮奶", itemRepo.added.single().name)
        assertEquals(3, itemRepo.added.single().quantity)
        assertEquals("冰箱", itemRepo.added.single().storageLocation)
        assertEquals(1_800_000_000_000L, itemRepo.added.single().expiryDateEpochMillis)
        assertEquals(ChatDraftStatus.CONFIRMED, vm.uiState.value.itemActions.single().status)
    }

    @Test
    fun `UPDATE 先名稱匹配再確認更新`() {
        itemRepo.seed(
            Item(id = 7L, name = "鮮奶", quantity = 1, createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L),
        )
        aiRepo.itemAction = ItemActionIntent(action = ItemActionType.UPDATE, name = "鮮奶", quantity = 5)
        val vm = createViewModel()
        vm.send("把鮮奶改成 5 罐")
        val card = vm.uiState.value.itemActions.single()
        assertEquals(7L, card.targetItem?.id)
        assertEquals(0, itemRepo.updated.size)
        vm.confirmItemAction(card.id)
        assertEquals(1, itemRepo.updated.size)
        assertEquals(5, itemRepo.updated.single().quantity)
        assertEquals("鮮奶", itemRepo.updated.single().name)
    }

    @Test
    fun `DELETE 確認後刪除物品`() {
        itemRepo.seed(
            Item(id = 7L, name = "鮮奶", quantity = 2, createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L),
        )
        aiRepo.itemAction = ItemActionIntent(action = ItemActionType.DELETE, name = "鮮奶")
        val vm = createViewModel()
        vm.send("刪除鮮奶")
        val card = vm.uiState.value.itemActions.single()
        assertEquals(7L, card.targetItem?.id)
        assertEquals(0, itemRepo.deleted.size)
        vm.confirmItemAction(card.id)
        assertEquals(listOf(7L), itemRepo.deleted)
        assertEquals(ChatDraftStatus.CONFIRMED, vm.uiState.value.itemActions.single().status)
    }

    @Test
    fun `找不到物品回覆助理訊息且零寫入`() {
        aiRepo.itemAction = ItemActionIntent(action = ItemActionType.DELETE, name = "鮮奶")
        val vm = createViewModel()
        vm.send("刪除鮮奶")
        assertEquals(0, vm.uiState.value.itemActions.size)
        assertEquals(0, itemRepo.deleted.size)
        assertEquals(2, vm.uiState.value.messages.size)
        assertTrue(vm.uiState.value.messages.last().text.contains("鮮奶"))
    }

    @Test
    fun `多筆符合列出候選請使用者講明`() {
        itemRepo.seed(
            Item(id = 7L, name = "鮮奶", quantity = 1, createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L),
        )
        itemRepo.seed(
            Item(id = 8L, name = "鮮奶", quantity = 2, createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L),
        )
        aiRepo.itemAction = ItemActionIntent(action = ItemActionType.UPDATE, name = "鮮奶", quantity = 5)
        val vm = createViewModel()
        vm.send("把鮮奶改成 5 罐")
        assertEquals(0, vm.uiState.value.itemActions.size)
        assertEquals(0, itemRepo.updated.size)
        assertEquals(2, vm.uiState.value.messages.size)
        assertTrue(vm.uiState.value.messages.last().text.contains("鮮奶"))
    }

    @Test
    fun `inventory 開關關閉時物品操作解析整段跳過`() {
        settingsRepo.featureFlags.update { it.copy(inventoryEnabled = false) }
        aiRepo.itemAction = ItemActionIntent(action = ItemActionType.CREATE, name = "鮮奶")
        val vm = createViewModel()
        vm.send("新增物品 鮮奶")
        assertEquals(0, aiRepo.parseItemActionCount)
        assertEquals(0, vm.uiState.value.itemActions.size)
        assertEquals(2, vm.uiState.value.messages.size)
        assertTrue(vm.uiState.value.messages.last().text.contains("AI 回覆"))
    }

    @Test
    fun `取消物品操作零寫入且終態不得再寫入`() {
        aiRepo.itemAction = ItemActionIntent(action = ItemActionType.CREATE, name = "鮮奶")
        val vm = createViewModel()
        vm.send("新增物品 鮮奶")
        val card = vm.uiState.value.itemActions.single()
        vm.cancelItemAction(card.id)
        assertEquals(ChatDraftStatus.CANCELLED, vm.uiState.value.itemActions.single().status)
        assertEquals(0, itemRepo.added.size)
        vm.confirmItemAction(card.id)
        assertEquals(0, itemRepo.added.size)
        assertEquals(ChatDraftStatus.CANCELLED, vm.uiState.value.itemActions.single().status)
    }

    @Test
    fun `有未決物品操作時再觸發只提示不產生新卡`() {
        aiRepo.itemAction = ItemActionIntent(action = ItemActionType.CREATE, name = "鮮奶")
        val vm = createViewModel()
        vm.send("新增物品 鮮奶")
        vm.send("再新增一個物品")
        assertEquals(1, vm.uiState.value.itemActions.size)
        assertTrue(vm.uiState.value.itemActionBlockedHint)
        vm.clearItemActionBlockedHint()
        assertTrue(!vm.uiState.value.itemActionBlockedHint)
    }

    @Test
    fun `物品操作意圖落空後續走查詢與一般對話`() {
        aiRepo.itemAction = null
        val vm = createViewModel()
        vm.send("你好")
        assertEquals(1, aiRepo.parseItemActionCount)
        assertEquals(0, vm.uiState.value.itemActions.size)
        assertEquals(2, vm.uiState.value.messages.size)
    }

    // ===== S48 意圖閘門與交易刪改 =====

    @Test
    fun `含物品關鍵字訊息只走物品操作不觸發記帳 parse`() {
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "漢堡"),
        )
        aiRepo.itemAction = ItemActionIntent(action = ItemActionType.CREATE, name = "鮮奶")
        val vm = createViewModel()
        vm.send("新增物品 鮮奶 放冰箱 3天後到期")
        assertEquals(0, aiRepo.parseTextDraftCount)
        assertEquals(1, aiRepo.parseItemActionCount)
        assertEquals(0, vm.uiState.value.draftGroups.size)
        assertEquals(1, vm.uiState.value.itemActions.size)
    }

    @Test
    fun `刪改動詞訊息先走交易操作解析`() {
        aiRepo.transactionAction = null
        aiRepo.itemAction = ItemActionIntent(action = ItemActionType.DELETE, name = "鮮奶")
        val vm = createViewModel()
        vm.send("刪除昨天的鮮奶那筆")
        assertEquals(1, aiRepo.parseTransactionActionCount)
        assertEquals(1, aiRepo.parseItemActionCount)
        assertEquals(0, vm.uiState.value.transactionActions.size)
    }

    @Test
    fun `交易操作與物品操作皆 null 續走記帳 parse`() {
        aiRepo.transactionAction = null
        aiRepo.itemAction = null
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 8000L, note = "便當"),
        )
        val vm = createViewModel()
        vm.send("把昨天那筆便當修改一下")
        assertEquals(1, aiRepo.parseTransactionActionCount)
        assertEquals(1, aiRepo.parseItemActionCount)
        assertEquals(1, aiRepo.parseTextDraftCount)
        assertEquals(1, vm.uiState.value.draftGroups.size)
    }

    @Test
    fun `交易 DELETE 零匹配回覆找不到且零寫入`() {
        aiRepo.transactionAction = TransactionActionIntent(
            action = TransactionActionType.DELETE,
            target = TransactionQuery(keyword = "鮮奶"),
        )
        txRepo.searchResults = emptyList()
        val vm = createViewModel()
        vm.send("刪除昨天的鮮奶那筆")
        assertEquals(0, vm.uiState.value.transactionActions.size)
        assertEquals(0, txRepo.deleted.size)
        assertEquals(2, vm.uiState.value.messages.size)
        assertTrue(vm.uiState.value.messages.last().text.contains("找不到"))
    }

    @Test
    fun `交易 DELETE 單筆匹配直接確認卡且確認後刪除`() {
        aiRepo.transactionAction = TransactionActionIntent(
            action = TransactionActionType.DELETE,
            target = TransactionQuery(keyword = "鮮奶"),
        )
        txRepo.searchResults = listOf(tx(id = 11L, note = "鮮奶"))
        val vm = createViewModel()
        vm.send("刪除昨天的鮮奶那筆")
        val card = vm.uiState.value.transactionActions.single()
        assertEquals(ChatDraftStatus.PENDING, card.status)
        assertEquals(11L, card.target?.id)
        assertEquals(0, txRepo.deleted.size)
        vm.confirmTransactionAction(card.id)
        assertEquals(listOf(11L), txRepo.deleted)
        assertEquals(ChatDraftStatus.CONFIRMED, vm.uiState.value.transactionActions.single().status)
        // 終態不得重複執行
        vm.confirmTransactionAction(card.id)
        assertEquals(1, txRepo.deleted.size)
    }

    @Test
    fun `交易 DELETE 多筆列候選選定後確認`() {
        aiRepo.transactionAction = TransactionActionIntent(
            action = TransactionActionType.DELETE,
            target = TransactionQuery(keyword = "鮮奶"),
        )
        txRepo.searchResults = listOf(tx(id = 11L, note = "鮮奶"), tx(id = 12L, note = "鮮奶"), tx(id = 13L, note = "鮮奶"))
        val vm = createViewModel()
        vm.send("刪除昨天的鮮奶那筆")
        var card = vm.uiState.value.transactionActions.single()
        assertEquals(3, card.candidates.size)
        assertEquals(null, card.target)
        vm.selectTransactionActionCandidate(card.id, 12L)
        card = vm.uiState.value.transactionActions.single()
        assertEquals(12L, card.target?.id)
        vm.confirmTransactionAction(card.id)
        assertEquals(listOf(12L), txRepo.deleted)
    }

    @Test
    fun `交易 UPDATE 併入 updates 確認後走既有 updateTransaction`() {
        aiRepo.transactionAction = TransactionActionIntent(
            action = TransactionActionType.UPDATE,
            target = TransactionQuery(keyword = "昨天的"),
            updates = TransactionUpdates(amountCents = 200000L),
        )
        txRepo.searchResults = listOf(tx(id = 21L, note = "昨天的咖啡", amountCents = 100000L))
        val vm = createViewModel()
        vm.send("把昨天那筆改成 2000")
        val card = vm.uiState.value.transactionActions.single()
        assertEquals(200000L, card.working?.amountCents)
        assertEquals(0, txRepo.updated.size)
        vm.confirmTransactionAction(card.id)
        assertEquals(1, txRepo.updated.size)
        assertEquals(21L, txRepo.updated.single().id)
        assertEquals(200000L, txRepo.updated.single().amountCents)
        assertEquals(ChatDraftStatus.CONFIRMED, vm.uiState.value.transactionActions.single().status)
    }

    @Test
    fun `取消交易操作零寫入且終態不得再執行`() {
        aiRepo.transactionAction = TransactionActionIntent(
            action = TransactionActionType.DELETE,
            target = TransactionQuery(keyword = "鮮奶"),
        )
        txRepo.searchResults = listOf(tx(id = 11L, note = "鮮奶"))
        val vm = createViewModel()
        vm.send("刪除昨天的鮮奶那筆")
        val card = vm.uiState.value.transactionActions.single()
        vm.cancelTransactionAction(card.id)
        assertEquals(ChatDraftStatus.CANCELLED, vm.uiState.value.transactionActions.single().status)
        assertEquals(0, txRepo.deleted.size)
        vm.confirmTransactionAction(card.id)
        assertEquals(0, txRepo.deleted.size)
        assertEquals(ChatDraftStatus.CANCELLED, vm.uiState.value.transactionActions.single().status)
    }

    @Test
    fun `有未決交易操作時再觸發只提示不產生新卡`() {
        aiRepo.transactionAction = TransactionActionIntent(
            action = TransactionActionType.DELETE,
            target = TransactionQuery(keyword = "鮮奶"),
        )
        txRepo.searchResults = listOf(tx(id = 11L, note = "鮮奶"))
        val vm = createViewModel()
        vm.send("刪除昨天的鮮奶那筆")
        vm.send("刪除昨天的鮮奶那筆")
        assertEquals(1, vm.uiState.value.transactionActions.size)
        assertTrue(vm.uiState.value.transactionActionBlockedHint)
        vm.clearTransactionActionBlockedHint()
        assertTrue(!vm.uiState.value.transactionActionBlockedHint)
    }

    // ===== S49 穩定 key／相對期限／空泛刪除 =====

    @Test
    fun `聊天草稿穩定 key 各筆相異且不因編輯改變`() {
        aiRepo.textDrafts = listOf(
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "漢堡"),
            TransactionDraft(type = TransactionType.EXPENSE, amountCents = 3000L, note = "可樂"),
        )
        val vm = createViewModel()
        vm.send("今天漢堡120可樂30")
        val group = vm.uiState.value.draftGroups.single()
        val keys = group.items.map { it.key }
        assertEquals(2, keys.distinct().size)
        val first = group.items.first()
        vm.updateDraftAmount(group.id, first.transaction, 5500L)
        val updatedGroup = vm.uiState.value.draftGroups.single()
        assertEquals(keys, updatedGroup.items.map { it.key })
        assertEquals(5500L, updatedGroup.items.first().transaction.amountCents)
    }

    @Test
    fun `UPDATE 相對期限增加三天＝現有到期日加三`() {
        val baseExpiry = 1_800_000_000_000L
        itemRepo.seed(
            Item(id = 7L, name = "鮭魚", quantity = 1, expiryDateEpochMillis = baseExpiry,
                createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L),
        )
        aiRepo.itemAction = ItemActionIntent(
            action = ItemActionType.UPDATE,
            name = "鮭魚",
            expiryDeltaDays = 3,
        )
        val vm = createViewModel()
        vm.send("將鮭魚的保存期限增加3天")
        val card = vm.uiState.value.itemActions.single()
        val zone = java.time.ZoneId.systemDefault()
        val expected = java.time.Instant.ofEpochMilli(baseExpiry).atZone(zone).toLocalDate()
            .plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, card.intent.expiryDateEpochMillis)
        assertEquals(null, card.intent.expiryDeltaDays)
        vm.confirmItemAction(card.id)
        assertEquals(1, itemRepo.updated.size)
        assertEquals(expected, itemRepo.updated.single().expiryDateEpochMillis)
    }

    @Test
    fun `空泛刪除記帳 recent 取最近交易列候選`() {
        aiRepo.transactionAction = TransactionActionIntent(
            action = TransactionActionType.DELETE,
            recent = true,
        )
        txRepo.searchResults = listOf(tx(id = 31L, note = "最新"), tx(id = 32L, note = "次新"))
        val vm = createViewModel()
        vm.send("刪除記帳")
        val card = vm.uiState.value.transactionActions.single()
        assertEquals(2, card.candidates.size)
        assertEquals(null, card.target)
        vm.selectTransactionActionCandidate(card.id, 31L)
        vm.confirmTransactionAction(vm.uiState.value.transactionActions.single().id)
        assertEquals(listOf(31L), txRepo.deleted)
    }

    @Test
    fun `空泛刪除記帳 recent 零筆回覆找不到不落回 chat`() {
        aiRepo.transactionAction = TransactionActionIntent(
            action = TransactionActionType.DELETE,
            recent = true,
        )
        txRepo.searchResults = emptyList()
        val vm = createViewModel()
        vm.send("刪除剛剛那筆")
        assertEquals(0, vm.uiState.value.transactionActions.size)
        assertEquals(2, vm.uiState.value.messages.size)
        assertTrue(vm.uiState.value.messages.last().text.contains("找不到"))
    }

    @Test
    fun `clearAll 全刪不補建且 currentSessionId 歸零`() {
        val vm = createViewModel()
        vm.send("問題一")
        vm.newSession()
        vm.send("問題二")
        assertEquals(2, chatRepo.createCount)
        vm.clearAll()
        assertEquals(2, chatRepo.createCount)
        assertTrue(chatRepo.sessions.value.isEmpty())
        assertEquals(0L, settingsRepo.aiSettings.value.currentSessionId)
        assertTrue(vm.uiState.value.messages.isEmpty())
    }
}

/** 記憶體版 ChatRepository：記錄建立次數與空 session 清理次數 */
private class FakeChatRepository : ChatRepository {

    val sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    private val messagesBySession = mutableMapOf<Long, MutableList<ChatMessage>>()
    private val messagesFlows = mutableMapOf<Long, MutableStateFlow<List<ChatMessage>>>()
    var nextId = 1L
    var createCount = 0
    var emptyCleanupCount = 0
    var seedId = 0L

    fun seedEmptySession() {
        val id = nextId++
        seedId = id
        sessions.update { it + ChatSession(id, "", id * 1000, id * 1000) }
    }

    fun seedTitledSession(title: String) {
        val id = nextId++
        seedId = id
        sessions.update { it + ChatSession(id, title, id * 1000, id * 1000) }
        messagesBySession[id] = mutableListOf(
            ChatMessage(id * 100, id, isUser = true, text = "舊訊息", createdAtEpochMillis = id * 1000),
        )
        messagesFlows[id] = MutableStateFlow(messagesBySession[id]!!.toList())
    }

    override fun observeSessions(): Flow<List<ChatSession>> = sessions

    override fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
        messagesFlows.getOrPut(sessionId) { MutableStateFlow(emptyList()) }

    override suspend fun getSession(id: Long): ChatSession? =
        sessions.value.firstOrNull { it.id == id }

    override suspend fun createSession(nowEpochMillis: Long): Long {
        createCount += 1
        val id = nextId++
        sessions.update { it + ChatSession(id, "", nowEpochMillis, nowEpochMillis) }
        return id
    }

    override suspend fun setSessionTitle(id: Long, title: String) {
        sessions.update { list -> list.map { if (it.id == id) it.copy(title = title) else it } }
    }

    override suspend fun addMessage(
        sessionId: Long,
        isUser: Boolean,
        text: String,
        nowEpochMillis: Long,
    ): Long {
        val list = messagesBySession.getOrPut(sessionId) { mutableListOf() }
        val message = ChatMessage(
            id = (sessionId * 1000) + list.size + 1,
            sessionId = sessionId,
            isUser = isUser,
            text = text,
            createdAtEpochMillis = nowEpochMillis,
        )
        list.add(message)
        messagesFlows.getOrPut(sessionId) { MutableStateFlow(emptyList()) }
            .update { it + message }
        sessions.update { s -> s.map { if (it.id == sessionId) it.copy(updatedAtEpochMillis = nowEpochMillis) else it } }
        return message.id
    }

    override suspend fun deleteMessage(id: Long) {
        messagesBySession.forEach { (sessionId, list) ->
            list.removeAll { it.id == id }
            messagesFlows[sessionId]?.update { flow -> flow.filterNot { it.id == id } }
        }
    }

    override suspend fun deleteSession(id: Long) {
        sessions.update { it.filterNot { s -> s.id == id } }
        messagesBySession.remove(id)
        messagesFlows[id]?.update { emptyList() }
    }

    override suspend fun clearAll() {
        sessions.update { emptyList() }
        messagesBySession.clear()
        messagesFlows.values.forEach { it.update { emptyList() } }
        messagesFlows.clear()
    }

    override suspend fun deleteSessionsOlderThan(cutoffEpochMillis: Long): Int {
        val old = sessions.value.filter { it.updatedAtEpochMillis < cutoffEpochMillis }
        old.forEach { deleteSession(it.id) }
        return old.size
    }

    override suspend fun deleteEmptySessions(): Int {
        emptyCleanupCount += 1
        val empty = sessions.value.filter { s -> messagesBySession[s.id].isNullOrEmpty() }
        empty.forEach { deleteSession(it.id) }
        return empty.size
    }
}

/** 記憶體版 SettingsRepository：僅實作 AI 助手相關成員 */
private class FakeSettingsRepository : SettingsRepository {

    val aiSettings = MutableStateFlow(SettingsRepository.AiAssistantSettings())
    val featureFlags = MutableStateFlow(SettingsRepository.FeatureFlagSettings())

    override fun observeAppearance(): Flow<AppearanceSettings> = emptyFlow()
    override suspend fun setDarkMode(modeName: String) = Unit
    override suspend fun setDynamicColor(enabled: Boolean) = Unit
    override fun observeReminder(): Flow<SettingsRepository.ReminderSettings> = emptyFlow()
    override suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) = Unit
    override suspend fun getFlag(key: String): Boolean = false
    override suspend fun setFlag(key: String, value: Boolean) = Unit
    override fun observeAiAssistant(): Flow<SettingsRepository.AiAssistantSettings> = aiSettings
    override suspend fun setAiAssistantIncludeContext(enabled: Boolean) = Unit
    override suspend fun setAiAssistantKeepHistory(enabled: Boolean) = Unit
    override suspend fun setAiAssistantAutoCleanup(enabled: Boolean) = Unit
    override suspend fun setAiAssistantShowQuickPrompts(enabled: Boolean) = Unit
    override suspend fun setAiAssistantQueryEnabled(enabled: Boolean) = Unit
    override suspend fun setAiCurrentSessionId(sessionId: Long) {
        aiSettings.update { it.copy(currentSessionId = sessionId) }
    }

    override fun observeFeatureFlags(): Flow<SettingsRepository.FeatureFlagSettings> = featureFlags
    override suspend fun setAiNlEntryEnabled(enabled: Boolean) = Unit
    override suspend fun setAiImageEntryEnabled(enabled: Boolean) = Unit
    override suspend fun setBarcodeEnabled(enabled: Boolean) = Unit
    override suspend fun setInventoryEnabled(enabled: Boolean) = Unit
    override suspend fun setOnboardingCompleted(enabled: Boolean) = Unit
    override suspend fun setOnboardingCoachStep(step: Int) = Unit

    private val lastSeen = MutableStateFlow(0L)
    override fun observeLastSeenInsight(): Flow<Long> = lastSeen
    override suspend fun setLastSeenInsightEpochMillis(epochMillis: Long) {
        lastSeen.value = epochMillis
    }
}

/** 記憶體版 AiRepository：chat 直接回成功 */
private class FakeAiRepository : AiRepository {

    override fun observeConfig(): Flow<AiConfig> = emptyFlow()
    override suspend fun getConfig(): AiConfig = AiConfig()
    override suspend fun saveConfig(baseUrl: String, apiKey: String, model: String) = Unit
    override suspend fun analyzeWeek() = throw UnsupportedOperationException()
    override suspend fun askQuestion(question: String) = throw UnsupportedOperationException()
    override suspend fun chat(question: String, includeContext: Boolean): AiTextResult =
        AiTextResult.Success("AI 回覆：$question")
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
    var textDrafts: List<TransactionDraft>? = null
    var imageDrafts: List<TransactionDraft>? = null
    var query: TransactionQuery? = null
    var parseQueryCount = 0
    var summarizeCount = 0
    var lastResultsText: String? = null

    override suspend fun parseTransactionQuery(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<TransactionQuery?> {
        parseQueryCount += 1
        return Result.success(query)
    }

    override suspend fun summarizeQueryResult(question: String, resultsText: String): AiTextResult {
        summarizeCount += 1
        lastResultsText = resultsText
        return AiTextResult.Success("AI 綜整：$resultsText")
    }

    var parseTextDraftCount = 0
    override suspend fun parseTransactionsFromText(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<List<TransactionDraft>> {
        parseTextDraftCount += 1
        return Result.success(textDrafts.orEmpty())
    }
    override suspend fun parseTransactionsFromImage(
        imageBase64: String,
        mimeType: String,
        hint: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<List<TransactionDraft>> = Result.success(imageDrafts.orEmpty())
    override suspend fun suggestItemFields(
        name: String,
        barcode: String?,
    ): Result<com.hmoney.app.domain.model.ItemSuggestion> = Result.success(com.hmoney.app.domain.model.ItemSuggestion())

    var itemAction: ItemActionIntent? = null
    var parseItemActionCount = 0
    override suspend fun parseItemAction(
        text: String,
        existingItemNames: List<String>,
    ): Result<ItemActionIntent?> {
        parseItemActionCount += 1
        return Result.success(itemAction)
    }

    var transactionAction: TransactionActionIntent? = null
    var parseTransactionActionCount = 0
    override suspend fun parseTransactionAction(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<TransactionActionIntent?> {
        parseTransactionActionCount += 1
        return Result.success(transactionAction)
    }

    override fun observeHistory() =
        emptyFlow<List<com.hmoney.app.domain.model.AiAnalysis>>()
    override suspend fun deleteHistory(id: Long) = Unit
}


/** 單元測試環境無 Android framework：以 Unsafe 配置 Uri／Bitmap 實例（VM 不觸碰其內容） */
@Suppress("DiscouragedPrivateApi")
private fun tx(id: Long, note: String, amountCents: Long = 12000L): MoneyTransaction =
    MoneyTransaction(
        id = id,
        type = TransactionType.EXPENSE,
        amountCents = amountCents,
        accountId = 3L,
        categoryId = 1L,
        dateEpochMillis = 1_800_000_000_000L,
        note = note,
    )

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

private class FakeReceiptImageStore : ReceiptImageStore {
    var decoded: Pair<String, String>? = null
    val deleted = mutableListOf<String>()
    override fun decodeAndStore(uri: android.net.Uri): Pair<String, String>? = decoded
    override fun delete(path: String) {
        deleted += path
    }
}

private class FakeBarcodeRepository : BarcodeRepository {
    override fun getPurchaseHistory(barcode: String): Flow<List<com.hmoney.app.domain.model.PurchaseSummary>> = emptyFlow()
    override fun getPriceTrend(barcode: String): Flow<PriceTrend> =
        MutableStateFlow(PriceTrend(lastPriceCents = 9950L))
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
    var searchResults: List<MoneyTransaction> = emptyList()
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
    ): List<MoneyTransaction> = searchResults
    override fun observeTransactionsByBarcode(barcode: String): Flow<List<MoneyTransaction>> = emptyFlow()
    override suspend fun addTransaction(transaction: MoneyTransaction): Long {
        added += transaction
        return added.size.toLong()
    }
    val updated = mutableListOf<MoneyTransaction>()
    val deleted = mutableListOf<Long>()
    override suspend fun updateTransaction(transaction: MoneyTransaction) {
        updated += transaction
    }
    override suspend fun deleteTransaction(id: Long) {
        deleted += id
    }
}

private class FakeCategoryRepository : CategoryRepository {
    private val categories = listOf(
        Category(id = 1, name = "餐飲", type = CategoryType.EXPENSE, iconKey = "restaurant", colorArgb = 0L),
        Category(id = 8, name = "其他收入", type = CategoryType.INCOME, iconKey = "add_circle", colorArgb = 0L),
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

/** S47 記憶體版 ItemRepository：記錄新增／更新／刪除供寫入斷言 */
private class FakeItemRepository : ItemRepository {
    val items = MutableStateFlow<List<Item>>(emptyList())
    val added = mutableListOf<Item>()
    val updated = mutableListOf<Item>()
    val deleted = mutableListOf<Long>()

    fun seed(item: Item) {
        items.update { it + item }
    }

    override fun observeAll(): Flow<List<Item>> = items
    override suspend fun findByBarcode(barcode: String): List<Item> = emptyList()
    override suspend fun findWithExpiryBefore(endEpochMillis: Long): List<Item> = emptyList()
    override suspend fun addItem(item: Item): Long {
        added += item
        items.update { it + item.copy(id = (it.size + 1).toLong()) }
        return items.value.size.toLong()
    }
    override suspend fun updateItem(item: Item) {
        updated += item
        items.update { list -> list.map { if (it.id == item.id) item else it } }
    }
    override suspend fun deleteItem(id: Long) {
        deleted += id
        items.update { list -> list.filterNot { it.id == id } }
    }
}
