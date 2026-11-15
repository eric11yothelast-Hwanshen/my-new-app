package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.RecurringFrequency
import com.hmoney.app.domain.model.RecurringTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * 週期排程邏輯（純邏輯，可單元測試）：
 * 計算下一次到期時間，並列出所有已到期的產生時間點。
 */
class RecurringScheduler @Inject constructor() {

    /** 依頻率推進下一次到期時間 */
    fun advanceNextDue(frequency: RecurringFrequency, fromMillis: Long, zone: ZoneId): Long {
        val dateTime = Instant.ofEpochMilli(fromMillis).atZone(zone)
        return when (frequency) {
            RecurringFrequency.WEEKLY ->
                dateTime.plusWeeks(1).toInstant().toEpochMilli()
            RecurringFrequency.MONTHLY -> {
                // 每月：保持指定日（大月／小月自動夾到當月最大日）
                val date = dateTime.toLocalDate()
                val nextMonth = date.plusMonths(1)
                val dayOfMonth = date.dayOfMonth.coerceAtMost(nextMonth.lengthOfMonth())
                nextMonth.withDayOfMonth(dayOfMonth)
                    .atTime(LocalTime.of(9, 0))
                    .atZone(zone).toInstant().toEpochMilli()
            }
        }
    }

    /**
     * 列出 [fromMillis, nowMillis] 區間內所有應產生的時間點（含起點、不含終點之後）。
     * 防呆上限：單次最多產生 60 期，避免規則異常時無限迴圈。
     */
    fun dueTimes(
        recurring: RecurringTransaction,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> {
        val result = mutableListOf<Long>()
        var next = recurring.nextDueEpochMillis
        var guard = 0
        while (next <= nowMillis && guard < 60) {
            result.add(next)
            next = advanceNextDue(recurring.frequency, next, zone)
            guard++
        }
        return result
    }

    /** 計算下一次到期時間（已全數產生後的下一期） */
    fun nextDueAfter(
        recurring: RecurringTransaction,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        var next = recurring.nextDueEpochMillis
        var guard = 0
        while (next <= nowMillis && guard < 60) {
            next = advanceNextDue(recurring.frequency, next, zone)
            guard++
        }
        return next
    }

    /** 依頻率與指定日，計算規則建立後的首次到期時間 */
    fun firstDue(
        frequency: RecurringFrequency,
        dayOfWeek: Int?,
        dayOfMonth: Int?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val today = LocalDate.now(zone)
        return when (frequency) {
            RecurringFrequency.WEEKLY -> {
                val target = today.with(java.time.DayOfWeek.of(dayOfWeek ?: 1))
                val date = if (!target.isBefore(today)) target else target.plusWeeks(1)
                date.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
            }
            RecurringFrequency.MONTHLY -> {
                val day = (dayOfMonth ?: 1).coerceAtMost(today.lengthOfMonth())
                var date = today.withDayOfMonth(day)
                if (date.isBefore(today)) {
                    val nextMonth = today.plusMonths(1)
                    date = nextMonth.withDayOfMonth(
                        (dayOfMonth ?: 1).coerceAtMost(nextMonth.lengthOfMonth()),
                    )
                }
                date.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
            }
        }
    }
}
