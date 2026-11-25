package com.zhuangxiu.ledger.ui.voice

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.voice.CloudVoiceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
internal class CloudVoiceViewModel @Inject constructor(private val settings: CloudVoiceSettings) : ViewModel() {
    val status = settings.status
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun save(key: String, onSaved: () -> Unit) = run({ settings.saveAndEnable(key) }, onSaved)
    fun enable(value: Boolean) = run({ settings.setEnabled(value) })
    fun clear() = run(settings::clear)

    private fun run(change: () -> Unit, onSaved: () -> Unit = {}) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { change() }
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (_: Exception) {
                error = "无法保存云端设置，请重试"
            } finally {
                busy = false
            }
        }
    }
}

@Composable
internal fun CloudVoiceScreen(onBack: () -> Unit, model: CloudVoiceViewModel = hiltViewModel()) {
    val status by model.status.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var key by remember { mutableStateOf("") }
    val context = LocalContext.current
    val window = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()?.window
    }
    DisposableEffect(window) {
        val flag = WindowManager.LayoutParams.FLAG_SECURE
        val secure = ((window?.attributes?.flags ?: 0) and flag) != 0
        window?.addFlags(flag)
        onDispose { if (!secure) window?.clearFlags(flag) }
    }

    SubPageScaffold(title = "云端语音整理", onBack = onBack) { modifier ->
        Column(modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            LedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("DeepSeek", style = MaterialTheme.typography.titleMedium)
                    Text("开启后，轻账在手机上听写，再将当次文字、当天日期和分类选项发送给 DeepSeek。原始录音和账本历史留在本机。调用按你的 DeepSeek 账户用量计费，草稿由你确认后保存。")
                    if (status.configured) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("启用云端整理")
                            Switch(checked = status.enabled, onCheckedChange = model::enable, enabled = !model.busy)
                        }
                    }
                    OutlinedTextField(
                        value = key,
                        onValueChange = { if (it.length <= 512) key = it },
                        label = { Text(if (status.configured) "更换 DeepSeek API Key" else "DeepSeek API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !model.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (status.configured) Text("密钥已加密保存在本设备", style = MaterialTheme.typography.bodySmall)
                    LedgerButton(
                        onClick = {
                            focus.clearFocus()
                            model.save(key) { key = "" }
                        },
                        enabled = key.isNotBlank() && !model.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保存并启用") }
                    if (status.configured) {
                        LedgerButton(onClick = model::clear, enabled = !model.busy, prominent = false) {
                            Text("移除密钥")
                        }
                    }
                    model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
