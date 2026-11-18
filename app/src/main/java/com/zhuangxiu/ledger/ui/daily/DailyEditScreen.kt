package com.zhuangxiu.ledger.ui.daily

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.ui.components.AmountKeypad
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import com.zhuangxiu.ledger.ui.components.GlassVariant
import com.zhuangxiu.ledger.ui.components.LedgerDatePickerDialog
import com.zhuangxiu.ledger.ui.components.LiquidGlassButton
import com.zhuangxiu.ledger.ui.components.LiquidGlassSurface
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.applyAmountKey
import com.zhuangxiu.ledger.ui.components.glassTextFieldColors
import com.zhuangxiu.ledger.ui.components.liquidGlass
import com.zhuangxiu.ledger.ui.components.liquidInteractive
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DailyEditScreen(onBack: () -> Unit, viewModel: DailyEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.complete) { if (state.complete) onBack() }
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
    val glass = LedgerTheme.glass
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
                        .liquidGlass(
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            variant = GlassVariant.Elevated,
                            cornerRadius = 28.dp,
                            shadowElevation = 16.dp,
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
                    LiquidGlassButton(
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
                IconButton(
                    onClick = { deleteDialog = true },
                    enabled = !state.busy,
                    modifier = Modifier
                        .size(40.dp)
                        .liquidGlass(CircleShape, GlassVariant.Clear, cornerRadius = 20.dp, shadowElevation = 0.dp),
                ) {
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
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 液态滑动分段控制器（支出 / 收入）
                LiquidKindSegmentedSelector(
                    selected = state.kind,
                    enabled = !state.busy,
                    onSelect = onKind,
                )

                DailyAmountDisplay(
                    state.amount,
                    state.amount.isNotEmpty() && !state.valid,
                    enabled = !state.busy,
                    onClick = { if (noteFocused) finishNote() },
                )

                val categories = DailyCategory.entries.filter { it.kind == state.kind }
                categories.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { category ->
                            val isSelected = state.category == category && state.categoryName == null
                            LiquidGlassSurface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                cornerRadius = 18.dp,
                                variant = if (isSelected) {
                                    GlassVariant.Tinted(MaterialTheme.colorScheme.primaryContainer)
                                } else {
                                    GlassVariant.Regular
                                },
                                shadowElevation = if (isSelected) 6.dp else 2.dp,
                                pressedScale = 1.05f,
                                enabled = !state.busy,
                                role = Role.RadioButton,
                                onClick = { onChange { it.copy(category = category, categoryName = null) } },
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        category.icon(),
                                        null,
                                        Modifier.size(22.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        category.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                val custom = (listOfNotNull(state.categoryName) + state.customCategories.filter { it.kind == state.kind }.map { it.name }).distinct()
                if (custom.isNotEmpty()) {
                    var categoriesOpen by remember { mutableStateOf(false) }
                    Box {
                        TextButton({ categoriesOpen = true }) { Text(state.categoryName ?: "更多分类") }
                        DropdownMenu(
                            expanded = categoriesOpen,
                            onDismissRequest = { categoriesOpen = false },
                            modifier = Modifier.heightIn(max = 320.dp),
                            shape = RoundedCornerShape(22.dp),
                            containerColor = glass.elevatedTop.copy(alpha = if (glass.isDark) 0.92f else 0.95f),
                        ) {
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
                state.originalCategory?.let { Text("原分类 · $it", style = MaterialTheme.typography.labelMedium) }
                state.account?.let { Text("原账户 · $it", style = MaterialTheme.typography.labelMedium) }
                state.sourceMethod?.let { Text("支付方式 · $it", style = MaterialTheme.typography.labelMedium) }

                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayMethod.entries.forEach { method ->
                        FilterChip(
                            selected = state.method == method,
                            onClick = { onChange { it.copy(method = method, sourceMethod = null) } },
                            enabled = !state.busy,
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = glass.surfaceTop.copy(alpha = 0.52f),
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            ),
                            label = { Text(method.label) },
                            leadingIcon = { Icon(method.icon(), null, Modifier.size(18.dp)) },
                        )
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        onClick = { datePicker = true },
                        enabled = !state.busy,
                        prominent = false,
                        minHeight = 40.dp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.CalendarToday, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(LocalDate.ofEpochDay(state.day).toString())
                    }
                    LiquidGlassButton(
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
                        minHeight = 40.dp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
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
                    colors = glassTextFieldColors(),
                    label = { Text("备注（可选）") },
                    maxLines = 3,
                    isError = state.note.length > 1000,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { finishNote() }),
                    trailingIcon = {
                        if (noteFocused) IconButton(onClick = finishNote) { Icon(Icons.Outlined.Check, "完成备注") }
                    },
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
private fun LiquidKindSegmentedSelector(
    selected: EntryKind,
    enabled: Boolean,
    onSelect: (EntryKind) -> Unit,
) {
    val entries = EntryKind.entries
    val selectedIndex = entries.indexOf(selected).coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.64f, stiffness = 400f),
        label = "kind-pill",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .liquidGlass(CircleShape, GlassVariant.Regular, cornerRadius = 23.dp, shadowElevation = 4.dp)
            .padding(4.dp),
    ) {
        val segmentWidth = maxWidth / entries.size
        val segmentPx = constraints.maxWidth.toFloat() / entries.size
        Box(
            modifier = Modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .offset { IntOffset((animatedIndex * segmentPx).roundToInt(), 0) }
                .liquidGlass(
                    shape = CircleShape,
                    variant = GlassVariant.Tinted(MaterialTheme.colorScheme.primaryContainer),
                    cornerRadius = 20.dp,
                    shadowElevation = 2.dp,
                ),
        )
        Row(Modifier.fillMaxSize()) {
            entries.forEach { kind ->
                val isSelected = selected == kind
                val interactionSource = remember(kind) { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .liquidInteractive(interactionSource, enabled = enabled, pressedScale = 1.04f)
                        .selectable(
                            selected = isSelected,
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(kind) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = kind.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyAmountDisplay(amount: String, isError: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // 保持纯展示点击面板（无文本输入会话，点击不会弹出系统软键盘，100% 兼容 DailyScreenTest）
    LiquidGlassSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "日常记账金额" },
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        cornerRadius = 22.dp,
        variant = if (isError) GlassVariant.Tinted(colors.errorContainer) else GlassVariant.Elevated,
        shadowElevation = 6.dp,
        pressedScale = 0.985f,
    ) {
        Row(
            Modifier
                .heightIn(min = 76.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("¥", fontSize = 28.sp, color = if (isError) colors.error else colors.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                amount.ifEmpty { "0.00" },
                style = MaterialTheme.typography.headlineLarge,
                color = if (amount.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
            )
        }
    }
    if (isError) {
        Text(
            "金额须大于 0，最多两位小数，最高 999,999,999.99",
            color = colors.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
