package com.hmoney.app.data.repository

import com.hmoney.app.data.db.dao.AccountDao
import com.hmoney.app.data.db.dao.TransactionDao
import com.hmoney.app.data.db.entity.AccountEntity
import com.hmoney.app.data.db.entity.TransactionEntity
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S45 AI 查詢記錄：fake DAO 的 searchTransactions 行為驗證（純 JVM）——
 * 關鍵字命中 note 與 tags、欄位過濾（類型／分類／帳戶／日期／金額）、筆數上限、日期新→舊。
 * 全程唯讀：fake DAO 不提供任何寫入方法供本測試使用。
 */
class TransactionSearchTest {

    private lateinit var dao: FakeSearchTransactionDao
    private lateinit var repository: TransactionRepository

    private val day = 86_400_000L
    private val base = 1_800_000_000_000L

    private fun entity(
        id: Long,
        note: String,
        tags: String = "",
        type: String = "EXPENSE",
        amountCents: Long = 100_000L,
        categoryId: Long? = 1L,
        accountId: Long = 3L,
        dateEpochMillis: Long = base,
    ): TransactionEntity = TransactionEntity(
        id = id,
        type = type,
        amountCents = amountCents,
        accountId = accountId,
        categoryId = categoryId,
        dateEpochMillis = dateEpochMillis,
        note = note,
        tags = tags,
    )

    @Before
    fun setUp() {
        dao = FakeSearchTransactionDao()
        repository = TransactionRepositoryImpl(dao, FakeSearchAccountDao())
    }

    @Test
    fun `關鍵字命中 note 與 tags`() = runTest {
        dao.seed(
            listOf(
                entity(id = 1, note = "AOC 螢幕"),
                entity(id = 2, note = "午餐", tags = "螢幕,3C"),
                entity(id = 3, note = "晚餐"),
            ),
        )
        val byNote = repository.searchTransactions(keyword = "AOC")
        assertEquals(listOf(1L), byNote.map { it.id })
        val byTags = repository.searchTransactions(keyword = "螢幕")
        assertEquals(listOf(2L, 1L), byTags.map { it.id })
    }

    @Test
    fun `欄位過濾（類型、分類、帳戶、日期、金額）`() = runTest {
        dao.seed(
            listOf(
                entity(id = 1, note = "支出甲", type = "EXPENSE", amountCents = 50_000L, dateEpochMillis = base),
                entity(id = 2, note = "收入乙", type = "INCOME", amountCents = 50_000L, dateEpochMillis = base),
                entity(id = 3, note = "支出丙", type = "EXPENSE", amountCents = 150_000L, categoryId = 9L, dateEpochMillis = base + day),
                entity(id = 4, note = "支出丁", type = "EXPENSE", amountCents = 200_000L, accountId = 7L, dateEpochMillis = base + 2 * day),
            ),
        )
        assertEquals(
            listOf(4L, 1L),
            repository.searchTransactions(type = TransactionType.EXPENSE, categoryId = 1L).map { it.id },
        )
        assertEquals(
            listOf(2L),
            repository.searchTransactions(type = TransactionType.INCOME).map { it.id },
        )
        assertEquals(
            listOf(3L),
            repository.searchTransactions(type = TransactionType.EXPENSE, minAmountCents = 100_000L, accountId = 3L).map { it.id },
        )
        assertEquals(
            listOf(4L),
            repository.searchTransactions(accountId = 7L).map { it.id },
        )
        assertEquals(
            listOf(3L),
            repository.searchTransactions(startDate = base + 1, endDate = base + day).map { it.id },
        )
    }

    @Test
    fun `筆數上限與日期新→舊排序`() = runTest {
        dao.seed(
            (1..10).map { i ->
                entity(id = i.toLong(), note = "交易$i", dateEpochMillis = base + i * day)
            },
        )
        val results = repository.searchTransactions(limit = 3)
        assertEquals(3, results.size)
        assertEquals(listOf(10L, 9L, 8L), results.map { it.id })
        assertTrue(results.zipWithNext().all { (a, b) -> a.dateEpochMillis >= b.dateEpochMillis })
    }
}

/** 記憶體版 TransactionDao：僅實作 searchTransactions 搜尋行為（模擬 SQL 的 LIKE 與過濾） */
private class FakeSearchTransactionDao : TransactionDao {
    private val rows = mutableListOf<TransactionEntity>()

    fun seed(entities: List<TransactionEntity>) {
        rows += entities
    }

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
    ): List<TransactionEntity> = rows
        .asSequence()
        .filter { row ->
            keyword.isEmpty() || row.note.contains(keyword) || row.tags.contains(keyword)
        }
        .filter { row -> startDate == null || row.dateEpochMillis >= startDate }
        .filter { row -> endDate == null || row.dateEpochMillis <= endDate }
        .filter { row -> accountId == null || row.accountId == accountId || row.toAccountId == accountId }
        .filter { row -> categoryId == null || row.categoryId == categoryId }
        .filter { row -> type == null || row.type == type }
        .filter { row -> minAmountCents == null || row.amountCents >= minAmountCents }
        .filter { row -> maxAmountCents == null || row.amountCents <= maxAmountCents }
        .sortedWith(compareByDescending<TransactionEntity> { it.dateEpochMillis }.thenByDescending { it.id })
        .take(limit)
        .toList()

    override fun observeFiltered(
        keyword: String,
        startDate: Long?,
        endDate: Long?,
        accountId: Long?,
        categoryId: Long?,
        type: String?,
    ): Flow<List<TransactionEntity>> = MutableStateFlow(emptyList())

    override fun observeByBarcode(barcode: String): Flow<List<TransactionEntity>> =
        MutableStateFlow(emptyList())

    override fun observePurchaseHistory(barcode: String): Flow<List<com.hmoney.app.data.db.dao.PurchaseHistoryRow>> =
        MutableStateFlow(emptyList())

    override suspend fun getById(id: Long): TransactionEntity? = null
    override suspend fun countByAccount(accountId: Long): Int = 0
    override suspend fun insert(transaction: TransactionEntity): Long = 0L
    override suspend fun update(transaction: TransactionEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun insertAll(transactions: List<TransactionEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private class FakeSearchAccountDao : AccountDao {
    override fun observeVisible(): Flow<List<AccountEntity>> = MutableStateFlow(emptyList())
    override fun observeAll(): Flow<List<AccountEntity>> = MutableStateFlow(emptyList())
    override suspend fun getById(id: Long): AccountEntity? = null
    override suspend fun insert(account: AccountEntity): Long = 0L
    override suspend fun update(account: AccountEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun insertAll(accounts: List<AccountEntity>) = Unit
    override suspend fun deleteAll() = Unit
}
