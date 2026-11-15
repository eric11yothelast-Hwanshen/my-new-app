package com.hmoney.app.data.repository

import androidx.datastore.preferences.core.preferencesOf
import com.hmoney.app.data.repository.SettingsRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.3.0 功能開關 DataStore 讀寫測試（Session 38）：
 * 未儲存時四個開關預設皆為 true；寫入 false 後讀回 false（key 對應正確）。
 */
class FeatureFlagsTest {

    @Test
    fun `未儲存任何開關時預設全為 true`() {
        val flags = SettingsRepositoryImpl.resolveFeatureFlags(preferencesOf())
        assertTrue(flags.aiNlEntryEnabled)
        assertTrue(flags.aiImageEntryEnabled)
        assertTrue(flags.barcodeEnabled)
        assertTrue(flags.inventoryEnabled)
    }

    @Test
    fun `寫入的開關值能以正確 key 讀回`() {
        val flags = SettingsRepositoryImpl.resolveFeatureFlags(
            preferencesOf(
                SettingsRepositoryImpl.AI_NL_ENTRY_ENABLED_KEY to false,
                SettingsRepositoryImpl.AI_IMAGE_ENTRY_ENABLED_KEY to false,
                SettingsRepositoryImpl.BARCODE_ENABLED_KEY to false,
                SettingsRepositoryImpl.INVENTORY_ENABLED_KEY to false,
            ),
        )
        assertFalse(flags.aiNlEntryEnabled)
        assertFalse(flags.aiImageEntryEnabled)
        assertFalse(flags.barcodeEnabled)
        assertFalse(flags.inventoryEnabled)
    }

    @Test
    fun `只關閉單一開關其餘保持預設 true`() {
        val flags = SettingsRepositoryImpl.resolveFeatureFlags(
            preferencesOf(SettingsRepositoryImpl.BARCODE_ENABLED_KEY to false),
        )
        assertTrue(flags.aiNlEntryEnabled)
        assertTrue(flags.aiImageEntryEnabled)
        assertFalse(flags.barcodeEnabled)
        assertTrue(flags.inventoryEnabled)
    }

    @Test
    fun `S49 新手引導旗標預設 false 且寫入 true 可讀回`() {
        val defaults = SettingsRepositoryImpl.resolveFeatureFlags(preferencesOf())
        assertFalse(defaults.onboardingCompleted)
        val completed = SettingsRepositoryImpl.resolveFeatureFlags(
            preferencesOf(SettingsRepositoryImpl.ONBOARDING_COMPLETED_KEY to true),
        )
        assertTrue(completed.onboardingCompleted)
        assertEquals("onboarding_completed", SettingsRepositoryImpl.ONBOARDING_COMPLETED_KEY.name)
    }

    @Test
    fun `S50 coach 步驟旗標預設 0 且寫入可讀回`() {
        val defaults = SettingsRepositoryImpl.resolveFeatureFlags(preferencesOf())
        assertEquals(0, defaults.onboardingCoachStep)
        val step2 = SettingsRepositoryImpl.resolveFeatureFlags(
            preferencesOf(SettingsRepositoryImpl.ONBOARDING_COACH_STEP_KEY to 2),
        )
        assertEquals(2, step2.onboardingCoachStep)
        assertEquals("onboarding_coach_step", SettingsRepositoryImpl.ONBOARDING_COACH_STEP_KEY.name)
    }

    @Test
    fun `開關 key 命名與規格一致`() {
        assertEquals("ai_nl_entry_enabled", SettingsRepositoryImpl.AI_NL_ENTRY_ENABLED_KEY.name)
        assertEquals("ai_image_entry_enabled", SettingsRepositoryImpl.AI_IMAGE_ENTRY_ENABLED_KEY.name)
        assertEquals("barcode_enabled", SettingsRepositoryImpl.BARCODE_ENABLED_KEY.name)
        assertEquals("inventory_enabled", SettingsRepositoryImpl.INVENTORY_ENABLED_KEY.name)
    }
}
