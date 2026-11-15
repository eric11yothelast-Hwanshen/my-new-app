package com.hmoney.app.data.repository

import com.hmoney.app.data.ai.AiApi
import com.hmoney.app.data.ai.AiConfigStore
import com.hmoney.app.data.db.dao.AiAnalysisDao
import com.hmoney.app.data.db.entity.AiAnalysisEntity
import com.hmoney.app.domain.model.AiAnalysis
import com.hmoney.app.domain.model.AiConfig
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.AiFilterSuggestion
import com.hmoney.app.domain.model.AiSuggestion
import com.hmoney.app.domain.model.AiTextResult
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.TransactionDraft
import com.hmoney.app.domain.model.TransactionQuery
import com.hmoney.app.domain.model.TransactionActionIntent
import com.hmoney.app.domain.model.ItemActionIntent
import com.hmoney.app.domain.model.ItemSuggestion
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.logic.BalanceCalculator
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.AiOutcome
import com.hmoney.app.domain.repository.AiRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.TransactionRepository
import com.hmoney.app.ui.common.Formats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** 對話類呼叫溫度（Session 33 決策）：較高溫讓口語回覆更自然 */
private const val AI_CHAT_TEMPERATURE = 0.65

/** 分析類呼叫溫度（Session 30 既有值，不變） */
private const val AI_ANALYSIS_TEMPERATURE = 0.4

/** 記帳草稿解析溫度（Session 39）：結構化輸出要求穩定，取低溫 */
private const val AI_DRAFT_TEMPERATURE = 0.1

/**
 * AI 倉儲實作：
 * - 設定存 EncryptedSharedPreferences
 * - 本週分析／自由提問皆以結構化記帳資料組 prompt，呼叫 OpenAI 相容 API
 * - 完整錯誤處理（未設定／無網路／HTTP／額度／解析），一律不拋出例外
 * - 分析結果存歷史（Room）可回看
 */
