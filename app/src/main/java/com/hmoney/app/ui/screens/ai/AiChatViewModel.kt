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
 * AiAssistantSheet 共用同一份資料，天然跨頁同步；「保留對話」關閉時改為純記憶體，
 * 離開即消失。目前 session id 記於 DataStore，關掉重開仍在同一個對話。
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
            settingsRepository.observeAiAssistant().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                ensureSession(settings)
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
                    val sessionId = activeSessionId
                    if (sessionId > 0L) {
                        chatRepository.addMessage(
                            sessionId,
                            isUser = true,
                            text = q,
                            nowEpochMillis = now,
                        )
                    }
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
                            maybeSetTitle(sessionId, result.text)
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

    /** 建立新對話：保留關閉時僅清空記憶體訊息 */
    fun newSession() {
        viewModelScope.launch {
            if (_uiState.value.settings.keepHistory) {
                val id = chatRepository.createSession(System.currentTimeMillis())
                settingsRepository.setAiCurrentSessionId(id)
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

    /** 刪除單一對話；若刪的是目前對話則自動建立新對話 */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (sessionId == activeSessionId) {
                val id = chatRepository.createSession(System.currentTimeMillis())
                settingsRepository.setAiCurrentSessionId(id)
            }
        }
    }

    /** 清空所有對話（保留關閉時僅清空記憶體） */
    fun clearAll() {
        viewModelScope.launch {
            if (_uiState.value.settings.keepHistory) {
                chatRepository.clearAll()
                val id = chatRepository.createSession(System.currentTimeMillis())
                settingsRepository.setAiCurrentSessionId(id)
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

    /** 依設定確保目前 session 存在並開始觀察其訊息 */
    private suspend fun ensureSession(settings: SettingsRepository.AiAssistantSettings) {
        if (!settings.keepHistory) {
            if (activeSessionId != 0L) {
                activeSessionId = 0L
                messagesJob?.cancel()
                _uiState.update { it.copy(currentSessionId = 0L) }
            }
            return
        }
        val existing = settings.currentSessionId.takeIf { it > 0 }
            ?.let { chatRepository.getSession(it) }
        val id = existing?.id ?: chatRepository.createSession(System.currentTimeMillis())
        if (existing == null) {
            settingsRepository.setAiCurrentSessionId(id)
        }
        if (activeSessionId != id) {
            activeSessionId = id
            observeMessages(id)
        }
        _uiState.update { it.copy(currentSessionId = id) }
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

    /** AI 回覆首行截 20 字作為 session 標題（僅在尚未命名時） */
    private suspend fun maybeSetTitle(sessionId: Long, aiReply: String) {
        val title = _uiState.value.sessions.firstOrNull { it.id == sessionId }?.title
        if (title.isNullOrBlank()) {
            chatRepository.setSessionTitle(sessionId, ChatTitle.from(aiReply))
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
