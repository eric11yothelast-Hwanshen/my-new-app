package com.hmoney.app.domain.model

/**
 * AI 查詢記錄的結構化查詢條件（S45，純資料類）：欄位皆可空＝不篩選；
 * keyword 對備註／標籤做模糊比對；limit 為結果筆數上限（預設 50）。
 */
data class TransactionQuery(
    val startDateEpochMillis: Long? = null,
    val endDateEpochMillis: Long? = null,
    val keyword: String? = null,
    val categoryName: String? = null,
    val accountName: String? = null,
    val type: TransactionType? = null,
    val minAmountCents: Long? = null,
    val maxAmountCents: Long? = null,
    val limit: Int = 50,
)
