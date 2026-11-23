package com.zhuangxiu.ledger.ui.mine

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.zhuangxiu.ledger.BuildConfig
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import com.zhuangxiu.ledger.ui.components.SurfaceTone
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerBackground
import com.zhuangxiu.ledger.ui.components.LedgerPanel
import com.zhuangxiu.ledger.ui.components.LedgerTopBar
import com.zhuangxiu.ledger.ui.navigation.Routes

private data class MineEntry(val icon: ImageVector, val title: String, val subtitle: String, val route: String)

private const val FEEDBACK_EMAIL = "a1070503737@gmail.com"

private val DATA_ENTRIES = listOf(
    MineEntry(Icons.Outlined.FileOpen, "数据导入", "预览并合并鲨鱼记账账单", Routes.IMPORT),
    MineEntry(Icons.Outlined.SettingsBackupRestore, "备份与恢复", "ZIP 备份、Google Drive 自动备份和恢复", Routes.BACKUP),
)

private val RENOVATION_ENTRIES = listOf(
    MineEntry(Icons.Outlined.AccountBalanceWallet, "原装修账本预算", "总预算、按阶段或类型的预算", Routes.BUDGET),
    MineEntry(Icons.Outlined.Category, "装修分类", "管理装修阶段和费用类型", Routes.CATEGORIES),
    MineEntry(Icons.Outlined.PhotoLibrary, "凭证库", "按时间查看票据和合同照片", Routes.GALLERY),
)

private val APP_ENTRIES = listOf(
    MineEntry(Icons.Outlined.NotificationsNone, "提醒设置", "待付款到期提醒、预算预警", Routes.REMINDER),
    MineEntry(Icons.Outlined.SystemUpdate, "检查更新", "当前版本 ${BuildConfig.VERSION_NAME}", Routes.UPDATE),
)

@Composable
internal fun MineScreen(
    bottomBar: @Composable () -> Unit,
    onNavigate: (String) -> Unit,
    feedbackFormUrl: String = BuildConfig.FEEDBACK_FORM_URL,
) {
    val context = LocalContext.current
    var feedbackToCopy by rememberSaveable { mutableStateOf<String?>(null) }
    val hasFeedbackForm = feedbackFormUrl.isNotEmpty()

    fun openFeedback(intent: Intent, address: String) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            feedbackToCopy = address
        } catch (_: SecurityException) {
            feedbackToCopy = address
        }
    }

    LedgerBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { LedgerTopBar(title = { Text("设置") }) },
            bottomBar = bottomBar,
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                SettingsSection("数据与备份", DATA_ENTRIES, onNavigate)
                SettingsSection("装修账本", RENOVATION_ENTRIES, onNavigate)
                SettingsSection("应用", APP_ENTRIES, onNavigate) {
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    if (hasFeedbackForm) {
                        SettingsRow(Icons.Outlined.Feedback, "意见反馈", "匿名填写，联系方式选填") {
                            val form = Intent(Intent.ACTION_VIEW, feedbackFormUrl.toUri())
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                            openFeedback(form, feedbackFormUrl)
                        }
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    val emailLabel = if (hasFeedbackForm) "邮件反馈" else "意见反馈"
                    SettingsRow(Icons.Outlined.MailOutline, emailLabel, "通过邮件反馈使用体验和建议") {
                        val email = Intent(Intent.ACTION_SENDTO, "mailto:$FEEDBACK_EMAIL".toUri())
                            .putExtra(Intent.EXTRA_SUBJECT, "轻账使用反馈")
                        openFeedback(email, FEEDBACK_EMAIL)
                    }
                }
                Text(
                    "账本默认保存在本机。可导出备份，或自行开启 Google Drive 自动备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
    feedbackToCopy?.let { address ->
        val isForm = address == feedbackFormUrl
        val label = if (isForm) "反馈表单链接" else "反馈邮箱"
        val instructions = if (isForm) {
            "无法打开浏览器。可以复制链接，在其他设备上填写反馈。"
        } else {
            "无法打开邮件应用。可以复制邮箱，用网页版邮箱发送反馈。"
        }
        ConfirmDialog(
            title = label,
            text = "$instructions\n\n$address",
            confirmLabel = if (isForm) "复制链接" else "复制邮箱",
            onConfirm = {
                context.getSystemService(ClipboardManager::class.java)?.let { clipboard ->
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, address))
                    feedbackToCopy = null
                    if (Build.VERSION.SDK_INT < 33) {
                        Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { feedbackToCopy = null },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    entries: List<MineEntry>,
    onNavigate: (String) -> Unit,
    additionalEntry: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Text(
        title,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp, top = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = colors.primary,
    )
    LedgerCard(Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = colors.outlineVariant)
            SettingsRow(entry.icon, entry.title, entry.subtitle) { onNavigate(entry.route) }
        }
        additionalEntry()
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    LedgerPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        variant = SurfaceTone.Flat,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
