package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.update.ApkDeltaApplier
import com.zhuangxiu.ledger.update.VerifiedDownload
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDeltaApplierTest {
    @get:Rule val directory = TemporaryFolder()

    private fun patch(records: DataOutputStream.() -> Unit): File =
        directory.newFile().also { file ->
            DataOutputStream(file.outputStream()).use {
                it.writeBytes("QZAPK001")
                it.records()
            }
        }

    @Test fun restoresExactBytesFromLiteralsAndUnalignedBaseRanges() {
        val base = directory.newFile().apply { writeText("before-MODEL-after") }
        val expected = directory.newFile().apply { writeText("metadata:MODEL!") }
        val update = patch {
            writeInt(9)
            writeBytes("metadata:")
            writeInt(-5)
            writeInt(7)
            writeInt(1)
            writeByte('!'.code)
        }
        val output = File(directory.root, "update.apk")
        ApkDeltaApplier.apply(base, update, output, expected.length(), VerifiedDownload.sha256(expected)) {}
        assertArrayEquals(expected.readBytes(), output.readBytes())
    }

    @Test fun rejectsOutOfBoundsTruncatedAndWrongDigestWithoutLeavingAnApk() {
        val base = directory.newFile().apply { writeText("1234") }
        val output = File(directory.root, "update.apk")
        val invalid = listOf(
            patch { writeInt(-4); writeInt(2) },
            patch { writeInt(4); writeBytes("123") },
            patch { writeInt(4); writeBytes("1234"); writeByte(1) },
            patch { writeInt(Int.MIN_VALUE) },
            patch { writeInt(-4); writeInt(0) },
        )
        for (update in invalid) {
            assertThrows(IOException::class.java) {
                ApkDeltaApplier.apply(base, update, output, 4, "0".repeat(64)) {}
            }
            assertFalse(output.exists())
            assertFalse(File(directory.root, "update.apk.part").exists())
        }
    }

    @Test fun stopsApplyingWhenCancelledAndRemovesPartialFile() {
        val base = directory.newFile().apply { writeText("1234") }
        val update = patch { writeInt(-4); writeInt(0) }
        val output = File(directory.root, "update.apk")
        assertThrows(CancellationException::class.java) {
            ApkDeltaApplier.apply(base, update, output, 4, VerifiedDownload.sha256(base)) {
                throw CancellationException()
            }
        }
        assertFalse(output.exists())
        assertFalse(File(directory.root, "update.apk.part").exists())
    }
}