@Singleton
class AiRepositoryImpl @Inject constructor(
    private val aiApi: AiApi,
    private val configStore: AiConfigStore,
    private val aiAnalysisDao: AiAnalysisDao,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val balanceCalculator: BalanceCalculator,
) : AiRepository {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ===== 設定 =====

    override fun observeConfig(): Flow<AiConfig> =
        kotlinx.coroutines.flow.flow { emit(configStore.read()) }

    override suspend fun getConfig(): AiConfig = configStore.read()

    override suspend fun saveConfig(baseUrl: String, apiKey: String, model: String) {
        configStore.write(AiConfig(baseUrl, apiKey, model))
    }

    // ===== 歷史 =====

    override fun observeHistory(): Flow<List<AiAnalysis>> =
        aiAnalysisDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun deleteHistory(id: Long) {
        aiAnalysisDao.deleteById(id)
    }

    private fun AiAnalysisEntity.toDomain(): AiAnalysis = AiAnalysis(
        id = id,
        createdAtEpochMillis = createdAtEpochMillis,
        query = query,
        summary = summary,
        insights = insights.lines().filter { it.isNotBlank() },
        anomalies = anomalies.lines().filter { it.isNotBlank() },
        tips = tips.lines().filter { it.isNotBlank() },
    )

    // ===== 通用 AI 功能（全 App 共用服務層） =====

    override suspend fun chat(question: String, includeContext: Boolean): AiTextResult {
        val systemPrompt = "你是一位專業的個人財務助理，請以繁體中文回答使用者的問題。" +
            "回答精簡有條理，並基於提供的記帳資料。" +
            "同一句話或相似句子不要重複出現。"
        if (!includeContext) {
            // 純聊天模式：不附任何財務資料，兼顧隱私
            return callRaw(
                systemPrompt = systemPrompt,
                userPrompt = "使用者問題：$question",
                reasoningEffort = "none",
                temperature = AI_CHAT_TEMPERATURE,
            )
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekData = buildWeekData(today.minusDays(7), today, today.minusDays(14), zone)
        val monthData = buildMonthData(today.withDayOfMonth(1), today, zone)
        val accountsData = buildAccountsData()
        return callRaw(
            systemPrompt = systemPrompt,
            userPrompt = "記帳資料（已精簡去敏感化）：\n$weekData\n$monthData\n$accountsData\n\n使用者問題：$question",
            reasoningEffort = "none",
            temperature = AI_CHAT_TEMPERATURE,
        )
    }

    override suspend fun polishNote(note: String): AiTextResult = callRaw(
        systemPrompt = "你是記帳小幫手。請將使用者輸入的消費備註潤飾擴充成更具體的描述，" +
            "以繁體中文回覆，只輸出潤飾後的備註文字本身，不要加任何說明或引號。",
        userPrompt = "原始備註：$note",
    )

    override suspend fun suggestForNote(
        note: String,
        categoryNames: List<String>,
    ): Result<AiSuggestion> {
        val result = callRaw(
            systemPrompt = "你是記帳分類助手。依消費備註判斷分類並建議標籤，以繁體中文回傳 JSON：" +
                "{\"category\": \"分類名稱\", \"tags\": [\"標籤1\", \"標籤2\"]}。" +
                "category 必須從以下分類中選擇：${categoryNames.joinToString(",")}。" +
                "tags 為 1–3 個簡短標籤。只回傳 JSON。",
            userPrompt = "備註：$note",
        )
        return when (result) {
            is AiTextResult.Failure -> Result.failure(
                AiCallException(result.error),
            )
            is AiTextResult.Success -> runCatching {
                val jsonText = result.text
                    .removePrefix("```json").removePrefix("```")
                    .removeSuffix("```").trim()
                val parsed = JSONObject(jsonText)
                AiSuggestion(
                    categoryName = parsed.optString("category").takeIf { it.isNotBlank() },
                    tags = runCatching {
                        val arr = parsed.getJSONArray("tags")
                        (0 until arr.length()).map { arr.getString(it) }
                    }.getOrDefault(emptyList()),
                    polishedNote = null,
                )
            }
        }
    }

    override suspend fun interpretFilter(
        query: String,
        categoryNames: List<String>,
        accountNames: List<String>,
    ): Result<AiFilterSuggestion> {
        val result = callRaw(
            systemPrompt = "你是記帳搜尋助手。把使用者的自然語言描述轉成篩選條件 JSON，" +
                "以繁體中文值回傳：{\"category\": \"分類名稱\", \"account\": \"帳戶名稱\", " +
                "\"keyword\": \"關鍵字\", \"startDate\": \"yyyy-MM-dd\", \"endDate\": \"yyyy-MM-dd\"}，" +
                "無法判斷的欄位填 null。" +
                "分類只能從：${categoryNames.joinToString(",")}；帳戶只能從：${accountNames.joinToString(",")}。" +
                "今天日期是 ${LocalDate.now()}。只回傳 JSON。",
            userPrompt = "描述：$query",
        )
        return when (result) {
            is AiTextResult.Failure -> Result.failure(
                AiCallException(result.error),
            )
            is AiTextResult.Success -> runCatching {
                val jsonText = result.text
                    .removePrefix("```json").removePrefix("```")
                    .removeSuffix("```").trim()
                val parsed = JSONObject(jsonText)
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                fun optDate(key: String): Long? = runCatching {
                    val text = parsed.optString(key)
                    if (text.isBlank() || text == "null") {
                        null
                    } else {
                        LocalDate.parse(text, formatter)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                    }
                }.getOrNull()
                AiFilterSuggestion(
                    categoryName = parsed.optString("category").takeIf {
                        it.isNotBlank() && it != "null"
                    },
                    accountName = parsed.optString("account").takeIf {
                        it.isNotBlank() && it != "null"
                    },
                    keyword = parsed.optString("keyword").takeIf { it.isNotBlank() && it != "null" },
                    startDateEpochMillis = optDate("startDate"),
                    endDateEpochMillis = optDate("endDate")?.let { end ->
                        // 結束日含當天：加一天減一毫秒
                        end + 86_399_999
                    },
                )
            }
        }
    }

    // ===== AI 記帳草稿（1.3.0，僅產生草稿、嚴禁寫入 DB） =====

    override suspend fun parseTransactionsFromText(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<List<TransactionDraft>> {
        val config = configStore.read()
        if (!config.isConfigured) {
            return Result.failure(AiCallException(AiError.NotConfigured))
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // 草稿回應是 {"type":"expense"...} 開頭的 JSON 陣列，會被 sanitizeAiText 誤刪，
        // 因此走 request() + extractDraftContent()，不經 callRaw／extractAiContent
        return when (
            val raw = request(
                config,
                systemPrompt = "你是記帳助理。把使用者的記帳描述轉成 JSON 陣列，每筆格式：" +
                    "{\"type\":\"expense|income\", \"amount\":\"金額字串\", \"note\":\"說明\", " +
                    "\"category\":\"分類名或null\", \"account\":\"帳戶名或null\", " +
                    "\"date\":\"自然語言或ISO日期\", \"tags\":[\"標籤\"]}。" +
                    "描述裡有多筆就回多筆。無法判斷的欄位填 null。只回傳 JSON 陣列，不加其他文字。" +
                    "若訊息其實是物品管理或交易刪改操作而非記帳，請回空陣列 []。" +
                    categoryAccountConstraint(expenseCategories, incomeCategories, accountNames),
                userPrompt = "今天是 $today。記帳描述：$text",
                reasoningEffort = "none",
                temperature = AI_DRAFT_TEMPERATURE,
            )
        ) {
            is AiError -> Result.failure(AiCallException(raw))
            is String -> draftsFromRaw(raw, today, zone)
            else -> Result.failure(AiCallException(AiError.Parse))
        }
    }

    override suspend fun parseTransactionsFromImage(
        imageBase64: String,
        mimeType: String,
        hint: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<List<TransactionDraft>> {
        val config = configStore.read()
        if (!config.isConfigured) {
            return Result.failure(AiCallException(AiError.NotConfigured))
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val systemPrompt = "你是記帳助理。請解析圖片（付款截圖／發票／明細照片）為記帳草稿 JSON 陣列，每筆格式：" +
            "{\"type\":\"expense|income\", \"amount\":\"金額字串\", \"note\":\"品項或說明\", " +
            "\"category\":\"分類名或null\", \"account\":\"付款方式或帳戶名或null\", " +
            "\"date\":\"發票或交易日期\", \"tags\":[\"標籤\"]}。" +
            "圖片有多筆明細就回多筆。無法辨識的欄位填 null。只回傳 JSON 陣列，不加其他文字。" +
            categoryAccountConstraint(expenseCategories, incomeCategories, accountNames)
        val userText = "今天是 $today。" +
            (hint.takeIf { it.isNotBlank() }?.let { "補充說明：$it。" } ?: "") +
            "請解析這張圖片為記帳草稿。"
        return when (val raw = requestVision(config, systemPrompt, userText, imageBase64, mimeType)) {
            is AiError -> Result.failure(AiCallException(raw))
            is String -> draftsFromRaw(raw, today, zone)
            else -> Result.failure(AiCallException(AiError.Parse))
        }
    }

    // ===== S45 AI 查詢記錄（唯讀檢索，嚴禁接入任何寫入流程） =====

    override suspend fun parseTransactionQuery(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<TransactionQuery?> {
        val config = configStore.read()
        if (!config.isConfigured) {
            return Result.failure(AiCallException(AiError.NotConfigured))
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // 模式照抄 parseTransactionsFromText：request() + extractDraftContent()，不經 callRaw／extractAiContent
        return when (
            val raw = request(
                config,
                systemPrompt = "你是記帳查詢助手。判斷使用者的訊息是否為「查詢記錄」語意（查過去消費／收入／轉帳記錄），" +
                    "並解析成 JSON 物件回傳，格式：" +
                    "{\"isQuery\": true|false, \"keyword\": \"備註或標籤關鍵字或null\", " +
                    "\"category\": \"分類名或null\", \"account\": \"帳戶名或null\", " +
                    "\"type\": \"expense|income|transfer或null\", " +
                    "\"startDate\": \"yyyy-MM-dd或null\", \"endDate\": \"yyyy-MM-dd或null\", " +
                    "\"minAmount\": \"金額字串或null\", \"maxAmount\": \"金額字串或null\", " +
                    "\"limit\": 50}。" +
                    "「這個月」「上週」等相對日期請依今天日期換算成明確起訖。" +
                    "category 只能從：[" + expenseCategories.joinToString("、") + "] 與 [" +
                    incomeCategories.joinToString("、") + "] 中選，account 只能從：[" +
                    accountNames.joinToString("、") + "] 中選，對不上就填 null。" +
                    "若訊息是在記一筆新帳、或只是閒聊／問知識，isQuery 填 false。只回傳 JSON，不要加其他文字。",
                userPrompt = "今天是 $today。使用者訊息：$text",
                reasoningEffort = "none",
                temperature = AI_DRAFT_TEMPERATURE,
            )
        ) {
            is AiError -> Result.failure(AiCallException(raw))
            is String -> {
                val content = extractDraftContent(raw)
                    ?: return Result.failure(AiCallException(AiError.Parse))
                Result.success(parseQueryFromContent(content, today, zone))
            }
            else -> Result.failure(AiCallException(AiError.Parse))
        }
    }

    override suspend fun summarizeQueryResult(question: String, resultsText: String): AiTextResult {
        if (resultsText.isBlank()) {
            return AiTextResult.Success("沒有符合的記錄。")
        }
        return callRaw(
            systemPrompt = "你是記帳查詢助手。請根據提供的查詢結果資料回答使用者的問題，" +
                "以繁體中文回覆，條列關鍵記錄（日期／金額／分類／帳戶／備註），" +
                "必要時做簡單加總，不要編造資料裡沒有的內容。",
            userPrompt = "使用者問題：$question\n查詢結果（日期新→舊）：\n$resultsText",
            reasoningEffort = "none",
            temperature = AI_CHAT_TEMPERATURE,
        )
    }

    /**
     * S41-fix：分類／帳戶名稱限制——提示詞明確規定 category 只能從對應 type 的清單中選、
     * account 只能從帳戶清單中選，對不上就填 null（讓既有 mapper fallback 接手），嚴禁發明清單外的名稱。
     */
    override suspend fun suggestItemFields(name: String, barcode: String?): Result<ItemSuggestion> {
        val config = configStore.read()
        if (!config.isConfigured) {
            return Result.failure(AiCallException(AiError.NotConfigured))
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // S46 AI 智慧填選：模式照 parseTransactionsFromText（request() + extractDraftContent()，不經 callRaw／extractAiContent）
        return when (
            val raw = request(
                config,
                systemPrompt = "你是物品管理助手。依使用者提供的物品名稱（與條碼，如有）推測常見的存放位置、" +
                    "有效日期與購買數量，回傳 JSON 物件：" +
                    "{\"storageLocation\": \"存放位置或null\", \"expiryDate\": \"yyyy-MM-dd或null\", " +
                    "\"expiryInDays\": 到期天數整數或null, \"quantity\": 數量整數或null}。" +
                    "expiryDate 與 expiryInDays 擇一即可（今日為基準）；無法推測的欄位填 null。" +
                    "只回傳 JSON，不要加其他文字。",
                userPrompt = "今天是 $today。物品名稱：$name" +
                    (barcode?.takeIf { it.isNotBlank() }?.let { "；條碼：$it" } ?: ""),
                reasoningEffort = "none",
                temperature = AI_DRAFT_TEMPERATURE,
            )
        ) {
            is AiError -> Result.failure(AiCallException(raw))
            is String -> {
                val content = extractDraftContent(raw)
                    ?: return Result.failure(AiCallException(AiError.Parse))
                Result.success(parseItemSuggestionFromContent(content, today, zone))
            }
            else -> Result.failure(AiCallException(AiError.Parse))
        }
    }

    /**
     * S47 AI 聊天室物品操作：模式照 parseTransactionQuery（request() ＋ extractDraftContent()，嚴禁經 extractAiContent）。
     * 帶入現有物品清單供模型比對名稱；非物品操作回 success(null)。
     */
    override suspend fun parseItemAction(
        text: String,
        existingItemNames: List<String>,
    ): Result<ItemActionIntent?> {
        val config = configStore.read()
        if (!config.isConfigured) {
            return Result.failure(AiCallException(AiError.NotConfigured))
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return when (
            val raw = request(
                config,
                systemPrompt = "你是物品管理助手。判斷使用者訊息是否為「物品管理操作」（新增物品／更新物品資訊／刪除物品），" +
                    "並解析成 JSON 物件回傳，格式：" +
                    "{\"isItemAction\": true|false, \"action\": \"create|update|delete\", " +
                    "\"name\": \"物品名稱（必要）\", \"storageLocation\": \"存放位置或null\", " +
                    "\"expiryDate\": \"yyyy-MM-dd或null\", \"expiryInDays\": 到期天數整數或null, " +
                    "\"expiryDeltaDays\": 相對現有到期日的增減天數整數或null, " +
                    "\"quantity\": 數量整數或null}。" +
                    "若使用者說「增加／延長／縮短／提前 N 天」到期（相對該物品現有到期日），" +
                    "expiryDeltaDays 填 N（縮短／提前為負數），且 expiryDate 與 expiryInDays 填 null。" +
                    "expiryDate 與 expiryInDays 擇一（今日為基準，相對天數如「3天後到期」填 expiryInDays=3）。" +
                    "更新／刪除時 name 請對照現有物品清單填入最接近的名稱。" +
                    "若訊息只是閒聊、記帳或查詢記錄，isItemAction 填 false。" +
                    "若訊息是記帳或交易刪改而非物品操作，isItemAction 也填 false。只回傳 JSON，不要加其他文字。",
                userPrompt = "今天是 $today。現有物品清單：[" +
                    existingItemNames.joinToString("、") + "]。使用者訊息：$text",
                reasoningEffort = "none",
                temperature = AI_DRAFT_TEMPERATURE,
            )
        ) {
            is AiError -> Result.failure(AiCallException(raw))
            is String -> {
                val content = extractDraftContent(raw)
                    ?: return Result.failure(AiCallException(AiError.Parse))
                Result.success(parseItemActionFromContent(content, today, zone))
            }
            else -> Result.failure(AiCallException(AiError.Parse))
        }
    }

    /**
     * S48 AI 聊天室交易刪改：模式照 parseTransactionQuery（request() ＋ extractDraftContent()，嚴禁經 extractAiContent）。
     * 非交易操作回 success(null)；解析器為純函式 parseTransactionActionFromContent。嚴禁在此層寫入 DB。
     */
    override suspend fun parseTransactionAction(
        text: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): Result<TransactionActionIntent?> {
        val config = configStore.read()
        if (!config.isConfigured) {
            return Result.failure(AiCallException(AiError.NotConfigured))
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return when (
            val raw = request(
                config,
                systemPrompt = "你是記帳助手。判斷使用者訊息是否為「交易刪改操作」（刪除某筆交易／修改某筆交易），" +
                    "並解析成 JSON 物件回傳，格式：" +
                    "{\"isTransactionAction\": true|false, \"action\": \"delete|update\", " +
                    "\"target\": {\"keyword\": \"備註關鍵字或null\", \"category\": \"分類名或null\", " +
                    "\"account\": \"帳戶名或null\", \"type\": \"expense|income|transfer或null\", " +
                    "\"startDate\": \"yyyy-MM-dd或null\", \"endDate\": \"yyyy-MM-dd或null\", " +
                    "\"minAmount\": \"金額字串或null\", \"maxAmount\": \"金額字串或null\"}, " +
                    "\"updates\": {\"amount\": \"金額字串或null\", \"note\": \"新備註或null\", " +
                    "\"category\": \"分類名或null\", \"account\": \"帳戶名或null\", \"date\": \"yyyy-MM-dd或null\"}}。" +
                    "target 描述要找哪一筆交易（日期範圍／關鍵字／分類／帳戶／金額上下限），「昨天」「上週」等相對日期請依今天日期換算成明確起訖。" +
                    "category 只能從：[" + expenseCategories.joinToString("、") + "] 與 [" +
                    incomeCategories.joinToString("、") + "] 中選，account 只能從：[" +
                    accountNames.joinToString("、") + "] 中選，對不上就填 null。" +
                    "updates 只有 update 才需要，描述要改成的新值，沒提到的欄位填 null。" +
                    "若訊息是刪除／移除＋「記帳／交易／支出／剛剛那筆／最新一筆／最近一筆」等空泛說法且沒有其他標的資訊，" +
                    "isTransactionAction 填 true、action 填 delete、recent 填 true、target 各欄位全填 null。" +
                    "若訊息只是閒聊、記帳（新增一筆）、查詢記錄或物品管理而非交易刪改，isTransactionAction 填 false。" +
                    "只回傳 JSON，不要加其他文字。",
                userPrompt = "今天是 $today。使用者訊息：$text",
                reasoningEffort = "none",
                temperature = AI_DRAFT_TEMPERATURE,
            )
        ) {
            is AiError -> Result.failure(AiCallException(raw))
            is String -> {
                val content = extractDraftContent(raw)
                    ?: return Result.failure(AiCallException(AiError.Parse))
                Result.success(parseTransactionActionFromContent(content, today, zone))
            }
            else -> Result.failure(AiCallException(AiError.Parse))
        }
    }

    private fun categoryAccountConstraint(
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        accountNames: List<String>,
    ): String = "分類與帳戶限制：使用者的支出分類清單：[" + expenseCategories.joinToString("、") +
        "]；收入分類清單：[" + incomeCategories.joinToString("、") +
        "]；帳戶清單：[" + accountNames.joinToString("、") +
        "]。category 只能從對應 type 的分類清單中選，account 只能從帳戶清單中選，" +
        "對不上就填 null，嚴禁使用清單以外或自己發明的名稱。"

    /** 原始回應 → 草稿清單（content 抽取失敗或 JSON 陣列完全無法解析才回 AiError.Parse） */
    private fun draftsFromRaw(
        raw: String,
        today: LocalDate,
        zone: ZoneId,
    ): Result<List<TransactionDraft>> {
        val content = extractDraftContent(raw)
            ?: return Result.failure(AiCallException(AiError.Parse))
        return parseDraftsFromContent(content, today, zone)?.let { Result.success(it) }
            ?: Result.failure(AiCallException(AiError.Parse))
    }

    /**
     * vision HTTP 請求（OpenAI 相容 content 陣列形狀）：user content = text + image_url data URI。
     * 模型不支援 vision（HTTP 4xx，401／403／429 除外）回 AiError.VisionUnsupported。
     */
    private suspend fun requestVision(
        config: AiConfig,
        systemPrompt: String,
        userText: String,
        imageBase64: String,
        mimeType: String,
    ): Any {
        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
        val body = JSONObject().apply {
            put("model", config.model)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", buildVisionUserContent(userText, imageBase64, mimeType)),
                    ),
            )
            put("temperature", AI_DRAFT_TEMPERATURE)
            put("max_tokens", 2048)
        }.toString().toRequestBody(jsonMediaType)
        return try {
            val response = aiApi.chat(
                url = endpoint,
                authorization = "Bearer ${config.apiKey}",
                body = body,
            )
            when {
                response.code() == 401 || response.code() == 403 -> AiError.Unauthorized
                response.code() == 429 -> AiError.QuotaExceeded
                !response.isSuccessful ->
                    if (response.code() in 400..499) {
                        AiError.VisionUnsupported
                    } else {
                        AiError.Http(response.code())
                    }
                else -> response.body()?.string() ?: AiError.Parse
            }
        } catch (e: IOException) {
            AiError.Network
        } catch (e: HttpException) {
            if (e.code() in 400..499) AiError.VisionUnsupported else AiError.Http(e.code())
        }
    }

    override suspend fun analyzeData(title: String, data: String): AiTextResult = callRaw(
        systemPrompt = "你是個人財務分析師。請依提供的財務數據，以繁體中文進行解讀，" +
            "包含趨勢觀察與具體可執行的建議，共 2–6 句，直接輸出內容。",
        userPrompt = "分析主題：$title\n數據：\n$data",
    )

    /** 帳戶餘額數據（去敏感化：只含名稱／餘額／幣別） */
    private suspend fun buildAccountsData(): String {
        val accountsWithBalance = accountRepository.observeAllWithBalance().first()
        val sb = StringBuilder()
        sb.appendLine("各帳戶餘額：")
        accountsWithBalance.forEach { (account, balance) ->
            sb.appendLine("- ${account.name}：${Formats.moneyWithCurrency(balance, account.currency)}")
        }
        return sb.toString()
    }

    /** 精簡交易清單（去敏感化：只含日期／分類／金額／備註） */
    private suspend fun buildTransactionsData(limit: Int): String {
        val categories = categoryRepository.observeAll(CategoryType.EXPENSE).first()
            .associateBy { it.id }
        val transactions = transactionRepository.observeTransactions().first().take(limit)
        val sb = StringBuilder()
        transactions.forEach { tx ->
            val name = tx.categoryId?.let { categories[it]?.name } ?: tx.type.name
            sb.appendLine("- ${Formats.date(tx.dateEpochMillis)} $name ${Formats.money(tx.amountCents)} ${tx.note}")
        }
        return sb.toString()
    }

    // ===== 分析與問答 =====

    override suspend fun analyzeWeek(): AiOutcome {
        val config = configStore.read()
        if (!config.isConfigured) return AiOutcome.Failure(AiError.NotConfigured)

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays(7)
        val prevStart = weekStart.minusDays(7)
        val data = buildWeekData(weekStart, today, prevStart, zone)

        val systemPrompt = """
            你是一位專業的個人財務分析師。請閱讀使用者提供的本週記帳資料，
            並以繁體中文回傳 JSON（格式：{"summary": "一段整體摘要", "topCategories": [{"name": "分類名稱", "amount": "金額或描述"}], "advice": "一條具體建議"}）。
            topCategories 最多 3 筆，依金額由大到小排列。只回傳 JSON，不要加其他文字。
        """.trimIndent()

        return execute(
            config = config,
            queryLabel = "本週花費分析",
            systemPrompt = systemPrompt,
            userPrompt = data,
        )
    }

    override suspend fun askQuestion(question: String): AiOutcome {
        val config = configStore.read()
        if (!config.isConfigured) return AiOutcome.Failure(AiError.NotConfigured)

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val monthStart = today.withDayOfMonth(1)
        val monthData = buildMonthData(monthStart, today, zone)
        val weekData = buildWeekData(today.minusDays(7), today, today.minusDays(14), zone)

        val systemPrompt = """
            你是一位專業的個人財務助理，請以繁體中文回答使用者的問題。
            回答請精簡有條理，並基於提供的記帳資料。
        """.trimIndent()

        val userPrompt = "記帳資料：\n$weekData\n$monthData\n\n使用者問題：$question"

        return execute(
            config = config,
            queryLabel = question,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            expectJson = false,
        )
    }

    /** 組本週結構化資料：分類合計、相較上週增減、前三大支出 */
    private suspend fun buildWeekData(
        weekStart: LocalDate,
        today: LocalDate,
        prevStart: LocalDate,
        zone: ZoneId,
    ): String {
        val categories = categoryRepository.observeAll(CategoryType.EXPENSE).first()
            .associateBy { it.id }
        val current = transactionRepository.observeTransactions(
            startDate = weekStart.atStartOfDay(zone).toInstant().toEpochMilli(),
            endDate = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        ).first().filter { it.type == TransactionType.EXPENSE }
        val previous = transactionRepository.observeTransactions(
            startDate = prevStart.atStartOfDay(zone).toInstant().toEpochMilli(),
            endDate = weekStart.atStartOfDay(zone).toInstant().toEpochMilli(),
        ).first().filter { it.type == TransactionType.EXPENSE }

        val currentByCategory = current.groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }
        val previousByCategory = previous.groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }

        val sb = StringBuilder()
        sb.appendLine("本週期間：$weekStart ～ ${today.minusDays(1)}")
        sb.appendLine("本週總支出：${Formats.money(currentByCategory.values.sum())}")
        sb.appendLine("各分類支出（與上週比較）：")
        currentByCategory.entries
            .sortedByDescending { it.value }
            .forEach { (categoryId, cents) ->
                val name = categories[categoryId]?.name ?: "未分類"
                val prev = previousByCategory[categoryId] ?: 0L
                val delta = cents - prev
                sb.appendLine("- $name：${Formats.money(cents)}（上週 ${Formats.money(prev)}，${if (delta >= 0) "+" else ""}${Formats.money(delta)}）")
            }
        sb.appendLine("前三大支出：")
        current.sortedByDescending { it.amountCents }.take(3).forEach { tx ->
            val name = categories[tx.categoryId]?.name ?: "未分類"
            sb.appendLine("- $name ${Formats.money(tx.amountCents)} ${tx.note}")
        }
        return sb.toString()
    }

    /** 組本月彙總資料 */
    private suspend fun buildMonthData(
        monthStart: LocalDate,
        today: LocalDate,
        zone: ZoneId,
    ): String {
        val categories = categoryRepository.observeAll(CategoryType.EXPENSE).first()
            .associateBy { it.id }
        val monthTx = transactionRepository.observeTransactions(
            startDate = monthStart.atStartOfDay(zone).toInstant().toEpochMilli(),
            endDate = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        ).first()
        val expense = monthTx.filter { it.type == TransactionType.EXPENSE }
        val income = monthTx.filter { it.type == TransactionType.INCOME }
        val byCategory = expense.groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }
        val sb = StringBuilder()
        sb.appendLine("本月期間：$monthStart ～ $today")
        sb.appendLine("本月總支出：${Formats.money(expense.sumOf { it.amountCents })}")
        sb.appendLine("本月總收入：${Formats.money(income.sumOf { it.amountCents })}")
        sb.appendLine("本月各分類支出：")
        byCategory.entries.sortedByDescending { it.value }.forEach { (categoryId, cents) ->
            val name = categories[categoryId]?.name ?: "未分類"
            sb.appendLine("- $name：${Formats.money(cents)}")
        }
        return sb.toString()
    }

    /** 通用文字呼叫：所有錯誤轉為 AiTextResult.Failure（不存歷史） */
    private suspend fun callRaw(
        systemPrompt: String,
        userPrompt: String,
        reasoningEffort: String? = null,
        temperature: Double = AI_ANALYSIS_TEMPERATURE,
    ): AiTextResult {
        val config = configStore.read()
        if (!config.isConfigured) return AiTextResult.Failure(AiError.NotConfigured)
        return when (val raw = request(config, systemPrompt, userPrompt, reasoningEffort, temperature)) {
            is String -> when (val text = extractContent(raw)) {
                null -> AiTextResult.Failure(AiError.Parse)
                else -> AiTextResult.Success(text)
            }
            is AiError -> AiTextResult.Failure(raw)
            else -> AiTextResult.Failure(AiError.Parse)
        }
    }

    /**
     * 從 API 原始回應 JSON 取出 choices[0].message.content。
     * Session 31 起解析邏輯抽至檔案尾端 extractAiContent()（支援三種 content 形態），
     * 延續 Session 10「解析 choices[0].message.content」的既有決策，僅相容性擴充。
     */
    private fun extractContent(raw: String): String? = extractAiContent(raw)

    /**
     * 共用 HTTP 請求：回傳模型回覆內容（String）或 AiError。
     * 對話類呼叫（chat()）帶 reasoningEffort="none" 關閉 thinking；
     * 400 階梯：reasoning_effort="none" → "low" → 移除 sampling 參數的裸 body（最多兩層重試）。
     * 分析類呼叫（reasoningEffort=null）維持原行為：帶 sampling 參數，400 時改裸 body 重試一次。
     */
    private suspend fun request(
        config: AiConfig,
        systemPrompt: String,
        userPrompt: String,
        reasoningEffort: String? = null,
        temperature: Double = AI_ANALYSIS_TEMPERATURE,
    ): Any {
        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"

        fun buildBody(effort: String?, withSampling: Boolean): RequestBody = JSONObject().apply {
            put("model", config.model)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt)),
            )
            put("temperature", temperature)
            if (effort != null) put("reasoning_effort", effort)
            if (withSampling) {
                put("frequency_penalty", 0.3)
                put("presence_penalty", 0.2)
                put("max_tokens", 1024)
            }
        }.toString().toRequestBody(jsonMediaType)

        fun toResult(response: Response<ResponseBody>): Any = when {
            response.code() == 401 || response.code() == 403 -> AiError.Unauthorized
            response.code() == 429 -> AiError.QuotaExceeded
            !response.isSuccessful -> AiError.Http(response.code())
            else -> response.body()?.string() ?: AiError.Parse
        }

        suspend fun send(effort: String?, withSampling: Boolean): Any = toResult(
            aiApi.chat(
                url = endpoint,
                authorization = "Bearer ${config.apiKey}",
                body = buildBody(effort, withSampling),
            ),
        )

        return try {
            var result = send(reasoningEffort, withSampling = true)
            if (result is AiError.Http && result.code == 400) {
                result = if (reasoningEffort != null) {
                    send("low", withSampling = true)
                } else {
                    send(null, withSampling = false)
                }
                if (result is AiError.Http && result.code == 400) {
                    result = send(null, withSampling = false)
                }
            }
            result
        } catch (e: IOException) {
            AiError.Network
        } catch (e: HttpException) {
            AiError.Http(e.code())
        }
    }

    /** 執行 API 呼叫並保存歷史；所有錯誤轉為 AiOutcome.Failure */
    private suspend fun execute(
        config: AiConfig,
        queryLabel: String,
        systemPrompt: String,
        userPrompt: String,
        expectJson: Boolean = true,
    ): AiOutcome {
        val content = when (val raw = request(config, systemPrompt, userPrompt)) {
            is String -> raw
            is AiError -> return AiOutcome.Failure(raw)
            else -> return AiOutcome.Failure(AiError.Parse)
        }

        // 解析回應（寬容處理：JSON 失敗時整段文字視為總結，不閃退）
        val analysis = parseContent(content, expectJson)
            ?: run {
                val cleaned = content
                    .substringAfter("\"content\":\"")
                    .substringBefore("\",")
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                AiAnalysis(
                    createdAtEpochMillis = System.currentTimeMillis(),
                    query = queryLabel,
                    summary = cleaned.ifBlank { "（無回應內容）" },
                )
            }

        val id = aiAnalysisDao.insert(
            AiAnalysisEntity(
                createdAtEpochMillis = analysis.createdAtEpochMillis,
                query = queryLabel,
                summary = analysis.summary,
                insights = analysis.insights.joinToString("\n"),
                anomalies = analysis.anomalies.joinToString("\n"),
                tips = analysis.tips.joinToString("\n"),
            ),
        )
        return AiOutcome.Success(analysis.copy(id = id, query = queryLabel))
    }

    /** 解析模型回覆：優先取 choices[0].message.content，再依 JSON 結構拆欄位 */
    private fun parseContent(raw: String, expectJson: Boolean): AiAnalysis? {
        return runCatching {
            val content = JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            if (!expectJson) {
                AiAnalysis(
                    createdAtEpochMillis = System.currentTimeMillis(),
                    query = "",
                    summary = content.trim(),
                )
            } else {
                // 去除可能包覆的 markdown 程式碼圍籬
                val jsonText = content
                    .removePrefix("```json").removePrefix("```")
                    .removeSuffix("```").trim()
                val parsed = JSONObject(jsonText)
                // topCategories：預期 [{"name","amount"}]，相容純字串陣列；
                // 解析失敗或全空時回傳 null，由呼叫端以原始文字作為摘要（不開新例外路徑）
                val topCategories = runCatching {
                    val arr = parsed.getJSONArray("topCategories")
                    (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.optJSONObject(i)
                        if (obj != null) {
                            val name = obj.optString("name").trim()
                            val amount = obj.optString("amount").trim()
                            when {
                                name.isBlank() -> null
                                amount.isBlank() -> name
                                else -> "$name：$amount"
                            }
                        } else {
                            arr.optString(i).trim().takeIf { it.isNotBlank() }
                        }
                    }
                }.getOrDefault(emptyList())
                val advice = parsed.optString("advice").trim().takeIf { it.isNotBlank() }
                AiAnalysis(
                    createdAtEpochMillis = System.currentTimeMillis(),
                    query = "",
                    summary = parsed.optString("summary"),
                    insights = topCategories,
                    tips = listOfNotNull(advice),
                ).takeIf {
                    it.summary.isNotBlank() || it.insights.isNotEmpty() || it.tips.isNotEmpty()
                }
            }
        }.getOrNull()
    }
}

/** AI 呼叫失敗的包裝例外（供 Result 回傳用；非 coroutine 取消） */
class AiCallException(val error: com.hmoney.app.domain.model.AiError) :
    Exception(error.toString())

/**
 * 從 API 原始回應 JSON 取出正文。
 * Session 31 決策（請勿誤改）：mistral-medium-latest／mistral-large-latest 會輸出
 * thinking 區塊，choices[0].message.content 可能是三種形態：
 * a. 純字串 → 直接使用（Session 10 既有行為，保留）
 * b. JSON 陣列 → 只串接 type == "text" 區塊的 text 欄位；type == "thinking" 一律丟棄
 * c. JSON 物件 → 只取 text 欄位，thinking 一律丟棄
 * 取出正文後防禦性清理：殘留 {"type": 開頭的 JSON 片段，能解析就抽 text，
 * 無法解析則移除該片段——絕不讓 JSON 或內部思考洩漏到 UI。
 * 延續 Session 10 extractContent 決策的相容性擴充，勿改回純字串處理。
 */
/** 從 API 原始回應取 choices[0].message.content 元素（三形態共用，Session 39 抽出） */
internal fun rawAiContent(raw: String): Any? = runCatching {
    JSONObject(raw)
        .getJSONArray("choices")
        .getJSONObject(0)
        .getJSONObject("message")
        .get("content")
}.getOrNull()

internal fun extractAiContent(raw: String): String? = runCatching {
    val content = rawAiContent(raw) ?: return@runCatching null
    when (content) {
        is String -> collapseFullTextDuplication(sanitizeAiText(content))
        is JSONArray -> {
            var merged = ""
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") != "text") continue
                merged = mergeBlocksWithOverlapDedup(merged, block.optString("text"))
            }
            collapseFullTextDuplication(sanitizeAiText(merged))
        }
        is JSONObject -> collapseFullTextDuplication(sanitizeAiText(content.optString("text")))
        else -> null
    }
}.getOrNull()

/**
 * 全文層重複摺疊（Session 33 決策，請勿誤改）：
 * 模型端 reasoning 洩漏退化會讓「整段回覆」在同一則訊息內逐字重複兩次，
 * 區塊層 mergeBlocksWithOverlapDedup 涵蓋不到單一 text 區塊內部的重複，
 * 因此在 sanitizeAiText 之後、回傳前做最後一道防禦：
 * 1. 整段兩半完全逐字相同（段落或中點切分，容許中間一個分隔字元）只保留一份；
 * 2. 段落序列去重——與先前完整段落完全相同的段落丟棄。
 * 刻意保守：只做「完全逐字相同」比對，不做相似度模糊比對、不刪短句；
 * 門檻 >= 20 字元才處理，避免誤傷「好」「1」等短行與列點開頭語。請勿改為模糊比對。
 * 處理後若為空白回 null。
 */
internal fun collapseFullTextDuplication(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val folded = foldExactHalfDuplication(trimmed)
    val seen = mutableSetOf<String>()
    val deduped = folded.split('\n').filter { line ->
        line.trim().length < AI_FULL_TEXT_DEDUP_MIN_LENGTH || seen.add(line.trim())
    }
    return deduped.joinToString("\n").trim().ifBlank { null }
}

private const val AI_FULL_TEXT_DEDUP_MIN_LENGTH = 20

/** 整段兩半完全逐字相同時只保留一份（切分點：換行邊界與中點，容許中間一個分隔字元） */
private fun foldExactHalfDuplication(text: String): String {
    if (text.length < AI_FULL_TEXT_DEDUP_MIN_LENGTH * 2) return text
    val candidates = mutableListOf<Int>()
    for (i in text.indices) {
        if (text[i] == '\n') candidates.add(i)
    }
    candidates.add(text.length / 2)
    for (i in candidates) {
        if (i <= 0 || i >= text.length) continue
        val head = text.substring(0, i).trim()
        val tail = text.substring(i).trim()
        if (head.length >= AI_FULL_TEXT_DEDUP_MIN_LENGTH && head == tail) return head
    }
    if (text.length % 2 == 1) {
        val mid = text.length / 2
        val head = text.substring(0, mid)
        val tail = text.substring(mid + 1)
        if (head.length >= AI_FULL_TEXT_DEDUP_MIN_LENGTH && head == tail) return head
    }
    return text
}

private val aiJsonFragmentRegex = Regex("\\{\\s*\"type\"\\s*:")

/**
 * 區塊層重疊去重（Session 32 決策，請勿誤改）：
 * 後一個 text 區塊以先前已累積內容為開頭（先前內容是前綴或兩者相同）時，
 * 丟棄較短者只保留最長；完全不重疊才正常串接。
 * 只做區塊層去重，不做任意句子層去重——避免誤刪模型刻意的重複表述。
 */
private fun mergeBlocksWithOverlapDedup(existing: String, next: String): String = when {
    next.isBlank() -> existing
    existing.isBlank() -> next.trim()
    next.trim() == existing.trim() -> existing
    next.trim().startsWith(existing.trim()) -> next.trim()
    existing.trim().startsWith(next.trim()) -> existing
    else -> existing + next
}

/** 從 start 起比對出完整平衡的 JSON 物件字串（尊重字串內的括號與跳脫） */
private fun extractBalancedJsonObject(text: String, start: Int): String? {
    if (text[start] != '{') return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
        when {
            inString -> when {
                escaped -> escaped = false
                text[i] == '\\' -> escaped = true
                text[i] == '"' -> inString = false
            }
            text[i] == '"' -> inString = true
            text[i] == '{' -> depth++
            text[i] == '}' -> {
                depth--
                if (depth == 0) return text.substring(start, i + 1)
            }
        }
    }
    return null
}

/**
 * 防禦性清理：整段為內容區塊陣列字串（雙重編碼）時結構化抽 text；
 * 其餘移除或抽換殘留的 {"type": JSON 片段，thinking／無法解析者一律丟棄。
 * 只處理「[」開頭的整段陣列，不碰「{」開頭的字串——AI 篩選／分類建議本來就回傳 JSON 物件，不可誤傷。
 */
private fun sanitizeAiText(text: String, depth: Int = 0): String {
    var result = text.trim()
    if (depth < 3 && result.startsWith("[")) {
        val arr = runCatching { JSONArray(result) }.getOrNull()
        if (arr != null) {
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val block = arr.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") sb.append(block.optString("text"))
            }
            return sanitizeAiText(sb.toString(), depth + 1)
        }
    }
    var guard = 0
    while (guard++ < 10) {
        val match = aiJsonFragmentRegex.find(result) ?: break
        val start = match.range.first
        val json = extractBalancedJsonObject(result, start)
        result = if (json != null) {
            val inner = runCatching { JSONObject(json).optString("text") }.getOrNull()
            result.substring(0, start) + inner.orEmpty() + result.substring(start + json.length)
        } else {
            result.substring(0, start)
        }
    }
    return result.trim()
}
