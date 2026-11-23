package com.zhuangxiu.ledger.ui.mine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.feedback.FeedbackClient
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

internal enum class FeedbackStatus { Editing, Sending, Submitted, Unconfirmed }

internal class FeedbackViewModel(private val client: FeedbackClient = FeedbackClient()) : ViewModel() {
    private val _status = MutableStateFlow(FeedbackStatus.Editing)
    val status = _status.asStateFlow()
    val available get() = client.available

    fun submit(feedback: String, contact: String) {
        if (!available || feedback.isBlank() ||
            _status.value == FeedbackStatus.Sending || _status.value == FeedbackStatus.Submitted
        ) return

        _status.value = FeedbackStatus.Sending
        viewModelScope.launch {
            _status.value = try {
                client.submit(feedback.trim(), contact.trim())
                FeedbackStatus.Submitted
            } catch (_: IOException) {
                FeedbackStatus.Unconfirmed
            }
        }
    }
}

@Composable
internal fun FeedbackScreen(onBack: () -> Unit, model: FeedbackViewModel = viewModel()) {
    val status by model.status.collectAsStateWithLifecycle()
    var feedback by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val sending = status == FeedbackStatus.Sending

    BackHandler(sending) {}
    SubPageScaffold(title = "意见反馈", onBack = { if (!sending) onBack() }) { modifier ->
        Column(
            modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            LedgerCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (status == FeedbackStatus.Submitted) {
                        Text("反馈已提交", style = MaterialTheme.typography.titleMedium)
                        LedgerButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("完成") }
                    } else {
                        Text("可匿名提交。你填写的内容将通过 Google 表单发送给开发者。")
                        OutlinedTextField(
                            value = feedback,
                            onValueChange = { feedback = it },
                            label = { Text("反馈内容") },
                            minLines = 5,
                            maxLines = 8,
                            enabled = !sending,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = contact,
                            onValueChange = { contact = it },
                            label = { Text("联系方式（选填）") },
                            singleLine = true,
                            enabled = !sending,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!model.available) {
                            Text("反馈暂时不可用。", color = MaterialTheme.colorScheme.error)
                        } else if (status == FeedbackStatus.Unconfirmed) {
                            Text("未能确认提交，请检查网络后重试。", color = MaterialTheme.colorScheme.error)
                        }
                        LedgerButton(
                            onClick = {
                                focus.clearFocus()
                                model.submit(feedback, contact)
                            },
                            enabled = model.available && feedback.isNotBlank() && !sending,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (sending) "正在提交…" else "提交反馈") }
                    }
                }
            }
        }
    }
}
