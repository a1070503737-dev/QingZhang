package com.zhuangxiu.ledger.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhuangxiu.ledger.data.db.*
import com.zhuangxiu.ledger.domain.*
import com.zhuangxiu.ledger.ui.components.*
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Money
import java.time.LocalDate
import java.time.YearMonth

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
fun PayMethod.icon(): ImageVector = when(this) {
    PayMethod.WECHAT -> Icons.Outlined.ChatBubbleOutline
    PayMethod.ALIPAY -> Icons.Outlined.AccountBalanceWallet
    PayMethod.BANK -> Icons.Outlined.CreditCard
    PayMethod.CASH -> Icons.Outlined.Payments
    PayMethod.OTHER -> Icons.Outlined.MoreHoriz
}

@Composable
fun BookHeader(state: BookState, onSelect: (Long) -> Unit, onSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TopAppBar(title = {
        Column {
            Text("轻账", style = MaterialTheme.typography.titleLarge)
            Box {
                TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.semantics { contentDescription = "切换账本" }) {
                    Text(state.selected?.name ?: "正在打开账本")
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                }
                DropdownMenu(expanded, { expanded = false }) {
                    state.books.forEach { book -> DropdownMenuItem(text = { Text(book.name) },
                        leadingIcon = { Icon(if (book.kind == BookKind.DAILY) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.Cottage, null) },
                        trailingIcon = { if (book.id == state.selected?.id) Icon(Icons.Outlined.Check, null) },
                        onClick = { onSelect(book.id); expanded = false }) }
                }
            }
        }
    }, actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "设置") } })
}

@Composable
fun MonthSelector(month: YearMonth, onMonth: (Long) -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonth(-1) }) { Icon(Icons.Outlined.ChevronLeft, "上个月") }
        Text("${month.year}年${month.monthValue}月", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onToday) { Text("本月") }
        IconButton(onClick = { onMonth(1) }) { Icon(Icons.Outlined.ChevronRight, "下个月") }
    }
}

@Composable
private fun TotalsCard(totals: DailyTotals) {
    LedgerCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("本月结余", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
            Text(Money.format(totals.balance), style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.semantics { contentDescription = "本月结余 ${Money.format(totals.balance)}" })
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .15f))
            BoxWithConstraints {
                val compact = maxWidth < 290.dp || LocalDensity.current.fontScale > 1.25f
                val income = @Composable { Metric("本月收入", totals.income, true) }
                val expense = @Composable { Metric("本月支出", totals.expense, false) }
                if (compact) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { income(); expense() }
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { income(); expense() }
            }
        }
    }
}
@Composable private fun Metric(label: String, cents: Long, income: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Money.format(cents), style = MaterialTheme.typography.titleLarge,
            color = if (income) LedgerTheme.extra.success else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = "$label ${Money.format(cents)}" })
    }
}

@Composable
fun DailyHomeScreen(state: DailyState, header: @Composable () -> Unit, bottomBar: @Composable () -> Unit,
                    onMonth: (Long) -> Unit, onToday: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = header, bottomBar = bottomBar, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("记一笔") },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { MonthSelector(state.month, onMonth, onToday) }
            if (state.error != null) item { Text(state.error, color = MaterialTheme.colorScheme.error) }
            else if (state.summary == null) item { LoadingHint() }
            else {
                item { TotalsCard(state.summary.totals) }
                item { Text("最近账目", style = MaterialTheme.typography.titleLarge) }
                item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("搜索分类、备注或支付方式") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Outlined.Close, "清空搜索") } }) }
                val entries = state.summary.entries.filter { query.isBlank() || "${it.category.label} ${it.note} ${it.payMethod.label}".contains(query.trim(), true) }
                if (entries.isEmpty()) item { LedgerCard(Modifier.fillMaxWidth()) {
                    EmptyHint(Icons.AutoMirrored.Outlined.ReceiptLong, if (query.isBlank()) "这个月还没有账目，记下生活的第一笔吧" else "没有找到匹配的账目")
                } }
                items(entries, key = { it.id }) { entry -> DailyEntryRow(entry) { onEdit(entry.id) } }
            }
        }
    }
}

