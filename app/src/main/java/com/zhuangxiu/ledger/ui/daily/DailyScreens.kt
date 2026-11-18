package com.zhuangxiu.ledger.ui.daily

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.zhuangxiu.ledger.data.db.BookEntity
import com.zhuangxiu.ledger.data.db.BookKind
import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.DailyEntryEntity
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.domain.CategorySlice
import com.zhuangxiu.ledger.domain.DailyMonth
import com.zhuangxiu.ledger.domain.DailyTotals
import com.zhuangxiu.ledger.ui.components.EmptyHint
import com.zhuangxiu.ledger.ui.components.GlassVariant
import com.zhuangxiu.ledger.ui.components.IsRobolectricRuntime
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerDatePickerDialog
import com.zhuangxiu.ledger.ui.components.LiquidGlassBackdrop
import com.zhuangxiu.ledger.ui.components.LiquidGlassButton
import com.zhuangxiu.ledger.ui.components.LiquidGlassSurface
import com.zhuangxiu.ledger.ui.components.LiquidGlassTopBar
import com.zhuangxiu.ledger.ui.components.LiquidLensIconButton
import com.zhuangxiu.ledger.ui.components.LiquidLensTrack
import com.zhuangxiu.ledger.ui.components.LoadingHint
import com.zhuangxiu.ledger.ui.components.PieChart
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.drawWaterDropletRim
import com.zhuangxiu.ledger.ui.components.glassTextFieldColors
import com.zhuangxiu.ledger.ui.components.liquidGlass
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

fun DailyCategory.icon(): ImageVector = when (this) {
    DailyCategory.FOOD -> Icons.Outlined.Restaurant
    DailyCategory.SHOPPING -> Icons.Outlined.ShoppingBag
    DailyCategory.TRANSPORT -> Icons.Outlined.DirectionsBus
    DailyCategory.HOME -> Icons.Outlined.Home
    DailyCategory.UTILITIES -> Icons.Outlined.Lightbulb
    DailyCategory.FUN -> Icons.Outlined.SportsEsports
    DailyCategory.HEALTH -> Icons.Outlined.LocalHospital
    DailyCategory.PHONE -> Icons.Outlined.PhoneAndroid
    DailyCategory.SALARY -> Icons.Outlined.WorkOutline
    DailyCategory.BONUS -> Icons.Outlined.EmojiEvents
    DailyCategory.SIDE_JOB -> Icons.Outlined.Schedule
    DailyCategory.GIFT -> Icons.Outlined.CardGiftcard
    DailyCategory.REFUND -> Icons.Outlined.Replay
    else -> Icons.Outlined.MoreHoriz
}

fun PayMethod.icon(): ImageVector = when (this) {
    PayMethod.WECHAT -> Icons.Outlined.ChatBubbleOutline
    PayMethod.ALIPAY -> Icons.Outlined.AccountBalanceWallet
    PayMethod.BANK -> Icons.Outlined.CreditCard
    PayMethod.CASH -> Icons.Outlined.Payments
    PayMethod.OTHER, PayMethod.UNSPECIFIED -> Icons.Outlined.MoreHoriz
}

