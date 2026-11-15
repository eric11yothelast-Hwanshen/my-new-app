package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.PurchaseSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 條碼價格趨勢純函式測試（S40）：空資料／單筆／多筆／同價重複。
 */
class PriceTrendCalculatorTest {

    private fun purchase(amountCents: Long, date: Long, note: String = "") =
        PurchaseSummary(amountCents = amountCents, dateEpochMillis = date, note = note, accountName = null)

    @Test
    fun `空資料回全空趨勢`() {
        val trend = computePriceTrend(emptyList())
        assertNull(trend.lastPriceCents)
        assertNull(trend.lowestPriceCents)
        assertNull(trend.highestPriceCents)
        assertNull(trend.averagePriceCents)
        assertEquals(0, trend.distinctPriceCount)
        assertNull(trend.firstPurchaseEpochMillis)
        assertNull(trend.latestPurchaseEpochMillis)
    }

    @Test
    fun `單筆資料所有價格統計相等`() {
        val trend = computePriceTrend(listOf(purchase(12000L, 100L)))
        assertEquals(12000L, trend.lastPriceCents)
        assertEquals(12000L, trend.lowestPriceCents)
        assertEquals(12000L, trend.highestPriceCents)
        assertEquals(12000L, trend.averagePriceCents)
        assertEquals(1, trend.distinctPriceCount)
        assertEquals(100L, trend.firstPurchaseEpochMillis)
        assertEquals(100L, trend.latestPurchaseEpochMillis)
    }

    @Test
    fun `多筆資料計算最低最高平均與首末日期`() {
        // 輸入為日期新→舊：450、300、600
        val trend = computePriceTrend(
            listOf(
                purchase(45000L, 300L),
                purchase(30000L, 200L),
                purchase(60000L, 100L),
            ),
        )
        assertEquals(45000L, trend.lastPriceCents)
        assertEquals(30000L, trend.lowestPriceCents)
        assertEquals(60000L, trend.highestPriceCents)
        assertEquals(45000L, trend.averagePriceCents)
        assertEquals(3, trend.distinctPriceCount)
        assertEquals(100L, trend.firstPurchaseEpochMillis)
        assertEquals(300L, trend.latestPurchaseEpochMillis)
    }

    @Test
    fun `平均無法整除時四捨五入`() {
        // (100 + 101) / 2 = 100.5 → 101（HALF_UP）
        val trend = computePriceTrend(
            listOf(purchase(101L, 200L), purchase(100L, 100L)),
        )
        assertEquals(101L, trend.averagePriceCents)
    }

    @Test
    fun `同價重複時 distinct 計數為 1`() {
        val trend = computePriceTrend(
            listOf(
                purchase(25000L, 300L),
                purchase(25000L, 200L),
                purchase(25000L, 100L),
            ),
        )
        assertEquals(1, trend.distinctPriceCount)
        assertEquals(25000L, trend.lastPriceCents)
        assertEquals(25000L, trend.averagePriceCents)
        assertEquals(100L, trend.firstPurchaseEpochMillis)
        assertEquals(300L, trend.latestPurchaseEpochMillis)
    }
}
