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
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        const val DB_NAME = "renovation.db"

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
