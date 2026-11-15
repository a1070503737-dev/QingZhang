package com.zhuangxiu.ledger.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zhuangxiu.ledger.MainActivity
import com.zhuangxiu.ledger.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 本地通知的统一出口 */
@Singleton
class Notifier @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** 创建通知渠道（重复调用是安全的） */
    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PAYMENT, "待付款提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "待付款到期前提醒"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BUDGET, "预算预警", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "预算用到 80% 或超支时提醒"
            },
        )
    }

    /** Android 13+ 需要用户授予通知权限 */
    fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** 待付款到期提醒；每笔账单一条通知，点击进入账单详情 */
    fun notifyPaymentDue(expenseId: Long, title: String, text: String) {
        show(CHANNEL_PAYMENT, ID_PAYMENT_BASE + (expenseId % 100_000).toInt(), title, text, expenseId)
    }

    /** 预算预警；每项预算一条通知 */
    fun notifyBudget(categoryId: Long, title: String, text: String) {
        show(CHANNEL_BUDGET, ID_BUDGET_BASE + (categoryId % 100_000).toInt(), title, text, null)
    }

    private fun show(channel: String, notificationId: Int, title: String, text: String, expenseId: Long?) {
        if (!canNotify()) return
        ensureChannels()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            expenseId?.let { putExtra(MainActivity.EXTRA_EXPENSE_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // 用户刚好撤销了通知权限，忽略即可
        }
    }

    private companion object {
        const val CHANNEL_PAYMENT = "payment_due"
        const val CHANNEL_BUDGET = "budget_alert"
        const val ID_PAYMENT_BASE = 100_000
        const val ID_BUDGET_BASE = 300_000
    }
}
