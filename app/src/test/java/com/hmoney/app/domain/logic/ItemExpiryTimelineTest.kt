package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** S48 到期時間軸分佈純函式（未來 30 天、密度＝當日數量、無到期隱藏） */
class ItemExpiryTimelineTest {

    private val today = LocalDate.of(2026, 1, 15)
    private val zone = ZoneId.of("Asia/Taipei")

    private fun item(expiryDaysFromToday: Long?): Item = Item(
        id = expiryDaysFromToday ?: -1,
        name = "物品$expiryDaysFromToday",
        quantity = 1,
        expiryDateEpochMillis = expiryDaysFromToday?.let {
            today.plusDays(it).atStartOfDay(zone).toInstant().toEpochMilli()
        },
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )

    @Test
    fun `有到期的日子畫點且密度為當日數量`() {
        val timeline = expiryTimelineCounts(
            items = listOf(item(0), item(0), item(3), item(29)),
            today = today,
            zone = zone,
        )
        assertEquals(3, timeline.size)
        assertEquals(today, timeline[0].date)
        assertEquals(2, timeline[0].count)
        assertEquals(today.plusDays(3), timeline[1].date)
        assertEquals(1, timeline[1].count)
        assertEquals(today.plusDays(29), timeline[2].date)
        assertEquals(1, timeline[2].count)
    }

    @Test
    fun `過去與超過 30 天與無到期日者不計`() {
        val timeline = expiryTimelineCounts(
            items = listOf(item(-1), item(30), item(null)),
            today = today,
            zone = zone,
        )
        assertTrue(timeline.isEmpty())
    }

    @Test
    fun `無到期物品時回空清單`() {
        assertTrue(
            expiryTimelineCounts(items = emptyList(), today = today, zone = zone).isEmpty(),
        )
    }

    @Test
    fun `點位對應分節：7 天內 SOON 其餘 OTHER`() {
        assertEquals(ItemExpirySectionType.SOON, sectionTypeForTimelineDate(today, today))
        assertEquals(ItemExpirySectionType.SOON, sectionTypeForTimelineDate(today.plusDays(7), today))
        assertEquals(ItemExpirySectionType.OTHER, sectionTypeForTimelineDate(today.plusDays(8), today))
    }
}
