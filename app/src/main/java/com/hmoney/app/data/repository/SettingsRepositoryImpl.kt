package com.hmoney.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    private val aiIncludeContextKey = booleanPreferencesKey("ai_include_context")
    private val aiKeepHistoryKey = booleanPreferencesKey("ai_keep_history")
    private val aiAutoCleanupKey = booleanPreferencesKey("ai_auto_cleanup")
    private val aiShowQuickPromptsKey = booleanPreferencesKey("ai_show_quick_prompts")
    private val aiQueryEnabledKey = booleanPreferencesKey("ai_query_enabled")
    private val aiCurrentSessionIdKey = longPreferencesKey("ai_current_session_id")
    private val lastSeenInsightKey = longPreferencesKey("last_seen_insight")

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

    override fun observeAiAssistant(): Flow<SettingsRepository.AiAssistantSettings> =
        context.settingsDataStore.data.map { prefs ->
            SettingsRepository.AiAssistantSettings(
                includeFinanceContext = prefs[aiIncludeContextKey] ?: true,
                keepHistory = prefs[aiKeepHistoryKey] ?: true,
                autoCleanup = prefs[aiAutoCleanupKey] ?: true,
                showQuickPrompts = prefs[aiShowQuickPromptsKey] ?: true,
                aiQueryEnabled = prefs[aiQueryEnabledKey] ?: true,
                currentSessionId = prefs[aiCurrentSessionIdKey] ?: 0L,
            )
        }

    override suspend fun setAiAssistantIncludeContext(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[aiIncludeContextKey] = enabled
        }
    }

    override suspend fun setAiAssistantKeepHistory(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[aiKeepHistoryKey] = enabled
        }
    }

    override suspend fun setAiAssistantAutoCleanup(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[aiAutoCleanupKey] = enabled
        }
    }

    override suspend fun setAiAssistantShowQuickPrompts(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[aiShowQuickPromptsKey] = enabled
        }
    }

    override suspend fun setAiAssistantQueryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[aiQueryEnabledKey] = enabled
        }
    }

    override suspend fun setAiCurrentSessionId(sessionId: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[aiCurrentSessionIdKey] = sessionId
        }
    }

    override fun observeFeatureFlags(): Flow<SettingsRepository.FeatureFlagSettings> =
        context.settingsDataStore.data.map(::resolveFeatureFlags)

    override suspend fun setAiNlEntryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[AI_NL_ENTRY_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setAiImageEntryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[AI_IMAGE_ENTRY_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setBarcodeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[BARCODE_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setInventoryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[INVENTORY_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setOnboardingCompleted(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED_KEY] = enabled
        }
    }

    override suspend fun setOnboardingCoachStep(step: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[ONBOARDING_COACH_STEP_KEY] = step.coerceIn(0, 3)
        }
    }

    override fun observeLastSeenInsight(): Flow<Long> =
        context.settingsDataStore.data.map { prefs ->
            prefs[lastSeenInsightKey] ?: 0L
        }

    override suspend fun setLastSeenInsightEpochMillis(epochMillis: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[lastSeenInsightKey] = epochMillis
        }
    }

    companion object {
        /** 開關 key 常數（供測試與之後設定頁 UI 使用） */
        val AI_NL_ENTRY_ENABLED_KEY = booleanPreferencesKey("ai_nl_entry_enabled")
        val AI_IMAGE_ENTRY_ENABLED_KEY = booleanPreferencesKey("ai_image_entry_enabled")
        val BARCODE_ENABLED_KEY = booleanPreferencesKey("barcode_enabled")
        val INVENTORY_ENABLED_KEY = booleanPreferencesKey("inventory_enabled")
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        val ONBOARDING_COACH_STEP_KEY = intPreferencesKey("onboarding_coach_step")

        /** 純函式解析：未儲存的開關一律回傳預設值 true（JVM 單元測試直接驗證） */
        fun resolveFeatureFlags(
            prefs: Preferences,
        ): SettingsRepository.FeatureFlagSettings =
            SettingsRepository.FeatureFlagSettings(
                aiNlEntryEnabled = prefs[AI_NL_ENTRY_ENABLED_KEY] ?: true,
                aiImageEntryEnabled = prefs[AI_IMAGE_ENTRY_ENABLED_KEY] ?: true,
                barcodeEnabled = prefs[BARCODE_ENABLED_KEY] ?: true,
                inventoryEnabled = prefs[INVENTORY_ENABLED_KEY] ?: true,
                onboardingCompleted = prefs[ONBOARDING_COMPLETED_KEY] ?: false,
                onboardingCoachStep = prefs[ONBOARDING_COACH_STEP_KEY] ?: 0,
            )
    }
}
