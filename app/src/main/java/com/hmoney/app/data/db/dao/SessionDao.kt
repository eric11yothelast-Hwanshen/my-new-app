package com.hmoney.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.hmoney.app.data.db.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

/** AI 對話 session DAO */
@Dao
interface SessionDao {

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun findById(id: Long): ChatSessionEntity?

    @Insert
    suspend fun insert(session: ChatSessionEntity): Long

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE chat_sessions SET updatedAtEpochMillis = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 刪除單一 session：包 @Transaction，訊息由 FK CASCADE 一併刪除 */
    @Transaction
    suspend fun deleteByIdWithMessages(id: Long) {
        deleteById(id)
    }

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAll()

    /** 清空所有對話：兩表刪除包在同一 @Transaction，避免半刪狀態 */
    @Transaction
    suspend fun clearAllTables() {
        deleteAllMessages()
        deleteAll()
    }

    /** 一次性清理：刪除零訊息的空 session（延遲建立前產生的髒資料） */
    @Query(
        "DELETE FROM chat_sessions WHERE id NOT IN " +
            "(SELECT DISTINCT sessionId FROM chat_messages)",
    )
    suspend fun deleteEmptySessions(): Int

    /** 自動清除：刪除 updatedAt 早於 cutoff 的 session（訊息因 FK CASCADE 一併刪除） */
    @Query("DELETE FROM chat_sessions WHERE updatedAtEpochMillis < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
