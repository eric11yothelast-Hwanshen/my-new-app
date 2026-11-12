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
    fun `未閉合符號原樣保留不拋例外`() {
        val lines = parseMarkdown("未閉合 **粗體 與 `程式碼")
        assertEquals(1, lines.size)
        assertEquals(
            listOf(MdSegment.Plain("未閉合 **粗體 與 `程式碼")),
            lines[0].segments,
        )
    }
}
