package com.zhuangxiu.ledger.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.zhuangxiu.ledger.data.repository.BackupManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

internal data class DriveBackupStatus(
    val account: DriveAccount? = null,
    val intervalDays: Int = 0,
    val lastUploaded: Instant? = null,
    val issue: String? = null,
    val keepLatest: Boolean = false,
)

internal class DriveAuthorizationRequired : IOException("请重新授权已连接的 Google 账号")
internal class DriveAuthorizationIncomplete : IOException("Google 授权未完成。请重新连接并确认授权。")

@Singleton
internal class DriveBackup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val backups: BackupManager,
) {
    private val auth by lazy { Identity.getAuthorizationClient(context) }
    private val drive = GoogleDriveFiles()
    private val operations = Mutex()
    val status = dataStore.data.map { prefs ->
        val account = prefs[ACCOUNT_ID]?.let { id -> prefs[ACCOUNT_EMAIL]?.let { email -> DriveAccount(id, email) } }
        DriveBackupStatus(
            account,
            prefs[INTERVAL]?.takeIf { it in VALID_INTERVALS } ?: 0,
            prefs[LAST_UPLOADED]?.let(Instant::ofEpochMilli),
            prefs[ISSUE],
            prefs[KEEP_LATEST] ?: false,
        )
    }

    suspend fun requestAuthorization(): AuthorizationResult = authorize(status.first().account)

    fun finishResolution(data: Intent?): AuthorizationResult =
        auth.getAuthorizationResultFromIntent(data ?: throw DriveAuthorizationIncomplete())

    suspend fun connect(result: AuthorizationResult) = operations.withLock {
        val account = drive.account(result.token())
        dataStore.edit { prefs ->
            if (prefs[ACCOUNT_ID] != account.id) {
                prefs.remove(LAST_UPLOADED)
                prefs.remove(INTERVAL)
                prefs.remove(KEEP_LATEST)
            }
            prefs[ACCOUNT_ID] = account.id
            prefs[ACCOUNT_EMAIL] = account.email
            prefs.remove(ISSUE)
        }
    }

    suspend fun disconnect() = operations.withLock {
        dataStore.edit { prefs ->
            prefs.remove(ACCOUNT_ID)
            prefs.remove(ACCOUNT_EMAIL)
            prefs.remove(INTERVAL)
            prefs.remove(LAST_UPLOADED)
            prefs.remove(ISSUE)
            prefs.remove(KEEP_LATEST)
        }
        DriveBackupWorker.cancelAll(context)
    }

    suspend fun setInterval(days: Int) = operations.withLock {
        require(days in VALID_INTERVALS)
        if (status.first().account == null) throw DriveAuthorizationRequired()
        dataStore.edit { it[INTERVAL] = days }
        DriveBackupWorker.schedule(context, days)
    }

    fun backupNow() = DriveBackupWorker.enqueueNow(context)

    suspend fun setKeepLatest(enabled: Boolean) = operations.withLock {
        if (status.first().account == null) throw DriveAuthorizationRequired()
        dataStore.edit { it[KEEP_LATEST] = enabled }
    }

    suspend fun runBackup(automatic: Boolean) = operations.withLock {
        val config = status.first()
        val account = config.account ?: return@withLock
        if (automatic && config.intervalDays == 0) return@withLock
        try {
            withAccount(account) { token ->
                val zip = backups.createBackupFile()
                val uploaded = try {
                    drive.upload(token, zip)
                } finally {
                    zip.delete()
                }
                dataStore.edit { prefs ->
                    prefs[LAST_UPLOADED] = uploaded.createdAt.toEpochMilli()
                    prefs.remove(ISSUE)
                }
                if (config.keepLatest) {
                    try {
                        drive.trashOlderBackups(token, uploaded)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        dataStore.edit { it[ISSUE] = "新备份已保存，部分旧备份未清理。下次成功备份后会重试。" }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            dataStore.edit { it[ISSUE] = driveIssue(error) }
            throw error
        }
    }

    suspend fun list(): List<DriveSnapshot> = operations.withLock {
        withAccount(status.first().account ?: throw DriveAuthorizationRequired(), drive::snapshots)
    }

    suspend fun restore(snapshot: DriveSnapshot) = operations.withLock {
        val account = status.first().account ?: throw DriveAuthorizationRequired()
        val file = File.createTempFile("ledger-drive-restore-", ".zip", context.cacheDir)
        try {
            withAccount(account) { token -> drive.download(token, snapshot, file) }
            backups.restoreFrom(file)
        } finally {
            file.delete()
        }
    }

    private suspend fun authorize(account: DriveAccount?): AuthorizationResult {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        if (account != null) request.setAccount(Account(account.email, "com.google"))
        return auth.authorize(request.build()).await()
    }

    private suspend fun <T> withAccount(account: DriveAccount, action: suspend (String) -> T): T {
        val token = authorize(account).token()
        try {
            if (drive.account(token).id != account.id) throw DriveAuthorizationRequired()
            return action(token)
        } catch (error: DriveHttpError) {
            if (error.status == 401) {
                try { auth.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: ApiException) { /* The foreground authorization can replace this token. */ }
                throw DriveAuthorizationRequired()
            }
            throw error
        }
    }

    private fun AuthorizationResult.token(): String = accessToken?.takeIf { it.isNotBlank() && !hasResolution() }
        ?: throw DriveAuthorizationRequired()

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        val ACCOUNT_ID = stringPreferencesKey("drive_backup_account_id")
        val ACCOUNT_EMAIL = stringPreferencesKey("drive_backup_account_email")
        val INTERVAL = intPreferencesKey("drive_backup_interval_days")
        val LAST_UPLOADED = longPreferencesKey("drive_backup_last_uploaded")
        val ISSUE = stringPreferencesKey("drive_backup_issue")
        val KEEP_LATEST = booleanPreferencesKey("drive_backup_keep_latest")
        val VALID_INTERVALS = setOf(0, 1, 7)
    }
}

internal fun driveIssue(error: Exception): String = when (error) {
    is DriveAuthorizationRequired, is DriveAuthorizationIncomplete -> error.message.orEmpty()
    is DriveHttpError -> when (error.reason) {
        "accessNotConfigured", "serviceDisabled" -> "此应用的 Google Drive API 尚未启用"
        "storageQuotaExceeded" -> "Google Drive 空间不足"
        "insufficientPermissions", "authError" -> "请重新授权 Google Drive"
        else -> "Google Drive 暂时无法完成操作（HTTP ${error.status}）"
    }
    is ApiException -> when (error.statusCode) {
        2 -> "请更新手机上的 Google Play 服务"
        7 -> "无法连接 Google。请检查网络后重试"
        CommonStatusCodes.DEVELOPER_ERROR -> "Google 授权配置有误（代码 10）。请核对 Android OAuth 客户端的包名和签名 SHA-1。"
        CommonStatusCodes.CANCELED -> "Google 授权未完成（代码 16）。请重新连接并确认授权。"
        else -> "无法完成 Google 授权（代码 ${error.statusCode}）。请检查 Google Play 服务和账号后重试。"
    }
    else -> "备份操作未完成。请检查网络和手机可用空间后重试"
}
