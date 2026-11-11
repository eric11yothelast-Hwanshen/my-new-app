package com.hmoney.app.data.repository

import com.hmoney.app.data.ai.AiApi
import com.hmoney.app.data.ai.AiConfigStore
import com.hmoney.app.data.db.dao.AiAnalysisDao
import com.hmoney.app.data.db.entity.AiAnalysisEntity
import com.hmoney.app.domain.model.AiAnalysis
import com.hmoney.app.domain.model.AiConfig
import com.hmoney.app.domain.model.AiError
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.TransactionType
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

    /** 執行 API 呼叫並保存歷史；所有錯誤轉為 AiOutcome.Failure */
    private suspend fun execute(
        config: AiConfig,
        queryLabel: String,
        systemPrompt: String,
        userPrompt: String,
        expectJson: Boolean = true,
    ): AiOutcome {
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

        val content = try {
            val response = aiApi.chat(
                url = endpoint,
                authorization = "Bearer ${config.apiKey}",
                body = body,
            )
            when {
                response.code() == 401 || response.code() == 403 ->
                    return AiOutcome.Failure(AiError.Unauthorized)
                response.code() == 429 ->
                    return AiOutcome.Failure(AiError.QuotaExceeded)
                !response.isSuccessful ->
                    return AiOutcome.Failure(AiError.Http(response.code()))
                else -> response.body()?.string() ?: return AiOutcome.Failure(AiError.Parse)
            }
        } catch (e: IOException) {
            return AiOutcome.Failure(AiError.Network)
        } catch (e: HttpException) {
            return AiOutcome.Failure(AiError.Http(e.code()))
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
