package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.drive.DriveSnapshot
import com.zhuangxiu.ledger.drive.GoogleDriveFiles
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class GoogleDriveBackupTest {
    @get:Rule val files = TemporaryFolder()

    private val payload = "abc".toByteArray()
    private val metadata = """{"id":"file-a","name":"ledger.zip","createdTime":"2026-11-21T12:00:00Z","size":"3","md5Checksum":"900150983cd24fb0d6963f7d28e17f72"}"""

    @Test fun uploadStartsVerifiedSessionAndTransfersTheZip() = runTest {
        val zip = files.newFile("ledger.zip").apply { writeBytes(payload) }
        val calls = mutableListOf<Reply>()
        val drive = GoogleDriveFiles { url ->
            val response = if (calls.isEmpty()) Reply(url, "", SESSION) else Reply(url, metadata)
            calls.add(response)
            response
        }
        val snapshot = drive.upload("fake-test-token", zip)
        assertEquals("file-a", snapshot.id)
        assertEquals(2, calls.size)
        val start = calls[0]
        assertEquals("POST", start.requestMethod)
        assertEquals("3", start.getRequestProperty("X-Upload-Content-Length"))
        val sent = JSONObject(start.sent.toString(Charsets.UTF_8.name()))
        assertEquals("ledger.zip", sent.getString("name"))
        assertEquals("1", sent.getJSONObject("appProperties").getString("qingzhangBackup"))
        val upload = calls[1]
        assertEquals("PUT", upload.requestMethod)
        assertEquals(SESSION, upload.url.toString())
        assertArrayEquals(payload, upload.sent.toByteArray())
        assertTrue(calls.all { it.getRequestProperty("Authorization") == "Bearer fake-test-token" && !it.instanceFollowRedirects })
    }

    @Test fun listingFiltersToLedgerZipsAndDownloadRequiresMatchingChecksum() = runTest {
        val drive = GoogleDriveFiles { url ->
            if (url.query == "alt=media") Reply(url, "abc")
            else {
                assertTrue(url.query.contains("qingzhangBackup"))
                assertTrue(url.query.contains("trashed%20%3D%20false"))
                Reply(url, """{"files":[$metadata]}""")
            }
        }
        val snapshot = drive.snapshots("fake-test-token").single()
        assertEquals(Instant.parse("2026-11-21T12:00:00Z"), snapshot.createdAt)
        val download = files.newFile()
        drive.download("fake-test-token", snapshot, download)
        assertArrayEquals(payload, download.readBytes())
        assertTrue(runCatching { drive.download("fake-test-token", snapshot.copy(checksum = "0".repeat(32)), download) }.exceptionOrNull() is IOException)
    }

    @Test fun credentialsAreNotSentToAnUntrustedUploadAddress() = runTest {
        val zip = files.newFile().apply { writeBytes(payload) }
        val attempts = mutableListOf<URL>()
        val drive = GoogleDriveFiles { url ->
            attempts.add(url)
            Reply(url, "", "https://www.googleapis.com.evil.example/upload/drive/v3/files")
        }
        assertTrue(runCatching { drive.upload("fake-test-token", zip) }.exceptionOrNull() is IOException)
        assertEquals(1, attempts.size)
        assertEquals("www.googleapis.com", attempts.single().host)
    }

    private class Reply(url: URL, private val body: String, private val location: String? = null) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getOutputStream() = sent
    }

    private companion object {
        const val SESSION = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&upload_id=test"
    }
}
