package com.zhuangxiu.ledger.ui.voice

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.zhuangxiu.ledger.voice.CloudVoiceClient
import com.zhuangxiu.ledger.voice.CloudVoiceConfig
import com.zhuangxiu.ledger.voice.CloudVoiceException
import com.zhuangxiu.ledger.voice.CloudVoiceProtocol
import com.zhuangxiu.ledger.voice.CloudVoiceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
internal class CloudVoiceViewModel @Inject constructor(
    private val settings: CloudVoiceSettings,
    private val client: CloudVoiceClient,
) : ViewModel() {
    val status = settings.status
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var success by mutableStateOf<String?>(null)
        private set

    fun test(config: CloudVoiceConfig, key: String) = run {
        val access = settings.access(config, key)
        "连接成功 · ${access.config.modelLabel(client.testConnection(access))}"
    }

    fun save(config: CloudVoiceConfig, key: String, onSaved: () -> Unit) = run(onSaved) {
        val access = settings.access(config, key)
        val reportedModel = client.testConnection(access)
        settings.saveAndEnable(access, reportedModel)
        "连接成功，已启用 · ${access.config.modelLabel(reportedModel)}"
    }

    fun enable(value: Boolean) = run {
        settings.setEnabled(value)
        null
    }

    fun clear() = run {
        settings.clear()
        null
    }

    fun resetResult() {
        error = null
        success = null
    }

    private fun run(onSaved: () -> Unit = {}, change: suspend () -> String?) {
        if (busy) return
        busy = true
        resetResult()
        viewModelScope.launch {
            try {
                success = withContext(Dispatchers.IO) { change() }
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: CloudVoiceException) {
                error = e.message
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (_: Exception) {
                error = "暂时无法完成，请检查网络、服务地址或设备存储"
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
    var config by remember(status.config) { mutableStateOf(status.config) }
    var menu by remember { mutableStateOf(false) }
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
                    Text("模型 API", style = MaterialTheme.typography.titleMedium)
                    Text("开启后，轻账在手机上听写，再将当次文字、当天日期和分类选项发给你选择的模型。原始录音和账本历史留在本机。测试只发送一条固定消息，按服务商用量计费。")
                    if (status.configured) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("启用云端整理")
                            Switch(checked = status.enabled, onCheckedChange = model::enable, enabled = !model.busy)
                        }
                        Text("当前服务 · ${status.config.modelLabel(status.reportedModel)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        LedgerButton(onClick = { menu = true }, enabled = !model.busy, prominent = false) {
                            Text(config.protocol.label)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            CloudVoiceProtocol.entries.forEach { protocol ->
                                DropdownMenuItem(text = { Text(protocol.label) }, onClick = {
                                    if (config.protocol != protocol) {
                                        config = CloudVoiceConfig(protocol)
                                        key = ""
                                        model.resetResult()
                                    }
                                    menu = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = config.baseUrl,
                        onValueChange = {
                            if (it.length <= 512) {
                                config = config.copy(baseUrl = it)
                                model.resetResult()
                            }
                        },
                        label = { Text("服务地址（HTTPS）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                        enabled = !model.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = config.model,
                        onValueChange = {
                            if (it.length <= 167) {
                                config = config.copy(model = it)
                                model.resetResult()
                            }
                        },
                        label = { Text("模型 ID") },
                        singleLine = true,
                        enabled = !model.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val savedKeyAvailable = status.configured && config.protocol == status.config.protocol &&
                        config.baseUrl.trim().trimEnd('/') == status.config.baseUrl
                    OutlinedTextField(
                        value = key,
                        onValueChange = {
                            if (it.length <= 512) {
                                key = it
                                model.resetResult()
                            }
                        },
                        label = { Text(if (savedKeyAvailable) "API Key（留空沿用已保存密钥）" else "API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !model.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val ready = !model.busy && config.baseUrl.isNotBlank() && config.model.isNotBlank() &&
                        (key.isNotBlank() || savedKeyAvailable)
                    LedgerButton(
                        onClick = {
                            focus.clearFocus()
                            model.test(config, key)
                        },
                        enabled = ready,
                        prominent = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (model.busy) "正在处理…" else "测试连接") }
                    LedgerButton(
                        onClick = {
                            focus.clearFocus()
                            model.save(config, key) { key = "" }
                        },
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("测试并启用") }
                    if (status.configured) {
                        LedgerButton(onClick = model::clear, enabled = !model.busy, prominent = false) {
                            Text("移除密钥")
                        }
                    }
                    model.success?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
