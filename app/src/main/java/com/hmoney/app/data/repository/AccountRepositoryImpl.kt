package com.hmoney.app.data.repository

import com.hmoney.app.data.db.dao.AccountDao
import com.hmoney.app.data.db.dao.TransactionDao
import com.hmoney.app.data.mapper.toDomain
import com.hmoney.app.data.mapper.toEntity
import com.hmoney.app.domain.logic.BalanceCalculator
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 帳戶倉儲實作：
 * 即時餘額 = 期初餘額 + 收入 − 支出 ± 轉帳（手續費計為轉出帳戶支出），
 * 由 BalanceCalculator 統一計算。
 */
@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val balanceCalculator: BalanceCalculator,
) : AccountRepository {

    override fun observeVisibleWithBalance(): Flow<List<Pair<Account, Long>>> =
        combine(
            accountDao.observeVisible(),
            transactionDao.observeFiltered(),
        ) { accounts, transactions ->
            val balances = balanceCalculator.computeBalances(
                accounts = accounts.map { it.toDomain() },
                transactions = transactions.map { it.toDomain() },
            )
            accounts.map { it.toDomain() to (balances[it.id] ?: 0L) }
        }

    override fun observeAllWithBalance(): Flow<List<Pair<Account, Long>>> =
        combine(
            accountDao.observeAll(),
            transactionDao.observeFiltered(),
        ) { accounts, transactions ->
            val balances = balanceCalculator.computeBalances(
                accounts = accounts.map { it.toDomain() },
                transactions = transactions.map { it.toDomain() },
            )
            accounts.map { it.toDomain() to (balances[it.id] ?: 0L) }
        }

    override fun observeAll(): Flow<List<Account>> =
        accountDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAccount(id: Long): Account? = accountDao.getById(id)?.toDomain()

    override suspend fun addAccount(account: Account): Long {
        require(account.name.isNotBlank()) { "帳戶名稱不可空白" }
        return accountDao.insert(account.toEntity())
    }

    override suspend fun updateAccount(account: Account) {
        require(account.name.isNotBlank()) { "帳戶名稱不可空白" }
        require(accountDao.getById(account.id) != null) { "帳戶不存在" }
        accountDao.update(account.toEntity())
    }

    override suspend fun hideAccount(id: Long) {
        val entity = accountDao.getById(id) ?: return
        accountDao.update(entity.copy(isHidden = true))
    }

    override suspend fun deleteAccount(id: Long): Boolean {
        // 防呆：有交易記錄的帳戶僅能隱藏，不得實刪，避免資料孤立
        if (transactionDao.countByAccount(id) > 0) return false
        accountDao.deleteById(id)
        return true
    }

    override fun observeTotalBalance(): Flow<Long> =
        combine(
            accountDao.observeAll(),
            transactionDao.observeFiltered(),
        ) { accounts, transactions ->
            balanceCalculator.computeTotalBalance(
                accounts = accounts.map { it.toDomain() },
                transactions = transactions.map { it.toDomain() },
            )
        }
}
