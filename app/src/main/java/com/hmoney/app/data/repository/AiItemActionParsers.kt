package com.hmoney.app.data.repository

import com.hmoney.app.domain.model.ItemActionIntent
import com.hmoney.app.domain.model.ItemActionType
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * S47 AI 物品操作意圖解析（純函式，JVM 單元測試直接驗證）：
 * 模型回傳 JSON 物件 {"isItemAction": true|false, "action": "create|update|delete", "name": ...,
 * "storageLocation": ..., "expiryDate": "yyyy-MM-dd", "expiryInDays": N, "quantity": N}。
 * 非物品操作（isItemAction false）、action 無法辨識或 name 缺失 → null（續走查詢／一般 chat）。
 * 日期解析模式沿用 suggestItemFields：expiryDate（yyyy-MM-dd）優先、expiryInDays（相對天數，今日為基準）擇一。
 * 嚴禁在此層寫入 DB——意圖僅供確認卡流程使用。
 */
internal fun parseItemActionFromContent(
    content: String,
    today: LocalDate,
    zone: ZoneId,
): ItemActionIntent? {
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
    if (!obj.optBoolean("isItemAction", false)) return null

    val action = when (obj.optString("action").trim().lowercase()) {
        "create", "add", "新增" -> ItemActionType.CREATE
        "update", "modify", "更新" -> ItemActionType.UPDATE
        "delete", "remove", "刪除" -> ItemActionType.DELETE
        else -> return null
    }
    val name = obj.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" } ?: return null

    fun optText(key: String): String? =
        obj.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    val storage = optText("storageLocation")
    val quantity = runCatching { obj.optInt("quantity", 0) }.getOrDefault(0).takeIf { it > 0 }
    val explicitDate = optText("expiryDate")?.let { parseDraftDate(it, today, zone) }
    val relativeDays = runCatching { obj.optInt("expiryInDays", -1) }.getOrDefault(-1)
    val expiry = explicitDate
        ?: relativeDays.takeIf { it >= 0 }?.let { days ->
            today.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        }
    // S49 相對現有到期日的增減天數（可為負）；僅在無絕對日期時採用，由 VM 於名稱匹配後換算
    val deltaDays = runCatching { obj.optInt("expiryDeltaDays", 0) }.getOrDefault(0)
        .takeIf { it != 0 }
    return ItemActionIntent(
        action = action,
        name = name,
        storageLocation = storage,
        quantity = quantity,
        expiryDateEpochMillis = expiry,
        expiryDeltaDays = if (expiry == null) deltaDays else null,
    )
}
