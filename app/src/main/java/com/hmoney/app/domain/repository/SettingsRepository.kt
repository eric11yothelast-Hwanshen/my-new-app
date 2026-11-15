package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.AppearanceSettings
import kotlinx.coroutines.flow.Flow

/** 外觀／通知設定倉儲介面 */
interface SettingsRepository {

    fun observeAppearance(): Flow<AppearanceSettings>

    suspend fun setDarkMode(modeName: String)

    suspend fun setDynamicColor(enabled: Boolean)

    /** 每日記帳提醒設定 */
    data class ReminderSettings(
        val enabled: Boolean = false,
        val hour: Int = 20,
        val minute: Int = 0,
    )

    fun observeReminder(): Flow<ReminderSettings>

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int)

    /** 一次性旗標（預算警示同週期防重複轟炸） */
    suspend fun getFlag(key: String): Boolean

    suspend fun setFlag(key: String, value: Boolean)

    /** AI 助手設定：context 附加、對話保留、自動清除、快捷提問、AI 查詢記錄與目前 session id */
    data class AiAssistantSettings(
        val includeFinanceContext: Boolean = true,
        val keepHistory: Boolean = true,
        val autoCleanup: Boolean = true,
        val showQuickPrompts: Boolean = true,
        val aiQueryEnabled: Boolean = true,
        val currentSessionId: Long = 0L,
    )

    fun observeAiAssistant(): Flow<AiAssistantSettings>

    suspend fun setAiAssistantIncludeContext(enabled: Boolean)

    suspend fun setAiAssistantKeepHistory(enabled: Boolean)

    suspend fun setAiAssistantAutoCleanup(enabled: Boolean)

    suspend fun setAiAssistantShowQuickPrompts(enabled: Boolean)

    suspend fun setAiAssistantQueryEnabled(enabled: Boolean)

    suspend fun setAiCurrentSessionId(sessionId: Long)

    /** 1.3.0 功能開關（預設全開；之後由設定頁 UI 接上） */
    data class FeatureFlagSettings(
        val aiNlEntryEnabled: Boolean = true,
        val aiImageEntryEnabled: Boolean = true,
        val barcodeEnabled: Boolean = true,
        val inventoryEnabled: Boolean = true,
        /** S49 新手引導：完成（含跳過）後寫入 true，之後永不再現；預設 false */
        val onboardingCompleted: Boolean = false,
        /** S50 實機點擊引導步驟：0=未開始、1~3=進行中步驟；三步完成或跳過時歸零 */
        val onboardingCoachStep: Int = 0,
    )

    fun observeFeatureFlags(): Flow<FeatureFlagSettings>

    suspend fun setAiNlEntryEnabled(enabled: Boolean)

    suspend fun setAiImageEntryEnabled(enabled: Boolean)

    suspend fun setBarcodeEnabled(enabled: Boolean)

    suspend fun setInventoryEnabled(enabled: Boolean)

    /** S49 新手引導完成旗標（完成或跳過皆寫入 true） */
    suspend fun setOnboardingCompleted(enabled: Boolean)

    /** S50 實機點擊引導步驟（0=未開始、1~3 進行中；持久化供中斷續步） */
    suspend fun setOnboardingCoachStep(step: Int)

    /** 總覽洞察已讀：最後查看的洞察時間（晚於它的新洞察才顯示圓點） */
    fun observeLastSeenInsight(): Flow<Long>

    suspend fun setLastSeenInsightEpochMillis(epochMillis: Long)
}
