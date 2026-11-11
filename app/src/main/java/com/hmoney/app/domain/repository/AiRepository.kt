package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.AiAnalysis
import com.hmoney.app.domain.model.AiConfig
import com.hmoney.app.domain.model.AiError
import kotlinx.coroutines.flow.Flow

/** AI 呼叫結果：成功或 AiError（各自有明確 UI 回饋） */
sealed class AiOutcome {
    data class Success(val analysis: AiAnalysis) : AiOutcome()
    data class Failure(val error: AiError) : AiOutcome()
}

/**
 * AI 分析倉儲介面：設定管理、本週分析、自由提問與歷史回看。
 * 呼叫一律在此層（repository）執行，UI 僅呈現狀態。
 */
interface AiRepository {

    fun observeConfig(): Flow<AiConfig>

    suspend fun getConfig(): AiConfig

    suspend fun saveConfig(baseUrl: String, apiKey: String, model: String)

    /** 本週花費分析：分類合計、相較上週增減、前三大支出 → 分析結果 */
    suspend fun analyzeWeek(): AiOutcome

    /** 自由提問：使用者問題＋本週／本月彙總資料一起送出 */
    suspend fun askQuestion(question: String): AiOutcome

    fun observeHistory(): Flow<List<AiAnalysis>>

    suspend fun deleteHistory(id: Long)
}
