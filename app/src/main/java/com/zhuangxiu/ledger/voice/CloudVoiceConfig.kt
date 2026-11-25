package com.zhuangxiu.ledger.voice

import java.net.URI

internal enum class CloudVoiceProtocol(val label: String, val base: String, val example: String) {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-flash"),
    CHAT("OpenAI 兼容 Chat Completions", "https://api.openai.com/v1", ""),
    RESPONSES("OpenAI Responses", "https://api.openai.com/v1", ""),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", ""),
    ANTHROPIC("Anthropic Messages", "https://api.anthropic.com/v1", ""),
}

internal data class CloudVoiceConfig(
    val protocol: CloudVoiceProtocol = CloudVoiceProtocol.DEEPSEEK,
    val baseUrl: String = protocol.base,
    val model: String = protocol.example,
) {
    fun validated(): CloudVoiceConfig {
        val base = baseUrl.trim().trimEnd('/')
        val uri = runCatching { URI(base) }.getOrNull()
        require(uri != null && uri.scheme.equals("https", ignoreCase = true) && uri.host != null &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.port in -1..65535 && uri.port != 0 && uri.normalize() == uri
        ) { "请填写完整的 HTTPS 服务地址，网址中不要包含密钥或查询参数" }
        val id = model.trim().let { if (protocol == CloudVoiceProtocol.GEMINI) it.removePrefix("models/") else it }
        val pattern = if (protocol == CloudVoiceProtocol.GEMINI) "[A-Za-z0-9][A-Za-z0-9._-]{0,159}"
        else "[A-Za-z0-9][A-Za-z0-9._:/@+\\-]{0,159}"
        require(id.matches(Regex(pattern))) { "请填写服务商提供的模型 ID" }
        return copy(baseUrl = base, model = id)
    }

    fun endpoint(): URI {
        val route = when (protocol) {
            CloudVoiceProtocol.DEEPSEEK, CloudVoiceProtocol.CHAT -> "/chat/completions"
            CloudVoiceProtocol.RESPONSES -> "/responses"
            CloudVoiceProtocol.GEMINI -> "/models/$model:generateContent"
            CloudVoiceProtocol.ANTHROPIC -> "/messages"
        }
        return URI(if (baseUrl.endsWith(route)) baseUrl else baseUrl + route)
    }

    val provider: String get() {
        val host = URI(baseUrl).host.lowercase()
        return when (host) {
            "api.deepseek.com" -> "DeepSeek"
            "api.openai.com" -> "OpenAI"
            "generativelanguage.googleapis.com" -> "Google Gemini"
            "api.anthropic.com" -> "Anthropic"
            "openrouter.ai" -> "OpenRouter"
            "dashscope.aliyuncs.com", "dashscope-intl.aliyuncs.com" -> "阿里云百炼"
            else -> host
        }
    }

    fun modelLabel(reportedModel: String?): String =
        "$provider · ${reportedModel ?: "$model（配置模型）"}"
}

internal data class CloudVoiceAccess(val config: CloudVoiceConfig, val key: String)
