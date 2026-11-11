package com.hmoney.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hmoney.app.data.notification.Notifier
import com.hmoney.app.data.scheduler.DailyReminderScheduler
import com.hmoney.app.data.work.WorkScheduler
import com.hmoney.app.di.ApplicationScope
import com.hmoney.app.domain.repository.RecurringRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 應用程式進入點：
 * - Hilt 全域元件由此建立；WorkManager 使用 HiltWorkerFactory
 * - 啟動時：建立通知渠道、檢查週期交易到期、重排 WorkManager 與每日提醒
 */
@HiltAndroidApp
class HmoneyApp : Application(), Configuration.Provider {

    @Inject lateinit var recurringRepository: RecurringRepository
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject lateinit var dailyReminderScheduler: DailyReminderScheduler
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 四個獨立通知渠道（可個別關閉）
        notifier.ensureChannels()
        // 啟動時補產已到期之週期交易
        applicationScope.launch {
            runCatching { recurringRepository.generateDue() }
        }
        // 重排背景任務與每日提醒（開機後亦由 BootCompletedReceiver 重排）
        runCatching { workScheduler.scheduleAll() }
        dailyReminderScheduler.reschedule()
    }
}
