package com.zhuangxiu.ledger.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.domain.BudgetItem
import com.zhuangxiu.ledger.domain.BudgetLevel
import com.zhuangxiu.ledger.domain.BudgetUsage
import com.zhuangxiu.ledger.domain.LedgerSummary
import com.zhuangxiu.ledger.ui.components.BudgetProgressBar
import com.zhuangxiu.ledger.ui.components.EmptyHint
import com.zhuangxiu.ledger.ui.components.ExpenseRow
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.SectionTitle
import com.zhuangxiu.ledger.ui.components.budgetLevelColor
import com.zhuangxiu.ledger.util.Money

/** 首页：预算概览、待付款、最近账单，右下角悬浮按钮快速记一笔 */
@Composable
fun HomeScreen(
    bottomBar: @Composable () -> Unit,
    onAdd: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onOpenBills: () -> Unit,
    onOpenBudget: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("记一笔") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("我家装修", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "每一笔花费，都清清楚楚",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { BudgetOverviewCard(state.summary, state.totalBudget, onOpenBudget) }
            item { PendingCard(state.summary.pendingCents, state.pendingCount, onOpenBills) }
            if (state.alerts.isNotEmpty()) {
                item { AlertsCard(state.alerts, onOpenBudget) }
            }
            item {
                LedgerCard {
                    SectionTitle(
                        "最近账单",
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp),
                        trailing = { TextButton(onClick = onOpenBills) { Text("全部") } },
                    )
                    if (state.recent.isEmpty() && !state.loading) {
                        EmptyHint(Icons.AutoMirrored.Outlined.ReceiptLong, "还没有账单，点右下角记一笔")
                    }
                    state.recent.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ExpenseRow(item, onClick = { onOpenExpense(item.expense.id) }, showDate = true)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

/** 预算概览卡片：总预算、已花、剩余、进度条 */
@Composable
private fun BudgetOverviewCard(summary: LedgerSummary, budget: BudgetUsage?, onOpenBudget: () -> Unit) {
    LedgerCard(onClick = onOpenBudget, containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp)) {
            val onColor = MaterialTheme.colorScheme.onPrimaryContainer
            Text("已花", style = MaterialTheme.typography.labelLarge, color = onColor.copy(alpha = 0.75f))
            Text(
                Money.format(summary.spentCents),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = onColor,
            )
            Spacer(Modifier.height(14.dp))
            if (budget == null) {
                Text("还没有设置总预算，点这里设置 ›", style = MaterialTheme.typography.bodyMedium, color = onColor)
            } else {
                BudgetProgressBar(budget)
                Spacer(Modifier.height(6.dp))
                val over = budget.level == BudgetLevel.OVER
                Text(
                    text = when (budget.level) {
                        BudgetLevel.OVER -> "已超出总预算 ${Money.format(-budget.remainingCents)}"
                        BudgetLevel.WARNING -> "已使用 ${budget.percent}%，预算快用完了"
                        else -> "已使用 ${budget.percent}%"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (budget.level == BudgetLevel.NORMAL) onColor else budgetLevelColor(budget.level),
                    fontWeight = if (over) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("总预算", style = MaterialTheme.typography.labelMedium, color = onColor.copy(alpha = 0.75f))
                        Text(Money.format(budget.budgetCents), style = MaterialTheme.typography.titleMedium, color = onColor)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (over) "超支" else "剩余",
                            style = MaterialTheme.typography.labelMedium,
                            color = onColor.copy(alpha = 0.75f),
                        )
                        Text(
                            Money.format(kotlin.math.abs(budget.remainingCents)),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (over) MaterialTheme.colorScheme.error else onColor,
                        )
                    }
                }
            }
        }
    }
}

/** 待付总额卡片 */
@Composable
private fun PendingCard(pendingCents: Long, pendingCount: Int, onClick: () -> Unit) {
    LedgerCard(onClick = onClick) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("待付总额", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (pendingCount == 0) "没有待付款" else "$pendingCount 笔账单还有余款",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                Money.format(pendingCents),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (pendingCents > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 分类预算预警列表：80% 以上琥珀色，超支红色 */
@Composable
private fun AlertsCard(alerts: List<BudgetItem>, onClick: () -> Unit) {
    LedgerCard(onClick = onClick) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("预算预警", style = MaterialTheme.typography.titleMedium)
            alerts.forEach { item ->
                val over = item.usage.level == BudgetLevel.OVER
                val color = budgetLevelColor(item.usage.level)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (over) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (over) "超支 ${Money.format(-item.usage.remainingCents)}" else "已用 ${item.usage.percent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
