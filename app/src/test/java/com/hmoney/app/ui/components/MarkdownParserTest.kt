package com.hmoney.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Markdown 解析純邏輯測試（保守掃描、不拋例外） */
class MarkdownParserTest {

    @Test
    fun `粗體符號解析為 Bold 區段`() {
        val lines = parseMarkdown("本月花費 **大幅上升**，請注意")
        assertEquals(1, lines.size)
        assertEquals(
            listOf(
                MdSegment.Plain("本月花費 "),
                MdSegment.Bold("大幅上升"),
                MdSegment.Plain("，請注意"),
            ),
            lines[0].segments,
        )
    }

    @Test
    fun `清單項目標記為 bullet`() {
        val lines = parseMarkdown("- 餐飲 500 元\n* 交通 120 元")
        assertEquals(2, lines.size)
        assertTrue(lines[0].bullet)
        assertTrue(lines[1].bullet)
        assertEquals(listOf(MdSegment.Plain("餐飲 500 元")), lines[0].segments)
        assertEquals(listOf(MdSegment.Plain("交通 120 元")), lines[1].segments)
    }

    @Test
    fun `無 Markdown 符號時原樣保留`() {
        val lines = parseMarkdown("這是一段普通文字，沒有任何符號")
        assertEquals(1, lines.size)
        assertEquals(listOf(MdSegment.Plain("這是一段普通文字，沒有任何符號")), lines[0].segments)
    }

    @Test
    fun `分隔線渲染為 divider 不印任何字元`() {
        val lines = parseMarkdown("---\n-----\n———")
        assertEquals(3, lines.size)
        lines.forEach {
            assertTrue(it.divider)
            assertEquals(emptyList<MdSegment>(), it.segments)
        }
    }

    @Test
    fun `有序清單保留編號不疊圓點`() {
        val lines = parseMarkdown("1. 先記一筆\n2. **再記一筆**")
        assertEquals(2, lines.size)
        assertEquals("1", lines[0].orderedNumber)
        assertEquals("2", lines[1].orderedNumber)
        assertEquals(listOf(MdSegment.Plain("先記一筆")), lines[0].segments)
        assertEquals(listOf(MdSegment.Bold("再記一筆")), lines[1].segments)
    }

    @Test
    fun `標題收尾 hash 被清除且分級正確`() {
        val lines = parseMarkdown("# 結論 ##\n### 1. 總結")
        assertEquals(2, lines.size)
        assertEquals(1, lines[0].headingLevel)
        assertEquals(listOf(MdSegment.Plain("結論")), lines[0].segments)
        assertEquals(3, lines[1].headingLevel)
        assertEquals(listOf(MdSegment.Plain("1. 總結")), lines[1].segments)
    }

    @Test
    fun `未支援符號原樣保留`() {
        val lines = parseMarkdown("~~刪除線~~ 與 [[連結]]")
        assertEquals(1, lines.size)
        assertEquals(
            listOf(MdSegment.Plain("~~刪除線~~ 與 [[連結]]")),
            lines[0].segments,
        )
    }

    @Test
    fun `未閉合符號原樣保留不拋例外`() {
        val lines = parseMarkdown("未閉合 **粗體 與 `程式碼")
        assertEquals(1, lines.size)
        assertEquals(
            listOf(MdSegment.Plain("未閉合 **粗體 與 `程式碼")),
            lines[0].segments,
        )
    }
}
