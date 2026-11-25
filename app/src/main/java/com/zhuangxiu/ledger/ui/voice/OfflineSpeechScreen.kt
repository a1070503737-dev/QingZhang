package com.zhuangxiu.ledger.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.voice.OfflineSpeechModels
import com.zhuangxiu.ledger.voice.SenseVoiceSource
import com.zhuangxiu.ledger.voice.SpeechModelOperation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class OfflineSpeechViewModel @Inject constructor(val models: OfflineSpeechModels) : ViewModel()

@Composable
internal fun OfflineSpeechScreen(
    onBack: () -> Unit,
    model: OfflineSpeechViewModel = hiltViewModel(),
) {
    val models = model.models
    val state by models.state.collectAsStateWithLifecycle()
    val total = SenseVoiceSource.totalBytes
    val progress = (state.bytes.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    SubPageScaffold(title = "离线语音模型", onBack = onBack) { modifier ->
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("内置 Zipformer", style = MaterialTheme.typography.titleMedium)
                    Text("轻量中文听写，安装后即可使用。", style = MaterialTheme.typography.bodyMedium)
                    if (state.senseVoiceActive) {
                        LedgerButton(
                            onClick = { models.select(false) },
                            enabled = state.operation == null,
                            prominent = false,
                        ) { Text("使用内置模型") }
                    } else {
                        Text("正在使用", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            LedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SenseVoice Small INT8", style = MaterialTheme.typography.titleMedium)
                    Text("约 237 MB，支持普通话和粤语。下载后在手机上离线听写。", style = MaterialTheme.typography.bodyMedium)
                    if (!state.installed && (state.bytes > 0 || state.operation == SpeechModelOperation.DOWNLOADING)) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        val verifying = state.operation == SpeechModelOperation.DOWNLOADING && state.bytes == total
                        Text(if (verifying) "正在校验模型…" else "已下载 ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium)
                    }
                    when {
                        state.operation == SpeechModelOperation.DOWNLOADING -> {
                            LedgerButton(onClick = models::pause, prominent = false) { Text("暂停下载") }
                        }
                        state.operation == SpeechModelOperation.REMOVING -> Text("正在删除…")
                        state.senseVoiceActive -> Text("正在使用", color = MaterialTheme.colorScheme.primary)
                        state.installed -> {
                            LedgerButton(onClick = { models.select(true) }) { Text("使用 SenseVoice") }
                        }
                        else -> {
                            LedgerButton(onClick = models::download) {
                                Text(if (state.bytes == 0L) "下载并使用" else "继续下载并使用")
                            }
                        }
                    }
                    if (state.operation == null && (state.installed || state.bytes > 0)) {
                        LedgerButton(onClick = { confirmRemove = true }, prominent = false) { Text("删除模型文件") }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = "删除 SenseVoice 模型？",
            text = "删除后将使用内置模型。需要时可以重新下载。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                confirmRemove = false
                models.remove()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}
