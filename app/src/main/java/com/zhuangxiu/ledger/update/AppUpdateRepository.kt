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
    private fun token(): String = UpdateCredentials.resolve(tokens::read)
    private val http = GitHubUpdateHttp(token = ::token)
    private val publicHttp = GitHubUpdateHttp()
    private val publicFeed = PublicUpdateFeed(publicHttp)
    private val downloadLock = Mutex()
    private val directory get() = File(context.cacheDir, "updates").apply { mkdirs() }

    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        val release = publicFeed.latest()
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()
        release.takeIf {
            it.version > requireNotNull(AppVersion.parse(BuildConfig.VERSION_NAME)) &&
                requireNotNull(it.versionCode) > BuildConfig.VERSION_CODE
        }?.let { update ->
            val delta = update.delta?.takeIf {
                val base = installedApk(it) ?: return@takeIf false
                try {
                    VerifiedDownload.sha256(base) { coroutine.ensureActive() } == it.baseSha256
                } catch (_: IOException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }
            update.copy(delta = delta)
        }
    }

    private fun installedApk(delta: ApkDelta): File? {
        val info = context.applicationInfo
        if (delta.baseVersionCode != BuildConfig.VERSION_CODE.toLong() || !info.splitSourceDirs.isNullOrEmpty()) return null
        return info.sourceDir?.let(::File)
    }

    suspend fun accessConfigured(): Boolean = withContext(Dispatchers.IO) { token().isNotBlank() }

    suspend fun configureAccess(value: String): AppRelease? = withContext(Dispatchers.IO) {
        val token = value.trim()
        if (!token.matches(Regex("[A-Za-z0-9_]{20,512}"))) throw IOException("请粘贴完整的 GitHub 访问令牌")
        // Explicit manual token validation must not silently pass through anonymous access.
        val release = checkWith(GitHubUpdateHttp(token = { token }, preferAnonymous = false))
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

    suspend fun download(
        release: AppRelease,
        onDownloading: (Long) -> Unit,
        onProgress: (Long) -> Unit,
        onVerifying: () -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            downloadLock.withLock {
                val file = File(directory, "update-${release.version}-${release.sha256.take(12)}.apk")
                val coroutine = currentCoroutineContext()
                try {
                    // A valid completed download can be reused after returning from system settings.
                    if (!file.exists() || file.length() != release.size ||
                        VerifiedDownload.sha256(file) { coroutine.ensureActive() } != release.sha256) {
                        directory.listFiles()?.forEach { it.delete() }
                        val delta = release.delta
                        val base = delta?.let(::installedApk)
                        val patched = delta != null && base != null && downloadDelta(
                            release, delta, base, file, { coroutine.ensureActive() }, onDownloading, onProgress, onVerifying,
                        )
                        if (!patched) {
                            coroutine.ensureActive()
                            onDownloading(release.size)
                            val client = if (release.versionCode != null) publicHttp else http
                            client.open(release.downloadUrl, "application/octet-stream").use { response ->
                                VerifiedDownload.copy(response.input, file, release.size, release.sha256,
                                    ensureActive = { coroutine.ensureActive() }, progress = onProgress)
                            }
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

    private fun downloadDelta(
        release: AppRelease, delta: ApkDelta, base: File, target: File, ensureActive: () -> Unit,
        onDownloading: (Long) -> Unit, onProgress: (Long) -> Unit, onVerifying: () -> Unit,
    ): Boolean {
        val patch = File(target.parentFile, delta.fileName)
        return try {
            ensureActive()
            onDownloading(delta.size)
            publicHttp.open(delta.downloadUrl(release.version), "application/octet-stream").use { response ->
                VerifiedDownload.copy(response.input, patch, delta.size, delta.sha256, ensureActive, onProgress)
            }
            onVerifying()
            ApkDeltaApplier.apply(base, patch, target, release.size, release.sha256, ensureActive)
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } finally {
            patch.delete()
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
