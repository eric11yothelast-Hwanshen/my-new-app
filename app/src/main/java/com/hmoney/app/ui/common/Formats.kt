package com.hmoney.app.ui.common

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * 格式化工具：金額與日期皆以本地化格式呈現（zh-TW）。
 */
object Formats {

    private val moneyFormat: NumberFormat = NumberFormat.getInstance(Locale.TAIWAN)

    /** 「分」→ 台幣字串（兩位小數、千分位） */
    fun money(cents: Long): String {
        val value = BigDecimal(cents).movePointLeft(2)
        return moneyFormat.format(value)
    }

    /** 「分」→ 無千分位的純數字字串（供輸入欄位使用） */
    fun plain(cents: Long): String =
        BigDecimal(cents).movePointLeft(2).toPlainString()

    /** 含貨幣代號的完整字串 */
    fun moneyWithCurrency(cents: Long, currency: String = "TWD"): String =
        "${money(cents)} $currency"

    /** 日期（本地化中等長度，例：2026年11月11日） */
    fun date(epochMillis: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.TAIWAN)
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate())

    /** 日期（LocalDate 版本） */
    fun date(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.TAIWAN).format(date)

    /** 時間（HH:mm） */
    fun time(epochMillis: Long): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.TAIWAN)
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    /** 年月標題（例：2026年11月） */
    fun yearMonth(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return "${date.year}年${date.monthValue}月"
    }

    /** 時間（LocalTime 版本） */
    fun time(time: java.time.LocalTime): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.TAIWAN).format(time)

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun toEpochMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
