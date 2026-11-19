package com.zhuangxiu.ledger.ui.daily

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.ui.components.AmountKeypad
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import com.zhuangxiu.ledger.ui.components.SurfaceTone
import com.zhuangxiu.ledger.ui.components.LedgerDatePickerDialog
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerPanel
import com.zhuangxiu.ledger.ui.components.LedgerIconButton
import com.zhuangxiu.ledger.ui.components.LedgerSegments
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.amountOperationHint
import com.zhuangxiu.ledger.ui.components.applyAmountKey
import com.zhuangxiu.ledger.ui.components.ledgerTextFieldColors
import com.zhuangxiu.ledger.ui.components.hasAmountOperation
import com.zhuangxiu.ledger.ui.components.ledgerContainer
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import java.time.LocalDate

@Composable
fun DailyEditScreen(onBack: () -> Unit, viewModel: DailyEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.complete) {
        if (state.complete) {
            Toast.makeText(context, state.resultMessage, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }
    BackHandler(state.busy) { }
    DailyEditContent(
        state,
        viewModel.editing,
        onBack = { if (!state.busy) onBack() },
        onChange = viewModel::change,
        onKind = viewModel::kind,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@Composable
fun DailyEditContent(
    state: DailyEditState,
    editing: Boolean,
    onBack: () -> Unit,
    onChange: ((DailyEditState) -> DailyEditState) -> Unit,
    onKind: (EntryKind) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var noteFocused by remember { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    var categoriesOpen by remember { mutableStateOf(false) }
    var paymentOpen by remember { mutableStateOf(false) }
    val finishNote = {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    SubPageScaffold(
        title = if (editing) "编辑账目" else "记一笔",
        onBack = onBack,
        bottomBar = {
            if (!state.loading && !state.missing) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .ledgerContainer(
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            variant = SurfaceTone.Raised,
                        )
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!noteFocused) {
                        AmountKeypad(enabled = !state.busy) { key ->
                            onChange { old -> old.copy(amount = applyAmountKey(old.amount, key)) }
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    LedgerButton(
                        onClick = onSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "保存日常账目" },
                        minHeight = 54.dp,
                        enabled = state.valid && !state.busy,
                    ) {
                        Text(
                            if (state.busy) "保存中..." else "保存${state.kind.label}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
        actions = {
            if (editing && !state.missing) {
                LedgerIconButton(onClick = { deleteDialog = true }, enabled = !state.busy) {
                    Icon(Icons.Outlined.DeleteOutline, "删除账目")
                }
            }
        },
    ) { modifier ->
        if (state.loading) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.missing) {
            Text(state.error ?: "记录不存在", modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
        } else {
            Column(
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.bookName?.let { Text("账本 · $it", style = MaterialTheme.typography.labelLarge) }

                val kinds = EntryKind.entries
                LedgerSegments(
                    itemCount = kinds.size,
                    selectedIndex = kinds.indexOf(state.kind).coerceAtLeast(0),
                    height = 48.dp,
                    role = Role.RadioButton,
                    enabled = !state.busy,
                    onSelect = { idx -> onKind(kinds[idx]) },
                ) { idx, isSelected ->
                    Text(
                        text = kinds[idx].label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DailyAmountDisplay(
                    state.amount,
                    state.kind == EntryKind.INCOME,
                    state.amount.isNotEmpty() && !hasAmountOperation(state.amount) && !state.valid,
                    enabled = !state.busy,
                    onClick = { if (noteFocused) finishNote() },
                )

                val categories = DailyCategory.entries.filter { it.kind == state.kind }
                val visibleCategories = categories.take(8)
                visibleCategories.chunked(4).forEach { row ->
                    val selectedInRow = row.indexOfFirst { state.category == it && state.categoryName == null }
                    LedgerSegments(
                        itemCount = row.size,
                        selectedIndex = selectedInRow,
                        height = 58.dp,
                        role = Role.RadioButton,
                        enabled = !state.busy,
                        onSelect = { idx -> onChange { it.copy(category = row[idx], categoryName = null) } },
                    ) { idx, isSelected ->
                        val category = row[idx]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                category.icon(),
                                null,
                                Modifier.size(21.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                category.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                val custom = (listOfNotNull(state.categoryName) + state.customCategories.filter { it.kind == state.kind }.map { it.name }).distinct()
                val extraCategories = categories.drop(visibleCategories.size)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (custom.isNotEmpty() || extraCategories.isNotEmpty()) {
                        Box {
                            LedgerButton(
                                onClick = { categoriesOpen = true },
                                prominent = false,
                                enabled = !state.busy,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                val selected = state.categoryName ?: state.category.takeIf { it in extraCategories }?.label
                                Text(selected?.let { "更多分类 · $it" } ?: "更多分类")
                                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = categoriesOpen,
                                onDismissRequest = { categoriesOpen = false },
                                modifier = Modifier.heightIn(max = 320.dp),
                                shape = RoundedCornerShape(22.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                extraCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.label) },
                                        onClick = {
                                            onChange { it.copy(category = category, categoryName = null) }
                                            categoriesOpen = false
                                        },
                                    )
                                }
                                custom.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            onChange { it.copy(categoryName = name, category = DailyCategory.entries.last { cat -> cat.kind == it.kind }) }
                                            categoriesOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Box {
                        LedgerButton(
                            onClick = { paymentOpen = true },
                            enabled = !state.busy,
                            prominent = false,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(state.method.icon(), "支付方式", Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(state.sourceMethod ?: state.method.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = paymentOpen, onDismissRequest = { paymentOpen = false }) {
                            PayMethod.entries.forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method.label) },
                                    leadingIcon = { Icon(method.icon(), null) },
                                    onClick = {
                                        onChange { it.copy(method = method, sourceMethod = null) }
                                        paymentOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                state.originalCategory?.let { Text("原分类 · $it", style = MaterialTheme.typography.labelMedium) }
                state.account?.let { Text("原账户 · $it", style = MaterialTheme.typography.labelMedium) }
                state.sourceMethod?.let { Text("支付方式 · $it", style = MaterialTheme.typography.labelMedium) }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerButton(
                        onClick = { datePicker = true },
                        enabled = !state.busy,
                        prominent = false,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.CalendarToday, "记账日期", Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(LocalDate.ofEpochDay(state.day).toString())
                    }
                    LedgerButton(
                        onClick = {
                            val initial = state.minute.coerceAtLeast(0)
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> onChange { it.copy(minute = hour * 60 + minute) } },
                                initial / 60,
                                initial % 60,
                                true,
                            ).show()
                        },
                        enabled = !state.busy,
                        prominent = false,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.Schedule, "记账时间", Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.minute < 0) "时间未指定" else "%02d:%02d".format(state.minute / 60, state.minute % 60))
                    }
                }

                OutlinedTextField(
                    value = state.note,
                    onValueChange = { note -> onChange { it.copy(note = note) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { noteFocused = it.isFocused },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(18.dp),
                    colors = ledgerTextFieldColors(),
                    label = { Text("备注（可选）") },
                    maxLines = 3,
                    isError = state.note.length > 1000,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { finishNote() }),
                    trailingIcon = {
                        if (noteFocused) {
                            LedgerIconButton(onClick = finishNote, size = 34.dp) {
                                Icon(Icons.Outlined.Check, "完成备注", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (datePicker) {
        LedgerDatePickerDialog(
            initialEpochDay = state.day,
            onDismiss = { datePicker = false },
            onPick = { day ->
                onChange { it.copy(day = day) }
                datePicker = false
            },
        )
    }
    if (deleteDialog) {
        ConfirmDialog(
            title = "删除这笔账目？",
            text = "删除后将从本地账本和统计中移除，无法撤销。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                deleteDialog = false
                onDelete()
            },
            onDismiss = { deleteDialog = false },
        )
    }
}

@Composable
private fun DailyAmountDisplay(amount: String, income: Boolean, isError: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val amounts = LedgerTheme.extra
    val ink = if (isError) colors.onErrorContainer else amounts.transaction(income)
    val container = if (isError) colors.errorContainer else if (income) amounts.incomeContainer else amounts.expenseContainer
    val calculationHint = amountOperationHint(amount)
    LedgerPanel(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "日常记账金额" },
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        variant = SurfaceTone.Accent(container),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                "金额",
                style = MaterialTheme.typography.labelMedium,
                color = ink,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "¥",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ink,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = amount.ifEmpty { "0.00" },
                    fontSize = if (calculationHint == null) 32.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (amount.isEmpty()) ink.copy(alpha = 0.65f) else ink,
                )
            }
            if (calculationHint != null) {
                Text(calculationHint, style = MaterialTheme.typography.bodySmall, color = ink)
            } else if (isError) {
                Text("请输入大于 0 且不超过 2 位小数的有效金额", style = MaterialTheme.typography.bodySmall, color = colors.onErrorContainer)
            }
        }
    }
}
