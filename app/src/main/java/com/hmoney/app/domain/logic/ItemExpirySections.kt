package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Item
import java.time.LocalDate
import java.time.ZoneId

/** 物品到期分節（S46）：已過期 → 7 天內到期（含當天）→ 其他 */
enum class ItemExpirySectionType {
    EXPIRED,
    SOON,
    OTHER,
}

/** 單一分節：類型＋物品清單（各節內以到期日升冪排序，無到期日者排最後） */
data class ItemSection(
    val type: ItemExpirySectionType,
    val items: List<Item>,
)

/**
 * 到期分節純函式（S46，JVM 單元測試直接驗證）：
 * - 已過期：到期日 < 今天 00:00
 * - 7 天內到期：今天 00:00 ≦ 到期日 ≦ 今天＋7 天 23:59（含當天）
 * - 其他：到期日晚於 7 天，或無到期日（無到期日排最後）
 * 各節依到期日升冪（越快到期越前面）；節本身依 EXPIRED → SOON → OTHER 順序輸出，空節不輸出。
 */
fun groupItemsByExpiry(
    items: List<Item>,
    today: LocalDate,
    zone: ZoneId,
    soonDays: Long = 7,
): List<ItemSection> {
    val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfSoon = today.plusDays(soonDays)
        .atTime(23, 59, 59, 999_999_999)
        .atZone(zone).toInstant().toEpochMilli()

    fun expiryOf(item: Item): Long? = item.expiryDateEpochMillis

    val expired = items.filter { expiryOf(it) != null && expiryOf(it)!! < startOfDay }
        .sortedBy { expiryOf(it)!! }
    val soon = items.filter { expiryOf(it) != null && expiryOf(it)!! in startOfDay..endOfSoon }
        .sortedBy { expiryOf(it)!! }
    val other = items.filter { expiryOf(it) == null || expiryOf(it)!! > endOfSoon }
        .sortedWith(compareBy(nullsLast()) { expiryOf(it) })

    return buildList {
        if (expired.isNotEmpty()) add(ItemSection(ItemExpirySectionType.EXPIRED, expired))
        if (soon.isNotEmpty()) add(ItemSection(ItemExpirySectionType.SOON, soon))
        if (other.isNotEmpty()) add(ItemSection(ItemExpirySectionType.OTHER, other))
    }
}
