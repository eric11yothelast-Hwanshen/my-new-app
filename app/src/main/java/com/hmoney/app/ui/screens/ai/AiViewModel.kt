package com.hmoney.app.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.R
import com.hmoney.app.domain.model.AiAnalysis
import com.hmoney.app.domain.model.AiConfig
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.repository.AiOutcome
import com.hmoney.app.domain.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI 助手 ViewModel：連線設定、本週分析、自由提問與歷史回看。
 * API 呼叫皆在 repository 層執行，UI 只呈現狀態。
 */
@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiRepository: AiRepository,
) : ViewModel() {

    /** 分析／問答的執行狀態（loading／成功／明確錯誤回饋） */
    sealed class AnalysisUiState {
        data object Idle : AnalysisUiState()
        data object Loading : AnalysisUiState()
        data class Success(val analysis: AiAnalysis) : AnalysisUiState()
        data class Error(val messageRes: Int) : AnalysisUiState()
    }

    val config: StateFlow<AiConfig> = aiRepository.observeConfig()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AiConfig(),
        )

    val history: StateFlow<List<AiAnalysis>> = aiRepository.observeHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _analysisState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysisState: StateFlow<AnalysisUiState> = _analysisState

    fun analyzeWeek() {
        if (_analysisState.value is AnalysisUiState.Loading) return
        _analysisState.value = AnalysisUiState.Loading
        viewModelScope.launch {
            try {
                _analysisState.value = when (val outcome = aiRepository.analyzeWeek()) {
                    is AiOutcome.Success -> AnalysisUiState.Success(outcome.analysis)
                    is AiOutcome.Failure -> AnalysisUiState.Error(outcome.error.toMessageRes())
                }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "本週分析失敗", t)
                _analysisState.value = AnalysisUiState.Error(R.string.ai_error_network)
            }
        }
    }

    fun askQuestion(question: String) {
        if (question.isBlank()) return
        if (_analysisState.value is AnalysisUiState.Loading) return
        _analysisState.value = AnalysisUiState.Loading
        viewModelScope.launch {
            try {
                _analysisState.value =
                    when (val outcome = aiRepository.askQuestion(question.trim())) {
                        is AiOutcome.Success -> AnalysisUiState.Success(outcome.analysis)
                        is AiOutcome.Failure -> AnalysisUiState.Error(outcome.error.toMessageRes())
                    }
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "自由提問失敗", t)
                _analysisState.value = AnalysisUiState.Error(R.string.ai_error_network)
            }
        }
    }

    fun resetState() {
        _analysisState.value = AnalysisUiState.Idle
    }

    fun saveConfig(baseUrl: String, apiKey: String, model: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            aiRepository.saveConfig(baseUrl, apiKey, model)
            onSaved()
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch { aiRepository.deleteHistory(id) }
    }

    private fun AiError.toMessageRes(): Int = when (this) {
        AiError.NotConfigured -> R.string.ai_not_configured
        AiError.Network -> R.string.ai_error_network
        AiError.Unauthorized -> R.string.ai_error_unauthorized
        AiError.QuotaExceeded -> R.string.ai_error_quota
        is AiError.Http -> R.string.ai_error_http
        AiError.Parse -> R.string.ai_error_parse
        // 專屬字串之後 session 再接（S41/S42），先以通用錯誤文字回饋
        AiError.VisionUnsupported -> R.string.ai_error_http
    }

    private companion object {
        private const val TAG = "AiVM"
    }
}
