package com.hmoney.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hmoney.app.domain.model.AppearanceSettings
import com.hmoney.app.domain.model.DarkMode
import com.hmoney.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// 模組級 DataStore 實例（Preferences DataStore）
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * 設定倉儲實作：外觀與通知設定存於 DataStore（Preferences）。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private val darkModeKey = stringPreferencesKey("dark_mode")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val reminderEnabledKey = booleanPreferencesKey("reminder_enabled")
    private val reminderHourKey = intPreferencesKey("reminder_hour")
    private val reminderMinuteKey = intPreferencesKey("reminder_minute")

    override fun observeAppearance(): Flow<AppearanceSettings> =
        context.settingsDataStore.data.map { prefs ->
            AppearanceSettings(
                darkMode = prefs[darkModeKey]?.let { stored ->
                    runCatching { DarkMode.valueOf(stored) }.getOrNull()
                } ?: DarkMode.SYSTEM,
                dynamicColor = prefs[dynamicColorKey] ?: true,
            )
        }

    override suspend fun setDarkMode(modeName: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[darkModeKey] = modeName
        }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[dynamicColorKey] = enabled
        }
    }

    override fun observeReminder(): Flow<SettingsRepository.ReminderSettings> =
        context.settingsDataStore.data.map { prefs ->
            SettingsRepository.ReminderSettings(
                enabled = prefs[reminderEnabledKey] ?: false,
                hour = prefs[reminderHourKey] ?: 20,
                minute = prefs[reminderMinuteKey] ?: 0,
            )
        }

    override suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[reminderEnabledKey] = enabled
            prefs[reminderHourKey] = hour.coerceIn(0, 23)
            prefs[reminderMinuteKey] = minute.coerceIn(0, 59)
        }
    }

    override suspend fun getFlag(key: String): Boolean =
        context.settingsDataStore.data.first()[booleanPreferencesKey(key)] ?: false

    override suspend fun setFlag(key: String, value: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[booleanPreferencesKey(key)] = value
        }
    }
}
