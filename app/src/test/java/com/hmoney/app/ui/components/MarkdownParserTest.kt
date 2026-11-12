package com.hmoney.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

/** Markdown 渲染（buildMdAnnotatedString）測試：文字不重複、前綴與樣式各出現一次 */
class MarkdownRenderTest {

    private fun render(line: MdLine) =
        buildMdAnnotatedString(line, Color(0xFFE0E0E0), Color(0xFF111111))

    @Test
    fun `單一 Plain 段渲染後文字恰好等於原文不重複`() {
        val line = parseMarkdown("這是一段普通文字，沒有任何符號").single()
        assertEquals("這是一段普通文字，沒有任何符號", render(line).text)
    }

    @Test
    fun `粗體混排輸出正確且 bold 只出現一次`() {
        val line = parseMarkdown("abc **bold** def").single()
        val result = render(line)
        assertEquals("abc bold def", result.text)
        assertEquals(1, result.text.split("bold").size - 1)
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, boldSpans.size)
        assertEquals(4, boldSpans[0].start)
        assertEquals(8, boldSpans[0].end)
    }

    @Test
    fun `bullet 前綴與正文各出現一次`() {
        val line = parseMarkdown("- 餐飲 500 元").single()
        assertTrue(line.bullet)
        val result = render(line)
        assertEquals("•  餐飲 500 元", result.text)
        assertEquals(1, result.text.count { it == '•' })
        assertEquals(1, result.text.split("餐飲 500 元").size - 1)
    }

    @Test
    fun `有序清單編號加粗一次正文出現一次`() {
        val line = parseMarkdown("1. 先記一筆").single()
        val result = render(line)
        assertEquals("1. 先記一筆", result.text)
        assertEquals(1, result.text.split("先記一筆").size - 1)
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, boldSpans.size)
        assertEquals(0, boldSpans[0].start)
        assertEquals(3, boldSpans[0].end)
    }

    @Test
    fun `標題行正文只出現一次且套用標題樣式`() {
        val line = parseMarkdown("# 結論 ##").single()
        assertEquals(1, line.headingLevel)
        val result = render(line)
        assertEquals("結論", result.text)
        assertEquals(1, result.text.split("結論").size - 1)
        val heading = result.spanStyles.filter {
            it.item.fontWeight == FontWeight.Bold && it.item.fontSize == 20.sp
        }
        assertEquals(1, heading.size)
        assertEquals(0, heading[0].start)
        assertEquals(2, heading[0].end)
    }

    @Test
    fun `行內程式碼文字與樣式各出現一次`() {
        val line = parseMarkdown("用 `fmt` 格式化").single()
        val result = render(line)
        assertEquals("用 fmt 格式化", result.text)
        val codeSpans = result.spanStyles.filter { it.item.fontFamily == FontFamily.Monospace }
        assertEquals(1, codeSpans.size)
        assertEquals(1, codeSpans[0].start)
        assertEquals(4, codeSpans[0].end)
    }
}
