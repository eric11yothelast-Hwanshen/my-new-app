package com.hmoney.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAll()

    /** 自動清除：刪除 updatedAt 早於 cutoff 的 session（訊息因 FK CASCADE 一併刪除） */
    @Query("DELETE FROM chat_sessions WHERE updatedAtEpochMillis < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