@Composable
fun BookHeader(state: BookState, onSelect: (Long) -> Unit, onSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val glass = LedgerTheme.glass
    LiquidGlassTopBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("轻账", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Box {
                    LiquidGlassButton(
                        onClick = { expanded = true },
                        prominent = false,
                        minHeight = 36.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                        modifier = Modifier.semantics { contentDescription = "切换账本" },
                    ) {
                        Text(
                            text = if (state.all) "全部账本" else state.selected?.name ?: "请选择账本",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        shape = RoundedCornerShape(22.dp),
                        containerColor = glass.elevatedTop.copy(alpha = if (glass.isDark) 0.92f else 0.95f),
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部账本") },
                            onClick = {
                                onSelect(0)
                                expanded = false
                            },
                        )
                        state.books.filterNot { it.archived }.forEach { book ->
                            DropdownMenuItem(
                                text = { Text(book.name) },
                                leadingIcon = {
                                    Icon(
                                        if (book.kind == BookKind.DAILY) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.Cottage,
                                        null,
                                    )
                                },
                                trailingIcon = {
                                    if (!state.all && book.id == state.selected?.id) Icon(Icons.Outlined.Check, null)
                                },
                                onClick = {
                                    onSelect(book.id)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        actions = {
            LiquidLensIconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, "设置")
            }
        },
    )
}

@Composable
fun MonthSelector(month: YearMonth, onMonth: (Long) -> Unit, onToday: () -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(CircleShape, GlassVariant.Regular, cornerRadius = 26.dp, shadowElevation = 4.dp)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LiquidLensIconButton(onClick = { onMonth(-1) }, size = 36.dp) {
            Icon(Icons.Outlined.ChevronLeft, "上个月")
        }
        LiquidGlassButton(
            onClick = { showDatePicker = true },
            prominent = false,
            minHeight = 36.dp,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium)
        }
        LiquidGlassButton(
            onClick = onToday,
            prominent = false,
            minHeight = 36.dp,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("本月")
        }
        LiquidLensIconButton(onClick = { onMonth(1) }, size = 36.dp) {
            Icon(Icons.Outlined.ChevronRight, "下个月")
        }
    }
    if (showDatePicker) {
        LedgerDatePickerDialog(
            month.atDay(1).toEpochDay(),
            onPick = {
                val target = YearMonth.from(LocalDate.ofEpochDay(it))
                onMonth(ChronoUnit.MONTHS.between(month, target))
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun TotalsCard(totals: DailyTotals) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        cornerRadius = 28.dp,
        variant = GlassVariant.Tinted(MaterialTheme.colorScheme.primaryContainer),
        shadowElevation = 14.dp,
        enableTilt = true,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "本月结余",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                Money.format(totals.balance),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics { contentDescription = "本月结余 ${Money.format(totals.balance)}" },
            )
            HorizontalDivider(
                Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            )
            BoxWithConstraints {
                val compact = maxWidth < 290.dp || LocalDensity.current.fontScale > 1.25f
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Metric("本月收入", totals.income, true, Modifier.fillMaxWidth())
                        Metric("本月支出", totals.expense, false, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric("本月收入", totals.income, true, Modifier.weight(1f))
                        Metric("本月支出", totals.expense, false, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, cents: Long, income: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .liquidGlass(RoundedCornerShape(18.dp), GlassVariant.Clear, cornerRadius = 18.dp, shadowElevation = 0.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            Money.format(cents),
            style = MaterialTheme.typography.titleLarge,
            color = if (income) LedgerTheme.extra.success else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = "$label ${Money.format(cents)}" },
        )
    }
}

@Composable
fun DailyHomeScreen(
    state: DailyState,
    header: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    onMonth: (Long) -> Unit,
    onToday: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onProjects: (() -> Unit)? = null,
    bookNames: Map<Long, String> = emptyMap(),
    canAdd: Boolean = true,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LiquidGlassBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = header,
            bottomBar = bottomBar,
            floatingActionButton = {
                if (canAdd) {
                    LiquidGlassButton(
                        onClick = onAdd,
                        minHeight = 54.dp,
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("记一笔", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { MonthSelector(state.month, onMonth, onToday) }
                if (onProjects != null) {
                    item {
                        LiquidGlassButton(
                            onClick = onProjects,
                            prominent = false,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Cottage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("装修项目", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (state.error != null) {
                    item { Text(state.error, color = MaterialTheme.colorScheme.error) }
                } else if (!canAdd && state.summary == null) {
                    item { Text("没有使用中的账本，请在账本页创建或恢复一个账本") }
                } else if (state.summary == null) {
                    item { LoadingHint() }
                } else {
                    item { TotalsCard(state.summary.totals) }
                    item { Text("最近账目", style = MaterialTheme.typography.titleLarge) }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = CircleShape,
                            colors = glassTextFieldColors(),
                            placeholder = { Text("搜索分类、备注或支付方式") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    LiquidLensIconButton(onClick = { query = "" }, size = 32.dp) {
                                        Icon(Icons.Outlined.Close, "清空搜索", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                        )
                    }
                    val entries = state.summary.entries.filter {
                        query.isBlank() ||
                            "${it.displayCategory} ${it.note} ${it.displayPayment} ${it.sourceCategory.orEmpty()}".contains(query.trim(), true)
                    }
                    if (entries.isEmpty()) {
                        item {
                            LedgerCard(Modifier.fillMaxWidth()) {
                                EmptyHint(
                                    Icons.AutoMirrored.Outlined.ReceiptLong,
                                    if (query.isBlank()) "这个月还没有账目，记下生活的第一笔吧" else "没有找到匹配的账目",
                                )
                            }
                        }
                    }
                    items(entries, key = { it.id }) { entry ->
                        DailyEntryRow(entry, bookNames[entry.bookId]) { onEdit(entry.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyEntryRow(entry: DailyEntryEntity, bookName: String?, onClick: () -> Unit) {
    LedgerCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .liquidGlass(
                            shape = RoundedCornerShape(15.dp),
                            variant = GlassVariant.Tinted(MaterialTheme.colorScheme.secondaryContainer),
                            cornerRadius = 15.dp,
                            shadowElevation = 2.dp,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(entry.category.icon(), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(entry.displayCategory, style = MaterialTheme.typography.titleMedium)
                    val time = if (entry.minute < 0) "" else " · ${"%02d:%02d".format(entry.minute / 60, entry.minute % 60)}"
                    Text(
                        "${LocalDate.ofEpochDay(entry.day)}$time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(bookName, entry.displayPayment).joinToString(" · "),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    (if (entry.kind == EntryKind.INCOME) "+" else "-") + Money.format(entry.amountCents),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (entry.kind == EntryKind.INCOME) LedgerTheme.extra.success else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (entry.note.isNotBlank()) {
                Text(
                    entry.note,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class CalendarMetricMode(val label: String) {
    NET("净收支"),
    EXPENSE("支出"),
    INCOME("收入"),
}

private data class DayCashflowStats(
    val incomeCents: Long = 0L,
    val expenseCents: Long = 0L,
    val entries: List<DailyEntryEntity> = emptyList(),
) {
    val netCents: Long get() = incomeCents - expenseCents

    fun valueFor(mode: CalendarMetricMode): Long = when (mode) {
        CalendarMetricMode.NET -> netCents
        CalendarMetricMode.EXPENSE -> -expenseCents
        CalendarMetricMode.INCOME -> incomeCents
    }
}

private fun formatCalendarCellAmount(cents: Long, compactMode: Boolean): String {
    if (cents == 0L) return if (compactMode) "0" else "0.00"
    val sign = if (cents > 0L) "+" else "-"
    val absCents = abs(cents)
    if (compactMode) {
        return when {
            absCents >= 1_000_000L -> "$sign${"%.1f".format(absCents / 1_000_000.0)}w"
            absCents >= 100_000L -> "$sign${"%.1f".format(absCents / 100_000.0)}k"
            else -> "$sign${absCents / 100}"
        }
    }
    return when {
        absCents >= 10_000_000L -> "$sign${"%.1f".format(absCents / 1_000_000.0)}万"
        absCents >= 1_000_000L -> "$sign${"%.2f".format(absCents / 1_000_000.0)}万"
        else -> "$sign${"%.2f".format(absCents / 100.0)}"
    }
}

/**
 * 统计页收支日历 + 当日收支明细联动模块（1:1 对标蚂蚁财富收益日历交互逻辑并融合 Liquid Glass 光学水滴手感）：
 * - 顶部支持「月历格 / 每日柱图」视图切换、「净收支 / 支出 / 收入」水滴分段切换、「¥ / 简」金额精度切换及 `< YYYY年MM月 >` 月份步进；
 * - 7 列周日-周六日历圆角色块：正收益/净流入呈暖珊红浅底 (`+427.90`)、净支出呈薄荷翡翠浅底 (`-488.58`)、平账呈清雾浅灰 (`0.00`)、今日标 `今`、未来无账目日留白；
 * - 选中态呈高饱和实心翡翠绿 / 珊瑚红 / 冰晶蓝并配纯白字与悬浮投影，支持单点或按住手指在日历格上连续滑动水滴放大镜实时选日；
 * - 下方实时联动「当日收支明细 (YYYY.MM.DD)」卡片，支持「金额排序 ⇅ / 时间排序 ⇅」切换及点击账目直达编辑。
 */
@Composable
private fun IncomeExpenseCalendarSection(
    month: YearMonth,
    entries: List<DailyEntryEntity>,
    onMonth: (Long) -> Unit,
    onToday: () -> Unit,
    onEdit: ((String) -> Unit)?,
    bookNames: Map<Long, String>,
) {
    val glass = LedgerTheme.glass
    val isDark = glass.isDark
    val haptic = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val density = LocalDensity.current
    val today = remember { LocalDate.now() }

    val dayMap = remember(entries) {
        val grouped = entries.groupBy { it.day }
        grouped.mapValues { (_, list) ->
            var inc = 0L
            var exp = 0L
            for (e in list) {
                if (e.kind == EntryKind.INCOME) inc += e.amountCents else exp += e.amountCents
            }
            DayCashflowStats(incomeCents = inc, expenseCents = exp, entries = list)
        }
    }

    val defaultEpochDay = remember(month, dayMap, today) {
        val todayEpoch = today.toEpochDay()
        when {
            YearMonth.from(today) == month && dayMap.containsKey(todayEpoch) -> todayEpoch
            dayMap.isNotEmpty() -> dayMap.keys.max()
            YearMonth.from(today) == month -> todayEpoch
            else -> month.atDay(1).toEpochDay()
        }
    }

    var selectedEpochDay by rememberSaveable(month.toString()) { mutableLongStateOf(defaultEpochDay) }
    LaunchedEffect(month, entries.size) {
        val selMonth = YearMonth.from(LocalDate.ofEpochDay(selectedEpochDay))
        if (selMonth != month) {
            selectedEpochDay = defaultEpochDay
        }
    }

    var viewModeIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = 日历网格, 1 = 每日柱状图
    var metricIndex by rememberSaveable { mutableIntStateOf(0) }   // 0 = 净收支, 1 = 支出, 2 = 收入
    var compactAmount by rememberSaveable { mutableStateOf(false) }
    var sortByAmount by rememberSaveable { mutableStateOf(true) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val metricMode = CalendarMetricMode.entries[metricIndex.coerceIn(0, 2)]
    val activeDaysCount = dayMap.size

    // 正负色板（兼顾浅色与深色高对比度）
    val posBg = if (isDark) Color(0xFF3D1D25) else Color(0xFFFDF0F0)
    val posText = if (isDark) Color(0xFFFF8A80) else Color(0xFFD93025)
    val posSelectedBg = Color(0xFFD93F3A)

    val negBg = if (isDark) Color(0xFF13382A) else Color(0xFFE7F6EE)
    val negText = if (isDark) Color(0xFF6EE7B7) else Color(0xFF178058)
    val negSelectedBg = Color(0xFF357A5B)

    val zeroBg = if (isDark) Color(0xFF1A2538) else Color(0xFFF3F5F8)
    val zeroText = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 卡片 1：收支日历主体
        LedgerCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 标题与月度记账概览胶囊
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("收支日历", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.55f else 0.78f))
                            .padding(horizontal = 11.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "本月记账 ${activeDaysCount} 天 · 共 ${entries.size} 笔",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // 顶部三联液态水滴切换条：[月历/柱图]  [净收支/支出/收入]  [¥/简]
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiquidLensTrack(
                        itemCount = 2,
                        selectedIndex = viewModeIndex,
                        onSelect = { viewModeIndex = it },
                        modifier = Modifier.width(76.dp),
                        height = 36.dp,
                        role = Role.RadioButton,
                    ) { idx, isSel ->
                        Icon(
                            imageVector = if (idx == 0) Icons.Outlined.CalendarToday else Icons.Outlined.BarChart,
                            contentDescription = if (idx == 0) "日历视图" else "柱图视图",
                            modifier = Modifier.size(16.dp),
                            tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    LiquidLensTrack(
                        itemCount = CalendarMetricMode.entries.size,
                        selectedIndex = metricIndex,
                        onSelect = { metricIndex = it },
                        modifier = Modifier.weight(1f),
                        height = 36.dp,
                        role = Role.RadioButton,
                    ) { idx, isSel ->
                        Text(
                            text = CalendarMetricMode.entries[idx].label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    LiquidLensTrack(
                        itemCount = 2,
                        selectedIndex = if (compactAmount) 1 else 0,
                        onSelect = { compactAmount = (it == 1) },
                        modifier = Modifier.width(66.dp),
                        height = 36.dp,
                        role = Role.RadioButton,
                    ) { idx, isSel ->
                        Text(
                            text = if (idx == 0) "¥" else "简",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 内嵌居中月份切换行：< [ 2026年 11月 ] >
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiquidLensIconButton(onClick = { onMonth(-1) }, size = 34.dp) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月")
                    }
                    Spacer(Modifier.width(10.dp))
                    LiquidGlassButton(
                        onClick = { showMonthPicker = true },
                        prominent = false,
                        minHeight = 36.dp,
                        shape = RoundedCornerShape(12.dp),
                        cornerRadius = 12.dp,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "${month.year}年 ${month.monthValue}月",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    LiquidLensIconButton(onClick = { onMonth(1) }, size = 34.dp) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月")
                    }
                    if (month != YearMonth.from(today)) {
                        Spacer(Modifier.width(8.dp))
                        LiquidGlassButton(
                            onClick = onToday,
                            prominent = false,
                            minHeight = 32.dp,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("今", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (viewModeIndex == 0) {
                    // 星期表头：日 一 二 三 四 五 六
                    val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")
                    Row(Modifier.fillMaxWidth()) {
                        weekdays.forEach { wd ->
                            Text(
                                text = wd,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 7 列日历网格 + 连续按住滑动水滴放大镜选日
                    val firstDayOffset = month.atDay(1).dayOfWeek.value % 7 // 周日=0 .. 周六=6
                    val daysInMonth = month.lengthOfMonth()
                    val totalSlots = firstDayOffset + daysInMonth
                    val rowCount = (totalSlots + 6) / 7

                    val calendarGridLayer = rememberGraphicsLayer()
                    var gridSize by remember { mutableStateOf(IntSize.Zero) }
                    var gridScrubbing by remember { mutableStateOf(false) }
                    var scrubPos by remember { mutableStateOf(Offset.Zero) }

                    val loupeProgress by animateFloatAsState(
                        targetValue = if (gridScrubbing) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 540f),
                        label = "calendar-loupe-lift",
                    )
                    val animatedScrubPos by animateOffsetAsState(
                        targetValue = scrubPos,
                        animationSpec = spring(dampingRatio = 0.92f, stiffness = 2400f),
                        label = "calendar-loupe-pos",
                    )

                    fun dayAtOffset(pos: Offset, size: IntSize): Int? {
                        if (size.width <= 0 || size.height <= 0 || rowCount <= 0) return null
                        val col = (pos.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                        val row = (pos.y / (size.height.toFloat() / rowCount)).toInt().coerceIn(0, rowCount - 1)
                        val dayNum = row * 7 + col - firstDayOffset + 1
                        return dayNum.takeIf { it in 1..daysInMonth }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (loupeProgress > 0.01f) 14f else 0f)
                            .onGloballyPositioned { gridSize = it.size }
                            .pointerInput(month, rowCount) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                                    var lockedHorizontal = false
                                    scrubPos = down.position
                                    try {
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                            if (ch != null && ch.pressed) {
                                                val dx = abs(ch.position.x - down.position.x)
                                                val dy = abs(ch.position.y - down.position.y)
                                                if (!lockedHorizontal && dy > viewConfig.touchSlop && dy > dx * 1.3f) {
                                                    gridScrubbing = false
                                                    break
                                                }
                                                if (!lockedHorizontal && dx > viewConfig.touchSlop * 0.45f) {
                                                    lockedHorizontal = true
                                                    gridScrubbing = true
                                                }
                                                if (lockedHorizontal) {
                                                    scrubPos = ch.position
                                                    dayAtOffset(ch.position, size)?.let { dNum ->
                                                        val ep = month.atDay(dNum).toEpochDay()
                                                        if (ep != selectedEpochDay) {
                                                            selectedEpochDay = ep
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }
                                                    ch.consume()
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    } finally {
                                        gridScrubbing = false
                                    }
                                }
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent {
                                    calendarGridLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawLayer(calendarGridLayer)
                                },
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (r in 0 until rowCount) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    for (c in 0 until 7) {
                                        val dayNumber = r * 7 + c - firstDayOffset + 1
                                        if (dayNumber !in 1..daysInMonth) {
                                            Spacer(Modifier.weight(1f).height(52.dp))
                                        } else {
                                            val date = month.atDay(dayNumber)
                                            val epochDay = date.toEpochDay()
                                            val dayStats = dayMap[epochDay]
                                            val hasEntries = dayStats != null && dayStats.entries.isNotEmpty()
                                            val valCents = dayStats?.valueFor(metricMode) ?: 0L
                                            val isSelected = epochDay == selectedEpochDay
                                            val isToday = date == today
                                            val isFuture = date.isAfter(today)

                                            val tileBg = when {
                                                isSelected && valCents > 0L -> posSelectedBg
                                                isSelected && valCents < 0L -> negSelectedBg
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                valCents > 0L -> posBg
                                                valCents < 0L -> negBg
                                                !isFuture || hasEntries -> zeroBg
                                                else -> Color.Transparent
                                            }
                                            val dayTextColor = when {
                                                isSelected -> Color.White
                                                isToday -> MaterialTheme.colorScheme.primary
                                                isFuture && !hasEntries -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                            val amountTextColor = when {
                                                isSelected -> Color.White.copy(alpha = 0.95f)
                                                valCents > 0L -> posText
                                                valCents < 0L -> negText
                                                else -> zeroText
                                            }
                                            val subLabel = when {
                                                valCents != 0L -> formatCalendarCellAmount(valCents, compactAmount)
                                                isToday && !hasEntries -> "无账目"
                                                !isFuture || hasEntries || isSelected -> if (compactAmount) "0" else "0.00"
                                                else -> ""
                                            }
                                            val cellShape = RoundedCornerShape(11.dp)
                                            val cellInteraction = remember(dayNumber) { MutableInteractionSource() }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(52.dp)
                                                    .then(
                                                        if (isSelected && !IsRobolectricRuntime) {
                                                            Modifier.shadow(
                                                                elevation = 6.dp,
                                                                shape = cellShape,
                                                                ambientColor = tileBg.copy(alpha = 0.45f),
                                                                spotColor = tileBg.copy(alpha = 0.55f),
                                                            )
                                                        } else {
                                                            Modifier
                                                        },
                                                    )
                                                    .clip(cellShape)
                                                    .background(tileBg)
                                                    .clickable(
                                                        interactionSource = cellInteraction,
                                                        indication = null,
                                                        role = Role.Button,
                                                        onClick = {
                                                            selectedEpochDay = epochDay
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        },
                                                    )
                                                    .semantics {
                                                        contentDescription = "${month.monthValue}月${dayNumber}日 $subLabel"
                                                    }
                                                    .padding(horizontal = 2.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center,
                                                ) {
                                                    Text(
                                                        text = if (isToday) "今" else dayNumber.toString(),
                                                        fontSize = 14.sp,
                                                        lineHeight = 17.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = dayTextColor,
                                                        maxLines = 1,
                                                    )
                                                    if (subLabel.isNotEmpty()) {
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(
                                                            text = subLabel,
                                                            fontSize = 9.5.sp,
                                                            lineHeight = 12.sp,
                                                            fontWeight = if (valCents != 0L || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                            color = amountTextColor,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = TextOverflow.Clip,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 日历滑动放大水滴透镜
                        if (loupeProgress > 0.02f && gridSize.width > 0) {
                            val loupeW = with(density) { 78.dp.toPx() }
                            val loupeH = with(density) { 64.dp.toPx() }
                            val left = (animatedScrubPos.x - loupeW / 2f).coerceIn(-loupeW * 0.1f, gridSize.width - loupeW * 0.9f)
                            val top = (animatedScrubPos.y - loupeH * 0.72f).coerceIn(-loupeH * 0.35f, gridSize.height - loupeH * 0.65f)
                            val loupeShape = RoundedCornerShape(20.dp)
                            Canvas(
                                modifier = Modifier
                                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                                    .size(78.dp, 64.dp)
                                    .then(if (!IsRobolectricRuntime) Modifier.shadow(12.dp, loupeShape) else Modifier)
                                    .clip(loupeShape),
                            ) {
                                drawRect(if (isDark) Color(0xFF101B30).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.94f))
                                withTransform({
                                    scale(1.22f, 1.22f, pivot = Offset(size.width / 2f, size.height / 2f))
                                    translate(-left, -top)
                                }) {
                                    drawLayer(calendarGridLayer)
                                }
                                drawWaterDropletRim(
                                    cornerRadiusPx = 20.dp.toPx(),
                                    isDark = isDark,
                                    intensity = loupeProgress,
                                )
                            }
                        }
                    }
                } else {
                    // 每日柱状视图（支持点击任意柱子切换下方当日明细）
                    val daysInMonth = month.lengthOfMonth()
                    val maxAbs = (1..daysInMonth)
                        .maxOfOrNull { d -> abs(dayMap[month.atDay(d).toEpochDay()]?.valueFor(metricMode) ?: 0L) }
                        ?.coerceAtLeast(100L) ?: 100L

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        for (d in 1..daysInMonth) {
                            val ep = month.atDay(d).toEpochDay()
                            val valCents = dayMap[ep]?.valueFor(metricMode) ?: 0L
                            val isSel = ep == selectedEpochDay
                            val barColor = when {
                                valCents > 0L -> if (isSel) posSelectedBg else posText
                                valCents < 0L -> if (isSel) negSelectedBg else negText
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                            val ratio = (abs(valCents).toDouble() / maxAbs).toFloat().coerceIn(0.05f, 1f)
                            Column(
                                modifier = Modifier
                                    .width(24.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else Color.Transparent)
                                    .clickable(
                                        interactionSource = remember(d) { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        selectedEpochDay = ep
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Box(
                                    Modifier
                                        .width(12.dp)
                                        .height((104f * ratio).dp)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                        .background(barColor),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${d}日",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 卡片 2：联动当日收支明细 (YYYY.MM.DD)
        val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
        val selectedDayStats = dayMap[selectedEpochDay]
        val dayEntries = remember(selectedDayStats, sortByAmount) {
            val raw = selectedDayStats?.entries.orEmpty()
            if (sortByAmount) {
                raw.sortedWith(compareByDescending<DailyEntryEntity> { it.amountCents }.thenByDescending { it.minute })
            } else {
                raw.sortedWith(compareByDescending<DailyEntryEntity> { it.minute }.thenByDescending { it.id })
            }
        }

        LedgerCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("当日收支明细", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "(${selectedDate.year}.${"%02d".format(selectedDate.monthValue)}.${"%02d".format(selectedDate.dayOfMonth)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    LiquidGlassButton(
                        onClick = { sortByAmount = !sortByAmount },
                        prominent = false,
                        minHeight = 30.dp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (sortByAmount) "金额排序 ⇅" else "时间排序 ⇅",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (dayEntries.isEmpty()) {
                    Text(
                        text = "当日暂无收支账目，点击或滑动上方日历中的着色日期即可查看明细。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    val inc = selectedDayStats?.incomeCents ?: 0L
                    val exp = selectedDayStats?.expenseCents ?: 0L
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .liquidGlass(RoundedCornerShape(14.dp), GlassVariant.Clear, cornerRadius = 14.dp, shadowElevation = 0.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("收入 +${Money.format(inc)}", style = MaterialTheme.typography.labelMedium, color = posText)
                        Text("支出 -${Money.format(exp)}", style = MaterialTheme.typography.labelMedium, color = negText)
                        val net = inc - exp
                        Text(
                            text = "净额 ${if (net >= 0) "+" else "-"}${Money.format(abs(net))}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (net > 0) posText else if (net < 0) negText else MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    dayEntries.forEachIndexed { idx, entry ->
                        if (idx > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
                        }
                        val isInc = entry.kind == EntryKind.INCOME
                        LiquidGlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            cornerRadius = 16.dp,
                            variant = GlassVariant.Clear,
                            shadowElevation = 0.dp,
                            onClick = onEdit?.let { handler -> { handler(entry.id) } },
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .liquidGlass(
                                            shape = RoundedCornerShape(12.dp),
                                            variant = GlassVariant.Tinted(MaterialTheme.colorScheme.secondaryContainer),
                                            cornerRadius = 12.dp,
                                            shadowElevation = 0.dp,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(entry.category.icon(), null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(entry.displayCategory, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                        val tagText = bookNames[entry.bookId] ?: entry.displayPayment
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(
                                                    if (isInc) posBg else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                )
                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = tagText,
                                                fontSize = 10.sp,
                                                color = if (isInc) posText else MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    val timePart = if (entry.minute < 0) null else "%02d:%02d".format(entry.minute / 60, entry.minute % 60)
                                    val subInfo = listOfNotNull(timePart, entry.note.takeIf { it.isNotBlank() }).joinToString(" · ")
                                    if (subInfo.isNotEmpty()) {
                                        Text(
                                            text = subInfo,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Text(
                                    text = (if (isInc) "+" else "-") + "%.2f".format(entry.amountCents / 100.0),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isInc) posText else negText,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        LedgerDatePickerDialog(
            month.atDay(1).toEpochDay(),
            onPick = {
                val target = YearMonth.from(LocalDate.ofEpochDay(it))
                onMonth(ChronoUnit.MONTHS.between(month, target))
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false },
        )
    }
}

@Composable
fun DailyStatsScreen(
    state: DailyState,
    header: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    onMonth: (Long) -> Unit,
    onToday: () -> Unit,
    onEdit: ((String) -> Unit)? = null,
    bookNames: Map<Long, String> = emptyMap(),
) {
    LiquidGlassBackdrop {
        Scaffold(containerColor = Color.Transparent, topBar = header, bottomBar = bottomBar) { padding ->
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = padding.calculateBottomPadding() + 36.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { MonthSelector(state.month, onMonth, onToday) }
                if (state.error != null) {
                    item { Text(state.error, color = MaterialTheme.colorScheme.error) }
                } else if (state.summary == null) {
                    item { LoadingHint() }
                } else {
                    val summary = state.summary
                    item { TotalsCard(summary.totals) }
                    item {
                        IncomeExpenseCalendarSection(
                            month = state.month,
                            entries = summary.entries,
                            onMonth = onMonth,
                            onToday = onToday,
                            onEdit = onEdit,
                            bookNames = bookNames,
                        )
                    }
                    item {
                        LedgerCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("支出分类", style = MaterialTheme.typography.titleLarge)
                                if (summary.categories.isEmpty()) {
                                    Text("本月暂无支出，收入会显示在下方趋势中。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    PieChart(
                                        summary.categories.mapIndexed { i, (label, cents) -> CategorySlice(i.toLong(), label, cents) },
                                        exactAmount = true,
                                    )
                                    summary.categories.forEach { (category, amount) ->
                                        val ratio = if (summary.totals.expense <= 0L) 0f else (amount.toDouble() / summary.totals.expense).toFloat().coerceIn(0f, 1f)
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row {
                                                Text(category, Modifier.weight(1f))
                                                Text(Money.format(amount), fontWeight = FontWeight.Medium)
                                            }
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                            ) {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth(ratio)
                                                        .height(8.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            Brush.horizontalGradient(
                                                                listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), MaterialTheme.colorScheme.primary),
                                                            ),
                                                        ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        LedgerCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("近六个月收支", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "截至 ${state.month} · 绿柱收入 / 蓝柱支出",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                DailyTrend(summary.months)
                                summary.months.forEach { month ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .liquidGlass(RoundedCornerShape(14.dp), GlassVariant.Clear, cornerRadius = 14.dp, shadowElevation = 0.dp)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Text(month.month.toString(), style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            "收入 ${Money.format(month.totals.income)}   支出 ${Money.format(month.totals.expense)}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                Text(
                                    "装修项目按实际付款日期计入支出。全部账本包含已归档账本的历史交易。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyTrend(months: List<DailyMonth>) {
    val max = months.maxOfOrNull { maxOf(it.totals.income, it.totals.expense) }?.coerceAtLeast(1) ?: 1
    val incomeColor = LedgerTheme.extra.success
    val expenseColor = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        months.forEach { month ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.height(130.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val incH = (month.totals.income.toDouble() / max * 130).toFloat().dp
                    val expH = (month.totals.expense.toDouble() / max * 130).toFloat().dp
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(incH)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(Brush.verticalGradient(listOf(incomeColor, incomeColor.copy(alpha = 0.55f)))),
                    )
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(expH)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(Brush.verticalGradient(listOf(expenseColor, expenseColor.copy(alpha = 0.55f)))),
                    )
                }
                Text("${month.month.monthValue}月", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun BooksScreen(
    state: BookState,
    bottomBar: @Composable () -> Unit,
    onSelect: (Long) -> Unit,
    onSave: (BookEntity, () -> Unit) -> Unit,
    onSettings: () -> Unit,
    onClearError: () -> Unit,
) {
    var editingId by rememberSaveable { mutableStateOf(0L) }
    var kind by rememberSaveable { mutableStateOf(BookKind.DAILY) }
    var trackPayments by rememberSaveable { mutableStateOf(true) }
    var trackPhotos by rememberSaveable { mutableStateOf(true) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(state.error) { if (state.error != null) saving = false }

    if (!showDialog) {
        LiquidGlassBackdrop {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    LiquidGlassTopBar(
                        title = { Text("我的账本") },
                        actions = {
                            LiquidLensIconButton(onClick = onSettings) {
                                Icon(Icons.Outlined.Settings, "设置")
                            }
                        },
                    )
                },
                bottomBar = bottomBar,
            ) { padding ->
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding()),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 20.dp,
                        bottom = padding.calculateBottomPadding() + 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.books, key = { it.id }) { book ->
                        val active = !book.archived && !state.all && book.id == state.selected?.id
                        LedgerCard(
                            Modifier.fillMaxWidth(),
                            onClick = if (book.archived) null else ({ onSelect(book.id) }),
                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(42.dp)
                                            .liquidGlass(RoundedCornerShape(14.dp), GlassVariant.Clear, cornerRadius = 14.dp, shadowElevation = 0.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            if (book.kind == BookKind.DAILY) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.Cottage,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(book.name, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleLarge)
                                    LiquidLensIconButton(
                                        onClick = {
                                            editingId = book.id
                                            name = book.name
                                            kind = book.kind
                                            archived = book.archived
                                            trackPayments = book.trackPayments
                                            trackPhotos = book.trackPhotos
                                            showDialog = true
                                        },
                                        size = 38.dp,
                                    ) {
                                        Icon(Icons.Outlined.Edit, "编辑${book.name}")
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(if (book.kind == BookKind.DAILY) "普通账本" else "装修账本", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = if (book.archived) "已归档" else if (active) "正在使用" else "点击切换",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier
                                            .liquidGlass(CircleShape, GlassVariant.Clear, cornerRadius = 12.dp, shadowElevation = 0.dp)
                                            .padding(horizontal = 10.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                    item {
                        LiquidGlassButton(
                            onClick = {
                                editingId = 0
                                kind = BookKind.DAILY
                                trackPayments = true
                                trackPhotos = true
                                archived = false
                                name = ""
                                showDialog = true
                            },
                            prominent = false,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("新建账本", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    item {
                        Text(
                            "数据仅保存在本机，无需登录。请定期在设置中导出全部账本备份。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.error != null && !showDialog) {
                        item {
                            Text(state.error, color = MaterialTheme.colorScheme.error)
                            LiquidGlassButton(onClick = onClearError, prominent = false, minHeight = 36.dp) {
                                Text("知道了")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        val close = {
            if (!saving) {
                showDialog = false
                onClearError()
            }
        }
        BackHandler(onBack = close)
        SubPageScaffold(if (editingId == 0L) "新建账本" else "编辑账本", onBack = close) { padding ->
            Column(
                padding
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LedgerCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(20) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("账本名称") },
                            singleLine = true,
                            enabled = !saving,
                            shape = RoundedCornerShape(18.dp),
                            colors = glassTextFieldColors(),
                        )
                        if (editingId == 0L) {
                            val kinds = listOf(BookKind.DAILY to "普通账本", BookKind.RENOVATION to "装修账本")
                            LiquidLensTrack(
                                itemCount = kinds.size,
                                selectedIndex = if (kind == BookKind.DAILY) 0 else 1,
                                height = 46.dp,
                                role = Role.RadioButton,
                                enabled = !saving,
                                onSelect = { idx -> kind = kinds[idx].first },
                            ) { idx, selected ->
                                Text(
                                    kinds[idx].second,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (kind == BookKind.RENOVATION) {
                            BookOption("分期付款、订金与尾款", trackPayments, !saving) { trackPayments = it }
                            BookOption("项目凭证照片", trackPhotos, !saving) { trackPhotos = it }
                        }
                        if (editingId != 0L) BookOption("归档账本", archived, !saving) { archived = it }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LiquidGlassButton(
                    enabled = name.isNotBlank() && !saving,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        saving = true
                        onClearError()
                        val done = {
                            showDialog = false
                            saving = false
                        }
                        onSave(
                            BookEntity(
                                editingId,
                                name,
                                kind,
                                archived,
                                trackPayments && kind == BookKind.RENOVATION,
                                trackPhotos && kind == BookKind.RENOVATION,
                            ),
                            done,
                        )
                    },
                ) {
                    Text(if (saving) "保存中" else "保存")
                }
            }
        }
    }
}

@Composable
private fun BookOption(label: String, enabled: Boolean, editable: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(enabled, onChange, enabled = editable)
        Text(label)
    }
}
