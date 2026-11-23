package com.zhuangxiu.ledger.assistant

import android.content.Context
import android.os.Build
import androidx.appfunctions.AppFunctionManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object AssistantPermission {
    private fun preferences(context: Context) = context.getSharedPreferences("system_assistant", Context.MODE_PRIVATE)

    fun enabled(context: Context) = preferences(context).getBoolean("allowed", false)

    fun manager(context: Context): AppFunctionManager? =
        if (Build.VERSION.SDK_INT >= 36) AppFunctionManager.getInstance(context) else null

    suspend fun setEnabled(context: Context, manager: AppFunctionManager, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val preferences = preferences(context)
            if (!enabled) check(preferences.edit().putBoolean("allowed", false).commit())
            val ids = listOf(
                LedgerAppFunctionService.FUNCTION_ID_CREATE_LEDGER_ENTRY,
                LedgerAppFunctionService.FUNCTION_ID_QUERY_LEDGER_ENTRIES,
                LedgerAppFunctionService.FUNCTION_ID_OPEN_MONTHLY_SUMMARY,
            )
            val target = if (enabled) AppFunctionManager.APP_FUNCTION_STATE_ENABLED else AppFunctionManager.APP_FUNCTION_STATE_DISABLED
            ids.forEach { manager.setAppFunctionEnabled(it, target) }
            if (enabled) check(preferences.edit().putBoolean("allowed", true).commit())
        }
    }
}

@Composable
internal fun AssistantAccessScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { AssistantPermission.manager(context) }
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(AssistantPermission.enabled(context)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SubPageScaffold(title = "系统助手", onBack = { if (!busy) onBack() }) { modifier ->
        LedgerCard(modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (manager == null) {
                    Text("这台设备暂不支持 Android AppFunctions。")
                } else {
                    Text("允许系统助手调用轻账", style = MaterialTheme.typography.titleMedium)
                    Text("经过系统授权的助手可以打开记账草稿、账单和月度统计。账目在轻账内查看，新增账目由你确认保存。")
                    Switch(checked = enabled, enabled = !busy, onCheckedChange = { requested ->
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                AssistantPermission.setEnabled(context, manager, requested)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "暂时无法更新系统助手授权，请稍后重试。"
                            } finally {
                                enabled = AssistantPermission.enabled(context)
                                busy = false
                            }
                        }
                    })
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
