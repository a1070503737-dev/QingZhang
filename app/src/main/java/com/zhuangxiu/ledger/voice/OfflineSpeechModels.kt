package com.zhuangxiu.ledger.voice

import android.content.Context
import com.zhuangxiu.ledger.update.VerifiedDownload
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class SpeechModelOperation { DOWNLOADING, REMOVING }

internal data class SpeechModelState(
    val installed: Boolean,
    val senseVoiceActive: Boolean,
    val bytes: Long,
    val operation: SpeechModelOperation? = null,
    val error: String? = null,
)

internal data class SenseVoiceFiles(val model: File, val tokens: File)

@Singleton
internal class OfflineSpeechModels @Inject constructor(@param:ApplicationContext context: Context) {
    private val directory = File(context.noBackupFilesDir, "offline-speech/${SenseVoiceSource.DIRECTORY}")
    private val preferences = context.getSharedPreferences("offline_speech", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val access = Mutex()
    private var task: Job? = null
    private var verified = false
    private val _state = MutableStateFlow(snapshot())
    val state = _state.asStateFlow()

    val wantsSenseVoice get() = preferences.getBoolean(SENSE_VOICE, false)

    @Synchronized
    fun select(useSenseVoice: Boolean) {
        if (task != null || useSenseVoice && !installed()) return
        preferences.edit().putBoolean(SENSE_VOICE, useSenseVoice).apply()
        _state.value = snapshot()
    }

    @Synchronized
    fun download() {
        if (task != null || installed()) return
        manage(SpeechModelOperation.DOWNLOADING) { ensureActive ->
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw SpeechModelDownloadException("无法创建模型目录，请检查存储空间")
            }
            val remaining = SenseVoiceSource.totalBytes - installedBytes()
            if (directory.usableSpace < remaining + 8 * 1024 * 1024) {
                throw SpeechModelDownloadException("存储空间不足，请释放空间后继续")
            }
            val downloader = SenseVoiceDownload()
            var finished = 0L
            var shown = _state.value.bytes
            SenseVoiceSource.files.forEach { file ->
                downloader.fetch(file, directory, ensureActive) { count ->
                    val total = finished + count
                    if (total < shown || total - shown >= 512 * 1024 || total == SenseVoiceSource.totalBytes) {
                        _state.value = _state.value.copy(bytes = total)
                        shown = total
                    }
                }
                finished += file.bytes
            }
            ensureActive()
            verified = true
            preferences.edit().putBoolean(SENSE_VOICE, true).apply()
        }
    }

    @Synchronized
    fun pause() {
        if (_state.value.operation == SpeechModelOperation.DOWNLOADING) task?.cancel()
    }

    @Synchronized
    fun remove() {
        if (task != null) return
        manage(SpeechModelOperation.REMOVING) { ensureActive ->
            preferences.edit().putBoolean(SENSE_VOICE, false).apply()
            verified = false
            for (file in SenseVoiceSource.files.flatMap { listOf(it.target(directory), it.partial(directory)) }) {
                ensureActive()
                if (file.exists() && !file.delete()) throw SpeechModelDownloadException("无法删除模型文件，请重试")
            }
        }
    }

    /** The files remain available until the native recognizer has opened both. */
    suspend fun <T> withSenseVoice(open: (SenseVoiceFiles) -> T): T? = access.withLock {
        if (!wantsSenseVoice || !installed()) return@withLock null
        if (!verified) {
            val damaged = withContext(Dispatchers.IO) {
                val job = currentCoroutineContext()
                SenseVoiceSource.files.firstOrNull {
                    VerifiedDownload.sha256(it.target(directory), job::ensureActive) != it.sha256
                }
            }
            if (damaged != null) {
                damaged.target(directory).delete()
                preferences.edit().putBoolean(SENSE_VOICE, false).apply()
                _state.value = snapshot(error = "模型文件已损坏，请重新下载")
                return@withLock null
            }
            verified = true
        }
        open(SenseVoiceFiles(SenseVoiceSource.model.target(directory), SenseVoiceSource.tokens.target(directory)))
    }

    private fun manage(operation: SpeechModelOperation, block: (() -> Unit) -> Unit) {
        _state.value = snapshot(operation = operation)
        var failure: String? = null
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val context = currentCoroutineContext()
            try {
                access.withLock { block(context::ensureActive) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SpeechModelDownloadException) {
                failure = error.message
            } catch (_: Exception) {
                failure = "操作未完成，请检查网络或存储空间后重试"
            }
        }
        task = job
        job.invokeOnCompletion {
            synchronized(this) {
                task = null
                _state.value = snapshot(error = failure)
            }
        }
        job.start()
    }

    private fun installed() = SenseVoiceSource.files.all { it.target(directory).length() == it.bytes }

    private fun installedBytes() = SenseVoiceSource.files.sumOf {
        maxOf(it.target(directory).length(), it.partial(directory).length()).coerceAtMost(it.bytes)
    }

    private fun snapshot(operation: SpeechModelOperation? = null, error: String? = null): SpeechModelState {
        val ready = installed()
        return SpeechModelState(ready, ready && wantsSenseVoice, installedBytes(), operation, error)
    }

    private companion object {
        const val SENSE_VOICE = "sensevoice"
    }
}
