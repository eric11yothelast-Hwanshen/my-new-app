package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

/** 餘額計算核心路徑測試：期初＋收入−支出±轉帳，手續費計為轉出帳戶支出 */
class BalanceCalculatorTest {

    private val calculator = BalanceCalculator()

    private fun account(id: Long, initial: Long = 0L, hidden: Boolean = false) =
        Account(id = id, name = "帳戶$id", iconKey = "payments", colorArgb = 0L, initialBalanceCents = initial, isHidden = hidden)

    private fun tx(
        type: TransactionType,
        amount: Long,
        accountId: Long,
        toAccountId: Long? = null,
        fee: Long = 0L,
    ) = MoneyTransaction(
        type = type,
        amountCents = amount,
        accountId = accountId,
        toAccountId = toAccountId,
        feeCents = fee,
        dateEpochMillis = 0L,
    )

    @Test
    fun `期初餘額為零且無交易時餘額為零`() {
        val balances = calculator.computeBalances(listOf(account(1)), emptyList())
        assertEquals(0L, balances[1])
    }

    @Test
    fun `收入增加支出減少餘額`() {
        val balances = calculator.computeBalances(
            accounts = listOf(account(1, initial = 1000_00)),
            transactions = listOf(
                tx(TransactionType.INCOME, 500_00, accountId = 1),
                tx(TransactionType.EXPENSE, 200_00, accountId = 1),
            ),
        )
        assertEquals(1300_00L, balances[1])
    }

    @Test
    fun `轉帳從轉出扣款並入帳轉入`() {
        val balances = calculator.computeBalances(
            accounts = listOf(account(1, initial = 1000_00), account(2, initial = 0L)),
            transactions = listOf(tx(TransactionType.TRANSFER, 300_00, accountId = 1, toAccountId = 2)),
        )
        assertEquals(700_00L, balances[1])
        assertEquals(300_00L, balances[2])
    }

    @Test
    fun `轉帳手續費由轉出帳戶吸收`() {
        val balances = calculator.computeBalances(
            accounts = listOf(account(1, initial = 1000_00), account(2, initial = 0L)),
            transactions = listOf(
                tx(TransactionType.TRANSFER, 300_00, accountId = 1, toAccountId = 2, fee = 15_00),
            ),
        )
        assertEquals(685_00L, balances[1])
        assertEquals(300_00L, balances[2])
    }

    @Test
    fun `總餘額僅計可見帳戶`() {
        val total = calculator.computeTotalBalance(
            accounts = listOf(account(1, initial = 500_00), account(2, initial = 999_00, hidden = true)),
            transactions = emptyList(),
        )
        assertEquals(500_00L, total)
    }
}
