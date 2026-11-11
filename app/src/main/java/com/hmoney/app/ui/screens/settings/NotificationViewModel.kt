package com.hmoney.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmoney.app.data.scheduler.DailyReminderScheduler
import com.hmoney.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通知設定 ViewModel：每日記帳提醒的開關與時間，設定後立即重排精確鬧鐘。
 */
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduler: DailyReminderScheduler,
) : ViewModel() {

    val reminder: StateFlow<SettingsRepository.ReminderSettings> =
        settingsRepository.observeReminder()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsRepository.ReminderSettings(),
            )

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setReminder(enabled, hour, minute)
            if (enabled) {
                scheduler.schedule(hour, minute)
            } else {
                scheduler.cancel()
            }
        }
    }
}
