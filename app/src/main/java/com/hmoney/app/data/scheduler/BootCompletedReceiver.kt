package com.hmoney.app.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hmoney.app.data.work.WorkScheduler
import com.hmoney.app.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 開機完成後重新排程所有 WorkManager 任務與每日提醒精確鬧鐘。
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var workScheduler: WorkScheduler

    @Inject lateinit var dailyReminderScheduler: DailyReminderScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                workScheduler.scheduleAll()
                dailyReminderScheduler.reschedule()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
