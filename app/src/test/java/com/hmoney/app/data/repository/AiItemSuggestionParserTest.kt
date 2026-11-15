package com.hmoney.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * S46 AI 智慧填選：suggestItemFields 的 JSON 解析多案例（純 JVM）。
 * 存放位置／明確日期／相對天數／數量／markdown 圍籬／欄位全空。
 */
class AiItemSuggestionParserTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val zone = ZoneId.of("Asia/Taipei")

    private fun epochOf(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `完整建議解析`() {
        val suggestion = parseItemSuggestionFromContent(
            """{"storageLocation": "冰箱", "expiryDate": "2026-11-18",
               "expiryInDays": null, "quantity": 3}""",
            today,
            zone,
        )
        assertEquals("冰箱", suggestion.storageLocation)
        assertEquals(epochOf(LocalDate.of(2026, 11, 18)), suggestion.expiryDateEpochMillis)
        assertEquals(3, suggestion.quantity)
    }

    @Test
    fun `相對天數轉明確日期`() {
        val suggestion = parseItemSuggestionFromContent(
            """{"storageLocation": null, "expiryDate": null, "expiryInDays": 7, "quantity": null}""",
            today,
            zone,
        )
        assertEquals(epochOf(today.plusDays(7)), suggestion.expiryDateEpochMillis)
        assertNull(suggestion.storageLocation)
        assertNull(suggestion.quantity)
    }

    @Test
    fun `明確日期優先於相對天數`() {
        val suggestion = parseItemSuggestionFromContent(
            """{"expiryDate": "2026-12-01", "expiryInDays": 3}""",
            today,
            zone,
        )
        assertEquals(epochOf(LocalDate.of(2026, 12, 1)), suggestion.expiryDateEpochMillis)
    }

    @Test
    fun `markdown 圍籬與欄位缺漏`() {
        val suggestion = parseItemSuggestionFromContent(
            "```json\n{\"storageLocation\": \"冷藏\", \"quantity\": 1}\n```",
            today,
            zone,
        )
        assertEquals("冷藏", suggestion.storageLocation)
        assertEquals(1, suggestion.quantity)
        assertNull(suggestion.expiryDateEpochMillis)
    }

    @Test
    fun `null 與零值視為無建議`() {
        val suggestion = parseItemSuggestionFromContent(
            """{"storageLocation": null, "expiryDate": null, "expiryInDays": null, "quantity": 0}""",
            today,
            zone,
        )
        assertNull(suggestion.storageLocation)
        assertNull(suggestion.expiryDateEpochMillis)
        assertNull(suggestion.quantity)
    }

    @Test
    fun `無法解析回全空建議不拋例`() {
        val suggestion = parseItemSuggestionFromContent("今天天氣好", today, zone)
        assertNotNull(suggestion)
        assertNull(suggestion.storageLocation)
        assertNull(suggestion.expiryDateEpochMillis)
        assertNull(suggestion.quantity)
    }
}
