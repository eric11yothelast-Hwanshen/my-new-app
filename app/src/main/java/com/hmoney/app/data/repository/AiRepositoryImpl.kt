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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

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

    override suspend fun chat(question: String): AiTextResult {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekData = buildWeekData(today.minusDays(7), today, today.minusDays(14), zone)
        val monthData = buildMonthData(today.withDayOfMonth(1), today, zone)
        val accountsData = buildAccountsData()
        val systemPrompt = "你是一位專業的個人財務助理，請以繁體中文回答使用者的問題。" +
            "回答精簡有條理，並基於提供的記帳資料。"
        return callRaw(
            systemPrompt = systemPrompt,
            userPrompt = "記帳資料（已精簡去敏感化）：\n$weekData\n$monthData\n$accountsData\n\n使用者問題：$question",
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
            並以繁體中文回傳 JSON（格式：{"summary": "總結", "insights": ["分類洞察", ...],
            "anomalies": ["異常支出偵測", ...], "tips": ["具體省錢建議", ...]}）。
            只回傳 JSON，不要加其他文字。
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
    private suspend fun callRaw(systemPrompt: String, userPrompt: String): AiTextResult {
        val config = configStore.read()
        if (!config.isConfigured) return AiTextResult.Failure(AiError.NotConfigured)
        return when (val raw = request(config, systemPrompt, userPrompt)) {
            is String -> when (val text = extractContent(raw)) {
                null -> AiTextResult.Failure(AiError.Parse)
                else -> AiTextResult.Success(text)
            }
            is AiError -> AiTextResult.Failure(raw)
            else -> AiTextResult.Failure(AiError.Parse)
        }
    }

    /** 從 API 原始回應 JSON 取出 choices[0].message.content */
    private fun extractContent(raw: String): String? = runCatching {
        JSONObject(raw)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }.getOrNull()

    /** 共用 HTTP 請求：回傳模型回覆內容（String）或 AiError */
    private suspend fun request(
        config: AiConfig,
        systemPrompt: String,
        userPrompt: String,
    ): Any {
        val body = JSONObject().apply {
            put("model", config.model)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt)),
            )
            put("temperature", 0.4)
        }.toString().toRequestBody(jsonMediaType)

        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"

        return try {
            val response = aiApi.chat(
                url = endpoint,
                authorization = "Bearer ${config.apiKey}",
                body = body,
            )
            when {
                response.code() == 401 || response.code() == 403 -> AiError.Unauthorized
                response.code() == 429 -> AiError.QuotaExceeded
                !response.isSuccessful -> AiError.Http(response.code())
                else -> response.body()?.string() ?: AiError.Parse
            }
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
                fun lines(key: String): List<String> = runCatching {
                    val arr = parsed.getJSONArray(key)
                    (0 until arr.length()).map { arr.getString(it) }
                }.getOrDefault(emptyList())
                AiAnalysis(
                    createdAtEpochMillis = System.currentTimeMillis(),
                    query = "",
                    summary = parsed.optString("summary"),
                    insights = lines("insights"),
                    anomalies = lines("anomalies"),
                    tips = lines("tips"),
                )
            }
        }.getOrNull()
    }
}

/** AI 呼叫失敗的包裝例外（供 Result 回傳用；非 coroutine 取消） */
class AiCallException(val error: com.hmoney.app.domain.model.AiError) :
    Exception(error.toString())
