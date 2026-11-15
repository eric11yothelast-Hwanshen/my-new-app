package com.hmoney.app.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hmoney.app.data.notification.Notifier
import com.hmoney.app.data.work.DailyReminderWorker
import com.hmoney.app.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日記帳提醒排程：
 * - 精確需求走 exact alarm（SCHEDULE_EXACT_ALARM，使用者於設定頁引導授予）
 * - 精確鬧鐘被系統收回時，fallback 改用 WorkManager 彈性排程（每日）
 */
@Singleton
class DailyReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val notifier: Notifier,
    @com.hmoney.app.di.ApplicationScope private val scope: CoroutineScope,
) {

    /** 依目前設定重排（App 啟動、開機、設定變更時呼叫） */
    fun reschedule() {
        scope.launch {
            val settings = settingsRepository.observeReminder().first()
            if (settings.enabled) {
                schedule(settings.hour, settings.minute)
            } else {
                cancel()
            }
        }
    }

    fun schedule(hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (canExact) {
            cancelFallbackWork()
            val nextTime = nextOccurrence(hour, minute)
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTime,
                pendingIntent(),
            )
        } else {
            // fallback：精確鬧鐘被收回，改用 WorkManager 每日彈性排程
            scheduleFallbackWork()
        }
    }

    fun cancel() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent())
        cancelFallbackWork()
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun nextOccurrence(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun scheduleFallbackWork() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FALLBACK_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DailyReminderWorker>(Duration.ofDays(1)).build(),
        )
    }

    private fun cancelFallbackWork() {
        WorkManager.getInstance(context).cancelUniqueWork(FALLBACK_WORK)
    }

    companion object {
        private const val REQUEST_CODE = 1001
        private const val FALLBACK_WORK = "daily_reminder_fallback"
    }
}

/** 精確鬧鐘觸發：推播每日記帳提醒 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: Notifier
    @Inject lateinit var scheduler: DailyReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        // 推播提醒後，排下一次
        notifier.post(
            channelId = Notifier.Channels.DAILY_REMINDER,
            notificationId = 1001,
            title = context.getString(com.hmoney.app.R.string.notification_daily_reminder),
            content = context.getString(com.hmoney.app.R.string.notification_daily_reminder_content),
            intent = notifier.appPendingIntent(),
        )
        scheduler.reschedule()
    }
}
