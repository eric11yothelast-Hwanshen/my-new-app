package com.hmoney.app.ui.screens.entry

import com.hmoney.app.domain.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 入口顯示邏輯測試（S41）：三個開關組合下入口選單內容；
 * 全部關閉回空清單（直接開 AddTransactionSheet，與現行為相同）。
 */
class EntryOptionsTest {

    @Test
    fun `三個開關全關時回空清單`() {
        val flags = SettingsRepository.FeatureFlagSettings(
            aiNlEntryEnabled = false,
            aiImageEntryEnabled = false,
            barcodeEnabled = false,
        )
        assertTrue(visibleEntryOptions(flags).isEmpty())
    }

    @Test
    fun `只開自然語言時列出手動與打字`() {
        val flags = SettingsRepository.FeatureFlagSettings(
            aiNlEntryEnabled = true,
            aiImageEntryEnabled = false,
            barcodeEnabled = false,
        )
        assertEquals(listOf(EntryOption.MANUAL, EntryOption.AI_TEXT), visibleEntryOptions(flags))
    }

    @Test
    fun `開圖片與條碼時列出對應入口`() {
        val flags = SettingsRepository.FeatureFlagSettings(
            aiNlEntryEnabled = false,
            aiImageEntryEnabled = true,
            barcodeEnabled = true,
        )
        assertEquals(
            listOf(EntryOption.MANUAL, EntryOption.AI_IMAGE, EntryOption.BARCODE_SCAN),
            visibleEntryOptions(flags),
        )
    }

    @Test
    fun `三個全開時完整清單`() {
        val flags = SettingsRepository.FeatureFlagSettings()
        assertEquals(4, visibleEntryOptions(flags).size)
    }
}
