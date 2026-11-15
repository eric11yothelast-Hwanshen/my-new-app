package com.hmoney.app.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/** 預算判斷測試：80% 轉警示、100% 轉紅 */
class BudgetEvaluatorTest {

    private val evaluator = BudgetEvaluator()

    @Test
    fun `未設定預算時為一般等級`() {
        val progress = evaluator.evaluate(budgetCents = 0, spentCents = 500_00)
        assertEquals(BudgetEvaluator.Level.NORMAL, progress.level)
        assertEquals(0f, progress.ratio, 0.001f)
    }

    @Test
    fun `低於八成為一般等級`() {
        val progress = evaluator.evaluate(budgetCents = 1000_00, spentCents = 500_00)
        assertEquals(BudgetEvaluator.Level.NORMAL, progress.level)
        assertEquals(0.5f, progress.ratio, 0.001f)
    }

    @Test
    fun `達八成轉警示等級`() {
        val progress = evaluator.evaluate(budgetCents = 1000_00, spentCents = 800_00)
        assertEquals(BudgetEvaluator.Level.WARNING, progress.level)
    }

    @Test
    fun `超過預算轉超支等級`() {
        val progress = evaluator.evaluate(budgetCents = 1000_00, spentCents = 1200_00)
        assertEquals(BudgetEvaluator.Level.OVER, progress.level)
        assertEquals(1.2f, progress.ratio, 0.001f)
    }

    @Test
    fun `剛好一百趴轉超支等級`() {
        val progress = evaluator.evaluate(budgetCents = 1000_00, spentCents = 1000_00)
        assertEquals(BudgetEvaluator.Level.OVER, progress.level)
    }
}
