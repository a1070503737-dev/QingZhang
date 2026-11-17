package com.zhuangxiu.ledger

import android.app.Application
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.importing.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SharkImportTest {
    private val header = "日期,收支类型,类别,账户,金额,备注"

    private fun read(bytes: ByteArray): ParsedImport {
        val table = SharkImport.read(bytes.inputStream()).sheets.first()
        return SharkImport.parse(table, table.headerIndex, table.autoMapping, false)
    }

    @Test fun actualHeaderVariantsAndChineseEncodings() {
        val sample = "$header\r\n2019年01月22日,支出,住房,银行卡,1200,房租\r\n"
        val examples = listOf(sample.toByteArray(), ("\uFEFF$sample").toByteArray(),
            sample.toByteArray(Charset.forName("GBK")), sample.toByteArray(Charset.forName("GB18030")))
        examples.forEach {
            val row = read(it).rows.single()
            assertEquals(LocalDate.of(2019, 1, 22).toEpochDay(), row.day)
            assertEquals(120000L, row.cents)
            assertEquals("住房", row.category)
            assertEquals("房租", row.note)
            assertEquals("银行卡", row.account)
            assertEquals(-1, row.minute)
        }
    }

    @Test fun quotingDelimitersCrossYearMissingAndRepeats() {
        val tabular = """
            导出明细
            备注;日期;付款方式;金额(元);收支;分类
            "公交; ""换乘""
            第二行";2025-12-31 23:53;现金;"1,000.25";支出;交通
            ;2026/1/1;;10.5;退款;
            ;2026/1/1;;10.5;退款;
        """.trimIndent()
        val parsed = read(tabular.toByteArray())
        assertTrue(parsed.issues.isEmpty())
        assertEquals("公交; \"换乘\"\n第二行", parsed.rows[0].note)
        assertEquals(1433, parsed.rows[0].minute)
        assertEquals("现金", parsed.rows[0].method)
        assertEquals("", parsed.rows[1].note)
        assertNull(parsed.rows[1].account)
        assertEquals(parsed.rows[1].fingerprint, parsed.rows[2].fingerprint)
        val monthly = importTotals(parsed.rows)
        assertEquals(100025L, monthly.getValue(YearMonth.of(2025, 12)).expense)
        assertEquals(2100L, monthly.getValue(YearMonth.of(2026, 1)).income)
    }

    @Test fun acceptsDocumentedSharkUtf16TabExportWithoutAccount() {
        val statement = "\uFEFF日期\t收支类型\t类别\t金额\t备注\r\n2026年1月31日\t支出\t交通\t0.90\t\r\n"
        listOf(Charsets.UTF_16LE, Charsets.UTF_16BE).forEach { charset ->
            val row = read(statement.toByteArray(charset)).rows.single()
            assertEquals(90L, row.cents)
            assertNull(row.account)
            assertEquals("交通", row.category)
        }
    }

    @Test fun signPolicyAndInvalidRows() {
        val sample = """$header
            |2025-01-01,支出,餐饮,,-12.20,
            |2025-01-01,收入,工资,,-12.20,
            |2025-01-01,退款,退款,,12.20,
            |2025-01-01,支出,餐饮,,1.005,
            |2025-02-31,支出,餐饮,,10,
            |2025-03-01,转账,,,10,
        """.trimMargin()
        val parsed = read(sample.toByteArray())
        assertEquals(listOf(EntryKind.EXPENSE, EntryKind.INCOME), parsed.rows.map { it.kind })
        assertEquals(listOf(1220L, 1220L), parsed.rows.map { it.cents })
        assertEquals(listOf(3, 5, 6, 7), parsed.issues.map { it.number })
        val signed = SharkImport.read("日期,金额\n2025-01-01,-8\n2025-01-02,9".byteInputStream()).sheets.single()
        assertThrows(IllegalArgumentException::class.java) { SharkImport.parse(signed, 0, signed.autoMapping, false) }
        assertEquals(listOf(EntryKind.EXPENSE, EntryKind.INCOME), SharkImport.parse(signed, 0, signed.autoMapping, true).rows.map { it.kind })
    }

    @Test fun largeStatementsRetainEveryOccurrence() {
        val csv = buildString {
            appendLine(header)
            repeat(25_000) { appendLine("2025-01-01,支出,餐饮,,0.01,") }
        }
        val parsed = read(csv.toByteArray())
        assertEquals(25_000, parsed.rows.size)
        assertEquals(25000L, importTotals(parsed.rows).values.single().expense)
    }

    @Test fun xlsxSharedAndInlineStringsSparseCellsAndExcelDates() {
        val sheet = """<worksheet><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="D1" t="inlineStr"><is><t>金额</t></is></c><c r="E1" t="inlineStr"><is><t>备注</t></is></c></row>
            <row r="2"><c r="A2"><v>46022.5</v></c><c r="B2" t="s"><v>2</v></c><c r="D2"><v>1200.25</v></c><c r="E2" t="inlineStr"><is><r><t>餐饮 &amp; </t></r><r><t>购物</t></r></is></c></row>
            <row r="3"><c r="A3" t="inlineStr"><is><t>2026年1月1日</t></is></c><c r="B3" t="s"><v>2</v></c><c r="D3"><f>1+1</f><v>2</v></c></row>
            </sheetData></worksheet>"""
        val parsed = read(xlsx(sheet))
        val row = parsed.rows.single()
        assertEquals(LocalDate.of(2025, 12, 31).toEpochDay(), row.day)
        assertEquals(720, row.minute)
        assertEquals(120025L, row.cents)
        assertEquals("餐饮 & 购物", row.note)
        assertEquals(3, parsed.issues.single().number)
    }

    @Test fun malformedFilesRejected() {
        assertThrows(IllegalArgumentException::class.java) { read("不是表格".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { read("$header\n2025-01-01,支出,食品,,1,\"断开的".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) {
            read(xlsx("""<!DOCTYPE worksheet [<!ENTITY x SYSTEM "file:///nonexistent">]><worksheet>&x;</worksheet>"""))
        }
    }

    private fun xlsx(sheet: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            mapOf(
                "xl/workbook.xml" to """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="明细" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to """<Relationships><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/></Relationships>""",
                "xl/sharedStrings.xml" to "<sst><si><t>日期</t></si><si><t>收支类型</t></si><si><t>支出</t></si></sst>",
                "xl/worksheets/sheet2.xml" to sheet,
            ).forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
