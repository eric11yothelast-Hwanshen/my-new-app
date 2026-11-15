package com.hmoney.app.data.repository

import com.hmoney.app.domain.model.TransactionQuery
import com.hmoney.app.domain.model.ItemSuggestion
import com.hmoney.app.domain.model.TransactionType
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * AI 查詢記錄解析（S45，純函式，JVM 單元測試直接驗證）：
 * 模型回傳 JSON 物件 {"isQuery": true|false, "keyword": ..., "category": ..., "account": ...,
 * "type": "expense|income|transfer", "startDate": ..., "endDate": ...,
 * "minAmount": ..., "maxAmount": ..., "limit": N}。
 * isQuery false／欄位全空 → null（非查詢語意）；日期走 parseDraftDate（自然語言＋ISO），
 * 結束日含當天（加一天減一毫秒）。嚴禁在此層寫入 DB——查詢僅供唯讀檢索。
 */

/** 模型回應內容 → 結構化查詢；非查詢語意或無任何有效條件回 null */
internal fun parseQueryFromContent(
    content: String,
    today: LocalDate,
    zone: ZoneId,
): TransactionQuery? {
    val cleaned = content
        .trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val obj = runCatching {
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        JSONObject(cleaned.substring(start, end + 1))
    }.getOrNull() ?: return null
    if (!obj.optBoolean("isQuery", false)) return null

    fun optText(key: String): String? =
        obj.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    fun optDate(key: String, endOfDay: Boolean): Long? {
        val raw = optText(key) ?: return null
        val base = parseDraftDate(raw, today, zone) ?: return null
        return if (endOfDay) base + 86_399_999L else base
    }

    fun optCents(key: String): Long? = optText(key)?.let { parseAmountToCents(it) }

    val query = TransactionQuery(
        startDateEpochMillis = optDate("startDate", endOfDay = false),
        endDateEpochMillis = optDate("endDate", endOfDay = true),
        keyword = optText("keyword"),
        categoryName = optText("category"),
        accountName = optText("account"),
        type = when (optText("type")?.lowercase()) {
            "expense" -> TransactionType.EXPENSE
            "income" -> TransactionType.INCOME
            "transfer" -> TransactionType.TRANSFER
            else -> null
        },
        minAmountCents = optCents("minAmount"),
        maxAmountCents = optCents("maxAmount"),
        limit = obj.optInt("limit", 50).takeIf { it > 0 } ?: 50,
    )
    val hasCondition = query.startDateEpochMillis != null ||
        query.endDateEpochMillis != null ||
        !query.keyword.isNullOrBlank() ||
        query.categoryName != null ||
        query.accountName != null ||
        query.type != null ||
        query.minAmountCents != null ||
        query.maxAmountCents != null
    return query.takeIf { hasCondition }
}

/**
 * S46 AI 智慧填選：模型回應內容 → ItemSuggestion。
 * 欄位：storageLocation／expiryDate（yyyy-MM-dd）／expiryInDays（相對天數）／quantity；
 * expiryDate 與 expiryInDays 擇一（明確日期優先）；欄位缺漏或 null → 無建議。
 * 無法解析回全空的 ItemSuggestion（呼叫端以「無建議」呈現，不視為錯誤）。
 */
internal fun parseItemSuggestionFromContent(
    content: String,
    today: LocalDate,
    zone: ZoneId,
): ItemSuggestion {
    val cleaned = content
        .trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val obj = runCatching {
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return ItemSuggestion()
        JSONObject(cleaned.substring(start, end + 1))
    }.getOrNull() ?: return ItemSuggestion()

    fun optText(key: String): String? =
        obj.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    val storage = optText("storageLocation")
    val quantity = runCatching { obj.optInt("quantity", 0) }.getOrDefault(0)
        .takeIf { it > 0 }
    val explicitDate = optText("expiryDate")?.let { parseDraftDate(it, today, zone) }
    val relativeDays = runCatching { obj.optInt("expiryInDays", -1) }.getOrDefault(-1)
    val expiry = explicitDate
        ?: relativeDays.takeIf { it >= 0 }?.let { days ->
            today.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        }
    return ItemSuggestion(
        storageLocation = storage,
        expiryDateEpochMillis = expiry,
        quantity = quantity,
    )
}
