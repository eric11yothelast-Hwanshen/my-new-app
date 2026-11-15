package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.AiAnalysis
import com.hmoney.app.domain.model.AiConfig
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.AiFilterSuggestion
import com.hmoney.app.domain.model.AiSuggestion
import com.hmoney.app.domain.model.AiTextResult
import com.hmoney.app.domain.model.TransactionDraft
import com.hmoney.app.domain.model.TransactionQuery
import com.hmoney.app.domain.model.TransactionActionIntent
import com.hmoney.app.domain.model.ItemActionIntent
import com.hmoney.app.domain.model.ItemSuggestion
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

    /**
     * 自然語言記帳草稿（1.3.0，Session 39）：把描述解析為一或多筆草稿。
     * 僅產生草稿，嚴禁寫入 DB——寫入走之後 session 的「草稿預覽 → 使用者確認」流程。
     */
    suspend fun parseTransactionsFromText(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<List<TransactionDraft>>

    /**
     * 圖片記帳草稿（1.3.0，Session 39）：解析付款截圖／發票／明細照片為草稿。
     * 模型不支援 vision（HTTP 4xx）時回 AiError.VisionUnsupported。
     */
    suspend fun parseTransactionsFromImage(
        imageBase64: String,
        mimeType: String,
        hint: String = "",
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<List<TransactionDraft>>

    /**
     * AI 查詢記錄（S45）：把使用者的問題解析為結構化 TransactionQuery；
     * 非查詢語意回 success(null)。模式照 parseTransactionsFromText 的請求／抽取基建（嚴禁經 extractAiContent）。
     */
    suspend fun parseTransactionQuery(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<TransactionQuery?>

    /**
     * AI 查詢記錄（S45）：把格式化的查詢結果交給 AI 綜整回答；
     * resultsText 為空時直接回「沒有符合的記錄」語意（不浪費 AI 呼叫）。
     */
    suspend fun summarizeQueryResult(question: String, resultsText: String): AiTextResult

    /**
     * AI 智慧填選（S46）：依物品名稱（與條碼，如有）建議存放位置／有效日期／數量。
     * 模式照 parseTransactionsFromText 的請求／抽取基建（嚴禁經 extractAiContent、嚴禁寫入 DB）；
     * 僅供「建議預覽 → 逐欄採用」，不得自動覆寫使用者輸入。
     */
    suspend fun suggestItemFields(name: String, barcode: String?): Result<ItemSuggestion>

    /**
     * S47 AI 聊天室物品操作：把使用者訊息解析為結構化 ItemActionIntent（CREATE／UPDATE／DELETE）；
     * 非物品操作回 success(null)（續走查詢／一般 chat）。模式照 parseTransactionQuery 的請求／抽取基建
     * （嚴禁經 extractAiContent），解析模式沿用 suggestItemFields（相對天數或明確日期）。
     */
    suspend fun parseItemAction(text: String, existingItemNames: List<String>): Result<ItemActionIntent?>

    /**
     * S48 AI 聊天室交易刪改：把使用者訊息解析為結構化 TransactionActionIntent（DELETE／UPDATE）；
     * target 重用 TransactionQuery 欄位描述要找哪筆，UPDATE 另帶 updates（金額／備註／分類／帳戶／日期）。
     * 非操作語意回 success(null)（續走物品操作／記帳／查詢／一般 chat）。模式照 parseTransactionQuery
     * 的請求／抽取基建（嚴禁經 extractAiContent），嚴禁寫入 DB——寫入只能走既有
     * updateTransaction／deleteTransaction，且必經確認卡。
     */
    suspend fun parseTransactionAction(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<TransactionActionIntent?>

    fun observeHistory(): Flow<List<AiAnalysis>>

    suspend fun deleteHistory(id: Long)
}
