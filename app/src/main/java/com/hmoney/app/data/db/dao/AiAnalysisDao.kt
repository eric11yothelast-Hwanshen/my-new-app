package com.hmoney.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hmoney.app.data.db.entity.AiAnalysisEntity
import kotlinx.coroutines.flow.Flow

/** AI 分析歷史 DAO */
@Dao
interface AiAnalysisDao {

    @Query("SELECT * FROM ai_analysis ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<AiAnalysisEntity>>

    @Insert
    suspend fun insert(analysis: AiAnalysisEntity): Long

    @Query("DELETE FROM ai_analysis WHERE id = :id")
    suspend fun deleteById(id: Long)
}
