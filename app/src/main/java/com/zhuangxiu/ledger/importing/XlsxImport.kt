package com.zhuangxiu.ledger.importing

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipInputStream

/** 读取 OOXML 单元格的值。忽略图片、样式和宏，不执行公式或加载外部资源。 */
internal object XlsxImport {
    fun read(bytes: ByteArray): List<ImportSheet> {
        val files = mutableMapOf<String, ByteArray>()
        var remaining = MAX_FILE * 3
        ZipInputStream(bytes.inputStream()).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 256) { "XLSX 包含过多文件" }
                val content = zip.readBounded(remaining)
                remaining -= content.size
                if (entry.name.startsWith("xl/") && (entry.name.endsWith(".xml") || entry.name.endsWith(".rels"))) {
                    require(files.put(entry.name, content) == null) { "XLSX 包含重复文件" }
                }
            }
        }
        val book = requireNotNull(files["xl/workbook.xml"]) { "文件不是有效的 XLSX 工作簿" }
        val relations = requireNotNull(files["xl/_rels/workbook.xml.rels"]) { "XLSX 缺少工作表关联" }
        val paths = mutableMapOf<String, String>()
        xml(relations) { token ->
            if (token.eventType == XmlPullParser.START_TAG && token.name == "Relationship" &&
                token.attr("TargetMode") != "External" && token.attr("Type").endsWith("/worksheet")) {
                val target = token.attr("Target")
                val path = if (target.startsWith('/')) target.removePrefix("/") else "xl/$target"
                if (path.startsWith("xl/worksheets/") && ".." !in path.split('/')) paths[token.attr("Id")] = path
            }
        }
        val sheets = mutableListOf<Pair<String, String>>()
        var date1904 = false
        xml(book) { token ->
            if (token.eventType == XmlPullParser.START_TAG) when (token.name) {
                "workbookPr" -> date1904 = token.attr("date1904") in listOf("1", "true")
                "sheet" -> paths[token.attr("id")]?.let { sheets += token.attr("name") to it }
            }
        }
        require(sheets.size in 1..20) { "找不到工作表，或工作表数量超过 20" }
        val strings = files["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        var rowsLeft = MAX_ROWS + 100
        return sheets.map { (name, path) ->
            val rows = sheetRows(requireNotNull(files[path]) { "XLSX 缺少工作表 $name" }, strings, rowsLeft)
            rowsLeft -= rows.size
            ImportSheet(name, rows, excel = true, date1904 = date1904)
        }.filter { it.rows.any { row -> row.cells.size >= 2 } }.also {
            require(it.isNotEmpty()) { "XLSX 中没有可读表格" }
        }
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val value = StringBuilder()
        var inText = false
        var phonetic = false
        xml(bytes) { token ->
            when (token.eventType) {
                XmlPullParser.START_TAG -> when (token.name) {
                    "si" -> value.clear()
                    "rPh" -> phonetic = true
                    "t" -> inText = !phonetic
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> if (inText) append(value, token.text)
                XmlPullParser.END_TAG -> when (token.name) {
                    "t" -> inText = false
                    "rPh" -> phonetic = false
                    "si" -> {
                        require(result.size < MAX_ROWS * 5) { "XLSX 共享文本过多" }
                        result += value.toString()
                    }
                }
            }
        }
        return result
    }

    private fun sheetRows(bytes: ByteArray, strings: List<String>, limit: Int): List<SheetRow> {
        val rows = mutableListOf<SheetRow>()
        val row = mutableListOf<String>()
        val value = StringBuilder()
        var number = 0
        var column = 0
        var type = ""
        var formula = false
        var readingValue = false
        var phonetic = false
        xml(bytes) { token ->
            when (token.eventType) {
                XmlPullParser.START_TAG -> when (token.name) {
                    "row" -> { row.clear(); number = token.attr("r").toIntOrNull() ?: number + 1 }
                    "c" -> {
                        val reference = token.attr("r").takeWhile { it in 'A'..'Z' }
                        require(reference.length <= 3) { "XLSX 单元格位置无效" }
                        column = if (reference.isEmpty()) row.size else reference.fold(0) { acc, char -> acc * 26 + (char - 'A') + 1 } - 1
                        type = token.attr("t")
                        value.clear(); formula = false
                    }
                    "f" -> formula = true
                    "rPh" -> phonetic = true
                    "v", "t" -> readingValue = !phonetic
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> if (readingValue) append(value, token.text)
                XmlPullParser.END_TAG -> when (token.name) {
                    "v", "t" -> readingValue = false
                    "rPh" -> phonetic = false
                    "c" -> if (value.isNotEmpty() || formula) {
                        require(column in 0 until MAX_COLUMNS) { "第 $number 行的列数过多" }
                        while (row.size <= column) row += ""
                        row[column] = when {
                            formula || type == "e" -> "\u0000"
                            type == "s" -> strings.getOrNull(value.toString().toIntOrNull() ?: -1)
                                ?: throw IllegalArgumentException("XLSX 共享文本索引无效")
                            else -> value.toString()
                        }
                    }
                    "row" -> if (row.any(String::isNotBlank)) {
                        require(rows.size < limit) { "工作簿最多支持 $MAX_ROWS 笔账单" }
                        rows += SheetRow(number, row.toList())
                    }
                }
            }
        }
        return rows
    }

    private fun append(target: StringBuilder, text: String?) {
        requireNotNull(text) { "XLSX 含有无法识别的字符实体" }
        require(target.length + text.length <= MAX_CELL) { "XLSX 单元格过长" }
        target.append(text)
    }

    private fun XmlPullParser.attr(name: String): String =
        (0 until attributeCount).firstOrNull { getAttributeName(it) == name }?.let(::getAttributeValue).orEmpty()

    private fun xml(bytes: ByteArray, handle: (XmlPullParser) -> Unit) {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(bytes.inputStream(), null)
        while (parser.nextToken() != XmlPullParser.END_DOCUMENT) {
            require(parser.eventType != XmlPullParser.DOCDECL) { "XLSX XML 含有不支持的文档声明" }
            handle(parser)
        }
    }
}
