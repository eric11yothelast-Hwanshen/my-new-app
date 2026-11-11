package com.hmoney.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.hmoney.app.data.db.HmoneyDatabase
import com.hmoney.app.data.db.entity.AccountEntity
import com.hmoney.app.data.db.entity.BudgetEntity
import com.hmoney.app.data.db.entity.CategoryEntity
import com.hmoney.app.data.db.entity.RecurringTransactionEntity
import com.hmoney.app.data.db.entity.TransactionEntity
import com.hmoney.app.ui.common.Formats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 備份管理：
 * - 完整 JSON 備份／還原（全部資料表）
 * - 手動匯出 CSV（交易明細）
 * - 每日自動本地備份（filesDir/backups/auto，保留近 7 份，走 WorkManager）
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: HmoneyDatabase,
) {

    // ===== 完整 JSON 備份 =====

    /** 匯出完整 JSON 到使用者指定位置 */
    suspend fun exportJsonTo(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = buildFullJson()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toString().toByteArray(Charsets.UTF_8))
            } ?: error("無法開啟輸出")
        }
    }

    /** 從 JSON 還原：覆蓋式重建全部資料表（單一交易） */
    suspend fun restoreFrom(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: error("無法開啟備份檔")
            val root = JSONObject(text)

            val accounts = jsonArrayToList(root.optJSONArray("accounts")) { AccountEntity(
                id = it.optLong("id"), name = it.getString("name"), iconKey = it.getString("iconKey"),
                colorArgb = it.optLong("colorArgb"), initialBalanceCents = it.optLong("initialBalanceCents"),
                currency = it.optString("currency", "TWD"), isHidden = it.optBoolean("isHidden"),
                sortOrder = it.optInt("sortOrder"),
            ) }
            val categories = jsonArrayToList(root.optJSONArray("categories")) { CategoryEntity(
                id = it.optLong("id"), name = it.getString("name"), type = it.getString("type"),
                iconKey = it.getString("iconKey"), colorArgb = it.optLong("colorArgb"),
                isHidden = it.optBoolean("isHidden"), sortOrder = it.optInt("sortOrder"),
            ) }
            val transactions = jsonArrayToList(root.optJSONArray("transactions")) { TransactionEntity(
                id = it.optLong("id"), type = it.getString("type"),
                amountCents = it.optLong("amountCents"), accountId = it.optLong("accountId"),
                toAccountId = if (it.isNull("toAccountId")) null else it.optLong("toAccountId"),
                categoryId = if (it.isNull("categoryId")) null else it.optLong("categoryId"),
                feeCents = it.optLong("feeCents"), dateEpochMillis = it.optLong("dateEpochMillis"),
                note = it.optString("note"), tags = it.optString("tags"),
            ) }
            val budgets = jsonArrayToList(root.optJSONArray("budgets")) { BudgetEntity(
                id = it.optLong("id"), type = it.getString("type"),
                categoryId = if (it.isNull("categoryId")) null else it.optLong("categoryId"),
                amountCents = it.optLong("amountCents"),
            ) }
            val recurring = jsonArrayToList(root.optJSONArray("recurring")) { RecurringTransactionEntity(
                id = it.optLong("id"), type = it.getString("type"),
                amountCents = it.optLong("amountCents"), accountId = it.optLong("accountId"),
                categoryId = it.optLong("categoryId"), note = it.optString("note"),
                frequency = it.getString("frequency"),
                dayOfMonth = if (it.isNull("dayOfMonth")) null else it.optInt("dayOfMonth"),
                dayOfWeek = if (it.isNull("dayOfWeek")) null else it.optInt("dayOfWeek"),
                nextDueEpochMillis = it.optLong("nextDueEpochMillis"),
                isEnabled = it.optBoolean("isEnabled", true),
            ) }

            database.withTransaction {
                database.accountDao().deleteAll()
                database.categoryDao().deleteAll()
                database.transactionDao().deleteAll()
                database.budgetDao().deleteAll()
                database.recurringTransactionDao().deleteAll()
                database.accountDao().insertAll(accounts)
                database.categoryDao().insertAll(categories)
                database.transactionDao().insertAll(transactions)
                database.budgetDao().insertAll(budgets)
                database.recurringTransactionDao().insertAll(recurring)
            }
        }
    }

    // ===== CSV 匯出 =====

    /** 匯出交易明細 CSV（UTF-8 with BOM，Excel 可直接開啟） */
    suspend fun exportCsvTo(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val accountNames = database.accountDao().observeAll().first()
                .associate { it.id to it.name }
            val categoryNames = (
                database.categoryDao().observeAllOfType("EXPENSE").first() +
                    database.categoryDao().observeAllOfType("INCOME").first()
                ).associate { it.id to it.name }
            val transactions = database.transactionDao().observeFiltered().first()

            val sb = StringBuilder()
            sb.append("\uFEFF") // BOM
            sb.appendLine("日期,類型,金額,帳戶,分類,備註,標籤")
            transactions.forEach { tx ->
                val type = when (tx.type) {
                    "EXPENSE" -> "支出"
                    "INCOME" -> "收入"
                    else -> "轉帳"
                }
                val row = listOf(
                    Formats.date(tx.dateEpochMillis),
                    type,
                    Formats.plain(tx.amountCents),
                    accountNames[tx.accountId] ?: "",
                    tx.categoryId?.let { categoryNames[it] } ?: "",
                    tx.note,
                    tx.tags,
                ).joinToString(",") { csvEscape(it) }
                sb.appendLine(row)
            }
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(sb.toString().toByteArray(Charsets.UTF_8))
            } ?: error("無法開啟輸出")
        }
    }

    // ===== 每日自動本地備份 =====

    /** 自動備份到應用私有目錄，保留近 7 份 */
    suspend fun runDailyBackup(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "backups/auto").apply { mkdirs() }
            val today = LocalDate.now(ZoneId.systemDefault())
            val file = File(dir, "hmoney-$today.json")
            file.writeText(buildFullJson().toString(), Charsets.UTF_8)
            // 保留近 7 份（依檔名日期排序刪除舊檔）
            dir.listFiles()?.filter { it.extension == "json" }
                ?.sortedBy { it.name }
                ?.dropLast(KEEP_COUNT)
                ?.forEach { it.delete() }
            file
        }
    }

    /** 最近一次自動備份的時間（無則 null） */
    suspend fun latestAutoBackupMillis(): Long? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "backups/auto")
        dir.listFiles()?.filter { it.extension == "json" }
            ?.maxByOrNull { it.name }?.lastModified()
    }

    // ===== 內部工具 =====

    private suspend fun buildFullJson(): JSONObject {
        val accounts = database.accountDao().observeAll().first()
        val categories = (
            database.categoryDao().observeAllOfType("EXPENSE").first() +
                database.categoryDao().observeAllOfType("INCOME").first()
            )
        val transactions = database.transactionDao().observeFiltered().first()
        val budgets = database.budgetDao().observeAll().first()
        val recurring = database.recurringTransactionDao().observeAll().first()

        return JSONObject().apply {
            put("version", 3)
            put("accounts", JSONArray().apply { accounts.forEach { put(entityToJson(it)) } })
            put("categories", JSONArray().apply { categories.forEach { put(entityToJson(it)) } })
            put("transactions", JSONArray().apply { transactions.forEach { put(entityToJson(it)) } })
            put("budgets", JSONArray().apply { budgets.forEach { put(entityToJson(it)) } })
            put("recurring", JSONArray().apply { recurring.forEach { put(entityToJson(it)) } })
        }
    }

    private fun entityToJson(e: Any): JSONObject = when (e) {
        is AccountEntity -> JSONObject().apply {
            put("id", e.id); put("name", e.name); put("iconKey", e.iconKey)
            put("colorArgb", e.colorArgb); put("initialBalanceCents", e.initialBalanceCents)
            put("currency", e.currency); put("isHidden", e.isHidden); put("sortOrder", e.sortOrder)
        }
        is CategoryEntity -> JSONObject().apply {
            put("id", e.id); put("name", e.name); put("type", e.type)
            put("iconKey", e.iconKey); put("colorArgb", e.colorArgb)
            put("isHidden", e.isHidden); put("sortOrder", e.sortOrder)
        }
        is TransactionEntity -> JSONObject().apply {
            put("id", e.id); put("type", e.type); put("amountCents", e.amountCents)
            put("accountId", e.accountId); put("toAccountId", e.toAccountId)
            put("categoryId", e.categoryId); put("feeCents", e.feeCents)
            put("dateEpochMillis", e.dateEpochMillis); put("note", e.note); put("tags", e.tags)
        }
        is BudgetEntity -> JSONObject().apply {
            put("id", e.id); put("type", e.type); put("categoryId", e.categoryId)
            put("amountCents", e.amountCents)
        }
        is RecurringTransactionEntity -> JSONObject().apply {
            put("id", e.id); put("type", e.type); put("amountCents", e.amountCents)
            put("accountId", e.accountId); put("categoryId", e.categoryId); put("note", e.note)
            put("frequency", e.frequency); put("dayOfMonth", e.dayOfMonth)
            put("dayOfWeek", e.dayOfWeek); put("nextDueEpochMillis", e.nextDueEpochMillis)
            put("isEnabled", e.isEnabled)
        }
        else -> JSONObject()
    }

    private fun <T> jsonArrayToList(
        array: JSONArray?,
        mapper: (JSONObject) -> T,
    ): List<T> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { mapper(array.getJSONObject(it)) }
    }

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    companion object {
        private const val KEEP_COUNT = 7
    }
}
