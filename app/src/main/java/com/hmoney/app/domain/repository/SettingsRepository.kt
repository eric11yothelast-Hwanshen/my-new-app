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
}
