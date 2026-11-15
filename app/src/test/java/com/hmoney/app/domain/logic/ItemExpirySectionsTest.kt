package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Item
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * S46 到期分節純函式測試：已過期／7 天內到期（含當天）／其他（無到期日排最後）、各節升冪。
 */
class ItemExpirySectionsTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val zone = ZoneId.of("Asia/Taipei")

    private fun epochOf(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun item(name: String, expiry: Long?) = Item(
        id = name.hashCode().toLong(),
        name = name,
        expiryDateEpochMillis = expiry,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )

    @Test
    fun `三節分類與各節升冪排序`() {
        val items = listOf(
            item("遠期", epochOf(today.plusDays(30))),
            item("明天", epochOf(today.plusDays(1))),
            item("前天過期", epochOf(today.minusDays(2))),
            item("今天", epochOf(today)),
            item("無到期日", null),
            item("上週過期", epochOf(today.minusDays(7))),
        )
        val sections = groupItemsByExpiry(items, today, zone)
        assertEquals(3, sections.size)
        assertEquals(
            listOf("上週過期", "前天過期"),
            sections[0].items.map { it.name },
        )
        assertEquals(
            listOf("今天", "明天"),
            sections[1].items.map { it.name },
        )
        assertEquals(
            listOf("遠期", "無到期日"),
            sections[2].items.map { it.name },
        )
    }

    @Test
    fun `第 7 天邊界屬於即將到期`() {
        val items = listOf(item("第七天", epochOf(today.plusDays(7))))
        val sections = groupItemsByExpiry(items, today, zone)
        assertEquals(1, sections.size)
        assertEquals(ItemExpirySectionType.SOON, sections.single().type)
    }

    @Test
    fun `空節不輸出`() {
        val sections = groupItemsByExpiry(emptyList(), today, zone)
        assertEquals(0, sections.size)
        val onlyOther = groupItemsByExpiry(listOf(item("無到期日", null)), today, zone)
        assertEquals(1, onlyOther.size)
        assertEquals(ItemExpirySectionType.OTHER, onlyOther.single().type)
    }
}
