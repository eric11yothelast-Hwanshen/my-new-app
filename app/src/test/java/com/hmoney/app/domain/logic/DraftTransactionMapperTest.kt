package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.TransactionDraft
import com.hmoney.app.domain.model.TransactionSource
import com.hmoney.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 草稿→交易映射測試（S41）：source／barcode／receiptImagePath 欄位正確、
 * 分類名稱比對與「其他」fallback、帳戶 fallback、日期 fallback。
 */
class DraftTransactionMapperTest {

    private val categories = listOf(
        Category(id = 1, name = "餐飲", type = CategoryType.EXPENSE, iconKey = "restaurant", colorArgb = 0L),
        Category(id = 7, name = "其他", type = CategoryType.EXPENSE, iconKey = "more_horiz", colorArgb = 0L),
        Category(id = 8, name = "其他收入", type = CategoryType.INCOME, iconKey = "add_circle", colorArgb = 0L),
    )
    private val accounts = listOf(Account(id = 3, name = "現金", iconKey = "payments", colorArgb = 0L))

    @Test
    fun `映射帶入 source 與 barcode 欄位`() {
        val tx = resolveDraftToTransaction(
            draft = TransactionDraft(type = TransactionType.EXPENSE, amountCents = 12000L, note = "午餐"),
            source = TransactionSource.BARCODE,
            categories = categories,
            accounts = accounts,
            nowEpochMillis = 1000L,
            barcode = "4711234567890",
        )
        assertEquals(TransactionSource.BARCODE, tx.source)
        assertEquals("4711234567890", tx.barcode)
        assertNull(tx.receiptImagePath)
        assertEquals(1000L, tx.dateEpochMillis)
    }

    @Test
    fun `拍照來源帶 receiptImagePath`() {
        val tx = resolveDraftToTransaction(
            draft = TransactionDraft(type = TransactionType.EXPENSE, amountCents = 9900L),
            source = TransactionSource.AI_IMAGE,
            categories = categories,
            accounts = accounts,
            nowEpochMillis = 1000L,
            receiptImagePath = "/receipts/r1.jpg",
        )
        assertEquals(TransactionSource.AI_IMAGE, tx.source)
        assertEquals("/receipts/r1.jpg", tx.receiptImagePath)
    }

    @Test
    fun `分類名稱比對成功`() {
        val tx = resolveDraftToTransaction(
            draft = TransactionDraft(
                type = TransactionType.EXPENSE,
                amountCents = 100L,
                categoryName = "餐飲",
            ),
            source = TransactionSource.AI_TEXT,
            categories = categories,
            accounts = accounts,
            nowEpochMillis = 1000L,
        )
        assertEquals(1L, tx.categoryId)
    }

    @Test
    fun `分類對不上時 fallback 其他`() {
        val tx = resolveDraftToTransaction(
            draft = TransactionDraft(
                type = TransactionType.EXPENSE,
                amountCents = 100L,
                categoryName = "不存在的分類",
            ),
            source = TransactionSource.AI_TEXT,
            categories = categories,
            accounts = accounts,
            nowEpochMillis = 1000L,
        )
        assertEquals(7L, tx.categoryId)
    }

    @Test
    fun `收入對不上時 fallback 其他收入`() {
        val tx = resolveDraftToTransaction(
            draft = TransactionDraft(type = TransactionType.INCOME, amountCents = 500000L),
            source = TransactionSource.AI_TEXT,
            categories = categories,
            accounts = accounts,
            nowEpochMillis = 1000L,
        )
        assertEquals(8L, tx.categoryId)
    }

    @Test
    fun `帳戶名稱比對失敗時 fallback 第一個帳戶`() {
        val tx = resolveDraftToTransaction(
            draft = TransactionDraft(
                type = TransactionType.EXPENSE,
                amountCents = 100L,
                accountName = "不存在帳戶",
            ),
            source = TransactionSource.AI_TEXT,
            categories = categories,
            accounts = accounts,
            nowEpochMillis = 1000L,
        )
        assertEquals(3L, tx.accountId)
    }
}
