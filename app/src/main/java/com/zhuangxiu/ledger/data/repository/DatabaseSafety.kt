package com.zhuangxiu.ledger.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.zhuangxiu.ledger.data.db.AppDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** 仅在建立 Room 单例前或关闭数据库后调用；单进程应用的恢复日志。 */
object DatabaseSafety {
    private fun root(context: Context) = File(context.filesDir, "database_safety")
    fun rollbackDir(context: Context) = File(root(context), "pending_restore")
    fun recoveryZip(context: Context) = File(root(context), "before-restore.zip")

    fun prepare(context: Context) {
        recoverInterruptedRestore(context)
        val db = context.getDatabasePath(AppDatabase.DB_NAME)
        if (!db.exists()) return
        val version = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        if (version == 1) {
            val dest = File(root(context), "before-upgrade-v1")
            if (!File(dest, "complete").exists()) {
                dest.deleteRecursively(); dest.mkdirs()
                // 在任何 Room / Worker 连接创建之前保存主库与 WAL；不修改源库。
                db.copyTo(File(dest, AppDatabase.DB_NAME), overwrite = true)
                File(db.path + "-wal").takeIf { it.exists() }?.copyTo(File(dest, AppDatabase.DB_NAME + "-wal"), overwrite = true)
                syncWrite(File(dest, "complete"), "1")
            }
        }
    }

    fun armRestore(context: Context, snapshot: File) {
        val dir = rollbackDir(context)
        dir.deleteRecursively()
        snapshot.copyRecursively(dir, overwrite = true)
        syncWrite(File(dir, "pending"), "rollback on next startup")
    }
    fun commitRestore(context: Context) {
        val marker = File(rollbackDir(context), "pending")
        if (marker.exists() && !marker.delete()) throw IOException("无法完成恢复日志，请重启应用")
    }
    fun recoverInterruptedRestore(context: Context) {
        val dir = rollbackDir(context)
        if (!File(dir, "pending").exists()) return
        replace(context, dir)
        commitRestore(context)
    }
    fun replace(context: Context, snapshot: File) {
        val dest = context.getDatabasePath(AppDatabase.DB_NAME)
        dest.parentFile?.mkdirs()
        val source = File(snapshot, BackupZip.DB_ENTRY)
        require(source.isFile) { "恢复副本缺少数据库" }
        val staged = File(dest.path + ".replacement")
        source.copyTo(staged, overwrite = true)
        FileOutputStream(staged, true).use { it.fd.sync() }
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val sidecar = File(dest.path + suffix)
            if (sidecar.exists() && !sidecar.delete()) throw IOException("无法清理数据库日志")
        }
        if (!staged.renameTo(dest)) throw IOException("无法替换数据库文件")
        val images = File(context.filesDir, "receipts")
        if (images.exists() && !images.deleteRecursively()) throw IOException("无法恢复凭证目录")
        images.mkdirs()
        File(snapshot, BackupZip.RECEIPTS_DIR).listFiles()?.filter { it.isFile }?.forEach { it.copyTo(File(images, it.name), overwrite = true) }
    }
    private fun syncWrite(file: File, text: String) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { it.write(text.toByteArray()); it.fd.sync() }
    }
}

class RestoreRestartRequired(cause: Throwable) : IOException("恢复未完成，原数据已保留。请关闭并重新打开应用后再试。", cause)
