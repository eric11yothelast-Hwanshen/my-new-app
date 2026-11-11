package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.RecurringFrequency
import com.hmoney.app.domain.model.RecurringTransaction
import com.hmoney.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 週期排程邏輯測試：每週／每月推進、補產到期、防呆上限 */
class RecurringSchedulerTest {

    private val scheduler = RecurringScheduler()
    private val zone = ZoneId.of("Asia/Taipei")

    private fun recurring(nextDue: Long, frequency: RecurringFrequency = RecurringFrequency.WEEKLY) =
        RecurringTransaction(
            type = TransactionType.EXPENSE,
            amountCents = 100_00,
            accountId = 1,
            categoryId = 1,
            frequency = frequency,
            dayOfWeek = 1,
            dayOfMonth = 1,
            nextDueEpochMillis = nextDue,
        )

    private fun millis(date: LocalDate) = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `每週推進一次七天`() {
        val start = millis(LocalDate.of(2026, 1, 5))
        val next = scheduler.advanceNextDue(RecurringFrequency.WEEKLY, start, zone)
        assertEquals(millis(LocalDate.of(2026, 1, 12)), next)
    }

    @Test
    fun `每月推進保持指定日`() {
        val start = millis(LocalDate.of(2026, 1, 31))
        val next = scheduler.advanceNextDue(RecurringFrequency.MONTHLY, start, zone)
        // 2 月只有 28 天：自動夾到當月最大日
        assertEquals(millis(LocalDate.of(2026, 2, 28)), next)
    }

    @Test
    fun `到期時間列出所有應補產期別`() {
        val due1 = millis(LocalDate.of(2026, 1, 5))
        val due2 = millis(LocalDate.of(2026, 1, 12))
        val now = millis(LocalDate.of(2026, 1, 13))
        val times = scheduler.dueTimes(recurring(due1), now, zone)
        assertEquals(listOf(due1, due2), times)
    }

    @Test
    fun `未到期時不產生任何期別`() {
        val due = millis(LocalDate.of(2026, 2, 1))
        val now = millis(LocalDate.of(2026, 1, 15))
        assertTrue(scheduler.dueTimes(recurring(due), now, zone).isEmpty())
    }

    @Test
    fun `補產後下次到期推進到未來`() {
        val due1 = millis(LocalDate.of(2026, 1, 5))
        val now = millis(LocalDate.of(2026, 1, 6))
        val next = scheduler.nextDueAfter(recurring(due1), now, zone)
        assertEquals(millis(LocalDate.of(2026, 1, 12)), next)
    }

    @Test
    fun `規則異常時單次最多產生 60 期`() {
        // 從極久以前開始的規則：不得無限迴圈
        val ancient = millis(LocalDate.of(2000, 1, 3))
        val now = millis(LocalDate.of(2026, 11, 11))
        assertEquals(60, scheduler.dueTimes(recurring(ancient), now, zone).size)
    }
}
