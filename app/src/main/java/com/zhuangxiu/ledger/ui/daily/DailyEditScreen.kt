package com.zhuangxiu.ledger.ui.daily

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.data.db.*
import com.zhuangxiu.ledger.ui.components.*
import java.time.LocalDate

@Composable
fun DailyEditScreen(onBack: () -> Unit, viewModel: DailyEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.complete) { if (state.complete) onBack() }
    BackHandler(state.busy) { }
    DailyEditContent(state, viewModel.editing, onBack = { if (!state.busy) onBack() }, onChange = viewModel::change,
        onKind = viewModel::kind, onSave = viewModel::save, onDelete = viewModel::delete)
}

@Composable
fun DailyEditContent(state: DailyEditState, editing: Boolean, onBack: () -> Unit,
                     onChange: ((DailyEditState) -> DailyEditState) -> Unit,
                     onKind: (EntryKind) -> Unit, onSave: () -> Unit, onDelete: () -> Unit) {
    var noteFocused by remember { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    LaunchedEffect(state.loading, state.missing) { if (!state.loading && !state.missing) { focus.requestFocus(); keyboard?.hide() } }
    SubPageScaffold(title = if (editing) "编辑账目" else "记一笔", onBack = onBack,
        bottomBar = {
            if (!state.loading && !state.missing) Surface {
                Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!noteFocused) {
            // 固定数字布局避免依赖输入法；金额框仍可接收粘贴和无障碍输入。
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { key -> FilledTonalButton(onClick = {
                        focus.requestFocus(); keyboard?.hide()
                        onChange { old -> old.copy(amount = when {
                            key == "⌫" -> old.amount.dropLast(1)
                            key == "." && old.amount.contains('.') -> old.amount
                            key == "." && old.amount.isBlank() -> "0."
                            else -> old.amount + key
                        }) }
                    }, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(key, fontSize = 21.sp, modifier = Modifier.semantics { contentDescription = if (key == "⌫") "退格" else "数字键$key" })
                    } }
                }
            }
                    }
                    Button(onSave, Modifier.fillMaxWidth().heightIn(min = 54.dp).semantics { contentDescription = "保存日常账目" }, enabled = state.valid && !state.busy) {
                        Text(if (state.busy) "保存中…" else "保存${state.kind.label}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        actions = { if (editing && !state.missing) IconButton(onClick = { deleteDialog = true }, enabled = !state.busy) { Icon(Icons.Outlined.DeleteOutline, "删除账目") } }) { modifier ->
        if (state.loading) Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (state.missing) Text(state.error ?: "记录不存在", modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
        else Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                EntryKind.entries.forEachIndexed { index, kind -> SegmentedButton(selected = state.kind == kind,
                    onClick = { onKind(kind) }, enabled = !state.busy,
                    shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(kind.label) } }
            }
            OutlinedTextField(state.amount, { input -> onChange { it.copy(amount = input) } },
                Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "日常记账金额" },
                enabled = !state.busy, singleLine = true, prefix = { Text("¥", fontSize = 28.sp) }, placeholder = { Text("0.00", fontSize = 36.sp) },
                textStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, showKeyboardOnFocus = false),
                isError = state.amount.isNotEmpty() && !state.valid,
                supportingText = { if (state.amount.isNotEmpty() && !state.valid) Text("金额须大于 0，最多两位小数，最高 999,999,999.99") })
            val categories = DailyCategory.entries.filter { it.kind == state.kind }
            categories.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    FilterChip(selected = state.category == category && state.categoryName == null, onClick = { onChange { it.copy(category = category, categoryName = null) } }, enabled = !state.busy,
                        label = { Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(category.icon(), null, Modifier.size(22.dp)); Text(category.label, style = MaterialTheme.typography.labelMedium)
                        } }, modifier = Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            } }
            val custom = (listOfNotNull(state.categoryName) + state.customCategories.filter { it.kind == state.kind }.map { it.name }).distinct()
            if (custom.isNotEmpty()) {
                var categoriesOpen by remember { mutableStateOf(false) }
                Box {
                    TextButton({ categoriesOpen = true }) { Text(state.categoryName ?: "更多分类") }
                    DropdownMenu(categoriesOpen, { categoriesOpen = false }, Modifier.heightIn(max = 320.dp)) {
                        custom.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                onChange { it.copy(categoryName = name, category = DailyCategory.entries.last { cat -> cat.kind == it.kind }) }
                                categoriesOpen = false
                            })
                        }
                    }
                }
            }
            state.originalCategory?.let { Text("原分类 · $it", style = MaterialTheme.typography.labelMedium) }
            state.account?.let { Text("原账户 · $it", style = MaterialTheme.typography.labelMedium) }
            state.sourceMethod?.let { Text("支付方式 · $it", style = MaterialTheme.typography.labelMedium) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayMethod.entries.forEach { method -> FilterChip(state.method == method, { onChange { it.copy(method = method, sourceMethod = null) } }, enabled = !state.busy,
                    label = { Text(method.label) }, leadingIcon = { Icon(method.icon(), null, Modifier.size(18.dp)) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { datePicker = true }, enabled = !state.busy) { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(LocalDate.ofEpochDay(state.day).toString()) }
                OutlinedButton(onClick = {
                    val initial = state.minute.coerceAtLeast(0)
                    TimePickerDialog(context, { _, hour, minute -> onChange { it.copy(minute = hour * 60 + minute) } }, initial / 60, initial % 60, true).show()
                }, enabled = !state.busy) { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (state.minute < 0) "时间未指定" else "%02d:%02d".format(state.minute / 60, state.minute % 60)) }
            }
            OutlinedTextField(state.note, { note -> onChange { it.copy(note = note) } }, Modifier.fillMaxWidth().onFocusChanged { noteFocused = it.isFocused }, enabled = !state.busy,
                label = { Text("备注（可选）") }, maxLines = 3, isError = state.note.length > 1000,
                trailingIcon = { if (noteFocused) IconButton({ keyboard?.hide(); focus.requestFocus() }) { Icon(Icons.Outlined.Check, "完成备注") } })
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(12.dp))
        }
    }
    if (datePicker) LedgerDatePickerDialog(initialEpochDay = state.day, onDismiss = { datePicker = false }, onPick = { day -> onChange { it.copy(day = day) }; datePicker = false })
    if (deleteDialog) ConfirmDialog(title = "删除这笔账目？", text = "删除后将从本地账本和统计中移除，无法撤销。", confirmLabel = "删除", destructive = true,
        onConfirm = { deleteDialog = false; onDelete() }, onDismiss = { deleteDialog = false })
}
