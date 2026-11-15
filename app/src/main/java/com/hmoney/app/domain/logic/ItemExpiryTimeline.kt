package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Item
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * S48 物品到期時間軸（純函式，JVM 單元測試直接驗證）：
 * 未來 horizonDays 天（含今天）內「有物品到期」的日子→當日到期數量；
 * 無到期日的物品不計；未來 horizonDays 內沒有任何到期物品時回空清單（UI 據此隱藏時間軸）。
 * 日期以 expiryDateEpochMillis 換算 local date（00:00 邊界一致於 groupItemsByExpiry）。
 */
data class ExpiryTimelineDay(
    val date: LocalDate,
    val count: Int,
)

fun expiryTimelineCounts(
    items: List<Item>,
    today: LocalDate,
    zone: ZoneId,
    horizonDays: Long = 30,
): List<ExpiryTimelineDay> {
    val endExclusive = today.plusDays(horizonDays)
    val counts = LinkedHashMap<LocalDate, Int>()
    items.forEach { item ->
        val expiry = item.expiryDateEpochMillis ?: return@forEach
        val date = Instant.ofEpochMilli(expiry).atZone(zone).toLocalDate()
        if (date in today..endExclusive.minusDays(1)) {
            counts[date] = (counts[date] ?: 0) + 1
        }
    }
    return counts.keys.sorted().map { ExpiryTimelineDay(it, counts.getValue(it)) }
}

/**
 * 時間軸點位 → 對應分節類型（捲動定位用）：與 groupItemsByExpiry 同一邊界——
 * 今天～soonDays 內（含）→ SOON；其餘未來 → OTHER。
 */
fun sectionTypeForTimelineDate(
    date: LocalDate,
    today: LocalDate,
    soonDays: Long = 7,
): ItemExpirySectionType =
    if (!date.isBefore(today) && !date.isAfter(today.plusDays(soonDays))) {
        ItemExpirySectionType.SOON
    } else {
        ItemExpirySectionType.OTHER
    }
