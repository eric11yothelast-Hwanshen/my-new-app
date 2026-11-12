package com.hmoney.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hmoney.app.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/** AI 對話訊息 DAO */
@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAtEpochMillis ASC, id ASC")
    fun observeBySession(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()

    /** 保護：單一 session 只保留最新 keep 則，防止無限增長 */
    @Query(
        "DELETE FROM chat_messages WHERE sessionId = :sessionId AND id NOT IN " +
            "(SELECT id FROM chat_messages WHERE sessionId = :sessionId " +
            "ORDER BY createdAtEpochMillis DESC, id DESC LIMIT :keep)",
    )
    suspend fun trimToLatest(sessionId: Long, keep: Int)
}
