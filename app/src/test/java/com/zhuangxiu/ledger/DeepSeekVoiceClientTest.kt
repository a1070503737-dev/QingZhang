package com.zhuangxiu.ledger

import android.app.Application
import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.domain.ChineseVoiceParser
import com.zhuangxiu.ledger.voice.DeepSeekVoiceClient
import java.io.IOException
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
class DeepSeekVoiceClientTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val transcript = "今晚吃了三块蛋糕，花了25元"

    @Test fun sendsOnlyTheCurrentTranscriptAndClassificationContext() {
        val request = DeepSeekVoiceClient.request(transcript, today)
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
        val cloud = DeepSeekVoiceClient.decode(response(entry("25.00")), local).single()
        assertEquals("25.00", cloud.amount)
        assertEquals(DailyCategory.FOOD, cloud.category)
        assertEquals("三块蛋糕", cloud.note)
        assertEquals("", DeepSeekVoiceClient.decode(response(entry("3")), local).single().amount)
        val repeated = DeepSeekVoiceClient.decode(response(entry("25"), entry("25")), local)
        assertEquals(listOf("25", ""), repeated.map { it.amount })
    }

    @Test fun rejectsTruncatedResponses() {
        assertThrows(IOException::class.java) {
            DeepSeekVoiceClient.decode(response(entry("25"), reason = "length"), emptyList())
        }
    }

    private fun entry(amount: String) = JSONObject().put("kind", "EXPENSE").put("category", "FOOD")
        .put("amount", amount).put("date", today.toString()).put("note", "三块蛋糕")

    private fun response(vararg entries: JSONObject, reason: String = "stop"): String {
        val content = JSONObject().put("entries", JSONArray(entries.toList())).toString()
        return JSONObject().put("choices", JSONArray().put(JSONObject().put("finish_reason", reason)
            .put("message", JSONObject().put("content", content)))).toString()
    }
}
