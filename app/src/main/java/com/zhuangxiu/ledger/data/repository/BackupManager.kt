package com.zhuangxiu.ledger.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import com.zhuangxiu.ledger.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val imageStore: ImageStore,
) {
    private val mutex = Mutex()
    fun defaultFileName(): String = "轻账备份_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + ".zip"

    suspend fun createBackupFile(): File = withContext(Dispatchers.IO) { mutex.withLock {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        val output = File(dir, defaultFileName())
        val snapshot = snapshot()
        try { output.outputStream().use { BackupZip.zip(File(snapshot, BackupZip.DB_ENTRY), File(snapshot, BackupZip.RECEIPTS_DIR), it) } }
        finally { snapshot.deleteRecursively() }
        output
    } }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = createBackupFile()
        try {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("无法写入所选位置")
            out.use { output -> backup.inputStream().use { it.copyTo(output) } }
        } finally { backup.delete() }
    }
    suspend fun exportRecoveryTo(uri: Uri) = withContext(Dispatchers.IO) {
        val file = DatabaseSafety.recoveryZip(context)
        if (!file.isFile) throw IOException("尚未执行过恢复，没有恢复前安全副本")
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("无法写入所选位置")
        out.use { output -> file.inputStream().use { it.copyTo(output) } }
    }

    /** 数据库写事务阻止并发写入，复制主库与 WAL 后在副本上合并，避免 checkpoint + copy 之间丢失写入。 */
    internal suspend fun snapshot(): File = withContext(Dispatchers.IO) {
        val dir = File.createTempFile("ledger-snapshot-", "", context.cacheDir).apply { delete(); mkdirs() }
        try {
            val target = File(dir, BackupZip.DB_ENTRY).apply { parentFile?.mkdirs() }
            db.withTransaction {
                val source = context.getDatabasePath(AppDatabase.DB_NAME)
                source.copyTo(target, overwrite = true)
                File(source.path + "-wal").takeIf { it.exists() }?.copyTo(File(target.path + "-wal"), overwrite = true)
                val images = File(dir, BackupZip.RECEIPTS_DIR).apply { mkdirs() }
                imageStore.dir.listFiles()?.filter { it.isFile }?.forEach { it.copyTo(File(images, it.name), overwrite = true) }
            }
            SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { result ->
                    if (result.moveToFirst() && result.getInt(0) != 0) throw IOException("无法生成一致的数据库快照")
                }
            }
            dir
        } catch (e: Exception) { dir.deleteRecursively(); throw e }
    }

    /** v1 备份只恢复装修，保留当前生活账本；v2 备份恢复所有账本。替换前保留可导出的安全副本。 */
    suspend fun restoreFrom(uri: Uri) = withContext(Dispatchers.IO) { mutex.withLock {
        val temp = File.createTempFile("ledger-restore-", "", context.cacheDir).apply { delete(); mkdirs() }
        var before: File? = null
        var closed = false
        try {
            (context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取所选文件")).use { BackupZip.unzip(it, temp) }
            if (!BackupZip.isValidBackup(temp)) throw IOException("这不是有效的轻账或装修记账备份")
            val current = db.openHelper.readableDatabase
            val identity = current.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use {
                if (!it.moveToFirst()) throw IOException("无法验证当前账本结构")
                it.getString(0)
            }
            val file = File(temp, BackupZip.DB_ENTRY)
            val oldVersion = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            when (oldVersion) {
                1 -> BackupDatabaseValidator.validate(file, 1, AppDatabase.V1_IDENTITY)
                2 -> BackupDatabaseValidator.validate(file, 2, AppDatabase.V2_IDENTITY)
                else -> BackupDatabaseValidator.validate(file, current.version, identity)
            }
            // 先在临时数据库迁移并验证，失败绝不关闭或替换当前数据库。
            val prepared = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
            try {
                prepared.openHelper.writableDatabase
                if (oldVersion == 1) db.withTransaction {
                    val books = db.dailyDao().books()
                    val entries = db.dailyDao().all()
                    prepared.withTransaction {
                        books.forEach { prepared.dailyDao().copyBook(it); prepared.dailyDao().updateBook(it) }
                        entries.forEach { prepared.dailyDao().insert(it) }
                        prepared.dailyDao().insertCategories(db.dailyDao().dailyCategories())
                        db.dailyDao().allBatches().forEach { prepared.dailyDao().insertBatch(it) }
                    }
                }
                // 枚举/业务字段也应能够读取；拒绝损坏的关联或非正金额。
                val books = prepared.dailyDao().books().associateBy { it.id }
                prepared.dailyDao().all().forEach { entry ->
                    if (entry.bookId !in books ||
                        entry.amountCents !in 1..99_999_999_999L || entry.category.kind != entry.kind ||
                        entry.minute !in -1..1439 || entry.note.length > 1000 ||
                        java.time.LocalDate.ofEpochDay(entry.day).year !in 1..9999) throw IOException("备份中有无效的生活账目")
                }
            } finally { prepared.close() }
            BackupDatabaseValidator.validate(file, current.version, identity)
            before = snapshot()
            val recovery = DatabaseSafety.recoveryZip(context).apply { parentFile?.mkdirs() }
            val recoveryTemp = File(recovery.path + ".tmp")
            recoveryTemp.outputStream().use { BackupZip.zip(File(before, BackupZip.DB_ENTRY), File(before, BackupZip.RECEIPTS_DIR), it) }
            if (!recoveryTemp.renameTo(recovery)) throw IOException("无法保存恢复前安全副本")
            DatabaseSafety.armRestore(context, before)
            db.close(); closed = true
            DatabaseSafety.replace(context, temp)
            DatabaseSafety.commitRestore(context)
        } catch (e: Exception) {
            if (closed) {
                try { DatabaseSafety.recoverInterruptedRestore(context) } catch (_: Exception) { /* 下次启动在打开 Room 前重试回滚 */ }
                throw RestoreRestartRequired(e)
            }
            throw e
        } finally { before?.deleteRecursively(); temp.deleteRecursively() }
    } }
}
