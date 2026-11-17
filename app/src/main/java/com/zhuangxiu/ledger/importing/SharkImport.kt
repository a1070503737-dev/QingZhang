package com.zhuangxiu.ledger.importing

import com.zhuangxiu.ledger.data.db.EntryKind
import java.io.InputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate

internal const val MAX_FILE = 16 * 1024 * 1024
internal const val MAX_ROWS = 50_000
internal const val MAX_COLUMNS = 128
internal const val MAX_CELL = 8_192

private fun ByteArray.sha256(): String = buildString(64) {
    val hex = "0123456789abcdef"
    MessageDigest.getInstance("SHA-256").digest(this@sha256).forEach {
        val byte = it.toInt() and 255
        append(hex[byte shr 4])
        append(hex[byte and 15])
    }
}

internal fun InputStream.readBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "文件或解压后的内容过大" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

enum class ImportField(val label: String, vararg val aliases: String) {
    DATE("日期", "交易日期", "记账日期", "交易时间", "记账时间", "日期时间", "date", "datetime"),
    TIME("时间", "时刻", "time"),
    DIRECTION("收支方向", "收支类型", "收支", "类型", "交易类型", "方向", "income/expense", "type"),
    AMOUNT("金额", "金额(元)", "交易金额", "交易金额(元)", "人民币", "amount"),
    CATEGORY("分类", "类别", "交易分类", "账目类别", "category"),
    NOTE("备注", "说明", "交易备注", "描述", "note", "memo"),
    ACCOUNT("账户", "账号", "资金账户", "账户名称", "account"),
    METHOD("支付方式", "付款方式", "支付渠道", "payment method"),
}

