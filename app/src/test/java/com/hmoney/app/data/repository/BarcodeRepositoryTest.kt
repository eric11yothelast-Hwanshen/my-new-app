package com.hmoney.app.data.repository

import com.hmoney.app.data.db.dao.PurchaseHistoryRow
import com.hmoney.app.data.db.dao.TransactionDao
import com.hmoney.app.data.db.entity.TransactionEntity
import com.hmoney.app.domain.model.PriceTrend
import com.hmoney.app.domain.model.PurchaseSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 條碼 Repository 測試（S40，JVM 慣例：fake DAO）：
 * 唯讀查詢列 → 摘要對應與趨勢組裝；SQL 正確性由 Room KSP 編譯期驗證（沙箱無 Android SDK，
 * 不引入 Robolectric——與既有測試慣例一致）。
 */
class BarcodeRepositoryTest {

    private val history = MutableStateFlow<List<PurchaseHistoryRow>>(emptyList())

    private val fakeDao = object : TransactionDao {
        override fun observeFiltered(
            keyword: String,
            startDate: Long?,
            endDate: Long?,
            accountId: Long?,
            categoryId: Long?,
            type: String?,
        ): Flow<List<TransactionEntity>> = throw UnsupportedOperationException()

        override fun observeByBarcode(barcode: String): Flow<List<TransactionEntity>> =
            throw UnsupportedOperationException()

        override fun observePurchaseHistory(barcode: String): Flow<List<PurchaseHistoryRow>> = history

        override suspend fun searchTransactions(
            keyword: String,
            startDate: Long?,
            endDate: Long?,
            accountId: Long?,
            categoryId: Long?,
            type: String?,
            minAmountCents: Long?,
            maxAmountCents: Long?,
            limit: Int,
        ): List<TransactionEntity> = throw UnsupportedOperationException()

        override suspend fun getById(id: Long): TransactionEntity? = throw UnsupportedOperationException()

        override suspend fun countByAccount(accountId: Long): Int = throw UnsupportedOperationException()

        override suspend fun insert(transaction: TransactionEntity): Long =
            throw UnsupportedOperationException()

        override suspend fun update(transaction: TransactionEntity) =
            throw UnsupportedOperationException()

        override suspend fun deleteById(id: Long) = throw UnsupportedOperationException()

        override suspend fun insertAll(transactions: List<TransactionEntity>) =
            throw UnsupportedOperationException()

        override suspend fun deleteAll() = throw UnsupportedOperationException()
    }

    private val repository = BarcodeRepositoryImpl(fakeDao)

    @Test
    fun `歷史交易列對應為摘要且保留帳戶名`() = runTest {
        history.value = listOf(
            PurchaseHistoryRow(45000L, 300L, "鮮奶", "現金"),
            PurchaseHistoryRow(42000L, 200L, "鮮奶（特價）", null),
        )
        val summaries = repository.getPurchaseHistory("4711234567890").first()
        assertEquals(
            listOf(
                PurchaseSummary(45000L, 300L, "鮮奶", "現金"),
                PurchaseSummary(42000L, 200L, "鮮奶（特價）", null),
            ),
            summaries,
        )
    }

    @Test
    fun `趨勢由歷史摘要組裝`() = runTest {
        history.value = listOf(
            PurchaseHistoryRow(45000L, 300L, "", null),
            PurchaseHistoryRow(30000L, 100L, "", null),
        )
        val trend = repository.getPriceTrend("4711234567890").first()
        assertEquals(45000L, trend.lastPriceCents)
        assertEquals(30000L, trend.lowestPriceCents)
        assertEquals(45000L, trend.highestPriceCents)
        assertEquals(37500L, trend.averagePriceCents)
        assertEquals(2, trend.distinctPriceCount)
        assertEquals(100L, trend.firstPurchaseEpochMillis)
        assertEquals(300L, trend.latestPurchaseEpochMillis)
    }

    @Test
    fun `無歷史時趨勢為空`() = runTest {
        history.value = emptyList()
        val trend = repository.getPriceTrend("0000000000000").first()
        assertEquals(PriceTrend(), trend)
        assertNull(trend.lastPriceCents)
    }
}
