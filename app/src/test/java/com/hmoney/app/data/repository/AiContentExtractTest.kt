package com.hmoney.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AI 回應解析測試（Session 31）：
 * choices[0].message.content 支援三種形態——純字串／內容區塊陣列／內容區塊物件，
 * 且 thinking 區塊與內部思考文字絕不出現在輸出。
 */
class AiContentExtractTest {

    private fun rawResponse(content: String) =
        """{"choices":[{"message":{"role":"assistant","content":$content}}]}"""

    @Test
    fun `content 為純字串直接回傳`() {
        val raw = rawResponse("\"本週餐飲支出明顯偏高。\"")
        assertEquals("本週餐飲支出明顯偏高。", extractAiContent(raw))
    }

    @Test
    fun `content 為陣列只串接 text 區塊並丟棄 thinking`() {
        val raw = rawResponse(
            """[{"type":"thinking","thinking":"內部思考不該出現"},{"type":"text","text":"正文第一段。"},{"type":"text","text":"正文第二段。"}]""",
        )
        val result = extractAiContent(raw)!!
        assertEquals("正文第一段。正文第二段。", result)
        assertFalse(result.contains("內部思考"))
        assertFalse(result.contains("{\"type\":"))
    }

    @Test
    fun `content 為物件只取 text 欄位並丟棄 thinking`() {
        val raw = rawResponse("""{"text":"只有正文。","thinking":"內部思考不該出現"}""")
        val result = extractAiContent(raw)!!
        assertEquals("只有正文。", result)
        assertFalse(result.contains("內部思考"))
        assertFalse(result.contains("thinking"))
    }

    @Test
    fun `純字串殘留 JSON 片段時抽取 text 丟棄 thinking`() {
        val raw = rawResponse(
            "\"好的。{\\\"type\\\":\\\"text\\\",\\\"text\\\":\\\"補充說明\\\"}請確認支出。\"",
        )
        assertEquals("好的。補充說明請確認支出。", extractAiContent(raw))
    }

    @Test
    fun `雙重編碼的內容區塊陣列字串也能抽出正文`() {
        val raw = rawResponse(
            "\"[{\\\"type\\\":\\\"thinking\\\",\\\"thinking\\\":\\\"內部思考\\\"},{\\\"type\\\":\\\"text\\\",\\\"text\\\":\\\"正文\\\"}]\"",
        )
        val result = extractAiContent(raw)!!
        assertEquals("正文", result)
        assertFalse(result.contains("內部思考"))
        assertFalse(result.contains("{\"type\":"))
    }

    @Test
    fun `content 陣列含重疊 text 區塊時去重保留最長`() {
        val raw = rawResponse(
            """[{"type":"thinking","thinking":"內部思考"},{"type":"text","text":"我這個月總支出"},{"type":"text","text":"我這個月總支出 1,000 元。"}]""",
        )
        assertEquals("我這個月總支出 1,000 元。", extractAiContent(raw))
    }

    @Test
    fun `content 陣列兩個完全相同的 text 區塊只出現一次`() {
        val raw = rawResponse(
            """[{"type":"text","text":"建議控制外食頻率。"},{"type":"text","text":"建議控制外食頻率。"}]""",
        )
        assertEquals("建議控制外食頻率。", extractAiContent(raw))
    }

    @Test
    fun `reasoning_effort none 的純字串回應行為不變`() {
        val raw = rawResponse("\"reasoning_effort=none 時 content 就是純字串。\"")
        assertEquals("reasoning_effort=none 時 content 就是純字串。", extractAiContent(raw))
    }

    @Test
    fun `單一 text 區塊內整段重複兩次摺疊為一份`() {
        val body = "本週餐飲支出明顯偏高，建議檢視每日飲食消費習慣。"
        val raw = rawResponse(
            """[{"type":"text","text":"$body\n\n$body"}]""",
        )
        assertEquals(body, extractAiContent(raw))
    }

    @Test
    fun `兩個完全相同的長 text 區塊仍只出現一份`() {
        val body = "建議控制外食頻率並設定每月餐飲預算上限。"
        val raw = rawResponse(
            """[{"type":"text","text":"$body"},{"type":"text","text":"$body"}]""",
        )
        assertEquals(body, extractAiContent(raw))
    }

    @Test
    fun `列點內容含重複開頭但語意不同的行不可被誤刪`() {
        val body = "以下三點建議：\n- 建議減少外食次數以降低餐飲支出\n- 建議設定每月娛樂預算上限避免超支\n- 建議定期檢視訂閱服務取消未使用者"
        val raw = rawResponse(
            """[{"type":"text","text":"$body"}]""".replace("\n", "\\n"),
        )
        assertEquals(body, extractAiContent(raw))
    }

    @Test
    fun `純字串 content 帶段落級重複正常摺疊`() {
        val body = "本月支出較上月增加一成，主因為餐飲與娛樂類別。"
        val raw = rawResponse("\"$body\\n\\n$body\"")
        assertEquals(body, extractAiContent(raw))
    }

    @Test
    fun `只有 thinking 區塊時回 null 交由上層顯示解析錯誤`() {
        val raw = rawResponse("""[{"type":"thinking","thinking":"內部思考"}]""")
        assertNull(extractAiContent(raw))
    }

    @Test
    fun `無法解析的原始回應回 null`() {
        assertNull(extractAiContent("not json"))
    }
}
