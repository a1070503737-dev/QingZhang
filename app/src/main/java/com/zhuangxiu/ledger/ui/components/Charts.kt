package com.zhuangxiu.ledger.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/**
 * 环形饼图 + 图例：展示各分类的花费占比。图表用 Compose Canvas 绘制。
 */
@Composable
fun PieChart(slices: List<CategorySlice>, modifier: Modifier = Modifier, exactAmount: Boolean = false) {
    val palette = LedgerTheme.extra.chart
    val total = slices.sumOf { it.cents }
    val gapColor = MaterialTheme.colorScheme.surfaceContainerLow
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(190.dp)) {
                val stroke = 34.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                var start = -90f
                slices.forEachIndexed { index, slice ->
                    val sweep = if (total == 0L) 0f else slice.cents.toFloat() / total * 360f
                    drawArc(
                        color = palette[index % palette.size],
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke),
                    )
                    start += sweep
                }
                // 在扇区交界处画细线，让各块之间有间隔
                if (slices.size > 1) {
                    var angle = -90f
                    val radius = size.minDimension / 2
                    slices.forEach { slice ->
                        val rad = Math.toRadians(angle.toDouble())
                        val direction = Offset(kotlin.math.cos(rad).toFloat(), kotlin.math.sin(rad).toFloat())
                        drawLine(
                            color = gapColor,
                            start = center + direction * (radius - stroke - 1f),
                            end = center + direction * (radius + 1f),
                            strokeWidth = 2.dp.toPx(),
                        )
                        angle += slice.cents.toFloat() / total * 360f
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("合计", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (exactAmount) Money.format(total) else Money.formatShort(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        // 图例：色块 + 名称 + 占比 + 金额
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slices.forEachIndexed { index, slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
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
                    Text(Money.format(slice.cents), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * 月度柱状图：每月花费趋势。月份多时可以横向滑动，默认滚动到最近的月份。
 */
@Composable
fun MonthlyBarChart(months: List<MonthAmount>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
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
                .height(200.dp),
        ) {
            val labelHeight = 22.dp.toPx()
            val valueHeight = 18.dp.toPx()
            val chartBottom = size.height - labelHeight
            val chartHeight = chartBottom - valueHeight
            val slot = size.width / months.size
            val barWidth = (slot * 0.5f).coerceAtMost(28.dp.toPx())

            // 底部基线
            drawLine(gridColor, Offset(0f, chartBottom), Offset(size.width, chartBottom), strokeWidth = 1.dp.toPx())

            months.forEachIndexed { index, item ->
                val centerX = slot * index + slot / 2
                val barHeight = (item.cents.toFloat() / max * chartHeight).coerceAtLeast(if (item.cents > 0) 2.dp.toPx() else 0f)
                if (barHeight > 0) {
                    // 顶部圆角的柱子
                    val radius = (barWidth / 3).coerceAtMost(barHeight)
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
                    drawPath(path, barColor)
                    // 柱顶金额
                    val value = measurer.measure(Money.formatShort(item.cents).removePrefix("¥"), valueStyle)
                    drawText(value, topLeft = Offset(centerX - value.size.width / 2, top - value.size.height - 2.dp.toPx()))
                }
                // 月份标签：1 月或第一根柱子带上年份
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
 * 预算与实际花费对比：每项预算两根横条，上面是预算，下面是实际（超支标红）。
 */
@Composable
fun BudgetCompareChart(items: List<BudgetItem>, modifier: Modifier = Modifier) {
    val max = items.maxOfOrNull { maxOf(it.usage.budgetCents, it.usage.spentCents) }?.coerceAtLeast(1) ?: 1
    val budgetColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 图例
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(budgetColor, "预算")
            Spacer(Modifier.width(16.dp))
            LegendDot(MaterialTheme.colorScheme.primary, "实际")
            Spacer(Modifier.width(16.dp))
            LegendDot(MaterialTheme.colorScheme.error, "超支")
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
                CompareBar(item.usage.spentCents.toFloat() / max, actualColor, Money.format(item.usage.spentCents))
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

/** 一根横向条 + 右侧金额 */
@Composable
private fun CompareBar(fraction: Float, color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .weight(1f)
                .height(12.dp),
        ) {
            val width = (size.width * fraction.coerceIn(0f, 1f)).coerceAtLeast(size.height)
            drawRoundRect(color, size = Size(width, size.height), cornerRadius = CornerRadius(size.height / 2))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 84.dp),
        )
    }
}
