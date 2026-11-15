package com.hmoney.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 金額輸入解析測試：不可為負、最多兩位小數 */
class AmountParserTest {

    @Test
    fun `整數金額`() {
        assertEquals(123_00L, AmountParser.toCents("123"))
    }

    @Test
    fun `兩位小數`() {
        assertEquals(123_45L, AmountParser.toCents("123.45"))
    }

    @Test
    fun `一位小數`() {
        assertEquals(120_50L, AmountParser.toCents("120.5"))
    }

    @Test
    fun `負數無效`() {
        assertNull(AmountParser.toCents("-50"))
    }

    @Test
    fun `超過兩位小數無效`() {
        assertNull(AmountParser.toCents("1.234"))
    }

    @Test
    fun `空白無效`() {
        assertNull(AmountParser.toCents(""))
        assertNull(AmountParser.toCents("  "))
    }

    @Test
    fun `非數字無效`() {
        assertNull(AmountParser.toCents("abc"))
    }
}
