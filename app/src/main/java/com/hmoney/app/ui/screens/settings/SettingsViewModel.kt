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
 * 設定 ViewModel：目前實作外觀（深色模式／動態色彩），
 * 預算、AI 連線、通知、備份將於後續階段加入。
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

    fun setDarkMode(mode: DarkMode) {
        viewModelScope.launch { settingsRepository.setDarkMode(mode.name) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }
}
