package com.zhuangxiu.ledger.voice

import android.content.Context
import com.zhuangxiu.ledger.update.TokenCipher
import com.zhuangxiu.ledger.update.deviceTokenKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CloudVoiceStatus(
    val configured: Boolean,
    val enabled: Boolean,
    val config: CloudVoiceConfig,
    val reportedModel: String?,
)

@Singleton
internal class CloudVoiceSettings @Inject constructor(@param:ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("cloud_voice", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(readStatus())
    val status = _status.asStateFlow()

    @Synchronized fun active(): CloudVoiceAccess? {
        val current = readStatus()
        if (!current.enabled) return null
        return CloudVoiceAccess(current.config.validated(), savedKey())
    }

    @Synchronized fun access(config: CloudVoiceConfig, input: String): CloudVoiceAccess {
        val normalized = config.validated()
        val current = readStatus()
        val key = input.trim().ifEmpty {
            require(current.configured && normalized.protocol == current.config.protocol &&
                normalized.baseUrl == current.config.baseUrl) { "请为此服务地址输入 API Key" }
            savedKey()
        }
        require(key.length in 1..512 && key.all { it in '!'..'~' }) { "请粘贴完整的 API Key" }
        return CloudVoiceAccess(normalized, key)
    }

    private fun savedKey(): String {
        return try {
            TokenCipher.decrypt(requireNotNull(preferences.getString(KEY, null)), deviceTokenKey(KEY_ALIAS))
        } catch (_: Exception) {
            throw CloudVoiceException("API Key 无法读取，请重新配置")
        }
    }

    @Synchronized fun saveAndEnable(access: CloudVoiceAccess, reportedModel: String?) {
        val config = access.config
        val encrypted = TokenCipher.encrypt(access.key, deviceTokenKey(KEY_ALIAS))
        persist(preferences.edit().putString(KEY, encrypted).putString(PROTOCOL, config.protocol.name)
            .putString(BASE, config.baseUrl).putString(MODEL, config.model).putString(REPORTED, reportedModel)
            .putBoolean(ENABLED, true).commit())
    }

    @Synchronized fun setEnabled(enabled: Boolean) {
        require(!enabled || readStatus().configured) { "请先配置 DeepSeek API Key" }
        persist(preferences.edit().putBoolean(ENABLED, enabled).commit())
    }

    @Synchronized fun clear() {
        persist(preferences.edit().remove(KEY).remove(ENABLED).remove(REPORTED).commit())
    }

    private fun persist(success: Boolean) {
        _status.value = readStatus()
        if (!success) throw IOException("无法保存云端设置，请重试")
    }

    private fun readStatus(): CloudVoiceStatus {
        val configured = preferences.contains(KEY)
        val protocol = CloudVoiceProtocol.entries.firstOrNull { it.name == preferences.getString(PROTOCOL, null) }
            ?: CloudVoiceProtocol.DEEPSEEK
        val config = CloudVoiceConfig(protocol, preferences.getString(BASE, null) ?: protocol.base,
            preferences.getString(MODEL, null) ?: protocol.example)
        return CloudVoiceStatus(configured, configured && preferences.getBoolean(ENABLED, false),
            config, preferences.getString(REPORTED, null))
    }

    private companion object {
        const val KEY = "encrypted_key"
        const val ENABLED = "enabled"
        const val PROTOCOL = "protocol"
        const val BASE = "base_url"
        const val MODEL = "model"
        const val REPORTED = "reported_model"
        const val KEY_ALIAS = "qingzhang-deepseek-voice"
    }
}
