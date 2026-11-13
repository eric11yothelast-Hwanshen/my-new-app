package com.hmoney.app.data.repository

import com.hmoney.app.domain.model.TransactionDraft
import com.hmoney.app.domain.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * AI 記帳草稿解析（Session 39，純函式，JVM 單元測試直接驗證）：
 * - 金額字串轉 cents（支援「1,200」「120.5」「NT$300」「450元」等格式）
 * - 自然語言／ISO 日期轉 epoch（一律以傳入時區換算，無法解析回 null）
 * - 模型回傳的 JSON 陣列 → TransactionDraft 清單；單筆欄位缺失不讓整批失敗
 * - vision 訊息 content 陣列組裝（text + image_url data URI）
 * 嚴禁在此層寫入 DB——草稿僅供確認流程使用。
 */

/**
 * 草稿路徑專用的 content 抽取（Session 39）：沿用 extractAiContent 的三形態解析
 * （純字串／區塊清單／物件），但不做 sanitizeAiText 防禦性清理——
 * 草稿 JSON 陣列的元素以 {"type":"expense"...} 開頭，會被 aiJsonFragmentRegex
 * 誤判成思考區塊而清空，故草稿嚴禁走 extractAiContent（既有 chat 行為不動）。
 */
internal fun extractDraftContent(raw: String): String? {
    val content = rawAiContent(raw) ?: return null
    return when (content) {
        is String -> content.trim().takeIf { it.isNotEmpty() }
        is JSONArray -> {
            val merged = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") != "text") continue
                merged.append(block.optString("text"))
            }
            merged.toString().trim().takeIf { it.isNotEmpty() }
        }
        is JSONObject -> content.optString("text").trim().takeIf { it.isNotEmpty() }
        else -> null
    }
}

/** 金額字串 → 分（容錯：幣別符號、千分位、全形逗號；負數取絕對值；無法解析回 null） */
internal fun parseAmountToCents(raw: String): Long? {
    val cleaned = raw
        .replace("NT$", "")
        .replace("nt$", "")
        .replace("$", "")
        .replace("￥", "")
        .replace("¥", "")
        .replace("元", "")
        .replace(",", "")
        .replace("，", "")
        .replace(" ", "")
        .trim()
    if (cleaned.isEmpty()) return null
    val amount = cleaned.toBigDecimalOrNull() ?: return null
    val cents = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    return if (cents < 0) -cents else cents
}

/** 自然語言／ISO 日期 → 當天 00:00 的 epoch millis；無法解析回 null */
internal fun parseDraftDate(raw: String?, today: LocalDate, zone: ZoneId): Long? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty() || text.equals("null", ignoreCase = true)) return null
    val date = when {
        text.contains("大前天") -> today.minusDays(3)
        text.contains("前天") -> today.minusDays(2)
        text.contains("昨天") -> today.minusDays(1)
        text.contains("今天") || text.contains("今日") -> today
        text.contains("上週五") || text.contains("上周五") ->
            today.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY))
        else -> parseIsoLikeDate(text)
    } ?: return null
    return date.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** ISO 類日期：yyyy-MM-dd 或 yyyy/M/d */
private fun parseIsoLikeDate(text: String): LocalDate? {
    listOf("yyyy-MM-dd", "yyyy/M/d", "yyyy-MM-dd HH:mm", "yyyy/MM/dd").forEach { pattern ->
        runCatching {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern))
        }
    }
    return null
}

/**
 * 模型回傳內容 → 草稿清單。
 * 容錯：剝除 markdown 圍籬、擷取首尾括號間的 JSON 陣列；
 * 單筆欄位缺失／格式錯誤時該筆以預設值照樣產出，不讓整批失敗；
 * 整份內容完全無法解析為 JSON 陣列時回 null（呼叫端轉 AiError.Parse）。
 */
internal fun parseDraftsFromContent(
    content: String,
    today: LocalDate,
    zone: ZoneId,
): List<TransactionDraft>? {
    val cleaned = content
        .trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val array = runCatching { JSONArray(cleaned) }.getOrElse {
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        runCatching { JSONArray(cleaned.substring(start, end + 1)) }.getOrNull() ?: return null
    }
    val drafts = mutableListOf<TransactionDraft>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        drafts += draftFromJson(obj, today, zone)
    }
    return drafts
}

private fun draftFromJson(obj: JSONObject, today: LocalDate, zone: ZoneId): TransactionDraft {
    val type = if (obj.optString("type").equals("income", ignoreCase = true)) {
        TransactionType.INCOME
    } else {
        TransactionType.EXPENSE
    }
    return TransactionDraft(
        type = type,
        amountCents = parseAmountToCents(obj.optString("amount")) ?: 0L,
        note = obj.optString("note"),
        categoryName = obj.optString("category").takeIf { it.isNotBlank() && it != "null" },
        accountName = obj.optString("account").takeIf { it.isNotBlank() && it != "null" },
        dateEpochMillis = parseDraftDate(obj.optString("date"), today, zone),
        tags = runCatching {
            val arr = obj.getJSONArray("tags")
            (0 until arr.length()).mapNotNull { arr.opt(it)?.toString() }
        }.getOrDefault(emptyList()),
        rawText = obj.toString(),
    )
}

/**
 * vision 訊息的 user content 陣列（OpenAI 相容形狀，Session 39 請勿誤改）：
 * content = [ {type:"text", text:...}, {type:"image_url", image_url:{url:"data:<mime>;base64,<b64>"}} ]
 * 改形狀前先查目標 API 文件。
 */
internal fun buildVisionUserContent(text: String, imageBase64: String, mimeType: String): JSONArray =
    JSONArray()
        .put(
            JSONObject()
                .put("type", "text")
                .put("text", text),
        )
        .put(
            JSONObject()
                .put("type", "image_url")
                .put(
                    "image_url",
                    JSONObject().put("url", "data:$mimeType;base64,$imageBase64"),
                ),
        )
