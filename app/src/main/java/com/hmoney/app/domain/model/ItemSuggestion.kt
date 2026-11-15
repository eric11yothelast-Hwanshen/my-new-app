package com.hmoney.app.domain.model

/**
 * AI 智慧填選建議（S46，純資料類）：依物品名稱（與條碼）建議存放位置／有效日期／數量；
 * 欄位皆可空＝無建議。僅供「建議預覽 → 逐欄採用」，不得自動覆寫使用者輸入。
 */
data class ItemSuggestion(
    val storageLocation: String? = null,
    val expiryDateEpochMillis: Long? = null,
    val quantity: Int? = null,
)
