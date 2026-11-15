package com.hmoney.app.data.repository

import com.hmoney.app.data.db.dao.AccountDao
import com.hmoney.app.data.db.dao.TransactionDao
import com.hmoney.app.data.mapper.toDomain
import com.hmoney.app.data.mapper.toEntity
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 交易倉儲實作：記帳 CRUD 與防呆驗證。
 * 金額以「分」為單位（不可為負、兩位小數內），驗證集中在 MoneyTransaction 建構與此層。
 */
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
) : TransactionRepository {

    override fun observeTransactions(
        keyword: String,
        startDate: Long?,
        endDate: Long?,
        accountId: Long?,
        categoryId: Long?,
        type: TransactionType?,
    ): Flow<List<MoneyTransaction>> =
        transactionDao
            .observeFiltered(
                keyword = keyword.trim(),
                startDate = startDate,
                endDate = endDate,
                accountId = accountId,
                categoryId = categoryId,
                type = type?.name,
            )
            .map { list -> list.map { it.toDomain() } }

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
    ): List<MoneyTransaction> =
        transactionDao
            .searchTransactions(
                keyword = keyword.trim(),
                startDate = startDate,
                endDate = endDate,
                accountId = accountId,
                categoryId = categoryId,
                type = type?.name,
                minAmountCents = minAmountCents,
                maxAmountCents = maxAmountCents,
                limit = limit,
            )
            .map { it.toDomain() }

    override suspend fun getTransaction(id: Long): MoneyTransaction? =
        transactionDao.getById(id)?.toDomain()

    override fun observeTransactionsByBarcode(barcode: String): Flow<List<MoneyTransaction>> =
        transactionDao.observeByBarcode(barcode.trim()).map { list -> list.map { it.toDomain() } }

    override suspend fun addTransaction(transaction: MoneyTransaction): Long {
        validate(transaction)
        return transactionDao.insert(transaction.toEntity())
    }

    override suspend fun updateTransaction(transaction: MoneyTransaction) {
        validate(transaction)
        require(transactionDao.getById(transaction.id) != null) { "交易不存在" }
        transactionDao.update(transaction.toEntity())
    }

    override suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteById(id)
    }

    // 關鍵防呆：帳戶必須存在；支出／收入必須有分類；轉帳兩帳戶不可相同
    private suspend fun validate(transaction: MoneyTransaction) {
        require(accountDao.getById(transaction.accountId) != null) { "交易帳戶不存在" }
        when (transaction.type) {
            TransactionType.EXPENSE, TransactionType.INCOME ->
                require(transaction.categoryId != null) { "支出／收入必須選擇分類" }
            TransactionType.TRANSFER -> {
                require(transaction.toAccountId != null) { "轉帳必須指定轉入帳戶" }
                require(accountDao.getById(transaction.toAccountId) != null) { "轉入帳戶不存在" }
            }
        }
    }
}
