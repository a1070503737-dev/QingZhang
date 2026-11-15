package com.zhuangxiu.ledger.data.repository

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份压缩包的打包 / 解包（不依赖 Android，便于单元测试）。
 *
 * 压缩包结构：
 * ```
 * database/renovation.db   数据库文件
 * receipts/xxx.jpg         凭证图片
 * ```
 */
object BackupZip {
    const val DB_ENTRY = "database/renovation.db"
    const val RECEIPTS_DIR = "receipts"

    /** SQLite 数据库文件固定的文件头 */
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** 把数据库文件和图片目录打包写入 [output] */
    fun zip(dbFile: File, receiptsDir: File, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DB_ENTRY))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            receiptsDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { image ->
                zip.putNextEntry(ZipEntry("$RECEIPTS_DIR/${image.name}"))
                image.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * 把压缩包解压到 [destDir]。
     * 会校验每个条目的路径，防止恶意压缩包把文件写到目标目录之外（Zip Slip）。
     */
    fun unzip(input: InputStream, destDir: File) {
        destDir.mkdirs()
        val root = destDir.canonicalFile
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(root, entry.name).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) {
                    throw IOException("备份文件包含非法路径：${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    /** 检查解压出来的内容是不是一份有效的备份（包含合法的 SQLite 数据库） */
    fun isValidBackup(extractedDir: File): Boolean {
        val db = File(extractedDir, DB_ENTRY)
        if (!db.isFile || db.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        db.inputStream().use { if (it.read(header) != header.size) return false }
        return header.contentEquals(SQLITE_HEADER)
    }
}
