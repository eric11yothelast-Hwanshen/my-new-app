package com.hmoney.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * S45 AI 查詢記錄：parseTransactionQuery 的 JSON 解析多案例（純 JVM）。
 * 日期範圍／關鍵字／分類帳戶／金額上下限／筆數上限／非查詢語意→null。
 * 服務層僅做唯讀檢索，無任何 DB 寫入。
 */
class AiQueryParserTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val zone = ZoneId.of("Asia/Taipei")

    private fun epochOf(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `日期範圍與關鍵字解析`() {
        val query = parseQueryFromContent(
            """{"isQuery": true, "keyword": "螢幕", "category": null, "account": null,
               "type": null, "startDate": "2026-11-01", "endDate": "2026-11-13",
               "minAmount": null, "maxAmount": null, "limit": 50}""",
            today,
            zone,
        )
        assertNotNull(query)
        assertEquals("螢幕", query!!.keyword)
        assertEquals(epochOf(LocalDate.of(2026, 11, 1)), query.startDateEpochMillis)
        assertEquals(epochOf(LocalDate.of(2026, 11, 13)) + 86_399_999L, query.endDateEpochMillis)
        assertEquals(50, query.limit)
    }

    @Test
    fun `分類帳戶與類型解析`() {
        val query = parseQueryFromContent(
            """{"isQuery": true, "keyword": null, "category": "餐飲", "account": "現金",
               "type": "expense", "startDate": null, "endDate": null,
               "minAmount": null, "maxAmount": null, "limit": 20}""",
            today,
            zone,
        )
        assertNotNull(query)
        assertEquals("餐飲", query!!.categoryName)
        assertEquals("現金", query.accountName)
        assertEquals(com.hmoney.app.domain.model.TransactionType.EXPENSE, query.type)
        assertEquals(20, query.limit)
    }

    @Test
    fun `金額上下限與自然語言日期解析`() {
        val query = parseQueryFromContent(
            """{"isQuery": true, "keyword": "", "category": null, "account": null,
               "type": "expense", "startDate": "2026-11-01", "endDate": "今天",
               "minAmount": "1000", "maxAmount": "5,000", "limit": 50}""",
            today,
            zone,
        )
        assertNotNull(query)
        assertEquals(100000L, query!!.minAmountCents)
        assertEquals(500000L, query.maxAmountCents)
        assertEquals(epochOf(LocalDate.of(2026, 11, 1)), query.startDateEpochMillis)
        assertEquals(epochOf(today) + 86_399_999L, query.endDateEpochMillis)
    }

    @Test
    fun `markdown 圍籬包裹仍可解析`() {
        val query = parseQueryFromContent(
            "```json\n{\"isQuery\": true, \"keyword\": \"耳機\"}\n```",
            today,
            zone,
        )
        assertNotNull(query)
        assertEquals("耳機", query!!.keyword)
    }

    @Test
    fun `非查詢語意回 null`() {
        assertNull(
            parseQueryFromContent(
                """{"isQuery": false, "keyword": null}""",
                today,
                zone,
            ),
        )
    }

    @Test
    fun `條件全空的查詢回 null`() {
        assertNull(
            parseQueryFromContent(
                """{"isQuery": true, "keyword": null, "category": null, "account": null,
                   "type": null, "startDate": null, "endDate": null,
                   "minAmount": null, "maxAmount": null, "limit": 50}""",
                today,
                zone,
            ),
        )
    }

    @Test
    fun `完全無法解析的內容回 null`() {
        assertNull(parseQueryFromContent("今天天氣真好", today, zone))
        assertNull(parseQueryFromContent("", today, zone))
    }
}
