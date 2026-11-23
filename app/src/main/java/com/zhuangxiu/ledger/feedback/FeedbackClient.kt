package com.zhuangxiu.ledger.feedback

import com.zhuangxiu.ledger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

private const val CONFIRMATION = "已收到你的反馈，谢谢！"
private val hiddenHtml = Regex("""(?is)<(script|style)\b[^>]*>.*?</\1\s*>|<!--.*?-->""")

internal class FeedbackClient(
    private val connect: (String) -> HttpURLConnection = { URI(it).toURL().openConnection() as HttpURLConnection },
) {
    val available: Boolean get() = BuildConfig.FEEDBACK_SUBMIT_URL.isNotEmpty()

    suspend fun submit(feedback: String, contact: String): Unit = withContext(Dispatchers.IO) {
        check(available)
        val body = buildList {
            add("entry.${BuildConfig.FEEDBACK_CONTENT_ENTRY}=${URLEncoder.encode(feedback, "UTF-8")}")
            if (contact.isNotEmpty()) {
                add("entry.${BuildConfig.FEEDBACK_CONTACT_ENTRY}=${URLEncoder.encode(contact, "UTF-8")}")
            }
            add("fvv=1")
            add("pageHistory=0")
        }.joinToString("&").toByteArray(Charsets.UTF_8)

        val connection = connect(BuildConfig.FEEDBACK_SUBMIT_URL)
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("Accept", "text/html")
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("反馈服务未接受提交")

            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            // The responder form also lists the confirmation text inside its setup script.
            if (CONFIRMATION !in hiddenHtml.replace(response, "")) throw IOException("反馈服务未确认提交")
        } finally {
            connection.disconnect()
        }
    }
}
