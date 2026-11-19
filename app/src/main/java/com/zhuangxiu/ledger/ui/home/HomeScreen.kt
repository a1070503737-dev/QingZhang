package com.zhuangxiu.ledger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.domain.BudgetItem
import com.zhuangxiu.ledger.domain.BudgetLevel
import com.zhuangxiu.ledger.domain.BudgetUsage
import com.zhuangxiu.ledger.domain.LedgerSummary
import com.zhuangxiu.ledger.ui.components.EmptyHint
import com.zhuangxiu.ledger.ui.components.ExpenseRow
import com.zhuangxiu.ledger.ui.components.SurfaceTone
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerBackground
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerPanel
import com.zhuangxiu.ledger.ui.components.LoadingHint
import com.zhuangxiu.ledger.ui.components.PageHeading
import com.zhuangxiu.ledger.ui.components.SectionTitle
import com.zhuangxiu.ledger.ui.components.budgetLevelColor
import com.zhuangxiu.ledger.ui.components.ledgerContainer
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Money
import kotlin.math.abs

@Composable
fun HomeScreen(
    bottomBar: @Composable () -> Unit,
    onAdd: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onOpenBills: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenPending: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    header: @Composable () -> Unit = {},
    showBudget: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state, bottomBar, onAdd, onOpenExpense, onOpenBills, onOpenBudget, onOpenPending, header, showBudget)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    bottomBar: @Composable () -> Unit,
    onAdd: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onOpenBills: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenPending: () -> Unit,
    header: @Composable () -> Unit = {},
    showBudget: Boolean = true,
) {
    LedgerBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = header,
            bottomBar = bottomBar,
            floatingActionButton = {
                LedgerButton(
                    onClick = onAdd,
                    minHeight = 54.dp,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("记一笔", fontWeight = FontWeight.SemiBold)
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PageHeading("我家装修", "新家花费，心里有数", Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(50.dp)
                                .ledgerContainer(
                                    shape = RoundedCornerShape(18.dp),
                                    variant = SurfaceTone.Accent(MaterialTheme.colorScheme.primaryContainer),

                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Cottage, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (state.loading) {
                    item { LoadingHint() }
                } else {
                    if (showBudget) item { BudgetOverviewCard(state.summary, state.totalBudget, onOpenBudget) }
                    item {
                        BoxWithConstraints {
                            val compact = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.2f
                            val subtitle = if (state.pendingCount == 0) "全部付清，安心装修" else "${state.pendingCount} 笔待结清 · 查看"
                            if (compact) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PaymentCard("实际已付", state.summary.paidCents, "已登记的付款", false, Modifier.fillMaxWidth())
                                    PaymentCard("剩余待付", state.summary.pendingCents, subtitle, true, Modifier.fillMaxWidth(), onOpenPending)
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PaymentCard("实际已付", state.summary.paidCents, "已登记的付款", false, Modifier.weight(1f))
                                    PaymentCard("剩余待付", state.summary.pendingCents, subtitle, true, Modifier.weight(1f), onOpenPending)
                                }
                            }
                        }
                    }
                    if (state.alerts.isNotEmpty()) item { AlertsCard(state.alerts, onOpenBudget) }
                    item {
                        LedgerCard(Modifier.fillMaxWidth()) {
                            SectionTitle(
                                "最近账单",
                                Modifier.padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                                trailing = {
                                    LedgerButton(
                                        onClick = onOpenBills,
                                        prominent = false,
                                        minHeight = 34.dp,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                                    ) { Text("查看全部 ›", color = MaterialTheme.colorScheme.primary) }
                                },
                            )
                            if (state.recent.isEmpty()) {
                                EmptyHint(Icons.AutoMirrored.Outlined.ReceiptLong, "新家的第一笔花费，从这里开始")
                                LedgerButton(
                                    onClick = onAdd,
                                    prominent = false,
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(bottom = 24.dp),
                                ) {
                                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text("记录第一笔", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            state.recent.forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        Modifier.padding(horizontal = 18.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    )
                                }
                                ExpenseRow(item, onClick = { onOpenExpense(item.expense.id) }, showDate = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetOverviewCard(summary: LedgerSummary, budget: BudgetUsage?, onOpenBudget: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = ink.copy(alpha = 0.82f)
    LedgerPanel(
        onClick = onOpenBudget,
        shape = RoundedCornerShape(30.dp),
        variant = SurfaceTone.Accent(LedgerTheme.extra.expenseContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("装修总花费", style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.weight(1f))
                Text(
                    "预算管理 ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = ink,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            val amount = Money.format(summary.spentCents)
            Text(
                amount,
                style = MaterialTheme.typography.displaySmall,
                fontSize = if (amount.length > 13) 26.sp else 36.sp,
                color = LedgerTheme.extra.expense,
            )
            Spacer(Modifier.height(4.dp))
            Text("按订单总价统计，包含未付尾款", style = MaterialTheme.typography.bodySmall, color = muted)
            Spacer(Modifier.height(22.dp))
            if (budget == null) {
                HorizontalDivider(color = ink.copy(alpha = 0.20f))
                Spacer(Modifier.height(16.dp))
                Text("给新家定个预算", style = MaterialTheme.typography.titleMedium, color = ink)
                Text("设置总预算，让每一笔花费更有数 ›", style = MaterialTheme.typography.bodySmall, color = muted)
            } else {
                val over = budget.level == BudgetLevel.OVER
                val progressColor = when (budget.level) {
                    BudgetLevel.OVER -> MaterialTheme.colorScheme.error
                    BudgetLevel.WARNING -> LedgerTheme.extra.warning
                    else -> LedgerTheme.extra.expense
                }
                BoxWithConstraints {
                    if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.2f) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BudgetMetric("总预算", budget.budgetCents, muted, ink)
                            BudgetMetric(if (over) "已超支" else "预算剩余", abs(budget.remainingCents), muted, if (over) progressColor else ink)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BudgetMetric("总预算", budget.budgetCents, muted, ink, Modifier.weight(1f))
                            BudgetMetric(
                                if (over) "已超支" else "预算剩余",
                                abs(budget.remainingCents),
                                muted,
                                if (over) progressColor else ink,
                                Modifier.weight(1f),
                                Alignment.End,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = 0.18f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(budget.fraction.coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(progressColor),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when (budget.level) {
                        BudgetLevel.OVER -> "已使用 ${budget.percent}% · 已超出预算"
                        BudgetLevel.WARNING -> "已使用 ${budget.percent}% · 留意后续花费"
                        else -> "已使用 ${budget.percent}% · 预算使用正常"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = progressColor,
                )
            }
        }
    }
}

@Composable
private fun PaymentCard(
    title: String,
    cents: Long,
    subtitle: String,
    pending: Boolean,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    LedgerCard(modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (pending) Icons.Outlined.Schedule else Icons.Outlined.TaskAlt,
                    null,
                    Modifier.size(18.dp),
                    tint = if (pending) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                Money.format(cents),
                color = if (pending) MaterialTheme.colorScheme.onSurface else LedgerTheme.extra.expense,
                style = MaterialTheme.typography.titleLarge,
                fontSize = if (Money.format(cents).length > 11) 16.sp else 20.sp,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlertsCard(alerts: List<BudgetItem>, onClick: () -> Unit) {
    LedgerCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = LedgerTheme.extra.warning.copy(alpha = 0.18f),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = LedgerTheme.extra.warning)
                Spacer(Modifier.width(8.dp))
                Text("留意这些预算", style = MaterialTheme.typography.titleMedium)
            }
            alerts.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (item.usage.level == BudgetLevel.OVER) "超支 ${Money.format(-item.usage.remainingCents)}" else "已用 ${item.usage.percent}%",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = budgetLevelColor(item.usage.level),
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetMetric(
    label: String,
    cents: Long,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
        Text(Money.format(cents), style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}
