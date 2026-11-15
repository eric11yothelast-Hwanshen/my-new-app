package com.hmoney.app.ui.screens.entry

import com.hmoney.app.domain.repository.SettingsRepository

/**
 * 記帳入口選項（S41 純函式，可單元測試）：
 * 三個 DataStore 開關（ai_nl_entry／ai_image_entry／barcode）全關時回空清單——
 * 此時不出選單、直接開 AddTransactionSheet（與現行為完全相同）。
 * 「手動記帳」選項僅在任一 AI 入口開啟時一併列出（入口選擇是 AI 快捷入口，不是取代手動記帳）。
 */
enum class EntryOption { MANUAL, AI_TEXT, AI_IMAGE, BARCODE_SCAN }

fun visibleEntryOptions(flags: SettingsRepository.FeatureFlagSettings): List<EntryOption> {
    val anyAiEnabled = flags.aiNlEntryEnabled || flags.aiImageEntryEnabled || flags.barcodeEnabled
    if (!anyAiEnabled) return emptyList()
    return buildList {
        add(EntryOption.MANUAL)
        if (flags.aiNlEntryEnabled) add(EntryOption.AI_TEXT)
        if (flags.aiImageEntryEnabled) add(EntryOption.AI_IMAGE)
        if (flags.barcodeEnabled) add(EntryOption.BARCODE_SCAN)
    }
}
