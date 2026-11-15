package com.hmoney.app.domain.model

/** AI 對話 session（title 為空代表尚未命名，UI 顯示「新對話」） */
data class ChatSession(
    val id: Long,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/** AI 對話訊息 */
data class ChatMessage(
    val id: Long,
    val sessionId: Long,
    val isUser: Boolean,
    val text: String,
    val createdAtEpochMillis: Long,
)

/** session 標題推導：取第一個非空行並截斷（可單元測試的純邏輯） */
object ChatTitle {

    const val MAX_LENGTH = 20

    fun from(text: String): String {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return firstLine.trim().take(MAX_LENGTH)
    }
}
