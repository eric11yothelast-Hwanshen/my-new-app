package com.hmoney.app.domain.model

/** 預算類型：月總預算／分類預算 */
enum class BudgetType {
    TOTAL,
    CATEGORY,
}

/** 預算：金額以「分」為單位，套用於每個月 */
data class Budget(
    val id: Long = 0L,
    val type: BudgetType,
    val categoryId: Long? = null,
    val amountCents: Long,
) {
    init {
        require(amountCents >= 0L) { "預算不可為負" }
    }
}
