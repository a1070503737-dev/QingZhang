package com.zhuangxiu.ledger.ui.mine

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.repository.ReminderSettings
import com.zhuangxiu.ledger.data.repository.SettingsRepository
import com.zhuangxiu.ledger.notification.Notifier
import com.zhuangxiu.ledger.notification.ReminderChecker
import com.zhuangxiu.ledger.ui.components.GlassVariant
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LiquidGlassButton
import com.zhuangxiu.ledger.ui.components.LiquidLensIconButton
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.liquidGlass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val reminderChecker: ReminderChecker,
    private val notifier: Notifier,
) : ViewModel() {

    val settings: StateFlow<ReminderSettings> = settingsRepository.reminderSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSettings())

    fun canNotify(): Boolean = notifier.canNotify()

    fun setPaymentReminder(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setPaymentReminderEnabled(enabled)
    }

    fun setDaysBefore(days: Int) = viewModelScope.launch { settingsRepository.setRemindDaysBefore(days) }

    fun setBudgetAlert(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBudgetAlertEnabled(enabled)
    }

    fun checkNow() = viewModelScope.launch {
        runCatching {
            reminderChecker.checkDuePayments()
            reminderChecker.checkBudgets()
        }
    }
}

@Composable
fun ReminderScreen(onBack: () -> Unit, viewModel: ReminderViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(viewModel.canNotify()) }
    LifecycleResumeEffect(Unit) {
        canNotify = viewModel.canNotify()
        onPauseOrDispose {}
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = viewModel.canNotify()
        if (!granted) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    SubPageScaffold(title = "提醒设置", onBack = onBack) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!canNotify) {
                LedgerCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "通知权限未开启，暂时收不到提醒。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        LiquidGlassButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                    )
                                }
                            },
                            tint = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ) { Text("开启通知") }
                    }
                }
            }
            LedgerCard(Modifier.fillMaxWidth()) {
                SwitchRow("待付款到期提醒", "设置了到期日的待付款，到期前发通知", settings.paymentReminderEnabled, viewModel::setPaymentReminder)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Row(
                    Modifier.padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("提前几天提醒", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "到期前每天早上提醒一次，直到付清",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .liquidGlass(CircleShape, GlassVariant.Clear, cornerRadius = 20.dp, shadowElevation = 0.dp)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LiquidLensIconButton(
                            onClick = { viewModel.setDaysBefore(settings.remindDaysBefore - 1) },
                            enabled = settings.paymentReminderEnabled && settings.remindDaysBefore > 0,
                            size = 36.dp,
                        ) { Icon(Icons.Outlined.Remove, contentDescription = "减少") }
                        Text("${settings.remindDaysBefore} 天", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
                        LiquidLensIconButton(
                            onClick = { viewModel.setDaysBefore(settings.remindDaysBefore + 1) },
                            enabled = settings.paymentReminderEnabled && settings.remindDaysBefore < 30,
                            size = 36.dp,
                        ) { Icon(Icons.Outlined.Add, contentDescription = "增加") }
                    }
                }
            }
            LedgerCard(Modifier.fillMaxWidth()) {
                SwitchRow("预算预警通知", "预算用到 80% 或超支时发通知", settings.budgetAlertEnabled, viewModel::setBudgetAlert)
            }
            LiquidGlassButton(
                onClick = { viewModel.checkNow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = canNotify,
            ) {
                Text("立即检查一次")
            }
            Text(
                "应用每天早上 9 点左右在后台自动检查。部分手机的省电策略可能会推迟或拦截后台任务，" +
                    "如果收不到提醒，请在系统设置里允许本应用自启动 / 后台运行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
