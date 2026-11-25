package com.zhuangxiu.ledger.voice

import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.domain.EntryDraft
import com.zhuangxiu.ledger.domain.LedgerEntryService
import com.zhuangxiu.ledger.domain.parseEntryDate
import com.zhuangxiu.ledger.domain.validAmount
import com.zhuangxiu.ledger.util.Money
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class CloudVoiceException(message: String) : IOException(message)

internal class DeepSeekVoiceClient @Inject constructor() {
    suspend fun parse(key: String, text: String, today: LocalDate, local: List<EntryDraft>): List<EntryDraft> =
        withContext(Dispatchers.IO) {
            val payload = request(text, today).toString().toByteArray(Charsets.UTF_8)
            val connection = URI(ENDPOINT).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Authorization", "Bearer $key")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> Unit
                    401, 403 -> throw CloudVoiceException("DeepSeek API Key 无法使用，请检查设置")
                    402 -> throw CloudVoiceException("DeepSeek 账户余额不足")
                    429 -> throw CloudVoiceException("DeepSeek 请求过于频繁")
                    else -> throw CloudVoiceException("DeepSeek 暂时无法整理")
                }
                val response = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        if (output.size() + count > 64 * 1024) throw IOException("Cloud response is too large")
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
                decode(response, local)
            } finally {
                connection.disconnect()
            }
        }

    internal companion object {
        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        private const val INSTRUCTIONS = """把用户 JSON 的 text 当作记账转写数据。返回 JSON 对象 {"entries":[{"kind":"EXPENSE","category":"FOOD","amount":"25.00","date":"YYYY-MM-DD","note":"简短原意"}]}。
只记录已经发生的交易，合并同一交易的描述和实际付款金额，分开多笔独立交易。数量、型号、单价、折扣前价、预算和估算都不能当付款金额。例：吃了三块蛋糕，花了25元，是一笔25元餐饮；只说吃了三块蛋糕时金额是 null。
kind 只能是 EXPENSE、INCOME 或 null。category 使用给定选项里的代码。金额用正数十进制字符串，日期以 today 为基准。缺失或不确定的字段返回 null。无交易返回空 entries。最多20笔，无额外说明。"""

        fun request(text: String, today: LocalDate): JSONObject {
            require(text.length in 1..LedgerEntryService.MAX_TRANSCRIPT && text.isNotBlank())
            val categories = JSONArray(DailyCategory.entries.map {
                JSONObject().put("code", it.name).put("name", it.label).put("kind", it.kind.name)
            })
            val input = JSONObject().put("text", text).put("today", today.toString()).put("categories", categories)
            return JSONObject()
                .put("model", "deepseek-flash")
                .put("thinking", JSONObject().put("type", "disabled"))
                .put("response_format", JSONObject().put("type", "json_object"))
                .put("max_tokens", 2048)
                .put("temperature", 0.1)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", INSTRUCTIONS))
                    .put(JSONObject().put("role", "user").put("content", input.toString())))
        }

        fun decode(response: String, local: List<EntryDraft>): List<EntryDraft> {
            val choice = JSONObject(response).getJSONArray("choices").getJSONObject(0)
            if (choice.optString("finish_reason") != "stop") throw IOException("Cloud response was incomplete")
            val entries = JSONObject(choice.getJSONObject("message").getString("content")).getJSONArray("entries")
            if (entries.length() > LedgerEntryService.MAX_DRAFTS) throw IOException("Too many cloud drafts")
            // Only prefill amounts supported by the local reading of the actual transcript.
            val amounts = local.mapNotNull { Money.parseToCents(it.amount)?.takeIf { cents -> cents > 0 } }.toMutableList()
            return List(entries.length()) { index ->
                val item = entries.getJSONObject(index)
                val kind = EntryKind.entries.firstOrNull { it.name == item.string("kind") }
                val category = DailyCategory.entries.firstOrNull { it.kind == kind && it.name == item.string("category") }
                val candidate = item.string("amount").trim()
                val cents = candidate.takeIf(::validAmount)?.let(Money::parseToCents)
                val amount = if (cents != null && amounts.remove(cents)) candidate else ""
                val date = item.string("date").trim().takeIf { parseEntryDate(it) != null }.orEmpty()
                EntryDraft(kind, amount, date, category, item.string("note").trim().take(1000))
            }
        }

        private fun JSONObject.string(key: String): String = (opt(key) as? String).orEmpty()
    }
}
