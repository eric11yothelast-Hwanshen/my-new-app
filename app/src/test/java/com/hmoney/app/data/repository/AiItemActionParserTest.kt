package com.hmoney.app.data.repository

import com.hmoney.app.domain.model.ItemActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * S47 AI 物品操作意圖解析測試（純函式，JVM 直接驗證）：
 * CREATE／UPDATE／DELETE、相對天數與明確日期、markdown 圍籬、
 * 非物品操作→null、action 無法辨識→null、name 缺失→null。
 */
class AiItemActionParserTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val zone = ZoneId.of("Asia/Taipei")

    @Test
    fun `S49 相對現有到期日增減天數 expiryDeltaDays`() {
        val intent = parseItemActionFromContent(
            """{"isItemAction": true, "action": "update", "name": "鮭魚", "expiryDeltaDays": 3}""",
            today,
            zone,
        )!!
        assertEquals(3, intent.expiryDeltaDays)
        assertNull(intent.expiryDateEpochMillis)
        // 負數（縮短／提前）同樣支援
        val negative = parseItemActionFromContent(
            """{"isItemAction": true, "action": "update", "name": "鮭魚", "expiryDeltaDays": -2}""",
            today,
            zone,
        )!!
        assertEquals(-2, negative.expiryDeltaDays)
        // 絕對日期優先，delta 不採用
        val absolute = parseItemActionFromContent(
            """{"isItemAction": true, "action": "update", "name": "鮭魚", "expiryDate": "2026-11-20", "expiryDeltaDays": 3}""",
            today,
            zone,
        )!!
        assertNull(absolute.expiryDeltaDays)
        assertEquals(
            LocalDate.of(2026, 11, 20).atStartOfDay(zone).toInstant().toEpochMilli(),
            absolute.expiryDateEpochMillis,
        )
    }

    @Test
    fun `CREATE 完整欄位`() {
        val intent = parseItemActionFromContent(
            """{"isItemAction": true, "action": "create", "name": "鮮奶",
               "storageLocation": "冰箱", "quantity": 3, "expiryDate": "2026-11-16"}""",
            today,
            zone,
        )!!
        assertEquals(ItemActionType.CREATE, intent.action)
        assertEquals("鮮奶", intent.name)
        assertEquals("冰箱", intent.storageLocation)
        assertEquals(3, intent.quantity)
        assertEquals(LocalDate.of(2026, 11, 16).atStartOfDay(zone).toInstant().toEpochMilli(), intent.expiryDateEpochMillis)
    }

    @Test
    fun `UPDATE 相對天數 expiryInDays`() {
        val intent = parseItemActionFromContent(
            """{"isItemAction": true, "action": "update", "name": "鮮奶", "quantity": 5, "expiryInDays": 3}""",
            today,
            zone,
        )!!
        assertEquals(ItemActionType.UPDATE, intent.action)
        assertEquals(5, intent.quantity)
        assertEquals(
            today.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli(),
            intent.expiryDateEpochMillis,
        )
    }

    @Test
    fun `DELETE 僅名稱`() {
        val intent = parseItemActionFromContent(
            """{"isItemAction": true, "action": "delete", "name": "鮮奶"}""",
            today,
            zone,
        )!!
        assertEquals(ItemActionType.DELETE, intent.action)
        assertEquals("鮮奶", intent.name)
        assertNull(intent.storageLocation)
        assertNull(intent.quantity)
        assertNull(intent.expiryDateEpochMillis)
    }

    @Test
    fun `明確日期優先於相對天數`() {
        val intent = parseItemActionFromContent(
            """{"isItemAction": true, "action": "create", "name": "優酪乳",
               "expiryDate": "2026-12-01", "expiryInDays": 5}""",
            today,
            zone,
        )!!
        assertEquals(
            LocalDate.of(2026, 12, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            intent.expiryDateEpochMillis,
        )
    }

    @Test
    fun `markdown 圍籬與前後綴文字容錯`() {
        val intent = parseItemActionFromContent(
            "```json\n{\"isItemAction\": true, \"action\": \"add\", \"name\": \"雞蛋\", \"quantity\": 2}\n```",
            today,
            zone,
        )!!
        assertEquals(ItemActionType.CREATE, intent.action)
        assertEquals("雞蛋", intent.name)
        assertEquals(2, intent.quantity)
    }

    @Test
    fun `非物品操作回 null`() {
        assertNull(parseItemActionFromContent("""{"isItemAction": false}""", today, zone))
        assertNull(parseItemActionFromContent("今天天氣如何？", today, zone))
    }

    @Test
    fun `action 無法辨識或 name 缺失回 null`() {
        assertNull(
            parseItemActionFromContent(
                """{"isItemAction": true, "action": "unknown", "name": "鮮奶"}""",
                today,
                zone,
            ),
        )
        assertNull(
            parseItemActionFromContent("""{"isItemAction": true, "action": "create", "name": ""}""", today, zone),
        )
        assertNull(
            parseItemActionFromContent("""{"isItemAction": true, "action": "create", "name": "null"}""", today, zone),
        )
    }

    @Test
    fun `null 與零值欄位容錯`() {
        val intent = parseItemActionFromContent(
            """{"isItemAction": true, "action": "create", "name": "豆漿",
               "storageLocation": null, "quantity": 0, "expiryDate": null, "expiryInDays": -1}""",
            today,
            zone,
        )!!
        assertNull(intent.storageLocation)
        assertNull(intent.quantity)
        assertNull(intent.expiryDateEpochMillis)
    }
}
