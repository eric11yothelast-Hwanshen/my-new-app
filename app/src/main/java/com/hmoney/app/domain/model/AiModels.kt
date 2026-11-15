package com.hmoney.app.domain.model

/** OpenAI 相容 API 連線設定（存於 EncryptedSharedPreferences，不進 Room／明文檔） */
data class AiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/** AI 呼叫錯誤：各自有明確 UI 回饋，不得閃退 */
sealed class AiError {
    data object NotConfigured : AiError()
    data object Network : AiError()
    data object Unauthorized : AiError()
    data object QuotaExceeded : AiError()
    data class Http(val code: Int) : AiError()
    data object Parse : AiError()

    /** 模型不支援 vision（圖片輸入）——HTTP 4xx 時的明確錯誤（Session 39） */
    data object VisionUnsupported : AiError()
}

/** 純文字回應結果（通用 AI 功能共用） */
sealed class AiTextResult {
    data class Success(val text: String) : AiTextResult()
    data class Failure(val error: AiError) : AiTextResult()
}

/** 記帳 AI 建議：分類＋標籤＋潤飾後備註 */
data class AiSuggestion(
    val categoryName: String? = null,
    val tags: List<String> = emptyList(),
    val polishedNote: String? = null,
)

/** 自然語言篩選轉換結果 */
data class AiFilterSuggestion(
    val categoryName: String? = null,
    val accountName: String? = null,
    val keyword: String? = null,
    val startDateEpochMillis: Long? = null,
    val endDateEpochMillis: Long? = null,
)

/** AI 分析結果（存歷史可回看） */
data class AiAnalysis(
    val id: Long = 0L,
    val createdAtEpochMillis: Long,
    val query: String,
    val summary: String,
    val insights: List<String> = emptyList(),
    val anomalies: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
)
