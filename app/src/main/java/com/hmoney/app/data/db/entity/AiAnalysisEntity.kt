package com.hmoney.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** AI 分析歷史資料表：insights／anomalies／tips 以換行分隔多條 */
@Entity(tableName = "ai_analysis")
data class AiAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val createdAtEpochMillis: Long,
    val query: String,
    val summary: String,
    val insights: String = "",
    val anomalies: String = "",
    val tips: String = "",
)
