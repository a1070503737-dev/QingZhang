package com.zhuangxiu.ledger.ui.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.data.db.CategoryKind
import com.zhuangxiu.ledger.ui.components.BudgetCompareChart
import com.zhuangxiu.ledger.ui.components.EmptyHint
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerBackground
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerChip
import com.zhuangxiu.ledger.ui.components.LedgerSegments
import com.zhuangxiu.ledger.ui.components.MonthlyBarChart
import com.zhuangxiu.ledger.ui.components.PageHeading
import com.zhuangxiu.ledger.ui.components.PieChart
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Dates
import com.zhuangxiu.ledger.util.Money

@Composable
fun StatsScreen(
    bottomBar: @Composable () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
    header: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }
    val options = state.options

    LedgerBackground {
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RangePreset.entries.forEach { preset ->
                        LedgerChip(
                            selected = options.preset == preset,
                            onClick = {
                                if (preset == RangePreset.CUSTOM) showRangePicker = true else viewModel.setPreset(preset)
                            },
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
                    containerColor = LedgerTheme.extra.expenseContainer,
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("花费合计", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(state.totalCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = LedgerTheme.extra.expense)
                        Text("共 ${state.count} 笔账单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                val kinds = CategoryKind.entries
                LedgerSegments(
                    itemCount = kinds.size,
                    selectedIndex = kinds.indexOf(options.kind).coerceAtLeast(0),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    height = 48.dp,
                    role = Role.RadioButton,
                    onSelect = { idx -> viewModel.setKind(kinds[idx]) },
                ) { idx, isSelected ->
                    Text(
                        "按${kinds[idx].label.takeLast(2)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            confirmButton = {
                val start = pickerState.selectedStartDateMillis
                val end = pickerState.selectedEndDateMillis
                LedgerButton(
                    onClick = {
                        viewModel.setCustomRange(Dates.utcMillisToEpochDay(start!!), Dates.utcMillisToEpochDay(end!!))
                        showRangePicker = false
                    },
                    enabled = start != null && end != null,
                    minHeight = 38.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) { Text("确定") }
            },
            dismissButton = {
                LedgerButton(
                    onClick = { showRangePicker = false },
                    prominent = false,
                    minHeight = 38.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) { Text("取消") }
            },
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
