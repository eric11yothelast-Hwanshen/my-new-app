package com.hmoney.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S50 頂層分頁 route→index 映射（含 inventoryEnabled 過濾）：
 * 一律以「過濾後清單」計算，嚴禁 hardcode index。
 */
class TopLevelDestinationMappingTest {

    @Test
    fun `全清單映射正確`() {
        val all = TopLevelDestination.entries.toList()
        assertEquals(0, topLevelTabIndex(all, "overview"))
        assertEquals(1, topLevelTabIndex(all, "transactions"))
        assertEquals(2, topLevelTabIndex(all, "stats"))
        assertEquals(3, topLevelTabIndex(all, "items"))
        assertEquals(4, topLevelTabIndex(all, "accounts"))
    }

    @Test
    fun `inventoryEnabled 關閉時物品分頁移除且其餘索引前移`() {
        val filtered = TopLevelDestination.entries.filterNot { it == TopLevelDestination.ITEMS }
        assertEquals(4, filtered.size)
        assertEquals(0, topLevelTabIndex(filtered, "overview"))
        assertEquals(2, topLevelTabIndex(filtered, "stats"))
        assertEquals(3, topLevelTabIndex(filtered, "accounts"))
        // 物品 route 不在過濾後清單 → fallback 0
        assertEquals(0, topLevelTabIndex(filtered, "items"))
    }

    @Test
    fun `非頂層 route 與 null 回 0`() {
        val all = TopLevelDestination.entries.toList()
        assertEquals(0, topLevelTabIndex(all, "settings"))
        assertEquals(0, topLevelTabIndex(all, null))
    }
}
