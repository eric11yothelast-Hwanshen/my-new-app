package com.hmoney.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.domain.model.AppearanceSettings
import com.hmoney.app.domain.model.DarkMode
import com.hmoney.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 設定 ViewModel：外觀（深色模式／動態色彩）與 AI 助手開關
 * （附財務資料、保留對話、自動清除、快捷提問）。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val appearance: StateFlow<AppearanceSettings> = settingsRepository.observeAppearance()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppearanceSettings(),
        )

    val aiAssistant: StateFlow<SettingsRepository.AiAssistantSettings> =
        settingsRepository.observeAiAssistant()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsRepository.AiAssistantSettings(),
            )

    fun setDarkMode(mode: DarkMode) {
        viewModelScope.launch { settingsRepository.setDarkMode(mode.name) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setAiIncludeContext(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiAssistantIncludeContext(enabled) }
    }

    fun setAiKeepHistory(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiAssistantKeepHistory(enabled) }
    }

    fun setAiAutoCleanup(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiAssistantAutoCleanup(enabled) }
    }

    fun setAiShowQuickPrompts(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiAssistantShowQuickPrompts(enabled) }
    }
}
