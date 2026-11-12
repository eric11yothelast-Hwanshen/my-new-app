package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.AiAnalysis
import com.hmoney.app.domain.model.AiConfig
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.AiFilterSuggestion
import com.hmoney.app.domain.model.AiSuggestion
import com.hmoney.app.domain.model.AiTextResult
import kotlinx.coroutines.flow.Flow

/** AI 呼叫結果：成功或 AiError（各自有明確 UI 回饋） */
sealed class AiOutcome {
    data class Success(val analysis: AiAnalysis) : AiOutcome()
    data class Failure(val error: AiError) : AiOutcome()
}

/**
 * AI 倉儲介面（共用服務層）：所有頁面的 AI 功能都經過這裡呼叫，
 * 設定管理、本週分析、自由提問、歷史回看，以及各場景的輔助功能
 * （潤飾備註、自動分類、標籤建議、自然語言篩選、財務解讀）。
 * 呼叫一律在此層（repository）執行，UI 僅呈現狀態。
 */
interface AiRepository {

    fun observeConfig(): Flow<AiConfig>

    suspend fun getConfig(): AiConfig

    suspend fun saveConfig(baseUrl: String, apiKey: String, model: String)

    /** 本週花費分析：分類合計、相較上週增減、前三大支出 → 分析結果（存歷史） */
    suspend fun analyzeWeek(): AiOutcome

    /** 自由提問：使用者問題＋本週／本月彙總資料一起送出（存歷史） */
    suspend fun askQuestion(question: String): AiOutcome

    /** 通用文字對話（全域 AI 助手用；includeContext = false 時為純聊天、不附財務資料） */
    suspend fun chat(question: String, includeContext: Boolean = true): AiTextResult

    /** 潤飾／擴充備註（記帳頁智慧備註） */
    suspend fun polishNote(note: String): AiTextResult

    /** 依備註建議分類與 1–3 個標籤（記帳頁自動分類／智慧標籤） */
    suspend fun suggestForNote(note: String, categoryNames: List<String>): Result<AiSuggestion>

    /** 自然語言轉篩選條件（明細頁 AI 搜尋） */
    suspend fun interpretFilter(
        query: String,
        categoryNames: List<String>,
        accountNames: List<String>,
    ): Result<AiFilterSuggestion>

    /** 通用財務數據解讀（總覽洞察、統計分析、帳戶解讀、批次交易分析） */
    suspend fun analyzeData(title: String, data: String): AiTextResult

    fun observeHistory(): Flow<List<AiAnalysis>>

    suspend fun deleteHistory(id: Long)
}
