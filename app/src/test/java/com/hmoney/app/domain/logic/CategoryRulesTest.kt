package com.hmoney.app.domain.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分類管理規則測試：fallback 保護、刪除／隱藏條件 */
class CategoryRulesTest {

    @Test
    fun `其他與其他收入為保護分類`() {
        assertTrue(CategoryRules.isProtectedFallback("其他"))
        assertTrue(CategoryRules.isProtectedFallback("其他收入"))
        assertFalse(CategoryRules.isProtectedFallback("餐飲"))
        assertFalse(CategoryRules.isProtectedFallback("自訂分類"))
    }

    @Test
    fun `保護分類不可隱藏不可刪除`() {
        assertFalse(CategoryRules.canHide("其他"))
        assertFalse(CategoryRules.canHide("其他收入"))
        assertFalse(CategoryRules.canDelete("其他", inUse = false))
        assertFalse(CategoryRules.canDelete("其他收入", inUse = false))
    }

    @Test
    fun `有關聯的分類只能隱藏不可刪除`() {
        assertTrue(CategoryRules.canHide("餐飲"))
        assertFalse(CategoryRules.canDelete("餐飲", inUse = true))
    }

    @Test
    fun `無關聯的一般分類可隱藏可刪除`() {
        assertTrue(CategoryRules.canHide("餐飲"))
        assertTrue(CategoryRules.canDelete("餐飲", inUse = false))
    }
}
