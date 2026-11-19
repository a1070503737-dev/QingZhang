package com.zhuangxiu.ledger.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuangxiu.ledger.domain.BudgetItem
import com.zhuangxiu.ledger.domain.BudgetLevel
import com.zhuangxiu.ledger.domain.CategorySlice
import com.zhuangxiu.ledger.domain.MonthAmount
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Money

@Composable
fun PieChart(slices: List<CategorySlice>, modifier: Modifier = Modifier, exactAmount: Boolean = false) {
    val palette = LedgerTheme.extra.expenseChart
    val expenseColor = LedgerTheme.extra.expense
    val total = slices.sumOf { it.cents }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(176.dp)) {
                val stroke = 24.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke),
                )
                var start = -90f
                val gapDegrees = if (slices.size > 1) 2.2f else 0f
                slices.forEachIndexed { index, slice ->
                    val rawSweep = if (total == 0L) 0f else slice.cents.toFloat() / total * 360f
                    val sweep = (rawSweep - gapDegrees).coerceAtLeast(if (rawSweep > 0f) 1.2f else 0f)
                    val baseColor = palette[index % palette.size]
                    drawArc(
                        color = baseColor,
                        startAngle = start + gapDegrees / 2f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                    start += rawSweep
                }
            }

            Column(
                modifier = Modifier.size(116.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("合计", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (exactAmount) Money.format(total) else Money.formatShort(total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = expenseColor,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slices.forEachIndexed { index, slice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .ledgerContainer(RoundedCornerShape(14.dp), SurfaceTone.Flat)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(11.dp)
                            .background(palette[index % palette.size], CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        slice.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val percent = if (total == 0L) 0.0 else slice.cents * 100.0 / total
                    Text(
                        String.format(java.util.Locale.ROOT, "%.1f%%", percent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(Money.format(slice.cents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = expenseColor)
                }
            }
        }
    }
}

@Composable
fun MonthlyBarChart(months: List<MonthAmount>, modifier: Modifier = Modifier) {
    val barColor = LedgerTheme.extra.expense
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = 10.sp, color = barColor, fontWeight = FontWeight.Medium)
    val measurer = rememberTextMeasurer()
    val max = months.maxOfOrNull { it.cents }?.coerceAtLeast(1) ?: 1
    val scroll = rememberScrollState()
    LaunchedEffect(months.size) { scroll.scrollTo(scroll.maxValue) }

    val slotWidth = 56.dp
    Box(modifier = modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Canvas(
            Modifier
                .widthIn(min = 280.dp)
                .width(slotWidth * months.size)
                .height(204.dp),
        ) {
            val labelHeight = 22.dp.toPx()
            val valueHeight = 18.dp.toPx()
            val chartBottom = size.height - labelHeight
            val chartHeight = chartBottom - valueHeight
            val slot = size.width / months.size
            val barWidth = (slot * 0.5f).coerceAtMost(28.dp.toPx())

            drawLine(gridColor, Offset(0f, chartBottom), Offset(size.width, chartBottom), strokeWidth = 1.dp.toPx())

            months.forEachIndexed { index, item ->
                val centerX = slot * index + slot / 2
                val barHeight = (item.cents.toFloat() / max * chartHeight)
                    .coerceAtLeast(if (item.cents > 0) 3.dp.toPx() else 0f)
                if (barHeight > 0) {
                    val radius = (barWidth / 2.2f).coerceAtMost(barHeight)
                    val left = centerX - barWidth / 2
                    val top = chartBottom - barHeight
                    val path = Path().apply {
                        moveTo(left, chartBottom)
                        lineTo(left, top + radius)
                        quadraticTo(left, top, left + radius, top)
                        lineTo(left + barWidth - radius, top)
                        quadraticTo(left + barWidth, top, left + barWidth, top + radius)
                        lineTo(left + barWidth, chartBottom)
                        close()
                    }
                    drawPath(path, color = barColor)
                    val value = measurer.measure(Money.formatShort(item.cents).removePrefix("¥"), valueStyle)
                    drawText(value, topLeft = Offset(centerX - value.size.width / 2, top - value.size.height - 2.dp.toPx()))
                }
                val text = if (item.month.monthValue == 1 || index == 0) {
                    "${item.month.year % 100}年${item.month.monthValue}月"
                } else {
                    "${item.month.monthValue}月"
                }
                val label = measurer.measure(text, labelStyle)
                drawText(label, topLeft = Offset(centerX - label.size.width / 2, chartBottom + 5.dp.toPx()))
            }
        }
    }
}

/**
 * 预算与实际花费对比。
 */
@Composable
fun BudgetCompareChart(items: List<BudgetItem>, modifier: Modifier = Modifier) {
    val max = items.maxOfOrNull { maxOf(it.usage.budgetCents, it.usage.spentCents) }?.coerceAtLeast(1) ?: 1
    val budgetColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.48f)
    val expenseColor = LedgerTheme.extra.expense
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(budgetColor, "预算")
            Spacer(Modifier.width(16.dp))
            LegendDot(expenseColor, "实际")
        }
        items.forEach { item ->
            val actualColor = if (item.usage.level == BudgetLevel.OVER) {
                MaterialTheme.colorScheme.error
            } else {
                budgetLevelColor(item.usage.level)
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${item.usage.percent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = actualColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                CompareBar(item.usage.budgetCents.toFloat() / max, budgetColor, Money.format(item.usage.budgetCents))
                Spacer(Modifier.height(4.dp))
                CompareBar(item.usage.spentCents.toFloat() / max, expenseColor, Money.format(item.usage.spentCents), labelColor = expenseColor)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompareBar(fraction: Float, color: Color, label: String, labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessLow),
        label = "compare-bar",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .weight(1f)
                .height(12.dp)
                .clip(CircleShape),
        ) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(color.copy(alpha = 0.12f), cornerRadius = radius)
            if (animated > 0f) {
                val width = (size.width * animated).coerceAtLeast(size.height)
                drawRoundRect(color, size = Size(width, size.height), cornerRadius = radius)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.widthIn(min = 84.dp),
        )
    }
}
