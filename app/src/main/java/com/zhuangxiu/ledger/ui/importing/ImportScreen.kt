package com.zhuangxiu.ledger.ui.importing

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.importing.ImportField
import com.zhuangxiu.ledger.importing.Repeat
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import com.zhuangxiu.ledger.ui.components.GlassVariant
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LiquidGlassButton
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.glassTextFieldColors
import com.zhuangxiu.ledger.ui.components.liquidGlass
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Money
import java.time.LocalDate

@Composable
fun ImportScreen(onBack: () -> Unit, model: ImportViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val books by model.books.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::open) }
    var confirm by remember { mutableStateOf(false) }
    BackHandler(state.busy) { }

    SubPageScaffold("数据导入", onBack = { if (!state.busy) onBack() }) { padding ->
        LazyColumn(
            padding
                .fillMaxSize()
                .testTag("import-list"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LedgerCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("从鲨鱼记账导入", style = MaterialTheme.typography.headlineSmall)
                        Text("选择导出的 CSV 或 XLSX。确认前可以核对金额、映射列和分类。", style = MaterialTheme.typography.bodyMedium)
                        LiquidGlassButton(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("选择 CSV 或 XLSX 文件") }
                    }
                }
            }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            val receipt = state.receipt
            if (receipt != null) {
                item {
                    LedgerCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("导入完成", style = MaterialTheme.typography.titleLarge)
                            Text("成功 ${receipt.imported} 笔 · 跳过 ${receipt.skipped} 笔 · 解析失败 ${receipt.failed} 笔")
                            ImportAmounts(receipt.months.values.sumOf { it.income }, receipt.months.values.sumOf { it.expense })
                        }
                    }
                }
                items(receipt.months.entries.toList()) { (month, total) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .liquidGlass(RoundedCornerShape(16.dp), GlassVariant.Clear, cornerRadius = 16.dp, shadowElevation = 0.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(month.toString(), style = MaterialTheme.typography.labelLarge)
                        ImportAmounts(total.income, total.expense)
                    }
                }
            }
            val document = state.document
            val table = state.table
            if (document != null && table != null && receipt == null) {
                item {
                    LedgerCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(document.format, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            if (document.sheets.size > 1) {
                                ImportChoice("工作表", table.name, document.sheets.mapIndexed { i, s -> i to s.name }, !state.busy, model::sheet)
                            }
                            val targets: List<Pair<Long?, String>> = books.map { it.id to it.name } + (null to "新建普通账本")
                            ImportChoice(
                                "目标账本",
                                targets.firstOrNull { it.first == state.target }?.second ?: "请选择",
                                targets,
                                !state.busy,
                            ) { id -> model.change { it.copy(target = id) } }
                            if (state.target == null) {
                                OutlinedTextField(
                                    state.newName,
                                    { name -> model.change { it.copy(newName = name.take(20)) } },
                                    Modifier.fillMaxWidth(),
                                    label = { Text("新账本名称") },
                                    singleLine = true,
                                    enabled = !state.busy,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = glassTextFieldColors(),
                                )
                            }
                        }
                    }
                }
                item {
                    Text("文件列映射", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    val headerChoices = (table.rows.take(100).indices.toList() + state.header).distinct().map { it to "第 ${table.rows[it].number} 行" }
                    ImportChoice("表头", "第 ${table.rows[state.header].number} 行", headerChoices, !state.busy, model::header)
                }
                val headers = table.rows[state.header].cells
                val choices = (listOf(null to "未映射") + (0 until table.width).map { i ->
                    i to "${i + 1} · ${headers.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "无列名"}"
                })
                items(ImportField.entries) { field ->
                    ImportChoice(
                        field.label,
                        choices.firstOrNull { it.first == state.columns[field] }?.second.orEmpty(),
                        choices,
                        !state.busy,
                    ) { col ->
                        model.change { it.copy(columns = if (col == null) it.columns - field else it.columns + (field to col)) }
                    }
                }
                item {
                    ImportCheck("没有收支列时，正数为收入，负数为支出", state.signedAmounts, !state.busy) { checked ->
                        model.change { it.copy(signedAmounts = checked) }
                    }
                    LiquidGlassButton(
                        onClick = model::preview,
                        enabled = !state.busy,
                        prominent = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("生成预览", color = MaterialTheme.colorScheme.primary) }
                }
            }
            val preview = state.preview
            if (preview != null && receipt == null) {
                val repeats = preview.rows.filter { it.repeat != Repeat.NONE }
                val eligible = preview.rows.filter { it.repeat != Repeat.EXACT && (!state.skipPossible || it.repeat != Repeat.POSSIBLE) }
                item {
                    LedgerCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("导入预览", style = MaterialTheme.typography.titleLarge)
                            Text("共 ${preview.rows.size + preview.issues.size} 笔 · 可解析 ${preview.rows.size} 笔 · 无法解析 ${preview.issues.size} 笔")
                            val from = preview.rows.minOfOrNull { it.row.day }
                            val to = preview.rows.maxOfOrNull { it.row.day }
                            if (from != null && to != null) Text("${LocalDate.ofEpochDay(from)} 至 ${LocalDate.ofEpochDay(to)}")
                            ImportAmounts(
                                preview.rows.filter { it.row.kind == EntryKind.INCOME }.sumOf { it.row.cents },
                                preview.rows.filter { it.row.kind == EntryKind.EXPENSE }.sumOf { it.row.cents },
                            )
                            Text("疑似重复 ${repeats.size} 笔。同文件中已导入的行会跳过，文件内的相同明细会分别保留。")
                            if (repeats.any { it.repeat == Repeat.POSSIBLE }) {
                                ImportCheck("跳过与历史记录完全匹配的行", state.skipPossible, !state.busy) { checked ->
                                    model.options { it.copy(skipPossible = checked) }
                                }
                            }
                            if (preview.issues.isNotEmpty()) {
                                ImportCheck("跳过无法解析的行，仅写入有效记录", state.skipInvalid, !state.busy) { checked ->
                                    model.options { it.copy(skipInvalid = checked) }
                                }
                            }
                        }
                    }
                }
                if (preview.categories.isNotEmpty()) item { Text("分类映射", style = MaterialTheme.typography.titleMedium) }
                items(preview.categories.keys.toList()) { key ->
                    val original = key.original
                    val default = preview.categories.getValue(key)
                    val available = preview.existingCategories.getValue(key.kind)
                    val options = (listOf(default) + available).distinct().map { name ->
                        name to if (name == default && name !in available && original != null) "创建「$name」" else name
                    }
                    ImportChoice(
                        "${key.kind.label} · ${original ?: "未指定"}",
                        state.categories[key] ?: default,
                        options,
                        !state.busy,
                    ) { name -> model.options { it.copy(categories = it.categories + (key to name)) } }
                }
                if (repeats.isNotEmpty()) item { Text("重复明细", style = MaterialTheme.typography.titleMedium) }
                items(repeats) { item ->
                    val row = item.row
                    Text("第 ${row.number} 行 · ${LocalDate.ofEpochDay(row.day)} · ${row.category ?: "未指定"} · ${Money.format(row.cents)} · ${item.repeat.label}")
                }
                if (preview.issues.isNotEmpty()) item { Text("无法解析的记录", style = MaterialTheme.typography.titleMedium) }
                items(preview.issues) { problem -> Text("第 ${problem.number} 行 · ${problem.reason}", color = MaterialTheme.colorScheme.error) }
                item {
                    LedgerCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("本次将写入 ${eligible.size} 笔", style = MaterialTheme.typography.titleMedium)
                            ImportAmounts(
                                eligible.filter { it.row.kind == EntryKind.INCOME }.sumOf { it.row.cents },
                                eligible.filter { it.row.kind == EntryKind.EXPENSE }.sumOf { it.row.cents },
                            )
                            LiquidGlassButton(
                                onClick = { confirm = true },
                                enabled = !state.busy &&
                                    (preview.rows.isNotEmpty() || preview.issues.isNotEmpty()) &&
                                    (preview.issues.isEmpty() || state.skipInvalid),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("确认导入") }
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            "合并到目标账本？",
            "选中的账单会写入目标账本，已有账单会保留。",
            "导入",
            onConfirm = {
                confirm = false
                model.commit()
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun ImportAmounts(income: Long, expense: Long) {
    Text("收入 ${Money.format(income)} · 支出 ${Money.format(expense)}")
}

@Composable
private fun ImportCheck(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, enabled = enabled)
        Text(label)
    }
}

@Composable
private fun <T> ImportChoice(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    enabled: Boolean,
    select: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val glass = LedgerTheme.glass
    Box(Modifier.fillMaxWidth()) {
        LiquidGlassButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            prominent = false,
            enabled = enabled && options.isNotEmpty(),
            shape = RoundedCornerShape(18.dp),
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("$label · $value", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text("▾", color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 360.dp),
            shape = RoundedCornerShape(22.dp),
            containerColor = glass.elevatedTop.copy(alpha = if (glass.isDark) 0.92f else 0.95f),
        ) {
            options.forEach { (key, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        open = false
                        select(key)
                    },
                )
            }
        }
    }
}
