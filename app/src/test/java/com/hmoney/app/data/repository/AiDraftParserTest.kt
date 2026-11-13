package com.hmoney.app.data.repository

import com.hmoney.app.domain.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * AI 記帳草稿解析測試（Session 39，純 JVM）：
 * 文字解析（單筆／多筆／金額格式／日期解析／欄位缺失）、vision payload 組裝、
 * extractAiContent 區塊清單相容。服務層僅產草稿、無任何 DB 寫入。
 */
class AiDraftParserTest {

    // 固定基準日與時區，讓日期斷言不受執行環境影響（2026-11-13 為星期五）
    private val today = LocalDate.of(2026, 11, 13)
    private val zone = ZoneId.of("Asia/Taipei")

    private fun epochOf(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    // ===== 金額 =====

    @Test
    fun `金額支援千分位與小數`() {
        assertEquals(120000L, parseAmountToCents("1,200"))
        assertEquals(12050L, parseAmountToCents("120.5"))
        assertEquals(12000L, parseAmountToCents("120"))
    }

    @Test
    fun `金額支援幣別符號與中文元`() {
        assertEquals(30000L, parseAmountToCents("NT$300"))
        assertEquals(45000L, parseAmountToCents("450元"))
        assertEquals(9900L, parseAmountToCents("$99"))
    }

    @Test
    fun `金額負數取絕對值且無法解析回 null`() {
        assertEquals(25000L, parseAmountToCents("-250"))
        assertNull(parseAmountToCents("abc"))
        assertNull(parseAmountToCents(""))
    }

    // ===== 日期 =====

    @Test
    fun `自然語言日期解析今天昨天前天`() {
        assertEquals(epochOf(today), parseDraftDate("今天", today, zone))
        assertEquals(epochOf(today.minusDays(1)), parseDraftDate("昨天", today, zone))
        assertEquals(epochOf(today.minusDays(2)), parseDraftDate("前天", today, zone))
        assertEquals(epochOf(today.minusDays(3)), parseDraftDate("大前天", today, zone))
    }

    @Test
    fun `上週五解析為前一個週五`() {
        assertEquals(
            epochOf(today.minusWeeks(1)),
            parseDraftDate("上週五", today, zone),
        )
    }

    @Test
    fun `ISO 日期與斜線日期可解析且一律以系統時區換算`() {
        assertEquals(epochOf(LocalDate.of(2026, 11, 8)), parseDraftDate("2026-11-08", today, zone))
        assertEquals(epochOf(LocalDate.of(2026, 11, 8)), parseDraftDate("2026/11/8", today, zone))
    }

    @Test
    fun `無法解析的日期回 null`() {
        assertNull(parseDraftDate("某天", today, zone))
        assertNull(parseDraftDate(null, today, zone))
        assertNull(parseDraftDate("null", today, zone))
    }

    // ===== 草稿陣列 =====

    @Test
    fun `單筆草稿欄位完整解析`() {
        val content =
            """[{"type":"expense","amount":"120","note":"午餐","category":"餐飲","account":"現金","date":"昨天","tags":["外食"]}]"""
        val drafts = parseDraftsFromContent(content, today, zone)!!
        assertEquals(1, drafts.size)
        val draft = drafts.first()
        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals(12000L, draft.amountCents)
        assertEquals("午餐", draft.note)
        assertEquals("餐飲", draft.categoryName)
        assertEquals("現金", draft.accountName)
        assertEquals(epochOf(today.minusDays(1)), draft.dateEpochMillis)
        assertEquals(listOf("外食"), draft.tags)
        assertTrue(draft.rawText.contains("午餐"))
    }

    @Test
    fun `多筆草稿一次解析且 income 類型正確`() {
        val content =
            """[{"type":"expense","amount":"120","note":"昨天午餐 120","date":"昨天"},
               {"type":"income","amount":"50000","note":"薪水","date":"今天"}]"""
        val drafts = parseDraftsFromContent(content, today, zone)!!
        assertEquals(2, drafts.size)
        assertEquals(TransactionType.EXPENSE, drafts[0].type)
        assertEquals(TransactionType.INCOME, drafts[1].type)
        assertEquals(5_000_000L, drafts[1].amountCents)
    }

    @Test
    fun `單筆欄位缺失不讓整批失敗且缺失欄位為 null`() {
        val content = """[{"note":"只備註"},{"amount":"abc","type":"expense"}]"""
        val drafts = parseDraftsFromContent(content, today, zone)!!
        assertEquals(2, drafts.size)
        assertNull(drafts[0].categoryName)
        assertNull(drafts[0].dateEpochMillis)
        assertEquals(0L, drafts[0].amountCents)
        assertEquals(0L, drafts[1].amountCents)
    }

    @Test
    fun `markdown 圍籬與前後綴文字可容錯`() {
        val content = "好的，以下是結果：\n```json\n[{\"type\":\"expense\",\"amount\":\"30\",\"note\":\"捷運\"}]\n```"
        val drafts = parseDraftsFromContent(content, today, zone)!!
        assertEquals(1, drafts.size)
        assertEquals(3000L, drafts.first().amountCents)
    }

    @Test
    fun `完全無法解析為 JSON 陣列時回 null`() {
        assertNull(parseDraftsFromContent("抱歉，我無法解析這段描述。", today, zone))
        assertNull(parseDraftsFromContent("", today, zone))
    }

    // ===== vision payload =====

    @Test
    fun `vision content 陣列形狀正確且 data URI 格式符合 OpenAI 相容規格`() {
        val content = buildVisionUserContent("請解析這張發票", "QUJD", "image/jpeg")
        assertEquals(2, content.length())
        val textBlock = content.getJSONObject(0)
        assertEquals("text", textBlock.getString("type"))
        assertEquals("請解析這張發票", textBlock.getString("text"))
        val imageBlock = content.getJSONObject(1)
        assertEquals("image_url", imageBlock.getString("type"))
        assertEquals(
            "data:image/jpeg;base64,QUJD",
            imageBlock.getJSONObject("image_url").getString("url"),
        )
    }

    // ===== extractAiContent 相容 =====

    @Test
    fun `content 為區塊清單時草稿路徑可抽出 JSON 並解析`() {
        val draftJson =
            """[{"type":"expense","amount":"99","note":"咖啡","category":"餐飲","date":"今天"}]"""
        val raw = JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject().put(
                        "message",
                        JSONObject().put(
                            "role",
                            "assistant",
                        ).put(
                            "content",
                            JSONArray()
                                .put(JSONObject().put("type", "thinking").put("thinking", "內部思考"))
                                .put(JSONObject().put("type", "text").put("text", draftJson)),
                        ),
                    ),
                ),
            )
            .toString()
        val content = extractDraftContent(raw)!!
        assertEquals(draftJson, content)
        val drafts = parseDraftsFromContent(content, today, zone)!!
        assertEquals(1, drafts.size)
        assertEquals(9900L, drafts.first().amountCents)
        assertEquals(epochOf(today), drafts.first().dateEpochMillis)
    }

    @Test
    fun `純字串 content 草稿路徑原樣保留不清理`() {
        // 草稿 JSON 以 {"type":"expense"...} 開頭，chat 用的 extractAiContent
        // 會把它誤判成思考區塊而清空——此為 Session 39 關鍵決策，抽取出來作迴歸測試
        val draftJson =
            """[{"type":"expense","amount":"99","note":"咖啡"}]"""
        val raw = JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject().put(
                        "message",
                        JSONObject().put("role", "assistant")
                            .put("content", draftJson),
                    ),
                ),
            )
            .toString()
        assertEquals(draftJson, extractDraftContent(raw))
    }
}
