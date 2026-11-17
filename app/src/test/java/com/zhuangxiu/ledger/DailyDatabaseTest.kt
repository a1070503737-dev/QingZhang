package com.zhuangxiu.ledger

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhuangxiu.ledger.data.db.*
import com.zhuangxiu.ledger.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import com.zhuangxiu.ledger.importing.*
import com.zhuangxiu.ledger.domain.DailyStatistics
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DailyDatabaseTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var repo: DailyRepository
    private val images get() = ImageStore(File(context.filesDir, "receipts").apply { mkdirs() })
    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .addCallback(AppDatabase.SEED_CALLBACK).allowMainThreadQueries().build()
    private fun entry(id: String = "test", amount: Long = 1250, book: Long = 1) = DailyEntryEntity(id, book, amount,
        EntryKind.EXPENSE, DailyCategory.FOOD, 20774, 750, PayMethod.WECHAT, "午饭")
    @Before fun setup() { context.deleteDatabase(AppDatabase.DB_NAME); File(context.filesDir, "database_safety").deleteRecursively(); db = open(); repo = DailyRepository(db) }
    @After fun close() { db.close() }

    @Test fun crudIsPersistentAndDuplicateSaveIsIdempotent() = runTest {
        repo.save(entry(), false); repo.save(entry(), false)
        assertEquals(1, db.dailyDao().all().size)
        repo.save(entry(amount = 2088), true)
        db.close(); db = open(); repo = DailyRepository(db)
        assertEquals(2088L, repo.get("test")!!.amountCents)
        assertEquals("午饭", repo.get("test")!!.note)
        repo.delete("test"); assertNull(repo.get("test"))
    }
    @Test fun booksAreIndependentAndRenovationRejectsDailyWrites() = runTest {
        assertEquals(listOf(BookKind.DAILY), db.dailyDao().books().map { it.kind })
        val book = repo.addBook("旅行")
        repo.save(entry("one"), false); repo.save(entry("two", book = book), false)
        assertEquals(listOf("one"), repo.observeRange(1, 0, 30000).first().map { it.id })
        assertEquals(listOf("two"), repo.observeRange(book, 0, 30000).first().map { it.id })
        val projectBook = repo.addBook("翻新", BookKind.RENOVATION)
        repo.save(entry("project", book = projectBook), false)
        assertEquals(listOf("project"), repo.observeRange(projectBook, 0, 30000).first().map { it.id })
        repo.renameBook(book, "出游"); assertEquals("出游", db.dailyDao().book(book)!!.name)
    }
    @Test fun rejectsInvalidAndStaleUpdates() = runTest {
        for (bad in listOf(entry(amount = 0), entry(amount = -1), entry().copy(category = DailyCategory.SALARY), entry().copy(minute = 1440))) {
            try { repo.save(bad, false); fail("应拒绝无效输入") } catch (_: IllegalArgumentException) { }
        }
        try { repo.save(entry(), true); fail("不能复活已删除记录") } catch (_: IllegalArgumentException) { }
        assertTrue(db.dailyDao().all().isEmpty())
    }
    @Test fun migratesActualV1SchemaWithoutChangingAnyRenovationRows() = runTest {
        db.close(); context.deleteDatabase(AppDatabase.DB_NAME)
        makeV1(context.getDatabasePath(AppDatabase.DB_NAME))
        DatabaseSafety.prepare(context)
        db = open()
        val expense = db.expenseDao().getById(21)!!
        assertEquals(123456L, expense.expense.totalCents)
        assertEquals(45678L, expense.paidCents)
        assertEquals(77778L, expense.remainingCents)
        assertEquals("legacy.jpg", expense.attachments.single().fileName)
        assertEquals(888888L, db.budgetDao().listAll().single().amountCents)
        assertEquals(listOf(1L, 2L), db.dailyDao().books().map { it.id })
        assertTrue(db.dailyDao().all().isEmpty())
        assertEquals(3, db.openHelper.readableDatabase.version)
        assertEquals(2L, expense.expense.bookId)
        assertTrue(File(context.filesDir, "database_safety/before-upgrade-v1/complete").exists())
    }
    @Test fun v2BackupRoundTripIncludesAllBooksAndImages() = runTest {
        repo.save(entry(), false)
        val custom = repo.addBook("旅行"); repo.save(entry("trip", book = custom), false)
        images.file("receipt.jpg").writeText("receipt")
        val manager = BackupManager(context, db, images)
        val backup = manager.createBackupFile()
        repo.delete("test"); repo.delete("trip"); images.file("receipt.jpg").delete()
        manager.restoreFrom(Uri.fromFile(backup))
        db = open()
        assertEquals(2, db.dailyDao().all().size)
        assertEquals("旅行", db.dailyDao().book(custom)!!.name)
        assertEquals("receipt", images.file("receipt.jpg").readText())
        assertTrue(DatabaseSafety.recoveryZip(context).isFile)
    }
    @Test fun oldBackupRestoresRenovationButPreservesCurrentDailyEntries() = runTest {
        repo.save(entry(), false)
        val custom = repo.addBook("旅行"); repo.save(entry("trip", book = custom), false)
        val legacy = File(context.cacheDir, "legacy.db"); makeV1(legacy)
        val zip = File(context.cacheDir, "legacy.zip")
        zip.outputStream().use { BackupZip.zip(legacy, File(context.cacheDir, "no-images"), it) }
        BackupManager(context, db, images).restoreFrom(Uri.fromFile(zip))
        db = open()
        assertEquals(2, db.dailyDao().all().size)
        assertEquals(45678L, db.expenseDao().getById(21)!!.paidCents)
        assertEquals("旅行", db.dailyDao().book(custom)!!.name)
    }
    @Test fun invalidBackupDoesNotCloseOrOverwriteCurrentDatabase() = runTest {
        repo.save(entry(), false)
        val corrupt = File(context.cacheDir, "broken.zip").apply { writeText("invalid") }
        try { BackupManager(context, db, images).restoreFrom(Uri.fromFile(corrupt)); fail("应拒绝损坏文件") } catch (_: Exception) { }
        assertEquals(1250L, repo.get("test")!!.amountCents)
        repo.save(entry("still-open"), false)
        assertEquals(2, db.dailyDao().all().size)
    }
    @Test fun interruptedRestoreRollsBackBeforeRoomOpens() = runTest {
        repo.save(entry(), false); images.file("safe.jpg").writeText("safe")
        val snapshot = BackupManager(context, db, images).snapshot()
        DatabaseSafety.armRestore(context, snapshot)
        repo.save(entry("later"), false)
        db.close(); context.getDatabasePath(AppDatabase.DB_NAME).writeText("interrupted")
        images.file("safe.jpg").delete()
        DatabaseSafety.prepare(context)
        db = open()
        assertEquals(listOf("test"), db.dailyDao().all().map { it.id })
        assertEquals("safe", images.file("safe.jpg").readText())
        assertFalse(File(DatabaseSafety.rollbackDir(context), "pending").exists())
    }

    @Test fun migrateVersion2RetainsBothKindsAndCountsOnlyPayments() = runTest {
        db.close()
        context.deleteDatabase(AppDatabase.DB_NAME)
        makeV1(context.getDatabasePath(AppDatabase.DB_NAME), version = 2)
        db = open(); repo = DailyRepository(db)
        assertEquals("老生活记录", repo.get("old-daily")?.note)
        val project = db.expenseDao().getById(21)!!
        assertEquals(PaymentStatus.DEPOSIT, project.expense.status)
        assertEquals(PayMethod.UNSPECIFIED, project.payments.single().payMethod)
        assertEquals("legacy.jpg", project.attachments.single().fileName)
        assertEquals(2L, project.expense.bookId)
        assertFalse(repo.book(1)!!.trackPayments)
        assertTrue(repo.book(2)!!.trackPayments)
        val month = java.time.LocalDate.ofEpochDay(20000).let { YearMonth.from(it) }
        val from = month.atDay(1).toEpochDay()
        val until = month.plusMonths(1).atDay(1).toEpochDay()
        assertEquals(45678L, DailyStatistics.calculate(repo.observeActual(2, from, until).first(), 2, month).totals.expense)
        assertEquals(45778L, DailyStatistics.calculate(repo.observeActual(0, from, until).first(), 0, month).totals.expense)
        ExpenseRepository(db, images).addPayment(21, 200, 20000, payMethod = PayMethod.BANK)
        val actual = repo.observeActual(2, from, until).first()
        assertTrue(actual.any { it.payMethod == PayMethod.BANK && it.amountCents == 200L })
        assertEquals(45878L, DailyStatistics.calculate(actual, 2, month).totals.expense)
    }

    @Test fun importMergesExactCentsAndPreservesDistinctOccurrencesAcrossBackup() = runTest {
        val csv = """
            日期,收支类型,类别,账户,金额,备注
            2025-12-31,支出,电动车,交通卡,12.35,
            2025-12-31,支出,电动车,交通卡,12.35,
            2026-01-01,收入,退款,,20.01,退款一笔
            2026-01-02,支出,其他,,错误,
        """.trimIndent()
        val doc = SharkImport.read(csv.byteInputStream())
        val sheet = doc.sheets.single()
        val source = ImportSource(doc.hash, "CSV", "auto")
        val parsed = SharkImport.parse(sheet, 0, sheet.autoMapping, false)
        repo.save(entry(amount = 777), false)
        val importing = ImportRepository(db, repo)
        val preview = importing.preview(1, source, parsed)
        assertEquals(listOf(Repeat.IN_FILE, Repeat.IN_FILE, Repeat.NONE), preview.rows.map { it.repeat })
        assertEquals("电动车", preview.categories.getValue(ImportCategory(EntryKind.EXPENSE, "电动车")))
        try {
            importing.commit(1, "", source, parsed, preview, emptyMap(), false, false)
            fail("Unconfirmed invalid rows should stop the batch")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, db.dailyDao().all().size)
        val receipt = importing.commit(1, "", source, parsed, preview, preview.categories, true, false)
        assertEquals(3, receipt.imported)
        assertEquals(1, receipt.failed)
        assertEquals(2470L, receipt.months.getValue(YearMonth.of(2025, 12)).expense)
        assertEquals(2001L, receipt.months.getValue(YearMonth.of(2026, 1)).income)
        receipt.months.forEach { (month, expected) ->
            val selected = repo.observeActual(1, month.atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay()).first()
            assertEquals(expected, DailyStatistics.calculate(selected, 1, month).totals)
        }
        val again = importing.preview(1, source, parsed)
        assertTrue(again.rows.all { it.repeat == Repeat.EXACT })
        val noOp = importing.commit(1, "", source, parsed, again, emptyMap(), true, false)
        assertEquals(3, noOp.skipped)
        assertEquals(4, db.dailyDao().all().size)
        val changedFile = source.copy(hash = "different-export")
        val possible = importing.preview(1, changedFile, parsed)
        assertTrue(possible.rows.all { it.repeat == Repeat.POSSIBLE })
        val manager = BackupManager(context, db, images)
        val backup = manager.createBackupFile()
        manager.restoreFrom(Uri.fromFile(backup))
        db = open(); repo = DailyRepository(db)
        assertEquals("交通卡", db.dailyDao().all().first { it.sourceCategory == "电动车" }.account)
        assertTrue(repo.categories().any { it.name == "电动车" })
        assertTrue(ImportRepository(db, repo).preview(1, source, parsed).rows.all { it.repeat == Repeat.EXACT })
        val edited = db.dailyDao().all().first { it.sourceCategory == "电动车" }
        repo.save(edited.copy(day = edited.day + 50), editing = true)
        assertTrue(ImportRepository(db, repo).preview(1, source, parsed).rows.all { it.repeat == Repeat.EXACT })
    }

    @Test fun importNewBookAndCategoryValidationAreAtomic() = runTest {
        val row = ImportRow(2, 20_000, -1, EntryKind.EXPENSE, 100, "行李", "", null, null)
        val source = ImportSource("file", "CSV", "auto")
        val parsed = ParsedImport(listOf(row), emptyList())
        val importing = ImportRepository(db, repo)
        val preview = importing.preview(null, source, parsed)
        try {
            importing.commit(null, "出行账本", source, parsed, preview, mapOf(ImportCategory(row.kind, row.category) to " "), false, false)
            fail("Invalid category must roll back creation of the target book")
        } catch (_: IllegalArgumentException) { }
        assertEquals(listOf("生活账本"), db.dailyDao().books().map { it.name })
        assertTrue(db.dailyDao().allBatches().isEmpty())
        val completed = importing.commit(null, "出行账本", source, parsed, preview, emptyMap(), false, false)
        assertEquals(1, completed.imported)
        val created = db.dailyDao().books().last()
        assertEquals(Repeat.EXACT, importing.preview(created.id, source, parsed).rows.single().repeat)
        repo.editBook(created.copy(archived = true))
        try {
            importing.commit(created.id, "", source, parsed, preview, emptyMap(), false, false)
            fail("Archived books should reject imports")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, db.dailyDao().all().size)
    }

    private fun makeV1(file: File, version: Int = 1) {
        file.parentFile?.mkdirs(); file.delete()
        val schema = JSONObject(File("schemas/com.zhuangxiu.ledger.data.db.AppDatabase/$version.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) old.execSQL(queries.getString(i))
            old.execSQL("INSERT INTO categories VALUES (1,'STAGE','水电',0),(2,'TYPE','人工',0)")
            old.execSQL("INSERT INTO expenses VALUES (21,123456,20000,1,2,'师傅','旧账', 'DEPOSIT',20030,1000)")
            old.execSQL("INSERT INTO payments VALUES (31,21,45678,20000,'定金')")
            old.execSQL("INSERT INTO attachments VALUES (41,21,'legacy.jpg',1000)")
            old.execSQL("INSERT INTO budgets VALUES (0,888888)")
            if (version == 2) {
                old.execSQL("INSERT INTO books VALUES (1,'生活账本','DAILY'),(2,'旧装修','RENOVATION')")
                old.execSQL("INSERT INTO daily_entries VALUES ('old-daily',1,100,'EXPENSE','FOOD',20000,0,'CASH','老生活记录')")
            }
            old.version = version
        }
    }
}
