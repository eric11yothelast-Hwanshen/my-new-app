package com.hmoney.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** session 標題推導測試：AI 回覆首行截 20 字 */
class ChatTitleTest {

    @Test
    fun `多行回覆取第一個非空行`() {
        val reply = "\n  \n本週餐飲支出明顯偏高，建議控制外食頻率。\n第二行不應出現"
        assertEquals("本週餐飲支出明顯偏高，建議控制外食頻率。", ChatTitle.from(reply))
    }

    @Test
    fun `超過 20 字截斷為 20 字`() {
        val reply = "這是一段非常長的 AI 回覆內容，遠遠超過二十個字的上限，必須截斷"
        assertEquals(reply.take(20), ChatTitle.from(reply))
    }

    @Test
    fun `空白內容回傳空字串代表未命名`() {
        assertEquals("", ChatTitle.from(""))
        assertEquals("", ChatTitle.from("   \n  "))
    }
}
