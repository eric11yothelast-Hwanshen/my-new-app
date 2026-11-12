package com.hmoney.app.data.repository

import com.hmoney.app.data.db.dao.ChatMessageDao
import com.hmoney.app.data.db.dao.SessionDao
import com.hmoney.app.data.db.entity.ChatMessageEntity
import com.hmoney.app.data.db.entity.ChatSessionEntity
import com.hmoney.app.domain.model.ChatMessage
import com.hmoney.app.domain.model.ChatSession
import com.hmoney.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 對話倉儲實作：Room 為單一資料來源——各頁 AiAssistantSheet 觀察同一份 flow，
 * 天然跨頁同步。新增訊息後自動修剪單一 session 超量的最舊訊息。
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val chatMessageDao: ChatMessageDao,
) : ChatRepository {

    override fun observeSessions(): Flow<List<ChatSession>> =
        sessionDao.observeAll().mapSessions()

    override fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
        chatMessageDao.observeBySession(sessionId).mapMessages()

    override suspend fun getSession(id: Long): ChatSession? =
        sessionDao.findById(id)?.toDomain()

    override suspend fun createSession(nowEpochMillis: Long): Long =
        sessionDao.insert(
            ChatSessionEntity(
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            ),
        )

    override suspend fun setSessionTitle(id: Long, title: String) {
        sessionDao.updateTitle(id, title)
    }

    override suspend fun addMessage(
        sessionId: Long,
        isUser: Boolean,
        text: String,
        nowEpochMillis: Long,
    ): Long {
        val id = chatMessageDao.insert(
            ChatMessageEntity(
                sessionId = sessionId,
                role = if (isUser) ROLE_USER else ROLE_AI,
                text = text,
                createdAtEpochMillis = nowEpochMillis,
            ),
        )
        sessionDao.touch(sessionId, nowEpochMillis)
        chatMessageDao.trimToLatest(sessionId, ChatRepository.MAX_MESSAGES_PER_SESSION)
        return id
    }

    override suspend fun deleteMessage(id: Long) {
        chatMessageDao.deleteById(id)
    }

    override suspend fun deleteSession(id: Long) {
        sessionDao.deleteByIdWithMessages(id)
    }

    override suspend fun clearAll() {
        sessionDao.clearAllTables()
    }

    override suspend fun deleteEmptySessions(): Int =
        sessionDao.deleteEmptySessions()

    override suspend fun deleteSessionsOlderThan(cutoffEpochMillis: Long): Int =
        sessionDao.deleteOlderThan(cutoffEpochMillis)

    private fun Flow<List<ChatSessionEntity>>.mapSessions(): Flow<List<ChatSession>> =
        map { list -> list.map { it.toDomain() } }

    private fun Flow<List<ChatMessageEntity>>.mapMessages(): Flow<List<ChatMessage>> =
        map { list -> list.map { it.toDomain() } }

    private fun ChatSessionEntity.toDomain(): ChatSession = ChatSession(
        id = id,
        title = title,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
        id = id,
        sessionId = sessionId,
        isUser = role == ROLE_USER,
        text = text,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    private companion object {
        const val ROLE_USER = "USER"
        const val ROLE_AI = "AI"
    }
}
