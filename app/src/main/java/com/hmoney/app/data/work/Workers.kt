package com.hmoney.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hmoney.app.R
import com.hmoney.app.data.notification.Notifier
import com.hmoney.app.domain.model.BudgetType
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.BudgetRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.RecurringRepository
import com.hmoney.app.domain.repository.SettingsRepository
import com.hmoney.app.domain.repository.TransactionRepository
import com.hmoney.app.ui.common.Formats
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/** AI 助手頁導覽路徑（通知深連結用） */
const val AI_ASSISTANT_ROUTE = "ai_assistant"

/**
 * 每日記帳提醒的 fallback Worker（精確鬧鐘被系統收回時使用）。
 */
@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        notifier.post(
            channelId = Notifier.Channels.DAILY_REMINDER,
            notificationId = 1001,
            title = applicationContext.getString(R.string.notification_daily_reminder),
            content = applicationContext.getString(R.string.notification_daily_reminder_content),
            intent = notifier.appPendingIntent(),
        )
        return Result.success()
    }
}

/**
 * 預算警示 Worker：月預算／分類預算達 80%／100% 時各推一次（同月不重複轟炸）。
 */
@HiltWorker
class BudgetCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val monthStart = today.withDayOfMonth(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val monthEnd = today.plusMonths(1).withDayOfMonth(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val monthKey = "%04d%02d".format(today.year, today.monthValue)

        val transactions = transactionRepository.observeTransactions(
            startDate = monthStart,
            endDate = monthEnd,
        ).first()
        val spentByCategory = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }
        val totalSpent = spentByCategory.values.sum()

        val budgets = budgetRepository.observeAll().first()
        val categories = categoryRepository.observeAll(CategoryType.EXPENSE).first()
            .associateBy { it.id }

        // 月總預算警示（同月僅推一次）
        val total = budgets.firstOrNull { it.type == BudgetType.TOTAL }
        if (total != null && total.amountCents > 0) {
            checkAndNotify(
                flagBase = "budget_total_${monthKey}",
                budgetCents = total.amountCents,
                spentCents = totalSpent,
                titleWarn = applicationContext.getString(R.string.notification_budget_warning_title),
                contentWarn = applicationContext.getString(R.string.notification_budget_warning_80),
                contentOver = applicationContext.getString(R.string.notification_budget_warning_100),
                idBase = 2001,
            )
        }

        // 分類預算警示
        budgets.filter { it.type == BudgetType.CATEGORY }.forEach { budget ->
            val category = categories[budget.categoryId] ?: return@forEach
            val spent = spentByCategory[budget.categoryId] ?: 0L
            checkAndNotify(
                flagBase = "budget_cat_${budget.categoryId}_$monthKey",
                budgetCents = budget.amountCents,
                spentCents = spent,
                titleWarn = category.name,
                contentWarn = applicationContext.getString(
                    R.string.notification_budget_category_80,
                    category.name,
                ),
                contentOver = applicationContext.getString(
                    R.string.notification_budget_category_100,
                    category.name,
                ),
                idBase = 2000 + (budget.categoryId ?: 0L).toInt() % 1000,
            )
        }
        return Result.success()
    }

    private suspend fun checkAndNotify(
        flagBase: String,
        budgetCents: Long,
        spentCents: Long,
        titleWarn: String,
        contentWarn: String,
        contentOver: String,
        idBase: Int,
    ) {
        val ratio = spentCents.toFloat() / budgetCents
        when {
            ratio >= 1f -> {
                if (!settingsRepository.getFlag("${flagBase}_over")) {
                    notifier.post(
                        channelId = Notifier.Channels.BUDGET_ALERT,
                        notificationId = idBase + 1,
                        title = titleWarn,
                        content = contentOver,
                        intent = notifier.appPendingIntent(),
                    )
                    settingsRepository.setFlag("${flagBase}_over", true)
                    settingsRepository.setFlag("${flagBase}_warn", true)
                }
            }
            ratio >= 0.8f -> {
                if (!settingsRepository.getFlag("${flagBase}_warn")) {
                    notifier.post(
                        channelId = Notifier.Channels.BUDGET_ALERT,
                        notificationId = idBase,
                        title = titleWarn,
                        content = contentWarn,
                        intent = notifier.appPendingIntent(),
                    )
                    settingsRepository.setFlag("${flagBase}_warn", true)
                }
            }
        }
    }
}

/**
 * 每週摘要 Worker：推播本週總支出與最大分類。
 * （AI 分析頁於後續階段加入後，點擊將直接開啟該頁）
 */
@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays(7)
        val transactions = transactionRepository.observeTransactions(
            startDate = weekStart.atStartOfDay(zone).toInstant().toEpochMilli(),
            endDate = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        ).first()
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        val total = expenses.sumOf { it.amountCents }
        val topCategory = expenses
            .groupBy { it.categoryId }
            .maxByOrNull { (_, txs) -> txs.sumOf { it.amountCents } }
            ?.let { (categoryId, _) ->
                categoryRepository.observeAll(CategoryType.EXPENSE).first()
                    .firstOrNull { it.id == categoryId }?.name
            } ?: "—"
        notifier.post(
            channelId = Notifier.Channels.WEEKLY_SUMMARY,
            notificationId = 3001,
            title = applicationContext.getString(R.string.notification_weekly_title),
            content = applicationContext.getString(
                R.string.notification_weekly_content,
                Formats.moneyWithCurrency(total),
                topCategory,
            ),
            // 點擊直接開啟 AI 分析頁
            intent = notifier.appPendingIntent(route = AI_ASSISTANT_ROUTE),
        )
        return Result.success()
    }
}

/**
 * 週期交易入帳 Worker：產生到期記錄並推播筆數。
 */
@HiltWorker
class RecurringGenerationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recurringRepository: RecurringRepository,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val generated = runCatching { recurringRepository.generateDue() }.getOrDefault(0)
        if (generated > 0) {
            notifier.post(
                channelId = Notifier.Channels.RECURRING,
                notificationId = 4001,
                title = applicationContext.getString(R.string.notification_recurring_title),
                content = applicationContext.getString(
                    R.string.notification_recurring_content,
                    generated,
                ),
                intent = notifier.appPendingIntent(),
            )
        }
        return Result.success()
    }
}
