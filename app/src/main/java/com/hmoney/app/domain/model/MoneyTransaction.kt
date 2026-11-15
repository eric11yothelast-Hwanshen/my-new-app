package com.hmoney.app.domain.model

/** 交易三類型：支出、收入、轉帳（轉帳不計入收支統計） */
enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
}

/**
 * 交易記錄：金額不可為負（以「分」為單位），日期時間可修改（允許補記過去），
 * 備註與標籤選填，標籤可多個。
 */
data class MoneyTransaction(
    val id: Long = 0L,
    val type: TransactionType,
    val amountCents: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val feeCents: Long = 0L,
    val dateEpochMillis: Long,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val source: TransactionSource = TransactionSource.MANUAL,
    val barcode: String? = null,
    val receiptImagePath: String? = null,
) {
    init {
        // 關鍵防呆：金額與手續費不可為負
        require(amountCents >= 0L) { "金額不可為負" }
        require(feeCents >= 0L) { "手續費不可為負" }
        if (type == TransactionType.TRANSFER) {
            require(toAccountId != null) { "轉帳必須指定轉入帳戶" }
            require(toAccountId != accountId) { "轉出與轉入帳戶不可相同" }
        }
    }
}
