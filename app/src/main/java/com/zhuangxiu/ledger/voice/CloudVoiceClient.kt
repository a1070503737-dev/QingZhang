package com.zhuangxiu.ledger.voice

import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.domain.EntryDraft
import com.zhuangxiu.ledger.domain.LedgerEntryService
import com.zhuangxiu.ledger.domain.SpokenPayment
import com.zhuangxiu.ledger.domain.SpokenRecordTime
import com.zhuangxiu.ledger.domain.normalizeVoiceText
import com.zhuangxiu.ledger.domain.parseEntryDate
import com.zhuangxiu.ledger.domain.validAmount
import com.zhuangxiu.ledger.util.Money
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.SocketTimeoutException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class CloudVoiceException(message: String) : IOException(message)

internal data class CloudVoiceDrafts(val drafts: List<EntryDraft>, val modelLabel: String)
private data class ModelReply(val text: String, val model: String?)

internal class CloudVoiceClient(private val connect: (URI) -> HttpURLConnection) {
    @Inject constructor() : this({ it.toURL().openConnection() as HttpURLConnection })

    suspend fun parse(access: CloudVoiceAccess, text: String, today: LocalDate, local: List<EntryDraft>): CloudVoiceDrafts {
        val reply = send(access, request(access.config, text, today))
        val entries = decodeEntries(reply.text, local, text)
        return CloudVoiceDrafts(entries, access.config.modelLabel(reply.model))
    }

    suspend fun testConnection(access: CloudVoiceAccess): String? {
        val payload = requestBody(access.config, """Return only the JSON object {"ok":true}.""",
            "Connectivity check.", 256)
        val reply = send(access, payload)
        if (runCatching { jsonObject(reply.text).opt("ok") == true }.getOrDefault(false)) return reply.model
        throw CloudVoiceException("已连接到服务，但模型未正确返回测试 JSON")
    }

