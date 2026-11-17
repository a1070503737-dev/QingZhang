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
enum class Repeat(val label: String) {
    NONE(""), EXACT("同文件中已导入"), POSSIBLE("与历史记录完全匹配"), IN_FILE("文件内有相同明细"),
}
data class PreviewRow(val row: ImportRow, val id: String, val repeat: Repeat)
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

    suspend fun preview(target: Long?, source: ImportSource, parsed: ParsedImport): ImportPreview = db.withTransaction {
        previewInTransaction(target, source, parsed)
    }

    private suspend fun previewInTransaction(target: Long?, source: ImportSource, parsed: ParsedImport): ImportPreview {
        val first = parsed.rows.minOfOrNull { it.day }
        val existing = if (target != null && first != null) {
            dao.existing(target, first, parsed.rows.maxOf { it.day })
        } else emptyList()
        val ids = if (target != null) dao.importedIds(target, source.hash, source.sheet, source.mapping).toHashSet() else emptySet()
        val fingerprints = existing.mapTo(hashSetOf()) {
            it.importFingerprint ?: ImportRow(0, it.day, it.minute, it.kind, it.amountCents,
                it.displayCategory, it.note, it.account,
                it.sourceMethod ?: it.payMethod.takeUnless { method -> method == PayMethod.UNSPECIFIED }?.label).fingerprint
        }
        val counts = parsed.rows.groupingBy { it.fingerprint }.eachCount()
        val rows = parsed.rows.map { row ->
            // Include the physical row number. Identical rows in one statement remain separate transactions.
            val key = source.hash + ":" + source.sheet + ":" + source.mapping + ":" + row.number + ":" + row.fingerprint
            val id = UUID.nameUUIDFromBytes("$target:$key".toByteArray()).toString()
            val repeat = when {
                id in ids -> Repeat.EXACT
                row.fingerprint in fingerprints -> Repeat.POSSIBLE
                counts.getValue(row.fingerprint) > 1 -> Repeat.IN_FILE
                else -> Repeat.NONE
            }
            PreviewRow(row, id, repeat)
        }
        val custom = dao.dailyCategories()
        val available = EntryKind.entries.associateWith { kind ->
            (DailyCategory.entries.filter { it.kind == kind }.map { it.label } +
                custom.filter { it.kind == kind }.map { it.name }).distinct()
        }
        val categories = parsed.rows.map { ImportCategory(it.kind, it.category) }.distinct().associateWith { category ->
            if (category.original == null) "未指定"
            else available.getValue(category.kind).firstOrNull { canonical(it) == canonical(category.original) } ?: category.original
        }
        return ImportPreview(rows, parsed.issues, categories, available)
    }

    suspend fun commit(
        target: Long?, newName: String, source: ImportSource, parsed: ParsedImport, preview: ImportPreview,
        mapping: Map<ImportCategory, String>, skipInvalid: Boolean, skipPossible: Boolean,
    ): ImportReceipt = db.withTransaction {
        require(skipInvalid || parsed.issues.isEmpty()) { "请先处理无法解析的记录，或确认跳过" }
        require(target == null || dao.book(target)?.archived == false) { "请选择未归档的账本" }
        val actual = previewInTransaction(target, source, parsed)
        require(actual.rows == preview.rows) { "目标账本已变更，请重新预览后再导入" }
        val selected = actual.rows.filter { it.repeat != Repeat.EXACT && (!skipPossible || it.repeat != Repeat.POSSIBLE) }
        if (selected.isEmpty()) return@withTransaction ImportReceipt(0, actual.rows.size, parsed.issues.size, emptyMap())
        val bookId = target ?: books.addBook(newName)
        val batch = UUID.randomUUID().toString()
        val entries = selected.map { (row, id) ->
            val category = ImportCategory(row.kind, row.category)
            val label = (mapping[category] ?: actual.categories.getValue(category)).trim()
            require(label.isNotEmpty() && label.length <= 100) { "分类名称需要 1–100 字" }
            val base = DailyCategory.entries.firstOrNull { it.kind == row.kind && it.label == label }
                ?: DailyCategory.entries.last { it.kind == row.kind }
            val method = PayMethod.entries.firstOrNull { canonical(it.label) == canonical(row.method.orEmpty()) }
                ?: PayMethod.UNSPECIFIED
            // New-book previews do not allocate an ID. Recompute stable row IDs using the committed book ID.
            val rowId = if (target == null) UUID.nameUUIDFromBytes(
                "$bookId:${source.hash}:${source.sheet}:${source.mapping}:${row.number}:${row.fingerprint}".toByteArray()).toString() else id
            DailyEntryEntity(rowId, bookId, row.cents, row.kind, base, row.day, row.minute, method, row.note,
                categoryName = label.takeUnless { it == base.label }, sourceCategory = row.category,
                account = row.account, sourceMethod = row.method, importFingerprint = row.fingerprint, importBatchId = batch)
        }
        dao.insertCategories(entries.mapNotNull { entry ->
            entry.categoryName?.takeUnless { it == "未指定" }?.let { DailyCategoryEntity(entry.kind, it) }
        }.distinct())
        dao.insertBatch(ImportBatchEntity(batch, bookId, source.hash, source.sheet, source.mapping, System.currentTimeMillis()))
        dao.insertImported(entries)
        ImportReceipt(entries.size, actual.rows.size - entries.size, parsed.issues.size, importTotals(selected.map { it.row }))
    }
}
