package com.zhuangxiu.ledger.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zhuangxiu.ledger.data.db.ExpenseWithDetails
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.data.db.PaymentEntity
import com.zhuangxiu.ledger.ui.components.AmountInputDialog
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import com.zhuangxiu.ledger.ui.components.GlassVariant
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LiquidGlassButton
import com.zhuangxiu.ledger.ui.components.PaymentMethodPicker
import com.zhuangxiu.ledger.ui.components.StatusChip
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.liquidGlass
import com.zhuangxiu.ledger.util.Dates
import com.zhuangxiu.ledger.util.Money

/** 账单详情页（Liquid Glass 版）：光学总价透镜卡、液态分期进度条与冰晶凭证相框 */
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onViewImage: (expenseId: Long, index: Int) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }
    var showAddPayment by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<PaymentEntity?>(null) }
    val item = state.item

    LaunchedEffect(state.loading, item) { if (!state.loading && item == null) onBack() }

    SubPageScaffold(
        title = "账单详情",
        onBack = onBack,
        actions = {
            IconButton(
                onClick = { onEdit(viewModel.expenseId) },
                modifier = Modifier
                    .size(40.dp)
                    .liquidGlass(CircleShape, GlassVariant.Clear, cornerRadius = 20.dp, shadowElevation = 0.dp),
            ) { Icon(Icons.Outlined.Edit, contentDescription = "编辑") }
            IconButton(
                onClick = { showDelete = true },
                modifier = Modifier
                    .size(40.dp)
                    .liquidGlass(CircleShape, GlassVariant.Clear, cornerRadius = 20.dp, shadowElevation = 0.dp),
            ) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除") }
        },
    ) { modifier ->
        if (item == null) return@SubPageScaffold
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SummaryCard(item)
            PaymentCard(item, onAddPayment = { showAddPayment = true }, onDeletePayment = { paymentToDelete = it })
            InfoCard(item)
            if (item.attachments.isNotEmpty()) {
                LedgerCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("凭证 (${item.attachments.size}) ", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item.attachments.forEachIndexed { index, attachment ->
                                AsyncImage(
                                    model = viewModel.imageFile(attachment.fileName),
                                    contentDescription = "凭证",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(92.dp)
                                        .liquidGlass(RoundedCornerShape(16.dp), GlassVariant.Clear, cornerRadius = 16.dp, shadowElevation = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onViewImage(item.expense.id, index) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "删除这笔账单？",
            text = "付款记录和凭证图片会一并删除，且无法恢复。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                showDelete = false
                viewModel.delete(onDone = {})
            },
            onDismiss = { showDelete = false },
        )
    }
    if (showAddPayment && item != null) {
        var method by remember { mutableStateOf(PayMethod.UNSPECIFIED) }
        AmountInputDialog(
            title = "登记付款",
            initialCents = item.remainingCents,
            label = "本次付款金额（元）",
            hint = "剩余待付 ${Money.format(item.remainingCents)}，超出部分按剩余金额记录。付款日期记为今天。",
            onConfirm = {
                viewModel.addPayment(it, Dates.today(), method)
                showAddPayment = false
            },
            onDismiss = { showAddPayment = false },
            extra = { PaymentMethodPicker(method) { method = it } },
        )
    }
    paymentToDelete?.let { payment ->
        ConfirmDialog(
            title = "删除这条付款记录？",
            text = "${Dates.format(payment.date)} 付款 ${Money.format(payment.amountCents)}。删除后付款状态会自动更新。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                viewModel.deletePayment(payment)
                paymentToDelete = null
            },
            onDismiss = { paymentToDelete = null },
        )
    }
}

@Composable
private fun SummaryCard(item: ExpenseWithDetails) {
    LedgerCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(22.dp)) {
            val onColor = MaterialTheme.colorScheme.onPrimaryContainer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.expense.payee.ifBlank { "${item.stage.name} · ${item.type.name}" },
                    style = MaterialTheme.typography.titleMedium,
                    color = onColor,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(item.expense.status)
            }
            Spacer(Modifier.height(10.dp))
            Text("总价", style = MaterialTheme.typography.labelMedium, color = onColor.copy(alpha = 0.78f))
            Text(
                Money.format(item.expense.totalCents),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = onColor,
            )
        }
    }
}

@Composable
private fun PaymentCard(
    item: ExpenseWithDetails,
    onAddPayment: () -> Unit,
    onDeletePayment: (PaymentEntity) -> Unit,
) {
    LedgerCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("付款情况", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier
                        .weight(1f)
                        .liquidGlass(RoundedCornerShape(16.dp), GlassVariant.Clear, cornerRadius = 16.dp, shadowElevation = 0.dp)
                        .padding(12.dp),
                ) {
                    Text("已付", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(item.paidCents), style = MaterialTheme.typography.titleMedium)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .liquidGlass(RoundedCornerShape(16.dp), GlassVariant.Clear, cornerRadius = 16.dp, shadowElevation = 0.dp)
                        .padding(12.dp),
                ) {
                    Text("剩余待付", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Money.format(item.remainingCents),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.remainingCents > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val total = item.expense.totalCents
            val progress = if (total <= 0) 1f else (item.paidCents.toFloat() / total).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(9.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f), MaterialTheme.colorScheme.primary),
                            ),
                        ),
                )
            }
            val due = item.expense.dueDate
            if (due != null && item.remainingCents > 0) {
                Spacer(Modifier.height(10.dp))
                val daysLeft = due - Dates.today()
                Text(
                    text = "到期日：${Dates.format(due)}" + when {
                        daysLeft < 0 -> "（已逾期 ${-daysLeft} 天）"
                        daysLeft == 0L -> "（今天到期）"
                        else -> "（还有 $daysLeft 天）"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (daysLeft < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.payments.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                item.payments.sortedWith(compareBy({ it.date }, { it.id })).forEachIndexed { index, payment ->
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "第 ${index + 1} 次付款" + if (payment.note.isNotBlank()) " · ${payment.note}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${Dates.format(payment.date)} · ${payment.payMethod.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Money.format(payment.amountCents), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { onDeletePayment(payment) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "删除付款记录",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            if (item.remainingCents > 0) {
                Spacer(Modifier.height(14.dp))
                LiquidGlassButton(
                    onClick = onAddPayment,
                    prominent = false,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("登记付款", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(item: ExpenseWithDetails) {
    LedgerCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow("日期", Dates.format(item.expense.date))
            InfoRow("装修阶段", item.stage.name)
            InfoRow("费用类型", item.type.name)
            if (item.expense.payee.isNotBlank()) InfoRow("商家 / 工人", item.expense.payee)
            if (item.expense.note.isNotBlank()) InfoRow("备注", item.expense.note)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
