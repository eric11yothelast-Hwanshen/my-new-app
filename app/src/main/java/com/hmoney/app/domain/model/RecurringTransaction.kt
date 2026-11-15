package com.hmoney.app.domain.model

/** 週期頻率：每週／每月 */
enum class RecurringFrequency {
    WEEKLY,
    MONTHLY,
}

/**
 * 週期交易：固定支出／收入（房租、訂閱、薪水），
 * 到期由排程自動產生交易記錄。
 */
data class RecurringTransaction(
    val id: Long = 0L,
    val type: TransactionType,
    val amountCents: Long,
    val accountId: Long,
    val categoryId: Long,
    val note: String = "",
    val frequency: RecurringFrequency,
    val dayOfMonth: Int? = null,
    val dayOfWeek: Int? = null,
    val nextDueEpochMillis: Long,
    val isEnabled: Boolean = true,
) {
    init {
        // 關鍵防呆：金額必須為正、分類必填
        require(amountCents > 0L) { "週期交易金額必須大於零" }
        require(type != TransactionType.TRANSFER) { "週期交易僅支援支出／收入" }
        if (frequency == RecurringFrequency.WEEKLY) {
            require(dayOfWeek in 1..7) { "每週週期需指定星期（1–7）" }
        }
        if (frequency == RecurringFrequency.MONTHLY) {
            require(dayOfMonth in 1..31) { "每月週期需指定日期（1–31）" }
        }
    }
}
