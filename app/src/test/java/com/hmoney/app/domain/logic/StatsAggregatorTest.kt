package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 統計彙總測試：分類占比、趨勢分桶、排行榜前期增減 */
class StatsAggregatorTest {

    private val aggregator = StatsAggregator()
    private val zone = ZoneId.of("Asia/Taipei")
    private val today = LocalDate.of(2026, 11, 11)

    private val food = Category(id = 1, name = "餐飲", type = CategoryType.EXPENSE, iconKey = "restaurant", colorArgb = 0xFFEF5350)
    private val traffic = Category(id = 2, name = "交通", type = CategoryType.EXPENSE, iconKey = "directions_bus", colorArgb = 0xFF5C6BC0)

    private fun tx(
        type: TransactionType,
        amount: Long,
        categoryId: Long? = null,
        date: LocalDate,
    ) = MoneyTransaction(
        type = type,
        amountCents = amount,
        accountId = 1,
        categoryId = categoryId,
        dateEpochMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun `月彙總計算收支合計與分類占比`() {
        val transactions = listOf(
            tx(TransactionType.EXPENSE, 300_00, categoryId = 1, date = LocalDate.of(2026, 11, 3)),
            tx(TransactionType.EXPENSE, 100_00, categoryId = 2, date = LocalDate.of(2026, 11, 5)),
            tx(TransactionType.INCOME, 5000_00, categoryId = 1, date = LocalDate.of(2026, 11, 1)),
            // 上月與本週期外的不計入
            tx(TransactionType.EXPENSE, 999_00, categoryId = 1, date = LocalDate.of(2026, 10, 20)),
        )
        val result = aggregator.aggregate(
            transactions = transactions,
            categories = listOf(food, traffic),
            period = StatsAggregator.Period.MONTH,
            today = today,
            zone = zone,
        )
        assertEquals(400_00L, result.totalExpenseCents)
        assertEquals(5000_00L, result.totalIncomeCents)
        assertEquals(2, result.categorySlices.size)
        // 餐飲占 75%、交通占 25%
        assertEquals(0.75f, result.categorySlices.first { it.category.id == 1 }.ratio, 0.001f)
        assertEquals(0.25f, result.categorySlices.first { it.category.id == 2 }.ratio, 0.001f)
    }

    @Test
    fun `排行榜含前期增減`() {
        val transactions = listOf(
            // 本月
            tx(TransactionType.EXPENSE, 300_00, categoryId = 1, date = LocalDate.of(2026, 11, 3)),
            // 上月（前期）
            tx(TransactionType.EXPENSE, 100_00, categoryId = 1, date = LocalDate.of(2026, 10, 3)),
        )
        val result = aggregator.aggregate(
            transactions = transactions,
            categories = listOf(food, traffic),
            period = StatsAggregator.Period.MONTH,
            today = today,
            zone = zone,
        )
        val entry = result.ranking.first()
        assertEquals(300_00L, entry.cents)
        assertEquals(100_00L, entry.previousCents)
    }

    @Test
    fun `趨勢分桶依日彙總`() {
        val transactions = listOf(
            tx(TransactionType.EXPENSE, 100_00, categoryId = 1, date = LocalDate.of(2026, 11, 3)),
            tx(TransactionType.EXPENSE, 50_00, categoryId = 1, date = LocalDate.of(2026, 11, 3)),
            tx(TransactionType.INCOME, 700_00, categoryId = 1, date = LocalDate.of(2026, 11, 4)),
        )
        val result = aggregator.aggregate(
            transactions = transactions,
            categories = listOf(food, traffic),
            period = StatsAggregator.Period.MONTH,
            today = today,
            zone = zone,
        )
        assertEquals(30, result.trend.size)
        assertEquals(150_00L, result.trend[2].expenseCents)
        assertEquals(0L, result.trend[2].incomeCents)
        assertEquals(700_00L, result.trend[3].incomeCents)
    }

    @Test
    fun `年彙總計算十二個月分桶`() {
        val result = aggregator.aggregate(
            transactions = emptyList(),
            categories = listOf(food, traffic),
            period = StatsAggregator.Period.YEAR,
            today = today,
            zone = zone,
        )
        assertEquals(12, result.trend.size)
        assertEquals(0L, result.totalExpenseCents)
    }
}
