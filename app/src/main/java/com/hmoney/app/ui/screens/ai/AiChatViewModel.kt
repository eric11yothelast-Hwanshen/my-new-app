package com.hmoney.app.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.AiTextResult
import com.hmoney.app.domain.model.ChatTitle
import com.hmoney.app.domain.repository.AiRepository
import com.hmoney.app.domain.repository.ChatRepository
import com.hmoney.app.domain.repository.SettingsRepository
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
) : ViewModel() {

    data class UiState(
        val messages: List<AiChatMessage> = emptyList(),
        val sessions: List<com.hmoney.app.domain.model.ChatSession> = emptyList(),
        val currentSessionId: Long = 0L,
        val settings: SettingsRepository.AiAssistantSettings =
            SettingsRepository.AiAssistantSettings(),
        val loading: Boolean = false,
        val error: AiError? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var job: Job? = null
    private var messagesJob: Job? = null
    private var activeSessionId = 0L
    private var ephemeralCounter = 0L

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

    fun cancel() {
        job?.cancel()
        _uiState.update { it.copy(loading = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** 新對話：只把 currentSessionId 歸零（不建立任何 DB 記錄），下次送訊息才建立 */
    fun newSession() {
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
    }
}
