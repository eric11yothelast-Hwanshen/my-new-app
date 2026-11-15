package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** ConfirmDraftsUseCase（S44 草稿唯一寫入路徑）：多筆寫入與剔除後寫入 */
class ConfirmDraftsUseCaseTest {

    private fun transaction(amountCents: Long) = MoneyTransaction(
        type = TransactionType.EXPENSE,
        amountCents = amountCents,
        accountId = 3L,
        dateEpochMillis = 0L,
    )

    @Test
    fun `多筆草稿全部寫入`() = runTest {
        val repo = FakeTransactionRepository()
        val useCase = ConfirmDraftsUseCase(repo)
        val written = useCase.execute(listOf(transaction(12000L), transaction(3000L)))
        assertEquals(2, written)
        assertEquals(2, repo.added.size)
        assertEquals(12000L, repo.added[0].amountCents)
        assertEquals(3000L, repo.added[1].amountCents)
    }

    @Test
    fun `剔除後僅寫入剩餘筆數`() = runTest {
        val repo = FakeTransactionRepository()
        val useCase = ConfirmDraftsUseCase(repo)
        val written = useCase.execute(listOf(transaction(12000L)))
        assertEquals(1, written)
        assertEquals(1, repo.added.size)
    }

    @Test
    fun `單筆失敗不中斷其餘筆數`() = runTest {
        val repo = FailingTransactionRepository(failOnAmount = 12000L)
        val useCase = ConfirmDraftsUseCase(repo)
        val written = useCase.execute(listOf(transaction(12000L), transaction(3000L)))
        assertEquals(1, written)
        assertEquals(listOf(3000L), repo.added.map { it.amountCents })
    }

    private open class FakeTransactionRepository : TransactionRepository {
        val added = mutableListOf<MoneyTransaction>()
        override fun observeTransactions(
            keyword: String,
            startDate: Long?,
            endDate: Long?,
            accountId: Long?,
            categoryId: Long?,
            type: TransactionType?,
        ): Flow<List<MoneyTransaction>> = emptyFlow()
        override suspend fun getTransaction(id: Long): MoneyTransaction? = null
        override suspend fun searchTransactions(
            keyword: String,
            startDate: Long?,
            endDate: Long?,
            accountId: Long?,
            categoryId: Long?,
            type: TransactionType?,
            minAmountCents: Long?,
            maxAmountCents: Long?,
            limit: Int,
        ): List<MoneyTransaction> = emptyList()
        override fun observeTransactionsByBarcode(barcode: String): Flow<List<MoneyTransaction>> = emptyFlow()
        override suspend fun addTransaction(transaction: MoneyTransaction): Long {
            added += transaction
            return added.size.toLong()
        }
        override suspend fun updateTransaction(transaction: MoneyTransaction) = Unit
        override suspend fun deleteTransaction(id: Long) = Unit
    }

    /** 特定金額拋例外，驗證 runCatching 不中斷其餘寫入 */
    private class FailingTransactionRepository(private val failOnAmount: Long) : FakeTransactionRepository() {
        override suspend fun addTransaction(transaction: MoneyTransaction): Long {
            if (transaction.amountCents == failOnAmount) {
                throw IllegalStateException("write failed")
            }
            return super.addTransaction(transaction)
        }
    }
}
