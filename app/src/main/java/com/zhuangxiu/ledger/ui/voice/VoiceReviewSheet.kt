package com.zhuangxiu.ledger.ui.voice

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.domain.EntryDraft
import com.zhuangxiu.ledger.domain.parseEntryDate
import com.zhuangxiu.ledger.domain.validAmount
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceReviewSheet(state: VoiceEntryState, model: VoiceEntryViewModel) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val canDismiss by rememberUpdatedState(!state.busy)
    LaunchedEffect(state.complete) {
        if (state.complete) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            Toast.makeText(context, "已保存 ${state.drafts.size} 笔账目", Toast.LENGTH_SHORT).show()
            model.dismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = model::dismiss,
        modifier = Modifier.fillMaxHeight(0.94f),
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = remember { { _: SheetValue -> canDismiss } },
        ),
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("核对记账草稿", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = model::dismiss, enabled = canDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭记账草稿")
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("voice-review-scroll")
                    .verticalScroll(rememberScrollState(), overscrollEffect = null)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.bookName?.let { bookName ->
                    Text("账本 · $bookName", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = model::text,
                        label = { Text("识别文字，也可直接输入") },
                        placeholder = { Text("早餐 8 元，午饭 25 元") },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LedgerButton(onClick = model::parse, enabled = state.text.isNotBlank() && !state.busy) {
                        Text("解析文字")
                    }
                    state.drafts.forEachIndexed { index, draft ->
                        ReviewEntry(index + 1, draft, enabled = !state.busy,
                            onChange = { change -> model.edit(index, change) }, onRemove = { model.remove(index) })
                    }
                    if (state.drafts.isNotEmpty()) {
                        LedgerButton(
                            onClick = { model.save() }, enabled = state.canSave, modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (state.busy) "正在保存…" else "确认保存 ${state.drafts.size} 笔") }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (state.confirmSimilar) {
        ConfirmDialog(
            title = "可能有重复账目",
            text = "同一天已有相同类别和金额的账目，或本次草稿内有重复。请确认它们是不同的交易。",
            confirmLabel = "仍然保存",
            onConfirm = { model.save(allowSimilar = true) },
            onDismiss = model::dismissSimilar,
        )
    }
}

@Composable
private fun ReviewEntry(
    number: Int,
    draft: EntryDraft,
    enabled: Boolean,
    onChange: ((EntryDraft) -> EntryDraft) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    LedgerCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("第 $number 笔 · 请核对", style = MaterialTheme.typography.titleSmall)
                LedgerButton(onClick = onRemove, enabled = enabled, prominent = false) { Text("移除草稿") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryKind.entries.forEach { kind ->
                    LedgerChip(selected = draft.kind == kind, enabled = enabled, onClick = {
                        onChange { it.copy(kind = kind, category = it.category?.takeIf { category -> category.kind == kind }) }
                    }, label = { Text(kind.label) })
                }
            }
            if (draft.kind == null) Text("请选择收入或支出", style = MaterialTheme.typography.labelSmall)
            OutlinedTextField(
                value = draft.amount,
                onValueChange = { amount -> onChange { it.copy(amount = amount) } },
                label = { Text("金额（元）") },
                isError = !validAmount(draft.amount),
                supportingText = if (draft.amount.isBlank()) {
                    { Text("请填写实际金额") }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            val categories = DailyCategory.entries.filter { it.kind == draft.kind }
            Box {
                LedgerButton(onClick = { menu = true }, enabled = enabled && categories.isNotEmpty(), prominent = false) {
                    Text(draft.category?.let { "分类 · ${it.label}" } ?: "请选择分类")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    categories.forEach { category ->
                        DropdownMenuItem(text = { Text(category.label) }, onClick = {
                            onChange { it.copy(category = category) }
                            menu = false
                        })
                    }
                }
            }
            OutlinedTextField(
                value = draft.date, onValueChange = { date -> onChange { it.copy(date = date) } },
                label = { Text("日期（年-月-日）") }, singleLine = true,
                isError = parseEntryDate(draft.date) == null, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.note, onValueChange = { note -> if (note.length <= 1000) onChange { it.copy(note = note) } },
                label = { Text("备注") }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
