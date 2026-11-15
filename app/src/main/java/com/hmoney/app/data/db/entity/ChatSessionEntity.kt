package com.hmoney.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** AI 對話 session 資料表：title 為空字串代表尚未命名（UI 顯示「新對話」） */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
