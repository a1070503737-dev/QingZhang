package com.zhuangxiu.ledger.voice

import com.zhuangxiu.ledger.update.VerifiedDownload
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class SenseVoiceArtifact(val name: String, val uri: URI, val bytes: Long, val sha256: String) {
    fun target(directory: File) = File(directory, name)
    fun partial(directory: File) = File(directory, "$name.part")
}

internal object SenseVoiceSource {
    const val DIRECTORY = "sensevoice-2025-09-09"
    private const val BASE = "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09/resolve/355f4d4884d8afd08aef04b9007a8556d7b463b2/"

    val tokens = artifact("tokens.txt", 315_894, "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc")
    val model = artifact("model.int8.onnx", 237_115_547, "12ca1a2ae7ecf3e0019ef2822307ee0b5cadc9196569e379b4c4026f8205276d")
    val files = listOf(tokens, model)
    val totalBytes = files.sumOf { it.bytes }

    private fun artifact(name: String, bytes: Long, sha256: String) =
        SenseVoiceArtifact(name, URI("$BASE$name?download=true"), bytes, sha256)
}

internal class SpeechModelDownloadException(message: String) : IOException(message)

internal class SenseVoiceDownload(
    private val connect: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
) {
    fun fetch(
        artifact: SenseVoiceArtifact,
        directory: File,
        ensureActive: () -> Unit,
        progress: (Long) -> Unit,
    ) {
        val target = artifact.target(directory)
        if (target.length() == artifact.bytes && VerifiedDownload.sha256(target, ensureActive) == artifact.sha256) {
            progress(artifact.bytes)
            return
        }
        if (target.exists() && !target.delete()) throw SpeechModelDownloadException("无法替换损坏的模型文件")
        val partial = artifact.partial(directory)
        if (partial.length() > artifact.bytes && !partial.delete()) {
            throw SpeechModelDownloadException("无法清理未完成的模型文件")
        }
        val offset = partial.length()
        progress(offset)
        if (offset < artifact.bytes) fetchRemaining(artifact, partial, offset, ensureActive, progress)
        ensureActive()
        if (partial.length() != artifact.bytes) throw SpeechModelDownloadException("下载未完成，请继续下载")
        if (VerifiedDownload.sha256(partial, ensureActive) != artifact.sha256) {
            partial.delete()
            throw SpeechModelDownloadException("模型校验失败，请重新下载")
        }
        ensureActive()
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun fetchRemaining(
        artifact: SenseVoiceArtifact,
        partial: File,
        offset: Long,
        ensureActive: () -> Unit,
        progress: (Long) -> Unit,
    ) {
        var uri = artifact.uri
        repeat(6) {
            ensureActive()
            if (!officialEndpoint(uri)) throw SpeechModelDownloadException("模型下载地址无法验证")
            val connection = connect(uri)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.useCaches = false
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
                when (connection.responseCode) {
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location")
                            ?: throw SpeechModelDownloadException("模型下载地址无效")
                        uri = uri.resolve(location)
                    }
                    200, 206 -> {
                        val start = if (connection.responseCode == 206) {
                            val range = connection.getHeaderField("Content-Range")?.let(contentRange::matchEntire)
                            val (_, first, last, total) = range?.groupValues
                                ?: throw SpeechModelDownloadException("模型下载范围无效")
                            if (first.toLongOrNull() != offset || total.toLongOrNull() != artifact.bytes ||
                                (last.toLongOrNull() ?: -1L) !in offset until artifact.bytes
                            ) throw SpeechModelDownloadException("模型下载范围无效")
                            offset
                        } else 0L
                        val length = connection.contentLengthLong
                        if (length > artifact.bytes - start) {
                            throw SpeechModelDownloadException("模型下载大小不正确")
                        }
                        connection.inputStream.use { input ->
                            FileOutputStream(partial, start > 0).use { output ->
                                var written = start
                                val buffer = ByteArray(128 * 1024)
                                while (true) {
                                    ensureActive()
                                    val count = input.read(buffer)
                                    if (count == -1) break
                                    written += count
                                    if (written > artifact.bytes) throw SpeechModelDownloadException("模型下载大小不正确")
                                    output.write(buffer, 0, count)
                                    progress(written)
                                }
                            }
                        }
                        return
                    }
                    else -> throw SpeechModelDownloadException("无法连接到模型下载服务，请稍后继续")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw SpeechModelDownloadException("模型下载重定向次数过多")
    }

    private fun officialEndpoint(uri: URI): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            (host == "huggingface.co" || host.endsWith(".huggingface.co") || host.endsWith(".hf.co"))
    }

    private companion object {
        val contentRange = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    }
}
