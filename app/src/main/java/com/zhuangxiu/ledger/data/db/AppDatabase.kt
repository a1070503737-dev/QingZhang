package com.zhuangxiu.ledger.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** 应用数据库。升级表结构时递增 version 并添加 Migration。 */
@Database(
    entities = [
        CategoryEntity::class,
        ExpenseEntity::class,
        PaymentEntity::class,
        AttachmentEntity::class,
        BudgetEntity::class,
        BookEntity::class,
        DailyEntryEntity::class,
        DailyCategoryEntity::class,
        ImportBatchEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun dailyDao(): DailyDao

    companion object {
        const val DB_NAME = "renovation.db"

        const val V1_IDENTITY = "fc076c42adb3b905e26f1393845b2934"
        const val V2_IDENTITY = "6e58b6bf156608820f1c0063fb2ba5e3"

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN trackPayments INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE books ADD COLUMN trackPhotos INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE books SET trackPayments = 0, trackPhotos = 0 WHERE kind = 'DAILY'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN bookId INTEGER NOT NULL DEFAULT 2")
                db.execSQL("CREATE INDEX index_expenses_bookId ON expenses(bookId)")
                db.execSQL("ALTER TABLE payments ADD COLUMN payMethod TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
                listOf("categoryName", "sourceCategory", "account", "sourceMethod", "importFingerprint", "importBatchId").forEach {
                    db.execSQL("ALTER TABLE daily_entries ADD COLUMN $it TEXT")
                }
                db.execSQL("CREATE INDEX index_daily_entries_bookId_importFingerprint ON daily_entries(bookId, importFingerprint)")
                db.execSQL("CREATE TABLE daily_categories (kind TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(kind, name))")
                db.execSQL("CREATE TABLE import_batches (id TEXT NOT NULL PRIMARY KEY, bookId INTEGER NOT NULL, fileHash TEXT NOT NULL, sheet TEXT NOT NULL, mapping TEXT NOT NULL, importedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX index_import_batches_bookId_fileHash ON import_batches(bookId, fileHash)")
            }
        }

        private fun seedBooks(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO books (id, name, kind) VALUES (1, '生活账本', 'DAILY'), (2, '装修账本', 'RENOVATION')")
        }

        /** 只新增表，不改动原有装修账单、付款、预算、分类和凭证。Room 在事务中执行迁移。 */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS daily_entries (id TEXT NOT NULL PRIMARY KEY, bookId INTEGER NOT NULL, amountCents INTEGER NOT NULL, kind TEXT NOT NULL, category TEXT NOT NULL, day INTEGER NOT NULL, minute INTEGER NOT NULL, payMethod TEXT NOT NULL, note TEXT NOT NULL, FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_entries_bookId_day_minute ON daily_entries(bookId, day, minute)")
                seedBooks(db)
            }
        }

        /** 默认装修阶段 */
        val DEFAULT_STAGES = listOf("设计", "拆改", "水电", "泥瓦", "木工", "油漆", "安装", "软装", "其他")

        /** 默认费用类型 */
        val DEFAULT_TYPES = listOf("主材", "辅材", "人工", "家电", "家具", "设计费", "杂费")

        /**
         * 数据库首次创建时写入默认分类。
         * 只在建库时触发一次；从备份恢复时数据库文件已存在，不会重复写入。
         */
        val SEED_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT INTO books (id, name, kind, trackPayments, trackPhotos) VALUES (1, '生活账本', 'DAILY', 0, 0)")
                db.execSQL("UPDATE sqlite_sequence SET seq = 2 WHERE name = 'books'")
                DEFAULT_STAGES.forEachIndexed { index, name ->
                    db.execSQL(
                        "INSERT INTO categories (kind, name, sortOrder) VALUES (?, ?, ?)",
                        arrayOf<Any>(CategoryKind.STAGE.name, name, index),
                    )
                }
                DEFAULT_TYPES.forEachIndexed { index, name ->
                    db.execSQL(
                        "INSERT INTO categories (kind, name, sortOrder) VALUES (?, ?, ?)",
                        arrayOf<Any>(CategoryKind.TYPE.name, name, index),
                    )
                }
            }
        }
    }
}
