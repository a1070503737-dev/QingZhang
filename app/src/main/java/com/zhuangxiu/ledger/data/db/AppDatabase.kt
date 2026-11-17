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
    ],
    version = 2,
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
                seedBooks(db)
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
