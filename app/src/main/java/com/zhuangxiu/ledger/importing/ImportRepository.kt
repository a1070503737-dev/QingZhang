package com.zhuangxiu.ledger.importing

import androidx.room.withTransaction
import com.zhuangxiu.ledger.data.db.*
import com.zhuangxiu.ledger.domain.DailyTotals
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

data class ImportCategory(val kind: EntryKind, val original: String?)
data class ImportSource(val hash: String, val sheet: String, val mapping: String)
data class ImportInput(val source: ImportSource, val parsed: ParsedImport, val fileName: String? = null)
enum class Repeat(val label: String) {
    NONE(""), EXACT("同文件中已导入"), POSSIBLE("与历史记录完全匹配"), IN_FILE("文件内有相同明细"),
    IN_BATCH("与本次所选的其他文件匹配");

    fun included(skipPossible: Boolean) = this != EXACT && (!skipPossible || (this != POSSIBLE && this != IN_BATCH))
}
data class PreviewRow(val row: ImportRow, val id: String, val repeat: Repeat, val source: ImportSource, val fileName: String?)
data class ImportPreview(
    val rows: List<PreviewRow>, val issues: List<ImportIssue>,
    val categories: Map<ImportCategory, String>, val existingCategories: Map<EntryKind, List<String>>,
)
data class ImportReceipt(val imported: Int, val skipped: Int, val failed: Int, val months: Map<YearMonth, DailyTotals>)

fun importTotals(rows: List<ImportRow>): Map<YearMonth, DailyTotals> =
    rows.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.day)) }.toSortedMap().mapValues { (_, month) ->
        DailyTotals(month.filter { it.kind == EntryKind.INCOME }.sumOf { it.cents },
            month.filter { it.kind == EntryKind.EXPENSE }.sumOf { it.cents })
    }

class ImportRepository @Inject constructor(private val db: AppDatabase, private val books: com.zhuangxiu.ledger.data.repository.DailyRepository) {
    private val dao get() = db.dailyDao()

    suspend fun preview(target: Long?, source: ImportSource, parsed: ParsedImport): ImportPreview =
        preview(target, listOf(ImportInput(source, parsed)))

    suspend fun preview(target: Long?, inputs: List<ImportInput>): ImportPreview = db.withTransaction {
        previewInTransaction(target, inputs)
    }

    private suspend fun previewInTransaction(target: Long?, inputs: List<ImportInput>): ImportPreview {
        require(inputs.isNotEmpty() && inputs.distinctBy { it.source }.size == inputs.size) { "请选择不同的账单文件" }
        val allRows = inputs.flatMap { it.parsed.rows }
        val first = allRows.minOfOrNull { it.day }
        val existing = if (target != null && first != null) {
            dao.existing(target, first, allRows.maxOf { it.day })
        } else emptyList()
        val fingerprints = existing.mapTo(hashSetOf()) {
            it.importFingerprint ?: ImportRow(0, it.day, it.minute, it.kind, it.amountCents,
                it.displayCategory, it.note, it.account,
                it.sourceMethod ?: it.payMethod.takeUnless { method -> method == PayMethod.UNSPECIFIED }?.label).fingerprint
        }
        val earlierFiles = hashSetOf<String>()
        val rows = inputs.flatMap { (source, parsed, name) ->
            val ids = if (target != null) dao.importedIds(target, source.hash, source.sheet, source.mapping).toHashSet() else emptySet()
            val counts = parsed.rows.groupingBy { it.fingerprint }.eachCount()
            parsed.rows.map { row ->
                val id = rowId(target, source, row)
                val repeat = when {
                    id in ids -> Repeat.EXACT
                    row.fingerprint in fingerprints -> Repeat.POSSIBLE
                    row.fingerprint in earlierFiles -> Repeat.IN_BATCH
                    counts.getValue(row.fingerprint) > 1 -> Repeat.IN_FILE
                    else -> Repeat.NONE
                }
                PreviewRow(row, id, repeat, source, name)
            }.also {
                earlierFiles.addAll(counts.keys)
            }
        }
        val custom = dao.dailyCategories()
        val available = EntryKind.entries.associateWith { kind ->
            (DailyCategory.entries.filter { it.kind == kind }.map { it.label } +
                custom.filter { it.kind == kind }.map { it.name }).distinct()
        }
        val categories = allRows.map { ImportCategory(it.kind, it.category) }.distinct().associateWith { category ->
            if (category.original == null) "未指定"
            else available.getValue(category.kind).firstOrNull { canonical(it) == canonical(category.original) } ?: category.original
        }
        val issues = inputs.flatMap { input -> input.parsed.issues.map { it.copy(fileName = input.fileName) } }
        return ImportPreview(rows, issues, categories, available)
    }

