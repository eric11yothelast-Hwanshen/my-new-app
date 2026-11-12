package com.hmoney.app.ui.screens.ai

import com.hmoney.app.domain.model.AiConfig
import com.hmoney.app.domain.model.AiTextResult
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
    private lateinit var aiRepo: FakeAiRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        chatRepo = FakeChatRepository()
        settingsRepo = FakeSettingsRepository()
        aiRepo = FakeAiRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AiChatViewModel =
        AiChatViewModel(aiRepo, chatRepo, settingsRepo)

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
        val session = chatRepo.getSession(sessionId)
        assertEquals(ChatTitle.from("這個月花最多的是什麼？"), session?.title)
        assertEquals(2, vm.uiState.value.messages.size)
    }

    @Test
    fun `既有已命名對話送訊息不覆寫標題`() {
        chatRepo.seedTitledSession(title = "既有標題")
        settingsRepo.aiSettings.update { it.copy(currentSessionId = chatRepo.seedId) }
        val vm = createViewModel()
        vm.send("新的問題內容")
        assertEquals("既有標題", chatRepo.getSession(chatRepo.seedId)?.title)
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
    override suspend fun setAiCurrentSessionId(sessionId: Long) {
        aiSettings.update { it.copy(currentSessionId = sessionId) }
    }

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
    override fun observeHistory() = emptyFlow<com.hmoney.app.domain.model.AiAnalysis>()
    override suspend fun deleteHistory(id: Long) = Unit
}
