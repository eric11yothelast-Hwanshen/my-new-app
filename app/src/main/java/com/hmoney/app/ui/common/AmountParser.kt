package com.hmoney.app.ui.common

/**
 * 金額輸入解析：不可為負、最多兩位小數，回傳「分」；格式錯誤回傳 null。
 */
object AmountParser {

    private val pattern = Regex("""\d+(\.\d{1,2})?""")

    /** "123.45" → 12345（分）；空字串或格式錯誤回傳 null */
    fun toCents(text: String): Long? {
        val trimmed = text.trim()
        if (!pattern.matches(trimmed)) return null
        val wholePart = trimmed.substringBefore('.')
        val decimalPart = trimmed.substringAfter('.', "").padEnd(2, '0')
        return (wholePart.toLong() * 100) + decimalPart.toLong()
    }
}
