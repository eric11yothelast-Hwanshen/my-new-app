package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.AppearanceSettings
import kotlinx.coroutines.flow.Flow

/** 外觀設定倉儲介面 */
interface SettingsRepository {

    fun observeAppearance(): Flow<AppearanceSettings>

    suspend fun setDarkMode(modeName: String)

    suspend fun setDynamicColor(enabled: Boolean)
}
