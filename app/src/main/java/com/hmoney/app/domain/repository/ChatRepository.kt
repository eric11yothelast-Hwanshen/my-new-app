package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.ChatMessage
import com.hmoney.app.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

/** AI 對話倉儲介面：session 與訊息的持久化（跨頁同步的單一資料來源） */
interface ChatRepository {

    fun observeSessions(): Flow<List<ChatSession>>

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>>

    suspend fun getSession(id: Long): ChatSession?

    suspend fun createSession(nowEpochMillis: Long): Long

    suspend fun setSessionTitle(id: Long, title: String)

    suspend fun addMessage(
        sessionId: Long,
        isUser: Boolean,
        text: String,
        nowEpochMillis: Long,
    ): Long

    suspend fun deleteMessage(id: Long)

    suspend fun deleteSession(id: Long)

    suspend fun clearAll()

    /** 一次性清理：刪除零訊息的空 session，回傳刪除數量 */
    suspend fun deleteEmptySessions(): Int

    /** 自動清除：刪除 updatedAt 早於 cutoff 的 session，回傳刪除數量 */
    suspend fun deleteSessionsOlderThan(cutoffEpochMillis: Long): Int

    companion object {
        /** 單一 session 最多保留訊息數（防止無限增長） */
        const val MAX_MESSAGES_PER_SESSION = 200
    }
}