@Composable private fun DailyEntryRow(entry: DailyEntryEntity, onClick: () -> Unit) {
    LedgerCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(entry.category.icon(), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(entry.category.label, style = MaterialTheme.typography.titleMedium)
                    Text("${LocalDate.ofEpochDay(entry.day)} · ${"%02d:%02d".format(entry.minute / 60, entry.minute % 60)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.payMethod.label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text((if (entry.kind == EntryKind.INCOME) "+" else "−") + Money.format(entry.amountCents), style = MaterialTheme.typography.titleLarge,
                    color = if (entry.kind == EntryKind.INCOME) LedgerTheme.extra.success else MaterialTheme.colorScheme.onSurface)
            }
            if (entry.note.isNotBlank()) Text(entry.note, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DailyStatsScreen(state: DailyState, header: @Composable () -> Unit, bottomBar: @Composable () -> Unit,
                     onMonth: (Long) -> Unit, onToday: () -> Unit) {
    Scaffold(topBar = header, bottomBar = bottomBar) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { MonthSelector(state.month, onMonth, onToday) }
            if (state.error != null) item { Text(state.error, color = MaterialTheme.colorScheme.error) }
            else if (state.summary == null) item { LoadingHint() }
            else {
                val summary = state.summary
                item { TotalsCard(summary.totals) }
                item { LedgerCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("支出分类", style = MaterialTheme.typography.titleLarge)
                        if (summary.categories.isEmpty()) Text("本月暂无支出，收入会显示在下方趋势中。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else {
                            PieChart(summary.categories.map { CategorySlice(it.first.ordinal.toLong(), it.first.label, it.second) }, exactAmount = true)
                            summary.categories.forEach { (category, amount) ->
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row { Text(category.label, Modifier.weight(1f)); Text(Money.format(amount)) }
                                    LinearProgressIndicator(progress = { (amount.toDouble() / summary.totals.expense).toFloat() }, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                } }
                item { LedgerCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("近六个月收支", style = MaterialTheme.typography.titleLarge)
                        Text("截至 ${state.month} · 绿柱收入 / 蓝柱支出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DailyTrend(summary.months)
                        summary.months.forEach { month ->
                            Column {
                                Text(month.month.toString(), style = MaterialTheme.typography.labelMedium)
                                Text("收入 ${Money.format(month.totals.income)}   支出 ${Money.format(month.totals.expense)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text("退款计入收入；装修账本独立统计。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
            }
        }
    }
}

@Composable private fun DailyTrend(months: List<DailyMonth>) {
    val max = months.maxOfOrNull { maxOf(it.totals.income, it.totals.expense) }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        months.forEach { month -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.height(130.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(16.dp).height((month.totals.income.toDouble() / max * 130).toFloat().dp).background(LedgerTheme.extra.success, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                Box(Modifier.width(16.dp).height((month.totals.expense.toDouble() / max * 130).toFloat().dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
            }
            Text("${month.month.monthValue}月", style = MaterialTheme.typography.labelSmall)
        } }
    }
}

@Composable
fun BooksScreen(state: BookState, bottomBar: @Composable () -> Unit, onSelect: (Long) -> Unit,
                onAdd: (String, () -> Unit) -> Unit, onRename: (Long, String, () -> Unit) -> Unit,
                onSettings: () -> Unit, onClearError: () -> Unit) {
    var editing by remember { mutableStateOf<BookEntity?>(null) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(state.error) { if (state.error != null) saving = false }
    Scaffold(topBar = { TopAppBar(title = { Text("我的账本") }, actions = { IconButton(onSettings) { Icon(Icons.Outlined.Settings, "设置") } }) }, bottomBar = bottomBar) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("把生活与装修分开，账目更清楚。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.books, key = { it.id }) { book -> LedgerCard(Modifier.fillMaxWidth(), onClick = { onSelect(book.id) }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (book.kind == BookKind.DAILY) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.Cottage, null, tint = MaterialTheme.colorScheme.primary)
                        Text(book.name, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleLarge)
                        IconButton({ editing = book; name = book.name; showDialog = true }) { Icon(Icons.Outlined.Edit, "重命名${book.name}") }
                    }
                    Text(if (book.kind == BookKind.DAILY) "日常收入、支出与月度统计" else "装修项目、预算、付款与凭证", style = MaterialTheme.typography.bodyMedium)
                    Text(if (book.id == state.selected?.id) "正在使用" else "点击切换", color = MaterialTheme.colorScheme.primary)
                }
            } }
            item { OutlinedButton({ editing = null; name = ""; showDialog = true }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("新建生活类型账本") } }
            item { Text("数据仅保存在本机，无需登录。请定期在设置中导出全部账本备份。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (state.error != null && !showDialog) item { Text(state.error, color = MaterialTheme.colorScheme.error); TextButton(onClearError) { Text("知道了") } }
        }
    }
    if (showDialog) AlertDialog(onDismissRequest = { if (!saving) { showDialog = false; onClearError() } }, title = { Text(if (editing == null) "新建账本" else "重命名账本") },
        text = { Column { OutlinedTextField(name, { name = it.take(20) }, label = { Text("账本名称") }, singleLine = true, enabled = !saving)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(enabled = name.isNotBlank() && !saving, onClick = {
            saving = true; onClearError(); val done = { showDialog = false; saving = false }
            editing?.let { onRename(it.id, name, done) } ?: onAdd(name, done)
        }) { Text(if (saving) "保存中" else "保存") } }, dismissButton = { TextButton(enabled = !saving, onClick = { showDialog = false; onClearError() }) { Text("取消") } })
}
