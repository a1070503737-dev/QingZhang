package com.zhuangxiu.ledger.ui.mine

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import com.zhuangxiu.ledger.ui.components.SurfaceTone
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.ledgerContainer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.system.exitProcess

data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val shareFile: File? = null,
    val restored: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

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

    fun onShareHandled() = _uiState.update { it.copy(shareFile = null) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun runBusy(block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: RestoreRestartRequired) {
                _uiState.update { it.copy(restored = true, message = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "操作失败：${e.message ?: "未知错误"}") }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }
}

/** 备份与恢复页 */
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val recoveryDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportRecoveryTo(uri)
    }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
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
            }
            if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            title = "从备份恢复？",
            text = "新版备份会替换全部账本；旧版装修备份仅替换装修数据，保留当前生活账目。恢复前自动保存安全副本，可在本页导出找回。完成后需要重启应用。",
            confirmLabel = "恢复",
            destructive = true,
            onConfirm = {
                viewModel.restoreFrom(uri)
                pendingRestore = null
            },
            onDismiss = { pendingRestore = null },
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
