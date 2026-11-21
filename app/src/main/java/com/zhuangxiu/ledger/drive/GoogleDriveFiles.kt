package com.zhuangxiu.ledger.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant

internal data class DriveAccount(val id: String, val email: String)

internal data class DriveSnapshot(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val size: Long,
    val checksum: String,
)

internal class DriveHttpError(val status: Int, val reason: String?) : IOException() {
    val retryable get() = status == 429 || status >= 500 || reason in setOf("rateLimitExceeded", "userRateLimitExceeded")
}

internal class GoogleDriveFiles(
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    suspend fun account(token: String): DriveAccount = withContext(Dispatchers.IO) {
        request("$API/about?fields=${encode("user(emailAddress,permissionId)")}", token) {
            val user = responseJson().getJSONObject("user")
            DriveAccount(user.getString("permissionId"), user.getString("emailAddress"))
                .also { require(it.id.isNotBlank() && it.email.isNotBlank()) }
        }
    }

    suspend fun snapshots(token: String): List<DriveSnapshot> = withContext(Dispatchers.IO) {
        val fields = encode("files($FILE_FIELDS)")
        request("$API/files?q=${encode(BACKUPS_QUERY)}&orderBy=createdTime%20desc&pageSize=$LIST_LIMIT&fields=$fields", token) {
            val files = responseJson().getJSONArray("files")
            (0 until files.length()).map { snapshot(files.getJSONObject(it)) }
        }
    }

    /** Collect every page before changing Drive's list. A newer concurrent upload is never eligible. */
    suspend fun trashOlderBackups(token: String, uploaded: DriveSnapshot) = withContext(Dispatchers.IO) {
        val query = "$BACKUPS_QUERY and 'me' in owners and createdTime < '${uploaded.createdAt}'"
        val fields = encode("nextPageToken,files(id,createdTime)")
        val olderIds = linkedSetOf<String>()
        var pageToken = ""
        do {
            currentCoroutineContext().ensureActive()
            val cursor = if (pageToken.isEmpty()) "" else "&pageToken=${encode(pageToken)}"
            val page = request("$API/files?q=${encode(query)}&pageSize=100&fields=$fields$cursor", token) {
                responseJson()
            }
            val files = page.getJSONArray("files")
            for (index in 0 until files.length()) {
                val file = files.getJSONObject(index)
                val id = file.getString("id")
                if (id != uploaded.id && Instant.parse(file.getString("createdTime")) < uploaded.createdAt) {
                    olderIds.add(id)
                }
            }
            pageToken = page.optString("nextPageToken")
        } while (pageToken.isNotEmpty())
        if (olderIds.isEmpty()) return@withContext

        val latestFields = encode("$FILE_FIELDS,trashed,ownedByMe,mimeType,appProperties")
        request("$API/files/${encode(uploaded.id)}?fields=$latestFields", token) {
            val file = responseJson()
            val latest = snapshot(file)
            if (file.getBoolean("trashed") || !file.optBoolean("ownedByMe") || file.optString("mimeType") != ZIP_MIME ||
                file.optJSONObject("appProperties")?.optString(MARKER) != "1" ||
                latest.id != uploaded.id || latest.size != uploaded.size || latest.checksum != uploaded.checksum
            ) throw IOException("无法确认最新备份仍可用")
        }

        val body = JSONObject().put("trashed", true).toString().toByteArray(Charsets.UTF_8)
        for (id in olderIds) {
            currentCoroutineContext().ensureActive()
            request("$API/files/${encode(id)}?fields=id,trashed", token, "POST") {
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
                setFixedLengthStreamingMode(body.size)
                outputStream.use { it.write(body) }
                if (responseCode != 404 && !responseJson().getBoolean("trashed")) {
                    throw IOException("Google Drive 未将旧备份移到回收站")
                }
            }
        }
    }

    suspend fun upload(token: String, zip: File): DriveSnapshot = withContext(Dispatchers.IO) {
        val size = zip.length()
        val checksum = zip.inputStream().use { fileChecksum(it) }
        val metadata = JSONObject().put("name", zip.name).put("mimeType", ZIP_MIME)
            .put("appProperties", JSONObject().put(MARKER, "1"))
        val location = request("$UPLOAD?uploadType=resumable&fields=${encode(FILE_FIELDS)}", token, "POST") {
            setRequestProperty("X-Upload-Content-Type", ZIP_MIME)
            setRequestProperty("X-Upload-Content-Length", size.toString())
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            doOutput = true
            val bytes = metadata.toString().toByteArray(Charsets.UTF_8)
            setFixedLengthStreamingMode(bytes.size)
            outputStream.use { it.write(bytes) }
            requireSuccess()
            getHeaderField("Location") ?: throw IOException("Google Drive 未返回上传地址")
        }
        validateUploadLocation(location)
        request(location, token, "PUT") {
            setRequestProperty("Content-Type", ZIP_MIME)
            doOutput = true
            setFixedLengthStreamingMode(size)
            outputStream.use { out -> zip.inputStream().use { it.copyChecked(out, size) } }
            snapshot(responseJson()).also { uploaded ->
                if (uploaded.size != size || uploaded.checksum != checksum) throw IOException("Google Drive 备份校验失败")
            }
        }
    }

    suspend fun download(token: String, snapshot: DriveSnapshot, target: File) = withContext(Dispatchers.IO) {
        if (snapshot.size > target.parentFile!!.usableSpace) throw IOException("手机空间不足，无法下载备份")
        request("$API/files/${encode(snapshot.id)}?alt=media", token) {
            requireSuccess()
            val digest = MessageDigest.getInstance("MD5")
            target.outputStream().buffered().use { out ->
                inputStream.use { input -> input.copyChecked(out, snapshot.size, digest) }
            }
            if (digest.digest().toHex() != snapshot.checksum) throw IOException("下载的备份校验失败")
        }
    }

    private suspend fun <T> request(
        url: String,
        token: String,
        method: String = "GET",
        block: suspend HttpURLConnection.() -> T,
    ): T {
        val connection = connect(URI(url).toURL()).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            return connection.block()
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.requireSuccess() {
        val code = responseCode
        if (code in 200..299) return
        val reason = runCatching {
            val error = errorStream?.bufferedReader()?.use { reader ->
                val chars = CharArray(4096)
                val count = reader.read(chars)
                if (count < 0) null else JSONObject(String(chars, 0, count)).optJSONObject("error")
            }
            error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")
        }.getOrNull()
        throw DriveHttpError(code, reason)
    }

    private fun HttpURLConnection.responseJson(): JSONObject {
        requireSuccess()
        return inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }

    private fun snapshot(json: JSONObject): DriveSnapshot {
        val size = json.getString("size").toLong()
        val checksum = json.getString("md5Checksum").lowercase()
        if (size <= 0 || !CHECKSUM.matches(checksum)) throw IOException("Google Drive 备份元数据无效")
        return DriveSnapshot(json.getString("id"), json.getString("name"), Instant.parse(json.getString("createdTime")), size, checksum)
    }

    private suspend fun InputStream.copyChecked(output: OutputStream, expected: Long, digest: MessageDigest? = null) {
        val context = currentCoroutineContext()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            context.ensureActive()
            val count = read(buffer)
            if (count == -1) break
            total += count
            if (total > expected) throw IOException("备份大小与 Google Drive 记录不符")
            output.write(buffer, 0, count)
            digest?.update(buffer, 0, count)
        }
        if (total != expected) throw IOException("备份传输不完整")
    }

    private suspend fun fileChecksum(input: InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count == -1) return digest.digest().toHex()
            digest.update(buffer, 0, count)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    internal companion object {
        const val LIST_LIMIT = 30
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FILE_FIELDS = "id,name,createdTime,size,md5Checksum"
        private const val MARKER = "qingzhangBackup"
        private const val ZIP_MIME = "application/zip"
        private const val BACKUPS_QUERY = "trashed = false and mimeType = '$ZIP_MIME' and appProperties has { key='$MARKER' and value='1' }"
        private val CHECKSUM = Regex("[a-f0-9]{32}")

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

        internal fun validateUploadLocation(location: String) {
            val uri = runCatching { URI(location) }.getOrNull()
            if (uri == null || uri.scheme != "https" || uri.host != "www.googleapis.com" ||
                uri.userInfo != null || uri.port !in listOf(-1, 443) || uri.path != "/upload/drive/v3/files" || uri.fragment != null
            ) throw IOException("Google Drive 上传地址无效")
        }
    }
}