internal fun canonical(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
private fun headerKey(value: String) = canonical(value).filterNot(Char::isWhitespace).lowercase()

data class SheetRow(val number: Int, val cells: List<String>)
data class ImportSheet(val name: String, val rows: List<SheetRow>, val excel: Boolean = false, val date1904: Boolean = false) {
    val width: Int = rows.maxOfOrNull { it.cells.size } ?: 0
    val headerIndex: Int = rows.take(100).indexOfFirst {
        val mapping = guessColumns(it.cells)
        ImportField.DATE in mapping && ImportField.AMOUNT in mapping
    }.takeIf { it >= 0 } ?: 0
    val columns: List<String> get() = List(width) { rows.getOrNull(headerIndex)?.cells?.getOrNull(it).orEmpty() }
    val autoMapping: Map<ImportField, Int> get() = guessColumns(columns)
}
data class ImportDocument(val hash: String, val sheets: List<ImportSheet>, val format: String)

data class ImportRow(
    val number: Int, val day: Long, val minute: Int, val kind: EntryKind, val cents: Long,
    val category: String?, val note: String, val account: String?, val method: String?,
) {
    // Length prefixes keep multi-line notes and separator characters unambiguous.
    val fingerprint: String by lazy(LazyThreadSafetyMode.NONE) { listOf(day.toString(), minute.toString(), kind.name, cents.toString(),
        category.orEmpty(), note, account.orEmpty(), method.orEmpty()).joinToString("") {
        val value = canonical(it).replace("\r\n", "\n")
        "${value.length}:$value"
    }.toByteArray().sha256() }
}
data class ImportIssue(val number: Int, val reason: String)
data class ParsedImport(val rows: List<ImportRow>, val issues: List<ImportIssue>)

fun guessColumns(headers: List<String>): Map<ImportField, Int> = buildMap {
    ImportField.entries.forEach { field ->
        val names = (listOf(field.label) + field.aliases).map(::headerKey)
        val matches = headers.indices.filter { headerKey(headers[it]) in names }
        if (matches.size == 1) put(field, matches.single())
    }
}

object SharkImport {
    fun read(input: InputStream): ImportDocument {
        val bytes = input.readBounded(MAX_FILE)
        require(bytes.isNotEmpty()) { "文件为空" }
        val hash = bytes.sha256()
        if (bytes.take(4) == listOf(80.toByte(), 75.toByte(), 3.toByte(), 4.toByte())) {
            return ImportDocument(hash, XlsxImport.read(bytes), "XLSX")
        }
        val (text, charset) = decode(bytes)
        require('\u0000' !in text) { "文件不是 CSV 或 XLSX" }
        val delimiter = listOf(',', '\t', ';', '，').maxBy { candidate ->
            runCatching {
                val sample = csvRows(text, candidate, 30, preview = true)
                sample.maxOfOrNull { guessColumns(it.cells).size * 100 + it.cells.size.coerceAtMost(12) } ?: 0
            }.getOrDefault(-1)
        }
        val rows = csvRows(text, delimiter, MAX_ROWS + 100).filter { row -> row.cells.any(String::isNotBlank) }
        require(rows.isNotEmpty() && rows.any { it.cells.size >= 2 }) { "没有找到表格列，请检查文件格式或分隔符" }
        return ImportDocument(hash, listOf(ImportSheet("CSV", rows)), "CSV · $charset")
    }

    private fun decode(bytes: ByteArray): Pair<String, String> {
        val bom = bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()
        fun decodeWith(charset: Charset, offset: Int = 0) = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        val utf16 = if (bytes.size >= 2) when {
            bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
            bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
            else -> null
        } else null
        if (utf16 != null) {
            try { return decodeWith(utf16, 2) to utf16.name() }
            catch (_: CharacterCodingException) { throw IllegalArgumentException("UTF-16 文件含有损坏的字符") }
        }
        return try {
            decodeWith(Charsets.UTF_8, if (bom) 3 else 0) to "UTF-8"
        } catch (_: CharacterCodingException) {
            require(!bom) { "UTF-8 文件含有损坏的字符" }
            try { decodeWith(Charset.forName("GB18030")) to "GB18030" }
            catch (_: CharacterCodingException) { throw IllegalArgumentException("无法识别文件编码，请另存为 UTF-8 CSV") }
        }
    }

    private fun csvRows(text: String, separator: Char, limit: Int, preview: Boolean = false): List<SheetRow> {
        val rows = mutableListOf<SheetRow>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var line = 1
        var rowLine = 1
        var index = 0
        fun finishField() {
            require(fields.size < MAX_COLUMNS) { "第 $rowLine 行的列数过多" }
            fields.add(field.toString()); field.clear(); closedQuote = false
        }
        fun finishRow() {
            require(rows.size < limit) { "单次最多导入 $MAX_ROWS 笔账单" }
            finishField()
            rows.add(SheetRow(rowLine, fields.toList()))
            fields.clear()
        }
        while (index < text.length) {
            val char = text[index++]
            if (quoted) {
                if (char == '"' && text.getOrNull(index) == '"') { field.append('"'); index++ }
                else if (char == '"') { quoted = false; closedQuote = true }
                else { field.append(char); if (char == '\n') line++ }
            } else when (char) {
                separator -> finishField()
                '\r', '\n' -> {
                    if (char == '\r' && text.getOrNull(index) == '\n') index++
                    finishRow(); rowLine = ++line
                    if (preview && rows.size >= limit) return rows
                }
                '"' -> {
                    require(field.isEmpty() && !closedQuote) { "第 $rowLine 行引号格式错误" }
                    quoted = true
                }
                else -> {
                    require(!closedQuote || char.isWhitespace()) { "第 $rowLine 行引号后有多余字符" }
                    if (!closedQuote) field.append(char)
                }
            }
            require(field.length <= MAX_CELL) { "第 $rowLine 行单元格过长" }
        }
        require(!quoted) { "第 $rowLine 行的引号没有闭合" }
        if (fields.isNotEmpty() || field.isNotEmpty() || closedQuote) finishRow()
        return rows
    }

    fun parse(sheet: ImportSheet, header: Int, mapping: Map<ImportField, Int>, signedAmount: Boolean): ParsedImport {
        require(ImportField.DATE in mapping && ImportField.AMOUNT in mapping) { "请选择日期列和金额列" }
        require(signedAmount || ImportField.DIRECTION in mapping) { "请选择收支列，或确认按金额正负识别收支" }
        require(mapping.values.toSet().size == mapping.size) { "每个字段需要映射到不同的列" }
        val result = mutableListOf<ImportRow>()
        val issues = mutableListOf<ImportIssue>()
        val rows = sheet.rows.drop(header + 1)
        require(rows.size <= MAX_ROWS) { "单次最多导入 $MAX_ROWS 笔账单" }
        rows.forEach { row ->
            fun value(field: ImportField): String = mapping[field]?.let { row.cells.getOrNull(it) }.orEmpty().trim()
            try {
                require(mapping.keys.none { '\u0000' in value(it) }) { "映射列包含公式或错误单元格，请先转成数值或文本" }
                val (day, embeddedMinute) = parseDate(value(ImportField.DATE), sheet.excel, sheet.date1904)
                val explicitTime = value(ImportField.TIME)
                val minute = if (explicitTime.isBlank()) embeddedMinute else parseTime(explicitTime, sheet.excel)
                val amount = parseAmount(value(ImportField.AMOUNT))
                val direction = canonical(value(ImportField.DIRECTION)).lowercase()
                val kind = when {
                    direction in listOf("收入", "退款", "income", "refund", "入账") -> {
                        require(amount > 0) { "收入或退款金额为负，请在原文件核对方向" }
                        EntryKind.INCOME
                    }
                    direction in listOf("支出", "expense", "消费", "出账") -> EntryKind.EXPENSE
                    ImportField.DIRECTION !in mapping && signedAmount -> if (amount < 0) EntryKind.EXPENSE else EntryKind.INCOME
                    else -> throw IllegalArgumentException("无法识别收支方向，转账需另行处理")
                }
                val note = value(ImportField.NOTE)
                require(note.length <= 1000) { "备注超过 1000 字" }
                fun optional(field: ImportField) = value(field).takeIf(String::isNotBlank)?.also {
                    require(it.length <= 100) { "${field.label}超过 100 字" }
                }
                result += ImportRow(row.number, day, minute, kind, kotlin.math.abs(amount),
                    optional(ImportField.CATEGORY) ?: if (direction in listOf("退款", "refund")) "退款" else null,
                    note, optional(ImportField.ACCOUNT), optional(ImportField.METHOD))
            } catch (e: IllegalArgumentException) {
                issues += ImportIssue(row.number, e.message ?: "记录格式错误")
            } catch (_: java.time.DateTimeException) {
                issues += ImportIssue(row.number, "日期或时间超出有效范围")
            }
        }
        return ParsedImport(result, issues)
    }

    private val datePattern = Regex("""(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?(?:[ T]+(.+))?""")
    private val timePattern = Regex("""(\d{1,2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?""")

    private fun parseDate(raw: String, excel: Boolean, date1904: Boolean): Pair<Long, Int> {
        val value = canonical(raw)
        datePattern.matchEntire(value)?.let {
            val (y, m, d, time) = it.destructured
            require(y.toInt() in 1..9999) { "日期年份无效" }
            return LocalDate.of(y.toInt(), m.toInt(), d.toInt()).toEpochDay() to
                if (time.isEmpty()) -1 else parseTime(time, false)
        }
        if (excel) {
            val decimal = value.toBigDecimalOrNull()
            if (decimal != null && decimal >= BigDecimal.ZERO && decimal < BigDecimal(2_958_000)) {
                val days = decimal.toLong()
                require(date1904 || days != 60L) { "Excel 日期为不存在的 1900-02-29" }
                val base = if (date1904) LocalDate.of(1904, 1, 1)
                    else LocalDate.of(1899, 12, if (days < 60) 31 else 30)
                val date = base.plusDays(days)
                require(date.year in 1..9999) { "日期年份无效" }
                val fraction = decimal - BigDecimal.valueOf(days)
                return date.toEpochDay() to if (fraction.signum() == 0) -1 else serialMinute(fraction)
            }
        }
        throw IllegalArgumentException("日期无法识别，请使用带年份的完整日期")
    }

    private fun serialMinute(value: BigDecimal): Int =
        (value * BigDecimal(86_400)).setScale(0, java.math.RoundingMode.HALF_UP).toInt().coerceAtMost(86_399) / 60

    private fun parseTime(raw: String, excel: Boolean): Int {
        val value = canonical(raw)
        timePattern.matchEntire(value)?.let {
            val (h, m, s) = it.destructured
            require(h.toInt() <= 23 && m.toInt() <= 59 && (s.toIntOrNull() ?: 0) <= 59) { "时间超出有效范围" }
            return h.toInt() * 60 + m.toInt()
        }
        if (excel) value.toBigDecimalOrNull()?.let { if (it >= BigDecimal.ZERO && it < BigDecimal.ONE) return serialMinute(it) }
        throw IllegalArgumentException("时间无法识别，请使用 24 小时制")
    }

    private val amountPattern = Regex("""[+\-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?""")
    private fun parseAmount(raw: String): Long {
        var value = canonical(raw).replace('−', '-').replace(Regex("""(?i)CNY|RMB|人民币|[¥元]"""), "").trim()
        if (value.startsWith("(") && value.endsWith(")")) value = "-" + value.substring(1, value.length - 1)
        require(value.matches(amountPattern)) { "金额无效或超过两位小数" }
        val decimal = value.replace(",", "").toBigDecimal()
        require(decimal.signum() != 0 && decimal.abs() <= BigDecimal("999999999.99")) { "金额为零或超出范围" }
        return decimal.movePointRight(2).longValueExact()
    }
}
