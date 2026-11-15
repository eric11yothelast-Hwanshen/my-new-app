package com.hmoney.app.data.repository

import com.hmoney.app.domain.model.TransactionActionIntent
import com.hmoney.app.domain.model.TransactionActionType
import com.hmoney.app.domain.model.TransactionQuery
import com.hmoney.app.domain.model.TransactionUpdates
import com.hmoney.app.domain.model.TransactionType
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * S48 AI 交易刪改意圖解析（純函式，JVM 單元測試直接驗證）：
 * 模型回傳 JSON 物件 {"isTransactionAction": true|false, "action": "delete|update",
 * "target": {keyword/category/account/type/startDate/endDate/minAmount/maxAmount},
 * "updates": {amount/note/category/account/date}}。
 * 非交易操作（isTransactionAction false）或 action 無法辨識 → null（續走物品操作／記帳／查詢／chat）。
 * target 欄位語意同 parseQueryFromContent（日期走 parseDraftDate、金額走 parseAmountToCents、
 * 結束日含當天）；updates 全空 → null（UPDATE 至少要有可更新欄位的意圖，否則視為無效）。
 * 嚴禁在此層寫入 DB——意圖僅供候選／確認卡流程使用。
 */
internal fun parseTransactionActionFromContent(
    content: String,
    today: LocalDate,
    zone: ZoneId,
): TransactionActionIntent? {
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
    if (!obj.optBoolean("isTransactionAction", false)) return null

    val action = when (obj.optString("action").trim().lowercase()) {
        "delete", "remove", "刪除", "移除" -> TransactionActionType.DELETE
        "update", "modify", "修改", "編輯", "改成" -> TransactionActionType.UPDATE
        else -> return null
    }

    val targetObj = obj.optJSONObject("target") ?: JSONObject()

    fun targetText(key: String): String? =
        targetObj.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    fun targetDate(key: String, endOfDay: Boolean): Long? {
        val raw = targetText(key) ?: return null
        val base = parseDraftDate(raw, today, zone) ?: return null
        return if (endOfDay) base + 86_399_999L else base
    }

    val target = TransactionQuery(
        startDateEpochMillis = targetDate("startDate", endOfDay = false),
        endDateEpochMillis = targetDate("endDate", endOfDay = true),
        keyword = targetText("keyword"),
        categoryName = targetText("category"),
        accountName = targetText("account"),
        type = when (targetText("type")?.lowercase()) {
            "expense" -> TransactionType.EXPENSE
            "income" -> TransactionType.INCOME
            "transfer" -> TransactionType.TRANSFER
            else -> null
        },
        minAmountCents = targetText("minAmount")?.let { parseAmountToCents(it) },
        maxAmountCents = targetText("maxAmount")?.let { parseAmountToCents(it) },
        limit = 5,
    )

    val updatesObj = obj.optJSONObject("updates") ?: JSONObject()

    fun updateText(key: String): String? =
        updatesObj.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    val updates = TransactionUpdates(
        amountCents = updateText("amount")?.let { parseAmountToCents(it) },
        note = updateText("note"),
        categoryName = updateText("category"),
        accountName = updateText("account"),
        dateEpochMillis = updateText("date")?.let { parseDraftDate(it, today, zone) },
    )
    val hasUpdate = updates.amountCents != null ||
        updates.note != null ||
        updates.categoryName != null ||
        updates.accountName != null ||
        updates.dateEpochMillis != null

    return TransactionActionIntent(
        action = action,
        target = target,
        updates = if (action == TransactionActionType.UPDATE && hasUpdate) updates else null,
        recent = obj.optBoolean("recent", false),
    )
}
