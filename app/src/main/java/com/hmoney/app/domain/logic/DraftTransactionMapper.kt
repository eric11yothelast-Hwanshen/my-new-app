package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionDraft
import com.hmoney.app.domain.model.TransactionSource
import com.hmoney.app.domain.model.TransactionType

/**
 * 草稿 → 交易映射（S41 純函式，寫入唯一入口＝草稿預覽的「確認」按鈕）：
 * - 分類：先依名稱比對（不分大小小寫、去空白），對不上時 fallback「其他」（支出）／「其他收入」（收入），
 *   再對不上取該類型第一個分類——「其他」分類不可刪隱藏（既有鐵律），因此通常存在。
 * - 帳戶：名稱比對，對不上取第一個帳戶。
 * - 日期：null 時用 nowEpochMillis。
 * - source／barcode／receiptImagePath 由呼叫端依入口帶入（打字→ai_text／拍照→ai_image／掃碼→barcode）。
 */
fun resolveDraftToTransaction(
    draft: TransactionDraft,
    source: TransactionSource,
    categories: List<Category>,
    accounts: List<Account>,
    nowEpochMillis: Long,
    barcode: String? = null,
    receiptImagePath: String? = null,
): MoneyTransaction {
    val wantedType = when (draft.type) {
        TransactionType.INCOME -> CategoryType.INCOME
        else -> CategoryType.EXPENSE
    }
    val typedCategories = categories.filter { it.type == wantedType }
    val categoryId = draft.categoryName
        ?.let { name -> typedCategories.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id }
        ?: typedCategories.firstOrNull { category ->
            category.name == if (wantedType == CategoryType.EXPENSE) "其他" else "其他收入"
        }?.id
        ?: typedCategories.firstOrNull()?.id
    val accountId = draft.accountName
        ?.let { name -> accounts.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.id }
        ?: accounts.firstOrNull()?.id
    return MoneyTransaction(
        type = draft.type,
        amountCents = draft.amountCents,
        accountId = requireNotNull(accountId) { "沒有任何帳戶可寫入" },
        categoryId = categoryId,
        dateEpochMillis = draft.dateEpochMillis ?: nowEpochMillis,
        note = draft.note,
        tags = draft.tags,
        source = source,
        barcode = barcode,
        receiptImagePath = receiptImagePath,
    )
}
