package com.hmoney.app.domain.logic

/**
 * 分類管理規則（純邏輯，可單元測試）：
 * - 「其他」（支出）與「其他收入」（收入）為 fallback 分類，不可隱藏、不可刪除；
 *   以種子名稱比對——若使用者改名，保護即失效（與記帳分類以名稱對應的行為一致）。
 * - 有交易／預算／週期交易關聯的分類一律只能隱藏（isHidden），不得實刪。
 */
object CategoryRules {
    const val FALLBACK_EXPENSE_NAME = "其他"
    const val FALLBACK_INCOME_NAME = "其他收入"

    fun isProtectedFallback(name: String): Boolean =
        name == FALLBACK_EXPENSE_NAME || name == FALLBACK_INCOME_NAME

    /** 可否刪除：保護分類不可刪；有交易／預算／週期交易關聯不可刪 */
    fun canDelete(name: String, inUse: Boolean): Boolean =
        !isProtectedFallback(name) && !inUse

    /** 可否隱藏：保護分類不可隱藏 */
    fun canHide(name: String): Boolean = !isProtectedFallback(name)
}
