package com.zhuangxiu.ledger.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 每日提醒任务：由 WorkManager 每天运行一次，
 * 检查待付款到期和预算使用情况并发出本地通知。
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    /** Worker 由 WorkManager 创建，通过 EntryPoint 从 Hilt 取依赖 */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun reminderChecker(): ReminderChecker
    }

    override suspend fun doWork(): Result {
        val checker = EntryPointAccessors
            .fromApplication(applicationContext, Dependencies::class.java)
            .reminderChecker()
        return try {
            checker.checkDuePayments()
            checker.checkBudgets()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "daily_reminder"

        /** 注册每日任务（已注册则保持不变）。首次运行安排在下一个早上 9 点。 */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(LocalTime.of(9, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
