package com.hmoney.app.domain.model

/** 條碼歷史交易摘要（唯讀查詢結果，S40）：金額／日期／備註／帳戶名 */ 
data class PurchaseSummary(
    val amountCents: Long,
    val dateEpochMillis: Long,
    val note: String,
    val accountName: String?,
)

/**
 * 同條碼價格趨勢（S40）：由 domain 層純函式計算（見 PriceTrendCalculator），
 * 空資料時所有欄位為 null／0，UI 端據此顯示「無購買紀錄」。
 */
data class PriceTrend(
    val lastPriceCents: Long? = null,
    val lowestPriceCents: Long? = null,
    val highestPriceCents: Long? = null,
    val averagePriceCents: Long? = null,
    val distinctPriceCount: Int = 0,
    val firstPurchaseEpochMillis: Long? = null,
    val latestPurchaseEpochMillis: Long? = null,
)
