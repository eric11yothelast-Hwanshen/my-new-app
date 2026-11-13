package com.hmoney.app.domain.model

/**
 * 記帳來源標記（1.3.0）：manual = 手動輸入、ai_text = 自然語言、
 * ai_image = 圖片記帳、barcode = 條碼記帳。語意值不得未經規劃擴充。
 */
enum class TransactionSource {
    MANUAL,
    AI_TEXT,
    AI_IMAGE,
    BARCODE,
}

/**
 * 物品管理記錄（1.3.0）：quantity 不可為負；
 * purchaseTransactionId 連動購買交易，交易刪除後此值歸 NULL。
 */
data class Item(
    val id: Long = 0L,
    val name: String,
    val barcode: String? = null,
    val storageLocation: String? = null,
    val quantity: Int = 1,
    val expiryDateEpochMillis: Long? = null,
    val purchaseTransactionId: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(quantity >= 0) { "數量不可為負" }
        require(name.isNotBlank()) { "物品名稱不可空白" }
    }
}
