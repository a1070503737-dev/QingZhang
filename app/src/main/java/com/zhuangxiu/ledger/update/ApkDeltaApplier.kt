package com.zhuangxiu.ledger.update

import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/** QZAPK001 records are big-endian signed lengths. Negative lengths copy from a base APK offset. */
internal object ApkDeltaApplier {
    private val magic = "QZAPK001".toByteArray(Charsets.US_ASCII)

    fun apply(
        base: File, patch: File, target: File, expectedBytes: Long, expectedSha256: String,
        ensureActive: () -> Unit,
    ) {
        if (expectedBytes !in 1..UpdateSource.MAX_APK_BYTES) invalidPatch()
        val partial = File(target.parentFile, "${target.name}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            DataInputStream(patch.inputStream().buffered()).use { input ->
                val header = ByteArray(magic.size)
                input.readFully(header)
                if (!header.contentEquals(magic)) invalidPatch()
                RandomAccessFile(base, "r").use { old ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var remaining = expectedBytes
                        while (remaining > 0) {
                            ensureActive()
                            val record = input.readInt()
                            val fromBase = record < 0
                            val length = kotlin.math.abs(record.toLong())
                            if (length !in 1..remaining) invalidPatch()
                            if (fromBase) {
                                val offset = input.readInt().toLong()
                                if (offset < 0 || offset + length > old.length()) invalidPatch()
                                old.seek(offset)
                            }
                            var pending = length
                            while (pending > 0) {
                                ensureActive()
                                val count = minOf(buffer.size.toLong(), pending).toInt()
                                if (fromBase) old.readFully(buffer, 0, count) else input.readFully(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                pending -= count
                            }
                            remaining -= length
                        }
                        if (input.read() != -1) invalidPatch()
                    }
                }
            }
            ensureActive()
            if (digest.digest().hex() != expectedSha256) invalidPatch()
            if (!partial.renameTo(target)) throw IOException("无法保存安装包，请检查剩余存储空间")
        } finally {
            partial.delete()
        }
    }

    private fun invalidPatch(): Nothing = throw IOException("增量安装包校验失败")
}
