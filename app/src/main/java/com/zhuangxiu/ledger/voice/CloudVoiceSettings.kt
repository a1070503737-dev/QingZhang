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

internal data class CloudVoiceStatus(val configured: Boolean, val enabled: Boolean)

@Singleton
internal class CloudVoiceSettings @Inject constructor(@param:ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("cloud_voice", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(readStatus())
    val status = _status.asStateFlow()

    @Synchronized fun activeKey(): String? {
        if (!readStatus().enabled) return null
        return try {
            TokenCipher.decrypt(requireNotNull(preferences.getString(KEY, null)), deviceTokenKey(KEY_ALIAS))
        } catch (_: Exception) {
            throw CloudVoiceException("API Key 无法读取，请重新配置")
        }
    }

    @Synchronized fun saveAndEnable(input: String) {
        val key = input.trim()
        require(key.matches(Regex("[A-Za-z0-9_-]{16,512}"))) { "请粘贴完整的 DeepSeek API Key" }
        val encrypted = TokenCipher.encrypt(key, deviceTokenKey(KEY_ALIAS))
        persist(preferences.edit().putString(KEY, encrypted).putBoolean(ENABLED, true).commit())
    }

    @Synchronized fun setEnabled(enabled: Boolean) {
        require(!enabled || readStatus().configured) { "请先配置 DeepSeek API Key" }
        persist(preferences.edit().putBoolean(ENABLED, enabled).commit())
    }

    @Synchronized fun clear() {
        persist(preferences.edit().remove(KEY).remove(ENABLED).commit())
    }

    private fun persist(success: Boolean) {
        _status.value = readStatus()
        if (!success) throw IOException("无法保存云端设置，请重试")
    }

    private fun readStatus(): CloudVoiceStatus {
        val configured = preferences.contains(KEY)
        return CloudVoiceStatus(configured, configured && preferences.getBoolean(ENABLED, false))
    }

    private companion object {
        const val KEY = "encrypted_key"
        const val ENABLED = "enabled"
        const val KEY_ALIAS = "qingzhang-deepseek-voice"
    }
}
