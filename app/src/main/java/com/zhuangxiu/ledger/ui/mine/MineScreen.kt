package com.zhuangxiu.ledger.ui.mine

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.FileOpen
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.zhuangxiu.ledger.BuildConfig
import com.zhuangxiu.ledger.ui.components.GlassVariant
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LiquidGlassBackdrop
import com.zhuangxiu.ledger.ui.components.LiquidGlassSurface
import com.zhuangxiu.ledger.ui.components.LiquidGlassTopBar
import com.zhuangxiu.ledger.ui.components.LiquidLensIconButton
import com.zhuangxiu.ledger.ui.components.PageHeading
import com.zhuangxiu.ledger.ui.components.liquidGlass
import com.zhuangxiu.ledger.ui.navigation.Routes

private data class MineEntry(val icon: ImageVector, val title: String, val subtitle: String, val route: String)

private val ENTRIES = listOf(
    MineEntry(Icons.Outlined.FileOpen, "数据导入", "预览并合并鲨鱼记账账单", Routes.IMPORT),
    MineEntry(Icons.Outlined.SettingsBackupRestore, "备份与恢复", "创建 ZIP 备份或覆盖恢复", Routes.BACKUP),
    MineEntry(Icons.Outlined.AccountBalanceWallet, "原装修账本预算", "总预算、按阶段或类型的预算", Routes.BUDGET),
    MineEntry(Icons.Outlined.Category, "装修分类", "装修阶段和费用类型的增删改、排序", Routes.CATEGORIES),
    MineEntry(Icons.Outlined.PhotoLibrary, "凭证库", "按时间查看所有小票、合同照片", Routes.GALLERY),
    MineEntry(Icons.Outlined.NotificationsNone, "提醒设置", "待付款到期提醒、预算预警", Routes.REMINDER),
    MineEntry(Icons.Outlined.SystemUpdate, "检查更新", "当前版本 ${BuildConfig.VERSION_NAME} · 下载新版应用", Routes.UPDATE),
)

@Composable
fun MineScreen(bottomBar: @Composable () -> Unit, onNavigate: (String) -> Unit, onBack: () -> Unit = {}) {
    LiquidGlassBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LiquidGlassTopBar(
                    title = { Text("设置") },
                    navigationIcon = {
                        LiquidLensIconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ChevronLeft, "返回")
                        }
                    },
                )
            },
            bottomBar = bottomBar,
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                PageHeading("轻账", "认真生活，轻松记账")
                Spacer(Modifier.height(18.dp))
                LedgerCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .liquidGlass(RoundedCornerShape(16.dp), GlassVariant.Clear, cornerRadius = 16.dp, shadowElevation = 0.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Cottage, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("每一笔，都心中有数", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "生活与装修 · 本地保存 · 无需登录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                LedgerCard(Modifier.fillMaxWidth()) {
                    ENTRIES.forEachIndexed { index, entry ->
                        if (index > 0) {
                            HorizontalDivider(
                                Modifier.padding(start = 72.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                        }
                        LiquidGlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            cornerRadius = 18.dp,
                            variant = GlassVariant.Clear,
                            shadowElevation = 0.dp,
                            pressedScale = 0.986f,
                            onClick = { onNavigate(entry.route) },
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(42.dp)
                                        .liquidGlass(
                                            shape = RoundedCornerShape(14.dp),
                                            variant = GlassVariant.Tinted(MaterialTheme.colorScheme.primaryContainer),
                                            cornerRadius = 14.dp,
                                            shadowElevation = 2.dp,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        entry.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "所有数据只保存在这台手机上，不会上传。换手机或重装前，请先在“备份与恢复”里导出备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
