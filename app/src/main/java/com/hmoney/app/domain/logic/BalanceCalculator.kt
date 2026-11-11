package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import javax.inject.Inject

/**
 * 餘額計算（核心純邏輯，可單元測試）：
 * 帳戶餘額 = 期初餘額 + 收入 − 支出 ± 轉帳，轉帳手續費計為轉出帳戶的支出。
 */
class BalanceCalculator @Inject constructor() {

    fun computeBalances(
        accounts: List<Account>,
        transactions: List<MoneyTransaction>,
    ): Map<Long, Long> {
        val balances = accounts.associate { it.id to it.initialBalanceCents }.toMutableMap()

        fun adjust(accountId: Long, delta: Long) {
            balances[accountId] = (balances[accountId] ?: 0L) + delta
        }

        transactions.forEach { transaction ->
            when (transaction.type) {
                TransactionType.EXPENSE -> adjust(transaction.accountId, -transaction.amountCents)
                TransactionType.INCOME -> adjust(transaction.accountId, transaction.amountCents)
                TransactionType.TRANSFER -> {
                    // 轉出扣款、轉入入帳，手續費由轉出帳戶吸收（計為支出）
                    adjust(transaction.accountId, -transaction.amountCents - transaction.feeCents)
                    transaction.toAccountId?.let { toId -> adjust(toId, transaction.amountCents) }
                }
            }
        }
        return balances
    }

    /** 全部（可見）帳戶的總餘額 */
    fun computeTotalBalance(
        accounts: List<Account>,
        transactions: List<MoneyTransaction>,
    ): Long {
        val balances = computeBalances(accounts, transactions)
        return accounts.filter { !it.isHidden }.sumOf { balances[it.id] ?: 0L }
    }
}
