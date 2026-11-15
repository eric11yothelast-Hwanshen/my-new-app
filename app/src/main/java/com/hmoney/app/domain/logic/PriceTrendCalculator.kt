package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.PriceTrend
import com.hmoney.app.domain.model.PurchaseSummary
import java.math.RoundingMode

/**
 * 價格趨勢純函式（S40，請勿誤改：趨勢計算必須保持純函式、可單元測試，
 * 不得改成 SQL 彙總或混入任何 DB 寫入）。
 * 輸入為同條碼的歷史交易摘要（日期新→舊），輸出統計趨勢。
 */
fun computePriceTrend(summaries: List<PurchaseSummary>): PriceTrend {
    if (summaries.isEmpty()) return PriceTrend()
    val prices = summaries.map { it.amountCents }
    val dates = summaries.map { it.dateEpochMillis }
    return PriceTrend(
        lastPriceCents = prices.first(),
        lowestPriceCents = prices.min(),
        highestPriceCents = prices.max(),
        averagePriceCents = prices.sum().toBigDecimal()
            .divide(prices.size.toBigDecimal(), 0, RoundingMode.HALF_UP)
            .toLong(),
        distinctPriceCount = prices.distinct().size,
        firstPurchaseEpochMillis = dates.min(),
        latestPurchaseEpochMillis = dates.max(),
    )
}
