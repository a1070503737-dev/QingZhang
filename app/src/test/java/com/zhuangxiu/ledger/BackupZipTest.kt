package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.data.repository.BackupZip
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 备份压缩包的打包与解包 */
class BackupZipTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** 打包后解包 数据库和图片内容完全一致 */
    @Test
    fun zipThenUnzip_restoresDatabaseAndImages() {
        val dbBytes = sqliteHeader + ByteArray(2048) { (it % 251).toByte() }
        val dbFile = temp.newFile("renovation.db").apply { writeBytes(dbBytes) }
        val receipts = temp.newFolder("receipts")
        File(receipts, "a.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(receipts, "b.png").writeBytes(ByteArray(5000) { 7 })

        val zipped = ByteArrayOutputStream()
        BackupZip.zip(dbFile, receipts, zipped)
        val out = temp.newFolder("out")
        BackupZip.unzip(ByteArrayInputStream(zipped.toByteArray()), out)

        assertTrue(BackupZip.isValidBackup(out))
        assertArrayEquals(dbBytes, File(out, BackupZip.DB_ENTRY).readBytes())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(out, "receipts/a.jpg").readBytes())
        assertEquals(5000L, File(out, "receipts/b.png").length())
    }

    /** 没有图片时也能正常备份 */
    @Test
    fun backupWorksWithoutImages() {
        val dbFile = temp.newFile("renovation.db").apply { writeBytes(sqliteHeader) }
        val zipped = ByteArrayOutputStream()
        BackupZip.zip(dbFile, File(temp.root, "not-exist"), zipped)
        val out = temp.newFolder("out")
        BackupZip.unzip(ByteArrayInputStream(zipped.toByteArray()), out)
        assertTrue(BackupZip.isValidBackup(out))
    }

    /** 不是备份的压缩包校验不通过 */
    @Test
    fun nonBackupZipFailsValidation() {
        val zipped = ByteArrayOutputStream()
        ZipOutputStream(zipped).use { zip ->
            zip.putNextEntry(ZipEntry("hello.txt"))
            zip.write("hi".toByteArray())
            zip.closeEntry()
            // 路径对了但内容不是 SQLite 数据库
            zip.putNextEntry(ZipEntry(BackupZip.DB_ENTRY))
            zip.write("this is not a database".toByteArray())
            zip.closeEntry()
        }
        val out = temp.newFolder("out")
        BackupZip.unzip(ByteArrayInputStream(zipped.toByteArray()), out)
        assertFalse(BackupZip.isValidBackup(out))
    }

    /** 拒绝包含越界路径的恶意压缩包 */
    @Test
    fun rejectsZipSlipEntries() {
        val zipped = ByteArrayOutputStream()
        ZipOutputStream(zipped).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.txt"))
            zip.write("x".toByteArray())
            zip.closeEntry()
        }
        val out = temp.newFolder("out")
        assertThrows(IOException::class.java) {
            BackupZip.unzip(ByteArrayInputStream(zipped.toByteArray()), out)
        }
        assertFalse(File(temp.root, "evil.txt").exists())
    }
}
