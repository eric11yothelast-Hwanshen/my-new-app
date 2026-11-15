package com.hmoney.app.ui.common

import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerFormatter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * S46 共用 DatePicker 短格式 formatter：選中日期以 yyyy/MM/dd 顯示（如 2026/11/12），
 * 月年與年份 headline 沿用系統預設（委派 DatePickerDefaults.dateFormatter()）。
 * 三個記帳表面（手動／聊天草稿／掃碼草稿）的 DatePicker 一律傳入——
 * 長中文日期 headline 會截斷（Session 25 同款問題，勿重蹈）。
 * 日期以 UTC 解析（與 material3 預設行為一致）。
 */
fun shortDatePickerFormatter(): DatePickerFormatter {
    val default = DatePickerDefaults.dateFormatter()
    return object : DatePickerFormatter {
        override fun formatDate(
            dateMillis: Long?,
            locale: CalendarLocale,
            forContentDescription: Boolean,
        ): String? = dateMillis?.let {
            DateTimeFormatter.ofPattern("yyyy/MM/dd", locale)
                .format(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC))
        } ?: default.formatDate(dateMillis, locale, forContentDescription)

        override fun formatMonthYear(monthMillis: Long?, locale: CalendarLocale): String? =
            default.formatMonthYear(monthMillis, locale)
    }
}