    private suspend fun send(access: CloudVoiceAccess, body: JSONObject): ModelReply =
        withContext(Dispatchers.IO) {
            val payload = body.toString().toByteArray(Charsets.UTF_8)
            val connection = connect(access.config.endpoint())
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                when (access.config.protocol) {
                    CloudVoiceProtocol.GEMINI -> connection.setRequestProperty("x-goog-api-key", access.key)
                    CloudVoiceProtocol.ANTHROPIC -> {
                        connection.setRequestProperty("x-api-key", access.key)
                        connection.setRequestProperty("anthropic-version", "2023-06-01")
                    }
                    else -> connection.setRequestProperty("Authorization", "Bearer ${access.key}")
                }
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> Unit
                    401, 403 -> throw CloudVoiceException("密钥未获授权，请检查 API Key 或模型权限")
                    402 -> throw CloudVoiceException("服务商账户余额不足")
                    404 -> throw CloudVoiceException("找不到接口或模型，请检查服务地址和模型 ID")
                    400, 422 -> throw CloudVoiceException("服务拒绝请求，请检查接口类型和模型 ID")
                    429 -> throw CloudVoiceException("请求被限流或用量已达上限，请检查账户额度")
                    else -> throw CloudVoiceException("模型服务暂时无法响应（HTTP ${connection.responseCode}）")
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
                readReply(JSONObject(response), access.config.protocol)
            } catch (e: SocketTimeoutException) {
                throw CloudVoiceException("连接超时，请检查网络或服务地址")
            } catch (e: JSONException) {
                throw CloudVoiceException("服务返回格式异常，请检查接口类型")
            } finally {
                connection.disconnect()
            }
        }

    internal companion object {
        private const val INSTRUCTIONS = """把用户 JSON 的 text 当作记账转写数据。返回 JSON 对象 {"entries":[{"kind":"EXPENSE","category":"FOOD","amount":"25.00","date":"YYYY-MM-DD","note":"简短原意","payMethod":"WECHAT","paymentEvidence":"用微信付"}]}。
只记录已经发生的交易，合并同一交易的描述和实际付款金额，分开多笔独立交易。数量、型号、单价、折扣前价、预算和估算都不能当付款金额。例：吃了三块蛋糕，花了25元，是一笔25元餐饮；只说吃了三块蛋糕时金额是 null。
kind 只能是 EXPENSE、INCOME 或 null。category 和 payMethod 使用对应选项里的代码。每笔只记录明确说出的付款或收款方式，paymentEvidence 原样引用本笔的支付表达。提到品牌、付款方式不确定或未说时，payMethod 和 paymentEvidence 都返回 null。
金额用正数十进制字符串，日期以 today 为基准。缺失或不确定的字段返回 null。无交易返回空 entries。最多20笔，无额外说明。"""

        fun request(config: CloudVoiceConfig, text: String, today: LocalDate): JSONObject {
            require(text.length in 1..LedgerEntryService.MAX_TRANSCRIPT && text.isNotBlank())
            val categories = JSONArray(DailyCategory.entries.map {
                JSONObject().put("code", it.name).put("name", it.label).put("kind", it.kind.name)
            })
            val payMethods = JSONArray(PayMethod.entries.filterNot { it == PayMethod.UNSPECIFIED }.map {
                JSONObject().put("code", it.name).put("name", it.label)
            })
            val input = JSONObject().put("text", text).put("today", today.toString())
                .put("categories", categories).put("payMethods", payMethods)
            return requestBody(config, INSTRUCTIONS, input.toString(), 2048)
        }

        private fun requestBody(config: CloudVoiceConfig, instructions: String, input: String, limit: Int): JSONObject {
            val user = JSONObject().put("role", "user").put("content", input)
            val body = JSONObject()
            when (config.protocol) {
                CloudVoiceProtocol.DEEPSEEK, CloudVoiceProtocol.CHAT -> {
                    body.put("model", config.model).put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", instructions))
                        .put(user))
                    val host = config.endpoint().host
                    body.put(if (host.equals("api.openai.com", true)) "max_completion_tokens" else "max_tokens", limit)
                    if (config.protocol == CloudVoiceProtocol.DEEPSEEK) {
                        body.put("thinking", JSONObject().put("type", "disabled"))
                            .put("response_format", JSONObject().put("type", "json_object"))
                    }
                }
                CloudVoiceProtocol.RESPONSES -> body.put("model", config.model).put("instructions", instructions)
                    .put("input", input).put("max_output_tokens", limit).put("store", false)
                CloudVoiceProtocol.ANTHROPIC -> body.put("model", config.model).put("system", instructions)
                    .put("messages", JSONArray().put(user)).put("max_tokens", limit)
                CloudVoiceProtocol.GEMINI -> {
                    val system = JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instructions)))
                    val content = JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", input)))
                    body.put("systemInstruction", system).put("contents", JSONArray().put(content))
                        .put("generationConfig", JSONObject().put("maxOutputTokens", limit).put("responseMimeType", "application/json"))
                }
            }
            return body
        }

        private fun readReply(response: JSONObject, protocol: CloudVoiceProtocol): ModelReply {
            fun incomplete(): Nothing = throw CloudVoiceException("模型返回不完整，请检查模型能力或重试")
            val text = when (protocol) {
                CloudVoiceProtocol.DEEPSEEK, CloudVoiceProtocol.CHAT -> {
                    val choice = response.getJSONArray("choices").getJSONObject(0)
                    if (choice.optString("finish_reason", "stop") != "stop") incomplete()
                    choice.getJSONObject("message").getString("content")
                }
                CloudVoiceProtocol.RESPONSES -> {
                    if (response.optString("status") != "completed") incomplete()
                    response.getJSONArray("output").objects().filter { it.optString("type") == "message" }
                        .flatMap { it.getJSONArray("content").objects() }.filter { it.optString("type") == "output_text" }
                        .joinToString("") { it.string("text") }
                }
                CloudVoiceProtocol.ANTHROPIC -> {
                    if (response.optString("stop_reason") !in listOf("end_turn", "stop_sequence")) incomplete()
                    response.getJSONArray("content").objects().filter { it.optString("type") == "text" }
                        .joinToString("") { it.string("text") }
                }
                CloudVoiceProtocol.GEMINI -> {
                    val candidate = response.getJSONArray("candidates").getJSONObject(0)
                    if (candidate.optString("finishReason", "STOP") != "STOP") incomplete()
                    candidate.getJSONObject("content").getJSONArray("parts").objects()
                        .filterNot { it.optBoolean("thought") }.joinToString("") { it.string("text") }
                }
            }
            if (text.isBlank()) throw CloudVoiceException("模型未返回可用文字")
            val model = response.string(if (protocol == CloudVoiceProtocol.GEMINI) "modelVersion" else "model")
                .filterNot { it.isISOControl() }.trim().take(160).ifEmpty { null }
            return ModelReply(text, model)
        }

        fun decode(response: String, local: List<EntryDraft>, transcript: String = ""): List<EntryDraft> =
            decodeEntries(readReply(JSONObject(response), CloudVoiceProtocol.DEEPSEEK).text, local, transcript)

        private fun decodeEntries(text: String, local: List<EntryDraft>, transcript: String): List<EntryDraft> {
            val entries = jsonObject(text).getJSONArray("entries")
            if (entries.length() > LedgerEntryService.MAX_DRAFTS) throw IOException("Too many cloud drafts")
            // Only prefill amounts supported by the local reading of the actual transcript.
            val amounts = local.mapNotNull { Money.parseToCents(it.amount)?.takeIf { cents -> cents > 0 } }.toMutableList()
            val singleEntrySource = normalizeVoiceText(transcript).takeIf { entries.length() == 1 }
            return List(entries.length()) { index ->
                val item = entries.getJSONObject(index)
                val kind = EntryKind.entries.firstOrNull { it.name == item.string("kind") }
                val category = DailyCategory.entries.firstOrNull { it.kind == kind && it.name == item.string("category") }
                val candidate = item.string("amount").trim()
                val cents = candidate.takeIf(::validAmount)?.let(Money::parseToCents)
                val amount = if (cents != null && amounts.remove(cents)) candidate else ""
                val date = item.string("date").trim().takeIf { parseEntryDate(it) != null }.orEmpty()
                val comparable = if (amount.isEmpty()) emptyList() else local.filter { Money.parseToCents(it.amount) == cents }
                val matching = comparable.singleOrNull() ?: comparable.singleOrNull { it.kind == kind && it.category == category }
                val paymentSource = matching?.note.orEmpty()
                val payment = matching?.payMethod?.takeUnless { it == PayMethod.UNSPECIFIED }
                    ?: verifiedPayment(item, singleEntrySource ?: paymentSource)
                // A model may invent a timestamp. Use hours/minutes spoken for this draft.
                val time = matching?.time?.takeIf { it.isNotBlank() }
                    ?: singleEntrySource?.let(SpokenRecordTime::time).orEmpty()
                EntryDraft(kind, amount, date, category, item.string("note").trim().take(1000), payment, time)
            }
        }

        private fun verifiedPayment(item: JSONObject, source: String): PayMethod {
            val code = item.string("payMethod").trim()
            val method = PayMethod.entries.firstOrNull { it.name == code && it != PayMethod.UNSPECIFIED }
                ?: return PayMethod.UNSPECIFIED
            val evidence = normalizeVoiceText(item.string("paymentEvidence")).trim()
            return method.takeIf {
                evidence.length in 1..100 && source.contains(evidence, ignoreCase = true) &&
                    SpokenPayment.identify(evidence) == method && SpokenPayment.identify(source) == method
            } ?: PayMethod.UNSPECIFIED
        }

        private val jsonFence = Regex("(?is)^```(?:json)?\\s*(.*?)\\s*```$")
        private fun jsonObject(text: String): JSONObject {
            val trimmed = text.trim()
            return JSONObject(jsonFence.matchEntire(trimmed)?.groupValues?.get(1) ?: trimmed)
        }

        private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
        private fun JSONObject.string(key: String): String = (opt(key) as? String).orEmpty()
    }
}
