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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DailyDatabaseTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var repo: DailyRepository
    private val images get() = ImageStore(File(context.filesDir, "receipts").apply { mkdirs() })
    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
        .addMigrations(AppDatabase.MIGRATION_1_2).addCallback(AppDatabase.SEED_CALLBACK).allowMainThreadQueries().build()
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
        assertEquals(listOf(BookKind.DAILY, BookKind.RENOVATION), db.dailyDao().books().map { it.kind })
        val book = repo.addBook("旅行")
        repo.save(entry("one"), false); repo.save(entry("two", book = book), false)
        assertEquals(listOf("one"), repo.observeRange(1, 0, 30000).first().map { it.id })
        assertEquals(listOf("two"), repo.observeRange(book, 0, 30000).first().map { it.id })
        try { repo.save(entry("bad", book = 2), false); fail("装修账本不能写入生活记录") } catch (_: IllegalArgumentException) { }
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
        assertEquals(2, db.openHelper.readableDatabase.version)
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

    private fun makeV1(file: File) {
        file.parentFile?.mkdirs(); file.delete()
        val schema = JSONObject(File("schemas/com.zhuangxiu.ledger.data.db.AppDatabase/1.json").readText()).getJSONObject("database")
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
            old.version = 1
        }
    }
}
