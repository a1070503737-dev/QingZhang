package com.zhuangxiu.ledger.ui.daily

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhuangxiu.ledger.data.db.BookEntity
import com.zhuangxiu.ledger.data.db.BookKind
import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.DailyEntryEntity
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.domain.CategorySlice
import com.zhuangxiu.ledger.domain.DailyMonth
import com.zhuangxiu.ledger.domain.DailyStatistics
import com.zhuangxiu.ledger.domain.DailyTotals
import com.zhuangxiu.ledger.ui.components.SurfaceTone
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerDatePickerDialog
import com.zhuangxiu.ledger.ui.components.LedgerBackground
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerPanel
import com.zhuangxiu.ledger.ui.components.LedgerTopBar
import com.zhuangxiu.ledger.ui.components.LedgerIconButton
import com.zhuangxiu.ledger.ui.components.LedgerSegments
import com.zhuangxiu.ledger.ui.components.LoadingHint
import com.zhuangxiu.ledger.ui.components.PieChart
import com.zhuangxiu.ledger.ui.components.incomeExpenseText
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.ledgerTextFieldColors
import com.zhuangxiu.ledger.ui.components.ledgerContainer
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

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
fun BookHeader(state: BookState, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    LedgerTopBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("轻账", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Box(Modifier.weight(1f, fill = false)) {
                    LedgerButton(
                        onClick = { expanded = true },
                        prominent = false,
                        minHeight = 48.dp,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        modifier = Modifier.semantics { contentDescription = "切换账本" },
                    ) {
                        Text(
                            text = if (state.all) "全部账本" else state.selected?.name ?: "请选择账本",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        shape = RoundedCornerShape(22.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("全部账本")
                                    Text("包含已归档账本", style = MaterialTheme.typography.bodySmall)
                                }
                            },
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
    )
}

@Composable
fun MonthSelector(month: YearMonth, onMonth: (Long) -> Unit, onToday: () -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    MonthSwipeRegion(month, onMonth, Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LedgerIconButton(onClick = { onMonth(-1) }, size = 36.dp) {
                Icon(Icons.Outlined.ChevronLeft, "上个月")
            }
            LedgerButton(
                onClick = { showDatePicker = true },
                prominent = false,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).semantics { contentDescription = "选择月份" },
            ) {
                Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (month != YearMonth.now()) {
                LedgerButton(
                    onClick = onToday,
                    prominent = false,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) { Text("本月") }
            }
            LedgerIconButton(onClick = { onMonth(1) }, size = 36.dp) {
                Icon(Icons.Outlined.ChevronRight, "下个月")
            }
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
    LedgerPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "本月结余",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                Money.format(totals.balance),
                style = MaterialTheme.typography.displaySmall,
                color = LedgerTheme.extra.balance(totals.balance, MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.semantics { contentDescription = "本月结余 ${Money.format(totals.balance)}" },
            )
            HorizontalDivider(
                Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
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
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            Money.format(cents),
            style = MaterialTheme.typography.titleLarge,
            color = LedgerTheme.extra.transaction(income),
            modifier = Modifier.semantics { contentDescription = "$label ${Money.format(cents)}" },
        )
    }
}

internal data class DailyDayGroup(
    val day: Long,
    val totals: DailyTotals,
    val entries: List<DailyEntryEntity>,
)

internal fun formatDailyGroupDate(day: Long, today: LocalDate = LocalDate.now()): String {
    val date = LocalDate.ofEpochDay(day)
    val weekday = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
    val base = if (date.year == today.year) {
        "${date.monthValue}月${date.dayOfMonth}日 $weekday"
    } else {
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日 $weekday"
    }
    return when (date) {
        today -> "$base · 今天"
        today.minusDays(1) -> "$base · 昨天"
        else -> base
    }
}

private fun matchesDailyQuery(entry: DailyEntryEntity, term: String): Boolean {
    if (term.isEmpty()) return true
    return entry.displayCategory.contains(term, ignoreCase = true) ||
        entry.note.contains(term, ignoreCase = true) ||
        entry.displayPayment.contains(term, ignoreCase = true) ||
        entry.sourceCategory?.contains(term, ignoreCase = true) == true ||
        Money.toInput(entry.amountCents).contains(term, ignoreCase = true)
}

internal fun filterAndGroupDailyEntries(entries: List<DailyEntryEntity>, query: String): List<DailyDayGroup> {
    val term = query.trim()
    val filtered = if (term.isEmpty()) entries else entries.filter { matchesDailyQuery(it, term) }
    return filtered.groupBy { it.day }.map { (day, dayEntries) ->
        DailyDayGroup(day = day, totals = DailyStatistics.totals(dayEntries), entries = dayEntries)
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
    readOnly: Boolean = false,
    archivedBookIds: Set<Long> = emptySet(),
    onDelete: ((String) -> Unit)? = null,
    entryAction: (@Composable () -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var query by rememberSaveable { mutableStateOf("") }
    val density = LocalDensity.current
    var addButtonHeight by remember { mutableIntStateOf(0) }
    val showEntryAction = (canAdd || entryAction != null) && WindowInsets.ime.getBottom(density) == 0
    val summaryEntries = state.summary?.entries.orEmpty()
    val dayGroups = remember(query, summaryEntries) {
        filterAndGroupDailyEntries(summaryEntries, query)
    }
    LedgerBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = header,
            bottomBar = bottomBar,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            },
            floatingActionButton = {
                if (showEntryAction) {
                    Box(Modifier.onSizeChanged { addButtonHeight = it.height }) {
                        if (entryAction != null) {
                            entryAction()
                        } else {
                            LedgerButton(
                                onClick = onAdd,
                                hapticFeedback = HapticFeedbackType.SegmentTick,
                                prominent = false,
                                shape = RoundedCornerShape(16.dp),
                                minHeight = 54.dp,
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                            ) {
                                Icon(Icons.Outlined.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("记一笔", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
        ) { padding ->
            val bottomClearance = if (showEntryAction) {
                with(density) { addButtonHeight.toDp() }.coerceAtLeast(54.dp) + 32.dp
            } else {
                24.dp
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = padding.calculateBottomPadding() + bottomClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { MonthSelector(state.month, onMonth, onToday) }
                if (readOnly) {
                    item { Text("已归档 · 历史明细。恢复使用后可继续记账。", style = MaterialTheme.typography.bodyMedium) }
                }
                if (onProjects != null) {
                    item {
                        LedgerButton(
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
                    item { Text("本月明细", style = MaterialTheme.typography.titleLarge) }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "搜索本月账目、分类或备注" },
                            singleLine = true,
                            shape = CircleShape,
                            colors = ledgerTextFieldColors(),
                            placeholder = { Text("搜索本月账目、分类或备注", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    LedgerIconButton(onClick = { query = "" }, size = 32.dp) {
                                        Icon(Icons.Outlined.Close, "清空搜索", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                        )
                    }
                    if (dayGroups.isEmpty()) {
                        item {
                            LedgerPanel(
                                modifier = Modifier.fillMaxWidth(),
                                variant = SurfaceTone.Flat,

                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        if (query.isBlank()) "本月还没有账目" else "没有找到匹配的账目",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    dayGroups.forEach { group ->
                        item(key = "day-${group.day}", contentType = "day-summary") {
                            DailyGroupHeader(group.day, group.totals)
                        }
                        items(group.entries, key = { "entry-${it.id}" }, contentType = { "entry" }) { entry ->
                            val showActions = !readOnly && entry.bookId !in archivedBookIds
                            DailyEntryRow(
                                entry,
                                bookNames[entry.bookId],
                                onClick = if (readOnly) null else ({ onEdit(entry.id) }),
                                showActions = showActions,
                                onDelete = if (showActions && onDelete != null) ({ onDelete(entry.id) }) else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyGroupHeader(day: Long, totals: DailyTotals) {
    val colors = MaterialTheme.colorScheme
    val amounts = LedgerTheme.extra
    val style = MaterialTheme.typography.bodySmall
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HorizontalDivider(color = colors.outlineVariant)
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(formatDailyGroupDate(day), style = style, color = colors.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("支出 ${Money.format(totals.expense)}", style = style, color = amounts.expense)
                Text("收入 ${Money.format(totals.income)}", style = style, color = amounts.income)
            }
        }
    }
}

@Composable
private fun DailyEntryRow(
    entry: DailyEntryEntity,
    bookName: String?,
    onClick: (() -> Unit)?,
    showActions: Boolean,
    onDelete: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val income = entry.kind == EntryKind.INCOME
    val projectPayment = entry.id.startsWith("payment:")
    val time = if (entry.minute < 0) null else "%02d:%02d".format(entry.minute / 60, entry.minute % 60)
    val subtitle = listOfNotNull(time, bookName, entry.displayPayment).joinToString(" · ")
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        LedgerPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            onClick = onClick,
            onLongClick = if (showActions) ({ menuOpen = true }) else null,
            onLongClickLabel = "账目操作",
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(42.dp).background(colors.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(entry.category.icon(), contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.displayCategory, style = MaterialTheme.typography.titleMedium)
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    if (entry.note.isNotBlank()) {
                        Text(entry.note, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                Text(
                    (if (income) "+" else "-") + Money.format(entry.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    color = LedgerTheme.extra.transaction(income),
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen && showActions,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = colors.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(if (projectPayment) "查看账单" else "编辑账目") },
                leadingIcon = {
                    Icon(if (projectPayment) Icons.AutoMirrored.Outlined.ReceiptLong else Icons.Outlined.Edit, null)
                },
                onClick = {
                    menuOpen = false
                    onClick?.invoke()
                },
            )
            if (onDelete != null && !projectPayment) {
                DropdownMenuItem(
                    text = { Text("删除账目", color = colors.error) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = colors.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
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

/** 统计日历与所选日期的账目。 */
@Composable
private fun IncomeExpenseCalendarSection(
    month: YearMonth,
    entries: List<DailyEntryEntity>,
    onEdit: ((String) -> Unit)?,
    bookNames: Map<Long, String>,
) {
    val isDark = LedgerTheme.isDark
    val haptic = LocalHapticFeedback.current
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

    val metricMode = CalendarMetricMode.entries[metricIndex.coerceIn(0, 2)]
    val activeDaysCount = dayMap.size

    val amounts = LedgerTheme.extra

    val zeroBg = if (isDark) MaterialTheme.colorScheme.surfaceContainer else Color(0xFFF3F5F8)
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
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 11.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "本月记账 ${activeDaysCount} 天 · 共 ${entries.size} 笔",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LedgerSegments(
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
                            tint = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    LedgerSegments(
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
                            color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    LedgerSegments(
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
                            color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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

                    val firstDayOffset = month.atDay(1).dayOfWeek.value % 7 // 周日=0 .. 周六=6
                    val daysInMonth = month.lengthOfMonth()
                    val totalSlots = firstDayOffset + daysInMonth
                    val rowCount = (totalSlots + 6) / 7

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
                            .pointerInput(month, rowCount) {
                                fun selectDay(pos: Offset) {
                                    val day = dayAtOffset(pos, size) ?: return
                                    val epochDay = month.atDay(day).toEpochDay()
                                    if (epochDay != selectedEpochDay) {
                                        selectedEpochDay = epochDay
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                detectDragGesturesAfterLongPress(
                                    onDragStart = ::selectDay,
                                    onDrag = { change, _ ->
                                        selectDay(change.position)
                                        change.consume()
                                    },
                                )
                            },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
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
                                                valCents > 0L -> amounts.incomeContainer
                                                valCents < 0L -> amounts.expenseContainer
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                !isFuture || hasEntries -> zeroBg
                                                else -> Color.Transparent
                                            }
                                            val dayTextColor = when {
                                                isSelected && valCents == 0L -> MaterialTheme.colorScheme.onPrimary
                                                isSelected -> MaterialTheme.colorScheme.onSurface
                                                isToday -> MaterialTheme.colorScheme.primary
                                                isFuture && !hasEntries -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                            val amountTextColor = amounts.balance(
                                                valCents,
                                                if (isSelected) MaterialTheme.colorScheme.onPrimary else zeroText,
                                            )
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
                                                    .clip(cellShape)
                                                    .background(tileBg)
                                                    .then(if (isSelected && valCents != 0L) Modifier.border(1.dp, amountTextColor, cellShape) else Modifier)
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
                            val barColor = amounts.balance(valCents, MaterialTheme.colorScheme.outlineVariant)
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
                                    color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    LedgerButton(
                        onClick = { sortByAmount = !sortByAmount },
                        prominent = false,
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
                        text = "当日暂无收支账目。点击日期查看明细，长按后可滑动选择日期。",
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
                            .ledgerContainer(RoundedCornerShape(14.dp), SurfaceTone.Flat)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("收入 +${Money.format(inc)}", style = MaterialTheme.typography.labelMedium, color = amounts.income)
                        Text("支出 -${Money.format(exp)}", style = MaterialTheme.typography.labelMedium, color = amounts.expense)
                        val net = inc - exp
                        Text(
                            text = "净额 ${if (net >= 0) "+" else "-"}${Money.format(abs(net))}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = amounts.balance(net, MaterialTheme.colorScheme.onSurface),
                        )
                    }

                    dayEntries.forEachIndexed { idx, entry ->
                        if (idx > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
                        }
                        val isInc = entry.kind == EntryKind.INCOME
                        LedgerPanel(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            variant = SurfaceTone.Flat,
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
                                        .ledgerContainer(
                                            shape = RoundedCornerShape(12.dp),
                                            variant = SurfaceTone.Accent(MaterialTheme.colorScheme.secondaryContainer),

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
                                                    if (isInc) amounts.incomeContainer else amounts.expenseContainer,
                                                )
                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = tagText,
                                                fontSize = 10.sp,
                                                color = amounts.transaction(isInc),
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
                                    color = amounts.transaction(isInc),
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
fun DailyStatsScreen(
    state: DailyState,
    header: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    onMonth: (Long) -> Unit,
    onToday: () -> Unit,
    onEdit: ((String) -> Unit)? = null,
    bookNames: Map<Long, String> = emptyMap(),
) {
    LedgerBackground {
        Scaffold(containerColor = Color.Transparent, topBar = header, bottomBar = bottomBar) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
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
                                                Text(Money.format(amount), fontWeight = FontWeight.Medium, color = LedgerTheme.extra.expense)
                                            }
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(8.dp)
                                                    .clip(CircleShape)
                                                    .background(LedgerTheme.extra.expenseContainer),
                                            ) {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth(ratio)
                                                        .height(8.dp)
                                                        .clip(CircleShape)
                                                        .background(LedgerTheme.extra.expense),
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
                                    "截至 ${state.month} · 红柱收入 / 绿柱支出",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                DailyTrend(summary.months)
                                summary.months.forEach { month ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .ledgerContainer(RoundedCornerShape(14.dp), SurfaceTone.Flat)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Text(month.month.toString(), style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            incomeExpenseText(month.totals.income, month.totals.expense),
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
    val incomeColor = LedgerTheme.extra.income
    val expenseColor = LedgerTheme.extra.expense
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
                            .background(incomeColor),
                    )
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(expH)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(expenseColor),
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
    onClearError: () -> Unit,
    onEditingChange: (Boolean) -> Unit = {},
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
    DisposableEffect(showDialog) {
        onEditingChange(showDialog)
        onDispose { onEditingChange(false) }
    }

    if (!showDialog) {
        LedgerBackground {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    LedgerTopBar(title = { Text("我的账本") })
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
                            containerColor = if (active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Row(
                                        Modifier.weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable(role = Role.Button, onClickLabel = if (book.archived) "查看历史" else "切换账本") {
                                                onSelect(book.id)
                                            }
                                            .heightIn(min = 48.dp)
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            if (book.kind == BookKind.DAILY) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.Cottage,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(book.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            val type = if (book.kind == BookKind.DAILY) "普通账本" else "装修账本"
                                            val status = if (book.archived) "已归档 · 查看历史" else if (active) "正在使用" else "切换账本"
                                            Text("$type · $status", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    LedgerIconButton(
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
                                    ) { Icon(Icons.Outlined.Edit, "编辑${book.name}") }
                                }
                                if (book.archived) {
                                    LedgerButton(
                                        onClick = {
                                            saving = true
                                            onSave(book.copy(archived = false)) { saving = false }
                                        },
                                        enabled = !saving,
                                        prominent = false,
                                        modifier = Modifier.align(Alignment.End),
                                    ) { Text("恢复使用") }
                                }
                            }
                        }
                    }
                    item {
                        LedgerButton(
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
                            Icon(Icons.Outlined.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("新建账本")
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
                            LedgerButton(onClick = onClearError, prominent = false) {
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
                            colors = ledgerTextFieldColors(),
                        )
                        if (editingId == 0L) {
                            val kinds = listOf(BookKind.DAILY to "普通账本", BookKind.RENOVATION to "装修账本")
                            LedgerSegments(
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
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
                LedgerButton(
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
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = enabled, enabled = editable, role = Role.Checkbox, onValueChange = onChange)
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(enabled, onCheckedChange = null, enabled = editable)
        Text(label)
    }
}
