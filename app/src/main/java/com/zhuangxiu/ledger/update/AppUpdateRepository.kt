package com.zhuangxiu.ledger.update

import android.content.Context
import com.zhuangxiu.ledger.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tokens: GitHubTokenStore,
) {
    private val http = GitHubUpdateHttp(token = tokens::read)
    private val downloadLock = Mutex()
    private val directory get() = File(context.cacheDir, "updates").apply { mkdirs() }

    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        if (tokens.read().isBlank()) throw IOException("此仓库为私有仓库，请先设置 GitHub 只读访问令牌")
        checkWith(http)
    }

    suspend fun accessConfigured(): Boolean = withContext(Dispatchers.IO) { tokens.read().isNotBlank() }

    suspend fun configureAccess(value: String): AppRelease? = withContext(Dispatchers.IO) {
        val token = value.trim()
        if (!token.matches(Regex("[A-Za-z0-9_]{20,512}"))) throw IOException("请粘贴完整的 GitHub 访问令牌")
        val release = checkWith(GitHubUpdateHttp(token = { token }))
        tokens.save(token)
        release
    }

    suspend fun clearAccess() = withContext(Dispatchers.IO) { tokens.clear() }

    private suspend fun checkWith(client: GitHubUpdateHttp): AppRelease? {
        val json = client.open(UpdateSource.LATEST_URL, "application/vnd.github+json").use { response ->
            val bytes = response.input.takeBytes(2 * 1024 * 1024)
            bytes.toString(Charsets.UTF_8)
        }
        currentCoroutineContext().ensureActive()
        return AppRelease.parse(json)?.takeIf { it.version > requireNotNull(AppVersion.parse(BuildConfig.VERSION_NAME)) }
    }

    suspend fun download(release: AppRelease, onProgress: (Long) -> Unit, onVerifying: () -> Unit): File =
        withContext(Dispatchers.IO) {
            downloadLock.withLock {
                val file = File(directory, "update-${release.assetId}.apk")
                val coroutine = currentCoroutineContext()
                try {
                    // A valid completed download can be reused after returning from system settings.
                    if (!file.exists() || file.length() != release.size || VerifiedDownload.sha256(file) != release.sha256) {
                        directory.listFiles()?.forEach { it.delete() }
                        http.open(release.downloadUrl, "application/octet-stream").use { response ->
                            VerifiedDownload.copy(response.input, file, release.size, release.sha256,
                                ensureActive = { coroutine.ensureActive() }, progress = onProgress)
                        }
                    }
                    coroutine.ensureActive()
                    onVerifying()
                    UpdateApkVerifier.verify(context, file, release)
                    file
                } catch (e: Exception) {
                    file.delete()
                    throw e
                }
            }
        }

    suspend fun verifyForInstall(file: File, release: AppRelease) = withContext(Dispatchers.IO) {
        UpdateApkVerifier.verify(context, file, release)
    }
}

private fun java.io.InputStream.takeBytes(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count == -1) return output.toByteArray()
        if (output.size() + count > limit) throw IOException("发行版信息过大，请稍后重试")
        output.write(buffer, 0, count)
    }
}
