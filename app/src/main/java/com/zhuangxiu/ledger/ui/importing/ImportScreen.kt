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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.zhuangxiu.ledger.ui.components.SurfaceTone
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.ledgerTextFieldColors
import com.zhuangxiu.ledger.ui.components.ledgerContainer
import com.zhuangxiu.ledger.util.Money
import java.time.LocalDate

@Composable
fun ImportScreen(onBack: () -> Unit, model: ImportViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val books by model.books.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) model.open(uris)
    }
    var confirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.preview, state.receipt, state.error) {
        if (state.preview != null || state.receipt != null || state.error != null) listState.scrollToItem(0)
    }
    BackHandler(state.busy) { }

    SubPageScaffold("数据导入", onBack = { if (!state.busy) onBack() }) { padding ->
        LazyColumn(
            padding
                .fillMaxSize()
                .testTag("import-list"),
            state = listState,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LedgerCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("从鲨鱼记账导入", style = MaterialTheme.typography.headlineSmall)
                        Text("可同时选择多个 CSV 或 XLSX 文件，核对预览后一并导入同一个账本。", style = MaterialTheme.typography.bodyMedium)
                        LedgerButton(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("选择 CSV 或 XLSX 文件") }
                    }
                }
            }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (state.duplicateFiles > 0) {
                item { Text("已跳过 ${state.duplicateFiles} 个内容完全相同的文件") }
            }
            if (state.fileErrors.isNotEmpty()) {
                item { Text("以下文件未加入导入", style = MaterialTheme.typography.titleMedium) }
                items(state.fileErrors) { file ->
                    Text("${file.name} · ${file.reason}", color = MaterialTheme.colorScheme.error)
                }
            }
            val receipt = state.receipt
            if (receipt != null) {
                item {
                    LedgerCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("导入完成", style = MaterialTheme.typography.titleLarge)
                            Text("已处理 ${state.files.size} 个文件")
                            Text("成功 ${receipt.imported} 笔 · 跳过 ${receipt.skipped} 笔 · 解析失败 ${receipt.failed} 笔")
                            ImportAmounts(receipt.months.values.sumOf { it.income }, receipt.months.values.sumOf { it.expense })
                        }
                    }
                }
                items(receipt.months.entries.toList()) { (month, total) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .ledgerContainer(RoundedCornerShape(16.dp), SurfaceTone.Flat)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(month.toString(), style = MaterialTheme.typography.labelLarge)
                        ImportAmounts(total.income, total.expense)
                    }
                }
            }
            val file = state.file
            if (file != null && receipt == null && state.preview == null) {
                val document = file.document
                val table = file.table
                item {
                    LedgerCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("已选 ${state.files.size} 个文件", style = MaterialTheme.typography.titleMedium)
                            if (state.files.size > 1) {
                                ImportChoice(
                                    "配置文件",
                                    file.name,
                                    state.files.mapIndexed { index, source -> index to "${index + 1} · ${source.name}" },
                                    !state.busy,
                                    model::selectFile,
                                )
                                LedgerButton(onClick = model::removeFile, enabled = !state.busy, prominent = false) {
                                    Text("移除当前文件")
                                }
                            } else {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                            }
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
                                    colors = ledgerTextFieldColors(),
                                )
                            }
                            LedgerButton(
                                onClick = model::preview,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("生成预览") }
                        }
                    }
                }
                item {
                    Text("当前文件的列映射", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    val headerChoices = (table.rows.take(100).indices.toList() + file.header).distinct().map { it to "第 ${table.rows[it].number} 行" }
                    ImportChoice("表头", "第 ${table.rows[file.header].number} 行", headerChoices, !state.busy, model::header)
                }
                val headers = table.rows[file.header].cells
                val choices = (listOf(null to "未映射") + (0 until table.width).map { i ->
                    i to "${i + 1} · ${headers.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "无列名"}"
                })
                items(ImportField.entries) { field ->
                    ImportChoice(
                        field.label,
                        choices.firstOrNull { it.first == file.columns[field] }?.second.orEmpty(),
                        choices,
                        !state.busy,
                    ) { col ->
                        model.changeFile { it.copy(columns = if (col == null) it.columns - field else it.columns + (field to col)) }
                    }
                }
                item {
                    ImportCheck("没有收支列时，正数为收入，负数为支出", file.signedAmounts, !state.busy) { checked ->
                        model.changeFile { it.copy(signedAmounts = checked) }
                    }
                }
            }
            val preview = state.preview
            if (preview != null && receipt == null) {
                val repeats = preview.rows.filter { it.repeat != Repeat.NONE }
                val eligible = preview.rows.filter { it.repeat.included(state.skipPossible) }
                item {
                    LedgerCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("导入预览", style = MaterialTheme.typography.titleLarge)
                            Text("合计 ${state.files.size} 个文件")
                            LedgerButton(onClick = { model.change { it } }, enabled = !state.busy, prominent = false) {
                                Text("返回调整文件")
                            }
                            Text("共 ${preview.rows.size + preview.issues.size} 笔 · 可解析 ${preview.rows.size} 笔 · 无法解析 ${preview.issues.size} 笔")
                            val from = preview.rows.minOfOrNull { it.row.day }
                            val to = preview.rows.maxOfOrNull { it.row.day }
                            if (from != null && to != null) Text("${LocalDate.ofEpochDay(from)} 至 ${LocalDate.ofEpochDay(to)}")
                            ImportAmounts(
                                preview.rows.filter { it.row.kind == EntryKind.INCOME }.sumOf { it.row.cents },
                                preview.rows.filter { it.row.kind == EntryKind.EXPENSE }.sumOf { it.row.cents },
                            )
                            Text("疑似重复 ${repeats.size} 笔。已导入过的行自动跳过，文件内的相同明细单独保留。")
                            if (repeats.any { it.repeat == Repeat.POSSIBLE || it.repeat == Repeat.IN_BATCH }) {
                                ImportCheck("跳过与历史或本次其他文件匹配的行", state.skipPossible, !state.busy) { checked ->
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
                    val source = item.fileName?.let { "$it · " }.orEmpty()
                    Text("${source}第 ${row.number} 行 · ${LocalDate.ofEpochDay(row.day)} · ${row.category ?: "未指定"} · ${Money.format(row.cents)} · ${item.repeat.label}")
                }
                if (preview.issues.isNotEmpty()) item { Text("无法解析的记录", style = MaterialTheme.typography.titleMedium) }
                items(preview.issues) { problem ->
                    val source = problem.fileName?.let { "$it · " }.orEmpty()
                    Text("${source}第 ${problem.number} 行 · ${problem.reason}", color = MaterialTheme.colorScheme.error)
                }
                item {
                    LedgerCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("本次将写入 ${eligible.size} 笔", style = MaterialTheme.typography.titleMedium)
                            ImportAmounts(
                                eligible.filter { it.row.kind == EntryKind.INCOME }.sumOf { it.row.cents },
                                eligible.filter { it.row.kind == EntryKind.EXPENSE }.sumOf { it.row.cents },
                            )
                            LedgerButton(
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
            "将 ${state.files.size} 个可读取文件中的有效记录一并写入目标账本，已有账单会保留。",
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
    Box(Modifier.fillMaxWidth()) {
        LedgerButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            prominent = false,
            enabled = enabled && options.isNotEmpty(),
            shape = RoundedCornerShape(18.dp),
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
