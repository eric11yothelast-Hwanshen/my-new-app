package com.hmoney.app.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hmoney.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知系統：四個獨立 channel（可個別關閉）：
 * 每日提醒、預算警示、每週摘要、週期交易入帳。
 */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    object Channels {
        const val DAILY_REMINDER = "daily_reminder"
        const val BUDGET_ALERT = "budget_alert"
        const val WEEKLY_SUMMARY = "weekly_summary"
        const val RECURRING = "recurring"
        const val ITEM_EXPIRY = "item_expiry"
    }

    /** 建立所有 channel（App 啟動時呼叫一次） */
    fun ensureChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(
                Channels.DAILY_REMINDER,
                context.getString(R.string.notification_daily_reminder),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            NotificationChannel(
                Channels.BUDGET_ALERT,
                context.getString(R.string.notification_budget_warning_title),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            NotificationChannel(
                Channels.WEEKLY_SUMMARY,
                context.getString(R.string.notification_weekly_title),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            NotificationChannel(
                Channels.RECURRING,
                context.getString(R.string.notification_recurring_title),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            NotificationChannel(
                Channels.RECURRING,
                context.getString(R.string.notification_recurring_title),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            NotificationChannel(
                Channels.ITEM_EXPIRY,
                context.getString(R.string.notification_item_expiry),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    /** 開啟 App 主畫面的 PendingIntent（一律 FLAG_IMMUTABLE）；可指定開啟頁面 */
    fun appPendingIntent(route: String? = null): android.app.PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return android.app.PendingIntent.getActivity(
                context, 0,
                android.content.Intent(),
                android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        if (route != null) {
            intent.putExtra(EXTRA_OPEN_ROUTE, route)
        }
        return android.app.PendingIntent.getActivity(
            context,
            route?.hashCode() ?: 0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or
                android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val EXTRA_OPEN_ROUTE = "open_route"
    }

    /** 是否有通知權限（API 33+ 動態請求） */
    fun hasNotificationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission")
    fun post(
        channelId: String,
        notificationId: Int,
        title: String,
        content: String,
        intent: android.app.PendingIntent,
    ) {
        // 無權限時靜默略過，不得崩潰
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
