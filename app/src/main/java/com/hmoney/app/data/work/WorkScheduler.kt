package com.hmoney.app.data.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager 排程主體：週期交易產生（每日）、預算檢查（每日）、
 * 每週摘要（每週日晚間）。不依賴常駐 Service。
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun scheduleAll() {
        val workManager = WorkManager.getInstance(context)

        // 週期交易產生：每日
        workManager.enqueueUniquePeriodicWork(
            "recurring_generation",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RecurringGenerationWorker>(Duration.ofDays(1)).build(),
        )

        // 預算警示：每日
        workManager.enqueueUniquePeriodicWork(
            "budget_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BudgetCheckWorker>(Duration.ofDays(1)).build(),
        )

        // 每日自動本地備份（保留近 7 份，由 BackupManager 清理）
        workManager.enqueueUniquePeriodicWork(
            "daily_backup",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BackupWorker>(Duration.ofDays(1)).build(),
        )

        // 每週摘要：每週日 20:00（對齊到下次週日 20:00 起算）
        val now = LocalDateTime.now()
        var next = now.toLocalDate().with(DayOfWeek.SUNDAY).atTime(20, 0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val initialDelay = Duration.between(
            now,
            next.atZone(ZoneId.systemDefault()).toInstant(),
        )
        workManager.enqueueUniquePeriodicWork(
            "weekly_summary",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WeeklySummaryWorker>(Duration.ofDays(7))
                .setInitialDelay(initialDelay)
                .build(),
        )
    }
}
