package com.zhuangxiu.ledger.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.common.api.ApiException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies { fun driveBackup(): DriveBackup }

    override suspend fun doWork(): Result {
        val backup = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).driveBackup()
        return try {
            backup.runBackup(automatic = inputData.getBoolean(AUTOMATIC, false))
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val retryable = when (error) {
                is DriveAuthorizationRequired -> false
                is DriveHttpError -> error.retryable
                is ApiException -> error.statusCode == 7
                else -> error is IOException
            }
            // A periodic failure must not disable subsequent scheduled backups.
            if (retryable && runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "google_drive_periodic_backup"
        private const val NOW_NAME = "google_drive_backup_now"
        private const val AUTOMATIC = "automatic"
        private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context, intervalDays: Int) {
            val manager = WorkManager.getInstance(context)
            if (intervalDays == 0) {
                manager.cancelUniqueWork(PERIODIC_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(intervalDays.toLong(), TimeUnit.DAYS)
                .setInputData(workDataOf(AUTOMATIC to true))
                .setInitialDelay(intervalDays.toLong(), TimeUnit.DAYS)
                .setConstraints(connected)
                .build()
            manager.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DriveBackupWorker>().setConstraints(connected).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun cancelAll(context: Context) {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork(PERIODIC_NAME)
            manager.cancelUniqueWork(NOW_NAME)
        }
    }
}
