package com.hmoney.app.data.repository

import com.hmoney.app.domain.model.TransactionActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * S48 parseTransactionActionFromContent 純函式解析（模式照 AiItemActionParserTest）：
 * DELETE／UPDATE／尋標條件／updates／非操作→null／action 無效→null。
 */
class AiTransactionActionParserTest {

    private val today = LocalDate.of(2026, 1, 15)
    private val zone = ZoneId.of("Asia/Taipei")

    private fun parse(content: String) =
        parseTransactionActionFromContent(content, today, zone)

    @Test
    fun `DELETE 意圖帶尋標條件`() {
        val intent = parse(
            """{"isTransactionAction": true, "action": "delete",
               "target": {"keyword": "鮮奶", "startDate": "2026-01-14", "endDate": "2026-01-14"}}""",
        )
        assertNotNull(intent)
        assertEquals(TransactionActionType.DELETE, intent!!.action)
        assertEquals("鮮奶", intent.target.keyword)
        assertEquals(today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            intent.target.startDateEpochMillis)
        assertEquals(
            today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 86_399_999L,
            intent.target.endDateEpochMillis,
        )
        assertNull(intent.updates)
    }

    @Test
    fun `UPDATE 意圖帶 updates 與金額上下限`() {
        val intent = parse(
            """{"isTransactionAction": true, "action": "update",
               "target": {"category": "餐飲", "minAmount": "100", "maxAmount": "500", "type": "expense"},
               "updates": {"amount": "2000", "note": "改備註", "category": "餐飲", "account": "現金", "date": "2026-01-16"}}""",
        )
        assertNotNull(intent)
        assertEquals(TransactionActionType.UPDATE, intent!!.action)
        assertEquals("餐飲", intent.target.categoryName)
        assertEquals(10000L, intent.target.minAmountCents)
        assertEquals(50000L, intent.target.maxAmountCents)
        val updates = intent.updates
        assertNotNull(updates)
        assertEquals(200000L, updates!!.amountCents)
        assertEquals("改備註", updates.note)
        assertEquals("餐飲", updates.categoryName)
        assertEquals("現金", updates.accountName)
        assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            updates.dateEpochMillis)
    }

    @Test
    fun `UPDATE 無任何可更新欄位時 updates 為 null`() {
        val intent = parse(
            """{"isTransactionAction": true, "action": "update",
               "target": {"keyword": "鮮奶"}, "updates": {"amount": null, "note": null}}""",
        )
        assertNotNull(intent)
        assertEquals(TransactionActionType.UPDATE, intent!!.action)
        assertNull(intent.updates)
    }

    @Test
    fun `非交易操作回 null`() {
        assertNull(parse("""{"isTransactionAction": false, "action": "delete", "target": {}}"""))
    }

    @Test
    fun `action 無法辨識回 null`() {
        assertNull(parse("""{"isTransactionAction": true, "action": "create", "target": {}}"""))
    }

    @Test
    fun `markdown 圍籬與夾雜文字可解析`() {
        val intent = parse(
            "好的，以下是解析結果：\n```json\n" +
                """{"isTransactionAction": true, "action": "刪除", "target": {"keyword": "咖啡"}}""" +
                "\n```",
        )
        assertNotNull(intent)
        assertEquals(TransactionActionType.DELETE, intent!!.action)
        assertEquals("咖啡", intent.target.keyword)
    }

    @Test
    fun `S49 空泛刪除語意 recent 為 true`() {
        val intent = parse(
            """{"isTransactionAction": true, "action": "delete", "recent": true, "target": {}}""",
        )
        assertNotNull(intent)
        assertEquals(TransactionActionType.DELETE, intent!!.action)
        assertTrue(intent.recent)
        assertEquals(null, intent.target.keyword)
        // 一般有標的的操作 recent 為 false
        val normal = parse(
            """{"isTransactionAction": true, "action": "delete", "target": {"keyword": "鮮奶"}}""",
        )
        assertTrue(!normal!!.recent)
    }

    @Test
    fun `null 與零值容錯`() {
        assertNull(parse("null"))
        assertNull(parse("隨便一句話"))
        val intent = parse("""{"isTransactionAction": true, "action": "remove", "target": null}""")
        assertNotNull(intent)
        assertEquals(TransactionActionType.DELETE, intent!!.action)
    }
}
