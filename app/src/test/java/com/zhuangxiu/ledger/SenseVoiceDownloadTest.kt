package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.update.VerifiedDownload
import com.zhuangxiu.ledger.voice.SenseVoiceArtifact
import com.zhuangxiu.ledger.voice.SenseVoiceDownload
import com.zhuangxiu.ledger.voice.SpeechModelDownloadException
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SenseVoiceDownloadTest {
    @get:Rule val folder = TemporaryFolder()
    private val payload = "offline test model".encodeToByteArray()

    @Test fun preservesIncompleteDownloadAndResumesItAfterRedirect() {
        val artifact = artifact()
        val cut = 7
        val short = Response(artifact.uri, 200, payload.copyOfRange(0, cut))
        assertThrows(SpeechModelDownloadException::class.java) {
            SenseVoiceDownload { short }.fetch(artifact, folder.root, {}, {})
        }
        assertFalse(artifact.target(folder.root).exists())
        assertEquals(cut.toLong(), artifact.partial(folder.root).length())

        val cdn = URI("https://cas-bridge.xethub.hf.co/model")
        val redirect = Response(artifact.uri, 302, headers = mapOf("Location" to cdn.toString()))
        val rest = Response(cdn, 206, payload.copyOfRange(cut, payload.size),
            mapOf("Content-Range" to "bytes $cut-${payload.lastIndex}/${payload.size}"))
        val visited = mutableListOf<URI>()
        SenseVoiceDownload {
            visited += it
            if (it == artifact.uri) redirect else rest
        }.fetch(artifact, folder.root, {}, {})
        assertEquals(listOf(artifact.uri, cdn), visited)
        assertEquals("bytes=$cut-", redirect.getRequestProperty("Range"))
        assertEquals("bytes=$cut-", rest.getRequestProperty("Range"))
        assertTrue(redirect.closed && rest.closed)
        assertArrayEquals(payload, artifact.target(folder.root).readBytes())
        assertFalse(artifact.partial(folder.root).exists())
    }

    @Test fun restartsSafelyIfServerIgnoresTheRange() {
        val artifact = artifact()
        artifact.partial(folder.root).writeBytes(byteArrayOf(0, 0, 0))
        val full = Response(artifact.uri, 200, payload)
        SenseVoiceDownload { full }.fetch(artifact, folder.root, {}, {})
        assertEquals("bytes=3-", full.getRequestProperty("Range"))
        assertArrayEquals(payload, artifact.target(folder.root).readBytes())
    }

    @Test fun rejectsDamagedContentBeforePromotingItToAnInstalledFile() {
        val artifact = artifact()
        val bad = Response(artifact.uri, 200, ByteArray(payload.size))
        val error = assertThrows(SpeechModelDownloadException::class.java) {
            SenseVoiceDownload { bad }.fetch(artifact, folder.root, {}, {})
        }
        assertTrue(error.message.orEmpty().contains("校验失败"))
        assertFalse(artifact.target(folder.root).exists())
        assertFalse(artifact.partial(folder.root).exists())
    }

    @Test fun rejectsUnexpectedRedirectAndLeavesPartialBytesUnchanged() {
        val artifact = artifact()
        artifact.partial(folder.root).writeBytes(payload.copyOfRange(0, 4))
        val redirect = Response(artifact.uri, 302, headers = mapOf("Location" to "https://example.invalid/file"))
        val opened = mutableListOf<URI>()
        assertThrows(SpeechModelDownloadException::class.java) {
            SenseVoiceDownload { opened += it; redirect }.fetch(artifact, folder.root, {}, {})
        }
        assertEquals(listOf(artifact.uri), opened)
        assertEquals(4L, artifact.partial(folder.root).length())
    }

    @Test fun cancelledTransferKeepsPartialBytesWithoutInstallingThem() {
        val artifact = artifact()
        var written = 0L
        val short = Response(artifact.uri, 200, payload.copyOfRange(0, 6))
        assertThrows(CancellationException::class.java) {
            SenseVoiceDownload { short }.fetch(artifact, folder.root,
                ensureActive = { if (written > 0) throw CancellationException() },
                progress = { written = it },
            )
        }
        assertEquals(6L, artifact.partial(folder.root).length())
        assertFalse(artifact.target(folder.root).exists())
        assertTrue(short.closed)
        assertNull(short.getRequestProperty("Range"))
    }

    private fun artifact(): SenseVoiceArtifact {
        val original = folder.newFile("original")
        original.writeBytes(payload)
        return SenseVoiceArtifact("model", URI("https://huggingface.co/model"), payload.size.toLong(),
            VerifiedDownload.sha256(original))
    }

    private class Response(
        uri: URI,
        private val status: Int,
        private val data: ByteArray = ByteArray(0),
        private val headers: Map<String, String> = emptyMap(),
    ) : HttpURLConnection(uri.toURL()) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?) = headers[name]
        override fun getContentLengthLong() = data.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(data)
    }
}
