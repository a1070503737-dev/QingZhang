package com.zhuangxiu.ledger.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Carpenter
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhuangxiu.ledger.data.db.ExpenseWithDetails
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.data.db.PaymentStatus
import com.zhuangxiu.ledger.domain.BudgetLevel
import com.zhuangxiu.ledger.domain.BudgetUsage
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Dates
import com.zhuangxiu.ledger.util.Money

/**
 * 统一圆角折射卡片：可点击卡片自带手指跟随水滴凸透镜放大交互。
 */
@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val defaultLow = MaterialTheme.colorScheme.surfaceContainerLow
    val variant = if (containerColor == defaultLow || containerColor == MaterialTheme.colorScheme.surface) {
        GlassVariant.Regular
    } else {
        GlassVariant.Tinted(tint = containerColor, vivid = false)
    }
    LiquidGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        cornerRadius = 26.dp,
        variant = variant,
        shadowElevation = 10.dp,
        pressedScale = 0.984f,
        enableTilt = onClick != null,
        onClick = onClick,
        content = content,
    )
}

/** 子页面通用骨架：返回按钮采用全局水滴凸透镜圆形按钮 */
@Composable
fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    LiquidGlassBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LiquidGlassTopBar(
                    title = { Text(title) },
                    navigationIcon = {
                        LiquidLensIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = actions,
                )
            },
            floatingActionButton = floatingActionButton,
            bottomBar = bottomBar,
        ) { padding -> content(Modifier.padding(padding)) }
    }
}

@Composable
fun budgetLevelColor(level: BudgetLevel): Color = when (level) {
    BudgetLevel.OVER -> MaterialTheme.colorScheme.error
    BudgetLevel.WARNING -> LedgerTheme.extra.warning
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun BudgetProgressBar(usage: BudgetUsage, modifier: Modifier = Modifier) {
    val animatedFraction by animateFloatAsState(
        targetValue = usage.fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
        label = "budget-progress",
    )
    val barColor = budgetLevelColor(usage.level)
    val glass = LedgerTheme.glass
    val trackColor = if (glass.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(11.dp)
            .clip(CircleShape),
    ) {
        val r = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = r)
        if (animatedFraction > 0.005f) {
            val fillWidth = (size.width * animatedFraction).coerceAtLeast(size.height)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(barColor.copy(alpha = 0.82f), barColor), 0f, fillWidth),
                size = Size(fillWidth, size.height),
                cornerRadius = r,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.45f),
                topLeft = Offset(size.height * 0.25f, size.height * 0.16f),
                size = Size((fillWidth - size.height * 0.5f).coerceAtLeast(2f), size.height * 0.26f),
                cornerRadius = CornerRadius(size.height / 4f),
            )
        }
    }
}

