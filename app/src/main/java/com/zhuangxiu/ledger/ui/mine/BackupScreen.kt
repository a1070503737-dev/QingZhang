package com.zhuangxiu.ledger.ui.mine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.repository.BackupManager
import com.zhuangxiu.ledger.data.repository.RestoreRestartRequired
import com.zhuangxiu.ledger.drive.DriveBackup
import com.zhuangxiu.ledger.drive.DriveBackupStatus
import com.zhuangxiu.ledger.drive.DriveSnapshot
import com.zhuangxiu.ledger.drive.GoogleDriveFiles
import com.zhuangxiu.ledger.drive.driveIssue
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import com.zhuangxiu.ledger.ui.components.SurfaceTone
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerChip
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.ledgerContainer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.system.exitProcess

internal data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val shareFile: File? = null,
    val restored: Boolean = false,
    val authorization: IntentSenderRequest? = null,
    val driveFiles: List<DriveSnapshot>? = null,
)

@HiltViewModel
internal class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val drive: DriveBackup,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()
    val driveStatus = drive.status

    fun defaultFileName(): String = backupManager.defaultFileName()
    fun exportRecoveryTo(uri: Uri) = runBusy {
        backupManager.exportRecoveryTo(uri)
        _uiState.update { it.copy(message = "恢复前安全副本已保存") }
    }

    fun exportTo(uri: Uri) = runBusy {
        backupManager.exportTo(uri)
        _uiState.update { it.copy(message = "备份已保存") }
    }

    fun prepareShare() = runBusy {
        val file = backupManager.createBackupFile()
        _uiState.update { it.copy(shareFile = file) }
    }

    fun restoreFrom(uri: Uri) = runBusy {
        backupManager.restoreFrom(uri)
        _uiState.update { it.copy(restored = true) }
    }

    fun connectDrive() = runDrive {
        val result = drive.requestAuthorization()
        if (result.hasResolution()) {
            val request = IntentSenderRequest.Builder(requireNotNull(result.pendingIntent)).build()
            _uiState.update { it.copy(authorization = request) }
        } else {
            drive.connect(result)
        }
    }
    fun onAuthorizationLaunched() = _uiState.update { it.copy(authorization = null) }
    fun completeAuthorization(data: Intent) = runDrive { drive.connect(drive.finishResolution(data)) }
    fun disconnectDrive() = runDrive { drive.disconnect() }
    fun setDriveInterval(days: Int) = runDrive { drive.setInterval(days) }
    fun keepLatestDriveBackup(enabled: Boolean) = runDrive { drive.setKeepLatest(enabled) }
    fun uploadToDrive() = runDrive {
        drive.backupNow()
        _uiState.update { it.copy(message = "已安排备份，联网后上传。完成时间会显示在 Google Drive 备份区域。") }
    }
    fun listDriveBackups() = runDrive {
        val files = drive.list()
        _uiState.update { it.copy(driveFiles = files) }
    }
    fun dismissDriveFiles() = _uiState.update { it.copy(driveFiles = null) }
    fun restoreDriveBackup(file: DriveSnapshot) = runDrive {
        drive.restore(file)
        _uiState.update { it.copy(restored = true) }
    }

    fun onShareHandled() = _uiState.update { it.copy(shareFile = null) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun runDrive(block: suspend () -> Unit) = runBusy(::driveIssue, block)

    private fun runBusy(
        failureMessage: (Exception) -> String = { "操作失败：${it.message ?: "未知错误"}" },
        block: suspend () -> Unit,
    ) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: RestoreRestartRequired) {
                _uiState.update { it.copy(restored = true, message = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = failureMessage(e)) }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }
}

