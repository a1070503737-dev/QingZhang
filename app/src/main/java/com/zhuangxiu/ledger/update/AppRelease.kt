package com.zhuangxiu.ledger.update

import org.json.JSONObject
import java.io.IOException
import java.net.URI

object UpdateSource {
    const val REPOSITORY = "a1070503737-dev/code"
    const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"
    const val LATEST_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    const val MAX_APK_BYTES = 150L * 1024 * 1024
}

/** Compare numeric components: 1.0.10 must be newer than 1.0.9. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(value: String): AppVersion? {
            val match = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)").matchEntire(value) ?: return null
            val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return AppVersion(parts[0], parts[1], parts[2])
        }
    }
}

data class AppRelease(
    val version: AppVersion,
    val notes: String,
    val assetId: Long,
    val size: Long,
    val sha256: String,
) {
    val downloadUrl: String get() = "https://api.github.com/repos/${UpdateSource.REPOSITORY}/releases/assets/$assetId"

    companion object {
        fun parse(json: String): AppRelease? {
            val release = JSONObject(json)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
            val version = AppVersion.parse(release.optString("tag_name"))
                ?: throw IOException("发行版的版本信息不完整，请稍后重试")
            val expectedName = "renovation-ledger-$version-signed.apk"
            val assets = release.optJSONArray("assets") ?: throw IOException("此版本尚未提供安装包")
            val matches = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name") == expectedName && it.optString("state") == "uploaded" }
            val asset = matches.singleOrNull() ?: throw IOException("此版本尚未提供可用的正式安装包")
            val id = asset.optLong("id")
            val size = asset.optLong("size")
            val digest = asset.optString("digest").removePrefix("sha256:").lowercase()
            if (id <= 0 || size !in 1..UpdateSource.MAX_APK_BYTES ||
                !asset.optString("digest").startsWith("sha256:") || !digest.matches(Regex("[a-f0-9]{64}"))) {
                throw IOException("安装包校验信息不完整，请等待维护者重新发布")
            }
            val notes = if (release.isNull("body")) "" else release.optString("body").take(12_000)
            return AppRelease(version, notes, id, size, digest)
        }
    }
}

/** Follow only GitHub's HTTPS asset redirects; never forward credentials to an asset host. */
internal object UpdateUrlPolicy {
    fun allowed(url: String): Boolean = runCatching {
        val uri = URI(url).normalize()
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443)) return false
        when (uri.host) {
            "api.github.com" -> uri.path.startsWith("/repos/${UpdateSource.REPOSITORY}/")
            "github.com" -> uri.path.startsWith("/${UpdateSource.REPOSITORY}/releases/")
            "release-assets.githubusercontent.com", "objects.githubusercontent.com" -> true
            else -> false
        }
    }.getOrDefault(false)

    fun canAuthorize(url: String): Boolean = allowed(url) && URI(url).host == "api.github.com"
}
