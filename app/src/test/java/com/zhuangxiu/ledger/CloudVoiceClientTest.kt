package com.zhuangxiu.ledger

import android.app.Application
import android.content.Context
import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.domain.ChineseVoiceParser
import com.zhuangxiu.ledger.voice.CloudVoiceAccess
import com.zhuangxiu.ledger.voice.CloudVoiceClient
import com.zhuangxiu.ledger.voice.CloudVoiceConfig
import com.zhuangxiu.ledger.voice.CloudVoiceProtocol
import com.zhuangxiu.ledger.voice.CloudVoiceSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
class CloudVoiceClientTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val transcript = "今晚吃了三块蛋糕，花了25元"

    @Test fun sendsOnlyTheCurrentTranscriptAndClassificationContext() {
        val request = CloudVoiceClient.request(CloudVoiceConfig(), transcript, today)
        assertEquals("deepseek-flash", request.getString("model"))
        assertEquals("disabled", request.getJSONObject("thinking").getString("type"))
        assertEquals("json_object", request.getJSONObject("response_format").getString("type"))
        val messages = request.getJSONArray("messages")
        assertEquals(2, messages.length())
        val input = JSONObject(messages.getJSONObject(1).getString("content"))
        assertEquals(setOf("text", "today", "categories"), input.keys().asSequence().toSet())
        assertEquals(transcript, input.getString("text"))
    }

    @Test fun mergesAQuantityAndPriceButLeavesUnsubstantiatedMoneyBlank() {
        val local = ChineseVoiceParser.parse(transcript, today)
        val cloud = CloudVoiceClient.decode(response(entry("25.00")), local).single()
        assertEquals("25.00", cloud.amount)
        assertEquals(DailyCategory.FOOD, cloud.category)
        assertEquals("三块蛋糕", cloud.note)
        assertEquals("", CloudVoiceClient.decode(response(entry("3")), local).single().amount)
        val repeated = CloudVoiceClient.decode(response(entry("25"), entry("25")), local)
        assertEquals(listOf("25", ""), repeated.map { it.amount })
    }

    @Test fun rejectsTruncatedResponses() {
        assertThrows(IOException::class.java) {
            CloudVoiceClient.decode(response(entry("25"), reason = "length"), emptyList())
        }
    }

    @Test fun connectivityUsesEachGenerationEndpointAndReturnsTheReportedModel() {
        val chat = """{"model":"served-model","choices":[{"finish_reason":"stop","message":{"content":"{\"ok\":true}"}}]}"""
        val responses = """{"model":"served-model","status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{\"ok\":true}"}]}]}"""
        val anthropic = """{"model":"served-model","stop_reason":"end_turn","content":[{"type":"text","text":"{\"ok\":true}"}]}"""
        val gemini = """{"modelVersion":"served-model","candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\"ok\":true}"}]}}]}"""
        val cases = mapOf(
            CloudVoiceProtocol.DEEPSEEK to ("/chat/completions" to chat),
            CloudVoiceProtocol.CHAT to ("/v1/chat/completions" to chat),
            CloudVoiceProtocol.RESPONSES to ("/v1/responses" to responses),
            CloudVoiceProtocol.ANTHROPIC to ("/v1/messages" to anthropic),
            CloudVoiceProtocol.GEMINI to ("/v1beta/models/configured-model:generateContent" to gemini),
        )
        for ((protocol, routeAndReply) in cases) {
            val (route, reply) = routeAndReply
            val config = CloudVoiceConfig(protocol, model = "configured-model").validated()
            val connection = TestConnection(config.endpoint(), 200, reply)
            val client = CloudVoiceClient { uri ->
                assertEquals(route, uri.path)
                connection
            }
            assertEquals("served-model", runBlocking { client.testConnection(CloudVoiceAccess(config, "test-key")) })
            val body = connection.request.toString(Charsets.UTF_8.name())
            assertTrue(body.contains("Connectivity check."))
            assertFalse(body.contains("categories"))
            assertFalse(body.contains("test-key"))
            assertFalse(connection.instanceFollowRedirects)
            assertTrue(connection.disconnected)
            when (protocol) {
                CloudVoiceProtocol.GEMINI -> assertEquals("test-key", connection.getRequestProperty("x-goog-api-key"))
                CloudVoiceProtocol.ANTHROPIC -> assertEquals("test-key", connection.getRequestProperty("x-api-key"))
                else -> assertEquals("Bearer test-key", connection.getRequestProperty("Authorization"))
            }
        }
    }

    @Test fun connectivityDistinguishesAuthorizationBalanceModelAndTimeout() {
        val access = CloudVoiceAccess(CloudVoiceConfig(), "test-key")
        for ((code, messagePart) in listOf(401 to "未获授权", 402 to "余额不足", 404 to "找不到", 429 to "限流", 0 to "超时")) {
            val client = CloudVoiceClient { TestConnection(it, code, "") }
            val error = assertThrows(IOException::class.java) { runBlocking { client.testConnection(access) } }
            assertTrue(error.message.orEmpty().contains(messagePart))
        }
    }

    @Test fun migratedKeyIsKeptAtItsOriginalEndpoint() {
        val application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("cloud_voice", Context.MODE_PRIVATE).edit()
            .putString("encrypted_key", "unread-on-other-endpoints").putBoolean("enabled", true).commit()
        val settings = CloudVoiceSettings(application)
        assertEquals(CloudVoiceConfig(), settings.status.value.config)
        assertTrue(settings.status.value.enabled)
        for (base in listOf("http://api.example.test/v1", "https://api.example.test/v1?key=bad")) {
            assertThrows(IllegalArgumentException::class.java) { CloudVoiceConfig(baseUrl = base).validated() }
        }
        assertThrows(IllegalArgumentException::class.java) {
            settings.access(CloudVoiceConfig(baseUrl = "https://api.example.test/v1"), "")
        }
    }

    private class TestConnection(uri: URI, private val code: Int, private val result: String) :
        HttpURLConnection(uri.toURL()) {
        val request = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getOutputStream() = request
        override fun getResponseCode(): Int = if (code == 0) throw SocketTimeoutException() else code
        override fun getInputStream() = ByteArrayInputStream(result.toByteArray(Charsets.UTF_8))
    }

    private fun entry(amount: String) = JSONObject().put("kind", "EXPENSE").put("category", "FOOD")
        .put("amount", amount).put("date", today.toString()).put("note", "三块蛋糕")

    private fun response(vararg entries: JSONObject, reason: String = "stop"): String {
        val content = JSONObject().put("entries", JSONArray(entries.toList())).toString()
        return JSONObject().put("choices", JSONArray().put(JSONObject().put("finish_reason", reason)
            .put("message", JSONObject().put("content", content)))).toString()
    }
}
