package com.hmoney.app.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.AiTextResult
import com.hmoney.app.domain.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 對話訊息（全域 AI 助手 bottom sheet 用） */
data class AiChatMessage(
    val isUser: Boolean,
    val text: String,
)

/**
 * 全域 AI 助手 ViewModel：可對話並讀取精簡去敏感化的財務資料（repository.chat 負責組 context）。
 */
@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
) : ViewModel() {

    data class UiState(
        val messages: List<AiChatMessage> = emptyList(),
        val loading: Boolean = false,
        val error: AiError? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var job: Job? = null

    fun send(question: String) {
        val q = question.trim()
        if (q.isBlank() || _uiState.value.loading) return
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + AiChatMessage(isUser = true, text = q),
            loading = true,
            error = null,
        )
        job = viewModelScope.launch {
            when (val result = aiRepository.chat(q)) {
                is AiTextResult.Success -> _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages +
                        AiChatMessage(isUser = false, text = result.text),
                    loading = false,
                )

                is AiTextResult.Failure -> _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = result.error,
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _uiState.value = _uiState.value.copy(loading = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
