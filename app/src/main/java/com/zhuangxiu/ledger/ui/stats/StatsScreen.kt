package com.zhuangxiu.ledger.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.data.db.CategoryKind
import com.zhuangxiu.ledger.ui.components.BudgetCompareChart
import com.zhuangxiu.ledger.ui.components.EmptyHint
import com.zhuangxiu.ledger.ui.components.GlassVariant
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LiquidGlassBackdrop
import com.zhuangxiu.ledger.ui.components.MonthlyBarChart
import com.zhuangxiu.ledger.ui.components.PageHeading
import com.zhuangxiu.ledger.ui.components.PieChart
import com.zhuangxiu.ledger.ui.components.liquidGlass
import com.zhuangxiu.ledger.ui.components.liquidInteractive
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Dates
import com.zhuangxiu.ledger.util.Money
import kotlin.math.roundToInt

/** 装修统计页（Liquid Glass 版）：玻璃筛选胶囊、弹性透镜分段切换与发光统计图表 */
@Composable
fun StatsScreen(
    bottomBar: @Composable () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
    header: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }
    val options = state.options
    val glass = LedgerTheme.glass

    LiquidGlassBackdrop {
        Scaffold(containerColor = Color.Transparent, topBar = header, bottomBar = bottomBar) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PageHeading("花费统计", "看清钱花在哪里，安排下一步装修", Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RangePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = options.preset == preset,
                            onClick = {
                                if (preset == RangePreset.CUSTOM) showRangePicker = true else viewModel.setPreset(preset)
                            },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = glass.surfaceTop.copy(alpha = 0.55f),
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                            ),
                            label = {
                                val custom = preset == RangePreset.CUSTOM && options.preset == RangePreset.CUSTOM &&
                                    options.customFrom != null && options.customTo != null
                                Text(
                                    if (custom) {
                                        "${Dates.formatShort(options.customFrom)} - ${Dates.formatShort(options.customTo)}"
                                    } else {
                                        preset.label
                                    },
                                )
                            },
                        )
                    }
                }
                LedgerCard(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("花费合计", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
                        Text(Money.format(state.totalCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("共 ${state.count} 笔账单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    }
                }
                // 液态透镜维度切换：按阶段 / 按类型
                val kinds = CategoryKind.entries
                val selectedIdx = kinds.indexOf(options.kind).coerceAtLeast(0)
                val animatedIdx by animateFloatAsState(
                    targetValue = selectedIdx.toFloat(),
                    animationSpec = spring(dampingRatio = 0.64f, stiffness = 380f),
                    label = "stats-kind-pill",
                )
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(46.dp)
                        .liquidGlass(CircleShape, GlassVariant.Regular, cornerRadius = 23.dp, shadowElevation = 4.dp)
                        .padding(4.dp),
                ) {
                    val itemWidth = maxWidth / kinds.size
                    val itemPx = constraints.maxWidth.toFloat() / kinds.size
                    Box(
                        Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .offset { IntOffset((animatedIdx * itemPx).roundToInt(), 0) }
                            .liquidGlass(CircleShape, GlassVariant.Tinted(MaterialTheme.colorScheme.primaryContainer), cornerRadius = 20.dp, shadowElevation = 2.dp),
                    )
                    Row(Modifier.fillMaxSize()) {
                        kinds.forEach { kind ->
                            val isSelected = options.kind == kind
                            val interactionSource = remember(kind) { MutableInteractionSource() }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .liquidInteractive(interactionSource, pressedScale = 1.04f)
                                    .selectable(
                                        selected = isSelected,
                                        interactionSource = interactionSource,
                                        indication = LocalIndication.current,
                                        role = Role.RadioButton,
                                        onClick = { viewModel.setKind(kind) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "按${kind.label.takeLast(2)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                ChartCard("花费占比") {
                    if (state.slices.isEmpty()) {
                        EmptyHint(Icons.Outlined.PieChartOutline, "这个时间范围内没有账单")
                    } else {
                        PieChart(state.slices)
                    }
                }
                ChartCard("每月花费") {
                    if (state.months.isEmpty()) {
                        EmptyHint(Icons.Outlined.BarChart, "这个时间范围内没有账单")
                    } else {
                        MonthlyBarChart(state.months)
                    }
                }
                ChartCard("预算与实际", subtitle = "按全部账单统计，不受时间范围影响") {
                    if (state.budgetItems.isEmpty()) {
                        EmptyHint(Icons.Outlined.BarChart, "还没有设置预算，可在“我的 › 预算设置”中添加")
                    } else {
                        BudgetCompareChart(state.budgetItems)
                    }
                }
            }
        }
    }

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = options.customFrom?.let(Dates::epochDayToUtcMillis),
            initialSelectedEndDateMillis = options.customTo?.let(Dates::epochDayToUtcMillis),
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            shape = RoundedCornerShape(30.dp),
            colors = DatePickerDefaults.colors(
                containerColor = glass.elevatedTop.copy(alpha = if (glass.isDark) 0.92f else 0.95f),
            ),
            confirmButton = {
                val start = pickerState.selectedStartDateMillis
                val end = pickerState.selectedEndDateMillis
                TextButton(
                    onClick = {
                        viewModel.setCustomRange(Dates.utcMillisToEpochDay(start!!), Dates.utcMillisToEpochDay(end!!))
                        showRangePicker = false
                    },
                    enabled = start != null && end != null,
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text("取消") } },
        ) {
            DateRangePicker(
                state = pickerState,
                title = { Text("选择时间范围", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                modifier = Modifier.height(480.dp),
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    LedgerCard(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}
