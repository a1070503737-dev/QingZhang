package com.zhuangxiu.ledger.update

import com.zhuangxiu.ledger.BuildConfig
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

internal class GitHubUpdateHttp(
    private val token: () -> String = { "" },
    private val connect: (String) -> HttpURLConnection = { URI(it).toURL().openConnection() as HttpURLConnection },
) {
    class Response(val input: InputStream, private val connection: HttpURLConnection) : Closeable {
        override fun close() {
            try { input.close() } finally { connection.disconnect() }
        }
    }

    fun open(initialUrl: String, accept: String): Response {
        var url = initialUrl
        repeat(6) {
            if (!UpdateUrlPolicy.allowed(url)) throw IOException("更新下载地址不受信任，已停止下载")
            val connection = connect(url)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Accept", accept)
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("User-Agent", "RenovationLedger/${BuildConfig.VERSION_NAME}")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                val activeToken = if (UpdateUrlPolicy.canAuthorize(url)) {
                    token().takeIf { it.isNotBlank() }
                } else null
                if (activeToken != null) {
                    connection.setRequestProperty("Authorization", "Bearer $activeToken")
                }
                when (val status = connection.responseCode) {
                    200 -> return Response(connection.inputStream, connection)
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location") ?: throw IOException("下载链接已失效，请重新检查更新")
                        url = URI(url).resolve(location).toString()
                        connection.disconnect()
                    }
                    401 -> throw IOException("GitHub 授权已失效，请检查访问设置")
                    403 -> throw IOException(if (connection.getHeaderField("X-RateLimit-Remaining") == "0")
                        "GitHub 暂时限制了请求，请稍后重试"
                    else if (activeToken != null)
                        "GitHub 拒绝了访问，请确认令牌对 code 仓库有 Contents 只读权限"
                    else
                        "GitHub 拒绝了访问，请稍后重试或配置访问令牌")
                    429 -> throw IOException("GitHub 暂时限制了请求，请稍后重试")
                    404 -> throw IOException("暂时无法访问发行版，请确认更新仓库已发布版本并允许访问")
                    else -> throw IOException("GitHub 暂时无法提供下载（$status），请稍后重试")
                }
            } catch (e: Exception) {
                connection.disconnect()
                throw e
            }
        }
        throw IOException("下载地址跳转过多，请稍后重试")
    }
}
