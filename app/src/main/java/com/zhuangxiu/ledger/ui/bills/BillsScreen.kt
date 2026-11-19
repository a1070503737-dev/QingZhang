package com.zhuangxiu.ledger.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.zhuangxiu.ledger.data.db.ExpenseWithDetails
import com.zhuangxiu.ledger.data.db.PaymentStatus
import com.zhuangxiu.ledger.ui.components.AmountInputDialog
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import com.zhuangxiu.ledger.ui.components.EmptyHint
import com.zhuangxiu.ledger.ui.components.ExpenseRow
import com.zhuangxiu.ledger.ui.components.SurfaceTone
import com.zhuangxiu.ledger.ui.components.LedgerBackground
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerChip
import com.zhuangxiu.ledger.ui.components.LedgerIconButton
import com.zhuangxiu.ledger.ui.components.LoadingHint
import com.zhuangxiu.ledger.ui.components.PageHeading
import com.zhuangxiu.ledger.ui.components.StatusChip
import com.zhuangxiu.ledger.ui.components.ledgerTextFieldColors
import com.zhuangxiu.ledger.ui.components.ledgerContainer
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Dates
import com.zhuangxiu.ledger.util.Money

@Composable
fun BillsScreen(
    bottomBar: @Composable () -> Unit,
    onAdd: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onEditExpense: (Long) -> Unit,
    onCopyExpense: (Long) -> Unit,
    viewModel: BillsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ExpenseWithDetails?>(null) }
    var menuTarget by remember { mutableStateOf<ExpenseWithDetails?>(null) }
    var paymentTarget by remember { mutableStateOf<ExpenseWithDetails?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { snackbar.showSnackbar(it) }
        }
    }
    LedgerBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = bottomBar,
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                LedgerButton(
                    onClick = onAdd,
                    minHeight = 56.dp,
                    contentPadding = PaddingValues(16.dp),
                ) { Icon(Icons.Outlined.Add, contentDescription = "记一笔") }
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                PageHeading("装修账单", "每一笔支出，都有迹可循", Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp))
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    placeholder = { Text("搜索商家、工人、备注") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.filter.query.isNotEmpty()) {
                            LedgerIconButton(onClick = { viewModel.setQuery("") }, size = 32.dp) {
                                Icon(Icons.Outlined.Close, contentDescription = "清空", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = ledgerTextFieldColors(),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LedgerChip(
                        selected = state.filter.pendingOnly,
                        onClick = { viewModel.setPendingOnly(!state.filter.pendingOnly) },
                        label = { Text("仅看待付") },
                    )
                    FilterDropdown(
                        label = "阶段",
                        selectedLabel = state.stages.firstOrNull { it.id == state.filter.stageId }?.name,
                        options = state.stages.map { it.id to it.name },
                        onSelect = viewModel::setStage,
                    )
                    FilterDropdown(
                        label = "类型",
                        selectedLabel = state.types.firstOrNull { it.id == state.filter.typeId }?.name,
                        options = state.types.map { it.id to it.name },
                        onSelect = viewModel::setType,
                    )
                    FilterDropdown(
                        label = "付款状态",
                        selectedLabel = state.filter.status?.label,
                        options = PaymentStatus.entries.map { it to it.label },
                        onSelect = viewModel::setStatus,
                    )
                    if (state.filter.isActive) {
                        LedgerButton(
                            onClick = viewModel::clearFilter,
                            prominent = false,
                            minHeight = 36.dp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text("清除", color = MaterialTheme.colorScheme.primary) }
                    }
                }
                val expenseColor = LedgerTheme.extra.expense
                Text(
                    buildAnnotatedString {
                        append("共 ${state.count} 笔，合计 ")
                        withStyle(SpanStyle(color = expenseColor)) { append(Money.format(state.totalCents)) }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                if (state.loading) LoadingHint()
                if (state.groups.isEmpty() && !state.loading) {
                    EmptyHint(
                        Icons.Outlined.SearchOff,
                        if (state.filter.isActive) "没有符合条件的账单" else "还没有账单",
                    )
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    state.groups.forEach { group ->
                        stickyHeader(key = "header-${group.date}") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .ledgerContainer(CircleShape, SurfaceTone.Raised)
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    Dates.formatGroupTitle(group.date),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    Money.format(group.totalCents),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = expenseColor,
                                )
                            }
                        }
                        items(group.items, key = { it.expense.id }) { item ->
                            SwipeToDeleteRow(
                                item = item,
                                confirming = pendingDelete?.expense?.id == item.expense.id,
                                onRequestDelete = { pendingDelete = item },
                                onLongClick = { menuTarget = item },
                                onClick = { onOpenExpense(item.expense.id) },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(22.dp)),
                            )
                        }
                    }
                }
            }
        }
    }

    menuTarget?.let { item ->
        ExpenseActionSheet(
            item = item,
            onDismiss = { menuTarget = null },
            onAction = { action ->
                menuTarget = null
                val id = item.expense.id
                when (action) {
                    ExpenseAction.OPEN -> onOpenExpense(id)
                    ExpenseAction.EDIT -> onEditExpense(id)
                    ExpenseAction.ADD_PAYMENT -> paymentTarget = item
                    ExpenseAction.MARK_PAID -> viewModel.markPaid(item)
                    ExpenseAction.COPY -> onCopyExpense(id)
                    ExpenseAction.DELETE -> pendingDelete = item
                }
            },
        )
    }

    paymentTarget?.let { item ->
        AmountInputDialog(
            title = "登记付款",
            initialCents = item.remainingCents,
            label = "本次付款金额（元）",
            hint = "剩余待付 ${Money.format(item.remainingCents)}，超出部分按剩余金额记录。付款日期记为今天。",
            onConfirm = { cents ->
                viewModel.addPayment(item.expense.id, cents)
                paymentTarget = null
            },
            onDismiss = { paymentTarget = null },
        )
    }

    pendingDelete?.let { item ->
        DeleteExpenseDialog(
            item = item,
            onConfirm = {
                viewModel.delete(item.expense.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private enum class ExpenseAction { OPEN, EDIT, ADD_PAYMENT, MARK_PAID, COPY, DELETE }

@Composable
private fun ExpenseActionSheet(
    item: ExpenseWithDetails,
    onDismiss: () -> Unit,
    onAction: (ExpenseAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.expense.payee.ifBlank { item.type.name },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(item.expense.status)
            }
            Text(
                "${item.stage.name} · ${item.type.name} · ${Dates.format(item.expense.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val expenseColor = LedgerTheme.extra.expense
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = expenseColor)) { append(Money.format(item.expense.totalCents)) }
                    if (item.remainingCents > 0) append(", 待付 ${Money.format(item.remainingCents)}")
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SheetItem(Icons.Outlined.Info, "查看详情") { onAction(ExpenseAction.OPEN) }
        SheetItem(Icons.Outlined.Edit, "编辑") { onAction(ExpenseAction.EDIT) }
        if (item.remainingCents > 0) {
            SheetItem(Icons.Outlined.Payments, "登记付款", "分期付款，记一笔这次付的钱") { onAction(ExpenseAction.ADD_PAYMENT) }
            SheetItem(Icons.Outlined.TaskAlt, "标记为已付清", "自动补记 ${Money.format(item.remainingCents)} 的结清付款") {
                onAction(ExpenseAction.MARK_PAID)
            }
        }
        SheetItem(Icons.Outlined.ContentCopy, "再记一笔", "沿用这笔的阶段、类型和商家") { onAction(ExpenseAction.COPY) }
        SheetItem(Icons.Outlined.DeleteOutline, "删除", destructive = true) { onAction(ExpenseAction.DELETE) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SheetItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title, color = color) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (destructive) color else MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwipeToDeleteRow(
    item: ExpenseWithDetails,
    confirming: Boolean,
    onRequestDelete: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeState = rememberSwipeToDismissBoxState(positionalThreshold = { width -> width * 0.4f })
    LaunchedEffect(confirming) {
        if (!confirming && swipeState.currentValue != SwipeToDismissBoxValue.Settled) swipeState.reset()
    }
    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { onRequestDelete() },
        backgroundContent = {
            val swiping = swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier
                    .fillMaxSize()
                    .background(if (swiping) MaterialTheme.colorScheme.error.copy(alpha = 0.88f) else Color.Transparent)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (swiping) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
                    Spacer(Modifier.width(6.dp))
                    Text("删除", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) {
        ExpenseRow(
            item = item,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.ledgerContainer(RoundedCornerShape(22.dp), SurfaceTone.Raised),
        )
    }
}

@Composable
private fun DeleteExpenseDialog(item: ExpenseWithDetails, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val extras = buildList {
        if (item.payments.isNotEmpty()) add("${item.payments.size} 条付款记录")
        if (item.attachments.isNotEmpty()) add("${item.attachments.size} 张凭证图片")
    }
    ConfirmDialog(
        title = "删除这笔账单？",
        text = buildString {
            append(item.expense.payee.ifBlank { "${item.stage.name} · ${item.type.name}" })
            append("，").append(Money.format(item.expense.totalCents)).append("，").append(Dates.format(item.expense.date))
            append("。\n\n")
            if (extras.isNotEmpty()) append(extras.joinToString("、")).append("会一并删除，")
            append("删除后无法恢复。")
        },
        confirmLabel = "删除",
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun <T> FilterDropdown(
    label: String,
    selectedLabel: String?,
    options: List<Pair<T, String>>,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        LedgerChip(
            selected = selectedLabel != null,
            onClick = { expanded = true },
            label = { Text(selectedLabel ?: label) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text("全部") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            options.forEach { (value, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