@Composable
fun StatusChip(status: PaymentStatus, modifier: Modifier = Modifier) {
    val (tint, foreground) = when (status) {
        PaymentStatus.PAID -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        PaymentStatus.DEPOSIT -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        PaymentStatus.BALANCE_DUE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PaymentStatus.UNPAID -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Text(
        text = status.label,
        modifier = modifier
            .liquidGlass(CircleShape, GlassVariant.Tinted(tint), cornerRadius = 12.dp, shadowElevation = 0.dp)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = foreground,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun ExpenseRow(
    item: ExpenseWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        cornerRadius = 20.dp,
        variant = GlassVariant.Clear,
        shadowElevation = 0.dp,
        pressedScale = 0.986f,
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = if (onLongClick != null) "更多操作" else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StageBadge(item.stage.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.expense.payee.ifBlank { item.type.name },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString { append(item.stage.name).append(" · ").append(item.type.name) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.attachments.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = "有凭证",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (showDate) {
                    Text(
                        Dates.formatShort(item.expense.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = Money.format(item.expense.totalCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                if (item.expense.status == PaymentStatus.PAID) {
                    StatusChip(item.expense.status)
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "待付 ${Money.format(item.remainingCents)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        StatusChip(item.expense.status)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(74.dp)
                .liquidGlass(RoundedCornerShape(24.dp), GlassVariant.Tinted(MaterialTheme.colorScheme.primaryContainer), cornerRadius = 24.dp, shadowElevation = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text,
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun glassTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = LedgerTheme.glass.elevatedTop.copy(alpha = if (LedgerTheme.glass.isDark) 0.52f else 0.72f),
    unfocusedContainerColor = LedgerTheme.glass.surfaceTop.copy(alpha = if (LedgerTheme.glass.isDark) 0.36f else 0.56f),
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
    unfocusedBorderColor = LedgerTheme.glass.specularBright.copy(alpha = if (LedgerTheme.glass.isDark) 0.28f else 0.65f),
)

@Composable
fun AmountInputDialog(
    title: String,
    initialCents: Long = 0,
    label: String = "金额（元）",
    hint: String? = null,
    allowEmpty: Boolean = false,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    var text by remember { mutableStateOf(Money.toInput(initialCents)) }
    val focusRequester = remember { FocusRequester() }
    val cents = Money.parseToCents(text)
    val valid = (cents != null && cents > 0) || (allowEmpty && (text.isBlank() || cents == 0L))
    val glass = LedgerTheme.glass
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        containerColor = glass.elevatedTop.copy(alpha = if (glass.isDark) 0.90f else 0.92f),
        tonalElevation = 0.dp,
        modifier = Modifier.border(BorderStroke(1.dp, glass.specularBright), RoundedCornerShape(30.dp)),
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = Money.sanitizeInput(it) },
                    label = { Text(label) },
                    isError = text.isNotBlank() && !valid,
                    supportingText = {
                        if (text.isNotBlank() && !valid) Text("请输入有效金额，整数最多 9 位、小数最多 2 位")
                    },
                    prefix = { Text("¥") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = glassTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (hint != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                extra()
            }
        },
        confirmButton = {
            LiquidGlassButton(
                onClick = { onConfirm(cents ?: 0) },
                enabled = valid,
                minHeight = 40.dp,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) { Text("确定") }
        },
        dismissButton = {
            LiquidGlassButton(
                onClick = onDismiss,
                prominent = false,
                minHeight = 40.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("取消") }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun PaymentMethodPicker(
    method: PayMethod,
    onChange: (PayMethod) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val glass = LedgerTheme.glass
    Box {
        LiquidGlassButton(
            onClick = { expanded = true },
            prominent = false,
            minHeight = 40.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("支付方式 · ${method.label} ▾", color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = glass.elevatedTop.copy(alpha = if (glass.isDark) 0.92f else 0.95f),
        ) {
            PayMethod.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        onChange(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    val glass = LedgerTheme.glass
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        containerColor = glass.elevatedTop.copy(alpha = if (glass.isDark) 0.90f else 0.92f),
        tonalElevation = 0.dp,
        modifier = Modifier.border(BorderStroke(1.dp, glass.specularBright), RoundedCornerShape(30.dp)),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(12) },
                label = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = glassTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            LiquidGlassButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
                minHeight = 40.dp,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) { Text("确定") }
        },
        dismissButton = {
            LiquidGlassButton(
                onClick = onDismiss,
                prominent = false,
                minHeight = 40.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("取消") }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "确定",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val glass = LedgerTheme.glass
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        containerColor = glass.elevatedTop.copy(alpha = if (glass.isDark) 0.90f else 0.92f),
        tonalElevation = 0.dp,
        modifier = Modifier.border(BorderStroke(1.dp, glass.specularBright), RoundedCornerShape(30.dp)),
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            LiquidGlassButton(
                onClick = onConfirm,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = if (destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                minHeight = 40.dp,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            LiquidGlassButton(
                onClick = onDismiss,
                prominent = false,
                minHeight = 40.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("取消") }
        },
    )
}

@Composable
fun LedgerDatePickerDialog(
    initialEpochDay: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val date = initialEpochDay ?: Dates.today()
    val year = java.time.LocalDate.ofEpochDay(date).year
    val state = rememberDatePickerState(
        initialSelectedDateMillis = Dates.epochDayToUtcMillis(date),
        yearRange = minOf(year, 1900)..maxOf(year, 2100),
    )
    val glass = LedgerTheme.glass
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        colors = DatePickerDefaults.colors(
            containerColor = glass.elevatedTop.copy(alpha = if (glass.isDark) 0.92f else 0.95f),
        ),
        confirmButton = {
            LiquidGlassButton(
                onClick = { state.selectedDateMillis?.let { onPick(Dates.utcMillisToEpochDay(it)) } },
                enabled = state.selectedDateMillis != null,
                minHeight = 38.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) { Text("确定") }
        },
        dismissButton = {
            LiquidGlassButton(
                onClick = onDismiss,
                prominent = false,
                minHeight = 38.dp,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) { Text("取消") }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun PageHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LoadingHint(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("正在读取账本…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun StageBadge(name: String) {
    val icon = when (name) {
        "设计" -> Icons.Outlined.Architecture
        "拆改" -> Icons.Outlined.Handyman
        "水电" -> Icons.Outlined.WaterDrop
        "泥瓦" -> Icons.Outlined.GridView
        "木工" -> Icons.Outlined.Carpenter
        "油漆" -> Icons.Outlined.FormatPaint
        "安装" -> Icons.Outlined.Build
        "软装" -> Icons.Outlined.Chair
        else -> null
    }
    Box(
        Modifier
            .size(44.dp)
            .liquidGlass(
                shape = RoundedCornerShape(15.dp),
                variant = GlassVariant.Tinted(MaterialTheme.colorScheme.primaryContainer),
                cornerRadius = 15.dp,
                shadowElevation = 2.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == null) {
            Text(name.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
        } else {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
    }
}