    suspend fun commit(
        target: Long?, newName: String, source: ImportSource, parsed: ParsedImport, preview: ImportPreview,
        mapping: Map<ImportCategory, String>, skipInvalid: Boolean, skipPossible: Boolean,
    ): ImportReceipt = commit(target, newName, listOf(ImportInput(source, parsed)), preview, mapping, skipInvalid, skipPossible)

    suspend fun commit(
        target: Long?, newName: String, inputs: List<ImportInput>, preview: ImportPreview,
        mapping: Map<ImportCategory, String>, skipInvalid: Boolean, skipPossible: Boolean,
    ): ImportReceipt = db.withTransaction {
        require(skipInvalid || inputs.all { it.parsed.issues.isEmpty() }) { "请先处理无法解析的记录，或确认跳过" }
        require(target == null || dao.book(target)?.archived == false) { "请选择未归档的账本" }
        val actual = previewInTransaction(target, inputs)
        require(actual.rows == preview.rows) { "目标账本已变更，请重新预览后再导入" }
        val selected = actual.rows.filter { it.repeat.included(skipPossible) }
        if (selected.isEmpty()) return@withTransaction ImportReceipt(0, actual.rows.size, actual.issues.size, emptyMap())
        val bookId = target ?: books.addBook(newName)
        val batches = selected.map { it.source }.distinct().associateWith { UUID.randomUUID().toString() }
        val entries = selected.map { (row, id, _, source) ->
            val category = ImportCategory(row.kind, row.category)
            val label = (mapping[category] ?: actual.categories.getValue(category)).trim()
            require(label.isNotEmpty() && label.length <= 100) { "分类名称需要 1–100 字" }
            val base = DailyCategory.entries.firstOrNull { it.kind == row.kind && it.label == label }
                ?: DailyCategory.entries.last { it.kind == row.kind }
            val method = PayMethod.entries.firstOrNull { canonical(it.label) == canonical(row.method.orEmpty()) }
                ?: PayMethod.UNSPECIFIED
            // New-book previews do not allocate an ID. Recompute stable row IDs using the committed book ID.
            val committedId = if (target == null) rowId(bookId, source, row) else id
            DailyEntryEntity(committedId, bookId, row.cents, row.kind, base, row.day, row.minute, method, row.note,
                categoryName = label.takeUnless { it == base.label }, sourceCategory = row.category,
                account = row.account, sourceMethod = row.method, importFingerprint = row.fingerprint, importBatchId = batches.getValue(source))
        }
        dao.insertCategories(entries.mapNotNull { entry ->
            entry.categoryName?.takeUnless { it == "未指定" }?.let { DailyCategoryEntity(entry.kind, it) }
        }.distinct())
        batches.forEach { (source, id) ->
            dao.insertBatch(ImportBatchEntity(id, bookId, source.hash, source.sheet, source.mapping, System.currentTimeMillis()))
        }
        dao.insertImported(entries)
        ImportReceipt(entries.size, actual.rows.size - entries.size, actual.issues.size, importTotals(selected.map { it.row }))
    }

    private fun rowId(target: Long?, source: ImportSource, row: ImportRow): String =
        UUID.nameUUIDFromBytes(
            "$target:${source.hash}:${source.sheet}:${source.mapping}:${row.number}:${row.fingerprint}".toByteArray(),
        ).toString()
}
