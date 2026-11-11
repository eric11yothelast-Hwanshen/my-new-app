package com.hmoney.app.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.data.repository.AiCallException
import com.hmoney.app.domain.model.AiAnalysis
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.AiFilterSuggestion
import com.hmoney.app.domain.model.AiTextResult
import com.hmoney.app.domain.repository.AiOutcome
import com.hmoney.app.domain.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 各畫面 AI 功能共用 ViewModel（總覽洞察、明細 AI 篩選／批次分析、統計分析、帳戶解讀）。
 * 呼叫一律可取消（Job），錯誤以 AiError 呈現，不閃退。
 */
@HiltViewModel
class FeatureAiViewModel @Inject constructor(
    private val aiRepository: AiRepository,
) : ViewModel() {

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class TextResult(val text: String) : UiState()
        data class Insight(val analysis: AiAnalysis) : UiState()
        data class Filter(val suggestion: AiFilterSuggestion) : UiState()
        data class Error(val error: AiError) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        _uiState.value = UiState.Idle
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    /** 通用財務數據解讀（統計分析、帳戶解讀、批次交易分析） */
    fun runText(title: String, data: String) {
        if (_uiState.value is UiState.Loading) return
        _uiState.value = UiState.Loading
        job = viewModelScope.launch {
            _uiState.value = when (val result = aiRepository.analyzeData(title, data)) {
                is AiTextResult.Success -> UiState.TextResult(result.text)
                is AiTextResult.Failure -> UiState.Error(result.error)
            }
        }
    }

    /** 本週 AI 洞察（沿用既有 analyzeWeek，結果存歷史） */
    fun weekInsight() {
        if (_uiState.value is UiState.Loading) return
        _uiState.value = UiState.Loading
        job = viewModelScope.launch {
            _uiState.value = when (val outcome = aiRepository.analyzeWeek()) {
                is AiOutcome.Success -> UiState.Insight(outcome.analysis)
                is AiOutcome.Failure -> UiState.Error(outcome.error)
            }
        }
    }

    /** 自然語言轉篩選條件（明細頁 AI 搜尋） */
    fun interpretFilter(
        query: String,
        categoryNames: List<String>,
        accountNames: List<String>,
    ) {
        if (query.isBlank() || _uiState.value is UiState.Loading) return
        _uiState.value = UiState.Loading
        job = viewModelScope.launch {
            aiRepository.interpretFilter(query.trim(), categoryNames, accountNames)
                .onSuccess { _uiState.value = UiState.Filter(it) }
                .onFailure { e ->
                    _uiState.value = UiState.Error(
                        (e as? AiCallException)?.error ?: AiError.Parse,
                    )
                }
        }
    }
}