/** 备份与恢复页 */
@Composable
internal fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drive by viewModel.driveStatus.collectAsStateWithLifecycle(initialValue = DriveBackupStatus())
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var pendingDriveRestore by remember { mutableStateOf<DriveSnapshot?>(null) }

    val recoveryDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportRecoveryTo(uri)
    }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val authorize = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.let(viewModel::completeAuthorization)
    }

    LaunchedEffect(state.authorization) {
        state.authorization?.let { request ->
            viewModel.onAuthorizationLaunched()
            authorize.launch(request)
        }
    }

    LaunchedEffect(state.shareFile) {
        state.shareFile?.let { file ->
            shareBackup(context, file)
            viewModel.onShareHandled()
        }
    }

    BackHandler(state.busy || state.restored) { }
    SubPageScaffold(title = "备份与恢复", onBack = { if (!state.busy && !state.restored) onBack() }) { modifier ->
        Box(modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "备份文件是一个 zip 压缩包，包含所有生活账本、装修账单、预算和凭证图片。旧版装修备份也可恢复，且会保留当前生活账目。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionCard(Icons.Outlined.SaveAlt, "保存备份到本地", "选择手机里的一个位置保存备份文件", !state.busy) {
                    createDocument.launch(viewModel.defaultFileName())
                }
                ActionCard(Icons.Outlined.IosShare, "分享备份", "通过微信、网盘、邮件等发送备份文件", !state.busy) {
                    viewModel.prepareShare()
                }
                ActionCard(Icons.Outlined.SaveAlt, "导出恢复前安全副本", "找回最近一次恢复之前的数据", !state.busy) {
                    recoveryDocument.launch("轻账恢复前安全副本.zip")
                }
                ActionCard(Icons.Outlined.Restore, "从备份恢复", "选择备份文件，覆盖当前的全部数据", !state.busy) {
                    openDocument.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                }
                DriveBackupCard(drive, state.busy, viewModel)
            }
            if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    val localRestore = pendingRestore
    val cloudRestore = pendingDriveRestore
    if (localRestore != null || cloudRestore != null) {
        ConfirmDialog(
            title = "从备份恢复？",
            text = if (cloudRestore != null) {
                "将使用 Google Drive 中的「${cloudRestore.name}」替换本机账本和凭证。恢复前会保存安全副本，完成后需要重启应用。"
            } else {
                "新版备份会替换全部账本；旧版装修备份仅替换装修数据，保留当前生活账目。恢复前自动保存安全副本，可在本页导出找回。完成后需要重启应用。"
            },
            confirmLabel = "恢复",
            destructive = true,
            onConfirm = {
                if (cloudRestore != null) viewModel.restoreDriveBackup(cloudRestore)
                else localRestore?.let(viewModel::restoreFrom)
                pendingRestore = null
                pendingDriveRestore = null
            },
            onDismiss = { pendingRestore = null; pendingDriveRestore = null },
        )
    }
    state.driveFiles?.let { files ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDriveFiles,
            title = { Text("从 Google Drive 恢复") },
            text = {
                if (files.isEmpty()) Text("这个 Google 账号里还没有轻账备份")
                else Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("最近 ${GoogleDriveFiles.LIST_LIMIT} 份备份", style = MaterialTheme.typography.bodySmall)
                    files.forEach { file ->
                        LedgerButton(
                            onClick = {
                                viewModel.dismissDriveFiles()
                                pendingDriveRestore = file
                            },
                            prominent = false,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(file.createdAt.displayTime()) }
                    }
                }
            },
            confirmButton = { LedgerButton(onClick = viewModel::dismissDriveFiles, prominent = false) { Text("取消") } },
        )
    }
    state.message?.let {
        ConfirmDialog(title = "提示", text = it, confirmLabel = "知道了", onConfirm = viewModel::clearMessage, onDismiss = viewModel::clearMessage)
    }
    if (state.restored) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(30.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = { Text("需要重启") },
            text = { Text("点击“重启”重新打开数据库。若恢复中断，启动时会自动恢复原数据。") },
            confirmButton = {
                LedgerButton(
                    onClick = { restartApp(context) },
                    minHeight = 40.dp,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) { Text("重启") }
            },
        )
    }
}

@Composable
private fun DriveBackupCard(status: DriveBackupStatus, busy: Boolean, model: BackupViewModel) {
    var confirmKeepLatest by rememberSaveable { mutableStateOf(false) }
    LedgerCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Google Drive 备份", style = MaterialTheme.typography.titleMedium)
            if (status.account == null) {
                Text(
                    "授权后可定期将账本和凭证照片打包上传到你的 Google Drive，也可在换机时选一份恢复。",
                    style = MaterialTheme.typography.bodySmall,
                )
                LedgerButton(onClick = model::connectDrive, enabled = !busy) { Text("连接 Google Drive") }
            } else {
                Text(status.account.email, style = MaterialTheme.typography.bodySmall)
                Text("自动备份频率", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "关闭", 1 to "每天", 7 to "每周").forEach { (days, label) ->
                        LedgerChip(
                            selected = status.intervalDays == days,
                            enabled = !busy,
                            onClick = { model.setDriveInterval(days) },
                            label = { Text(label) },
                        )
                    }
                }
                LedgerChip(
                    selected = status.keepLatest,
                    enabled = !busy,
                    onClick = {
                        if (status.keepLatest) model.keepLatestDriveBackup(false)
                        else confirmKeepLatest = true
                    },
                    label = { Text("只保留最新一份") },
                )
                Text(
                    status.lastUploaded?.let { "最近成功备份：${it.displayTime()}" } ?: "还没有上传过备份",
                    style = MaterialTheme.typography.bodySmall,
                )
                status.issue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LedgerButton(onClick = model::uploadToDrive, enabled = !busy, prominent = false) { Text("立即备份") }
                    LedgerButton(onClick = model::listDriveBackups, enabled = !busy, prominent = false) { Text("查看云端备份") }
                    if (status.issue != null) LedgerButton(onClick = model::connectDrive, enabled = !busy, prominent = false) { Text("重新授权") }
                    LedgerButton(onClick = model::disconnectDrive, enabled = !busy, prominent = false) { Text("断开连接") }
                }
            }
        }
    }
    if (confirmKeepLatest && status.account != null) {
        ConfirmDialog(
            title = "只保留最新一份云端备份？",
            text = "下一份备份上传并校验通过后，此账号先前由轻账上传的云端备份会移到 Google Drive 回收站。",
            confirmLabel = "启用",
            onConfirm = {
                confirmKeepLatest = false
                model.keepLatestDriveBackup(true)
            },
            onDismiss = { confirmKeepLatest = false },
        )
    }
}

private fun Instant.displayTime(): String = atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

@Composable
private fun ActionCard(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    LedgerCard(Modifier.fillMaxWidth(), onClick = { if (enabled) onClick() }) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .ledgerContainer(
                        shape = RoundedCornerShape(14.dp),
                        variant = SurfaceTone.Accent(MaterialTheme.colorScheme.primaryContainer),

                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun shareBackup(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享备份文件"))
}

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    exitProcess(0)
}
