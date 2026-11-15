package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S47 物品名稱匹配純函式測試：唯一／多筆／無（equals→contains，皆 ignoreCase） */
class ItemNameMatcherTest {

    private fun item(id: Long, name: String) =
        Item(id = id, name = name, createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L)

    @Test
    fun `完全相等唯一命中`() {
        val items = listOf(item(1, "鮮奶"), item(2, "雞蛋"))
        val match = matchItemByName(items, "鮮奶")
        assertEquals(1L, (match as ItemNameMatch.Unique).item.id)
    }

    @Test
    fun `大小寫不一致仍命中`() {
        val items = listOf(item(1, "Milk"))
        val match = matchItemByName(items, "milk")
        assertEquals(1L, (match as ItemNameMatch.Unique).item.id)
    }

    @Test
    fun `包含關係命中`() {
        val items = listOf(item(1, "光泉鮮奶"))
        val match = matchItemByName(items, "鮮奶")
        assertEquals(1L, (match as ItemNameMatch.Unique).item.id)
    }

    @Test
    fun `多筆符合回 Ambiguous`() {
        val items = listOf(item(1, "光泉鮮奶"), item(2, "乳香鮮奶"), item(3, "雞蛋"))
        val match = matchItemByName(items, "鮮奶")
        assertEquals(2, (match as ItemNameMatch.Ambiguous).items.size)
    }

    @Test
    fun `完全相等多筆同名也回 Ambiguous`() {
        val items = listOf(item(1, "鮮奶"), item(2, "鮮奶"))
        val match = matchItemByName(items, "鮮奶")
        assertTrue(match is ItemNameMatch.Ambiguous)
    }

    @Test
    fun `無匹配回 NotFound`() {
        val items = listOf(item(1, "雞蛋"))
        assertEquals(ItemNameMatch.NotFound, matchItemByName(items, "鮮奶"))
        assertEquals(ItemNameMatch.NotFound, matchItemByName(items, "  "))
    }
}
