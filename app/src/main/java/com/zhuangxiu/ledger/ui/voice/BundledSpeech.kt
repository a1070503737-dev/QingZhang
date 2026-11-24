package com.zhuangxiu.ledger.ui.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.zhuangxiu.ledger.domain.compactChineseSpeech
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

private const val MODEL_NAME = "vosk-model-small-cn-0.22"
private const val SAMPLE_RATE = 16_000
private const val SESSION_LIMIT_MS = 45_000L

internal object BundledSpeech {
    private val modelLock = Mutex()

    private suspend fun modelDirectory(context: Context): File = modelLock.withLock {
        val directory = File(context.noBackupFilesDir, MODEL_NAME)
        if (directory.isDirectory) return@withLock directory

        val staging = File(directory.parentFile, "$MODEL_NAME.tmp")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("无法准备语音模型目录")
        try {
            ZipInputStream(context.assets.open("voice/$MODEL_NAME.zip")).use { archive ->
                val root = staging.canonicalFile
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = archive.nextEntry ?: break
                    if (!entry.name.startsWith("$MODEL_NAME/")) throw IOException("语音模型文件结构无效")
                    val relative = entry.name.removePrefix("$MODEL_NAME/")
                    if (relative.isEmpty()) continue
                    val target = File(root, relative).canonicalFile
                    if (!target.toPath().startsWith(root.toPath()) || target == root) {
                        throw IOException("语音模型文件路径无效")
                    }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { archive.copyTo(it) }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (!File(staging, "am/final.mdl").isFile || !staging.renameTo(directory)) {
                throw IOException("无法保存语音模型")
            }
            directory
        } finally {
            staging.deleteRecursively()
        }
    }

    suspend fun listen(context: Context, stop: AtomicBoolean, onRecording: () -> Unit): String = withContext(Dispatchers.IO) {
        val directory = modelDirectory(context)
        currentCoroutineContext().ensureActive()
        Model(directory.absolutePath).use { model ->
            Recognizer(model, SAMPLE_RATE.toFloat()).use { recognizer ->
                record(recognizer, stop, onRecording)
            }
        }
    }

    @SuppressLint("MissingPermission") // The voice button requests RECORD_AUDIO before starting.
    private suspend fun record(recognizer: Recognizer, stop: AtomicBoolean, onRecording: () -> Unit): String {
        currentCoroutineContext().ensureActive()
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, channel, encoding)
        if (minimum <= 0) throw IOException("设备无法录制所需格式的音频")

        val samples = ShortArray(1024)
        val microphone = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            channel,
            encoding,
            maxOf(minimum, samples.size * Short.SIZE_BYTES * 2),
        )
        try {
            if (microphone.state != AudioRecord.STATE_INITIALIZED) throw IOException("麦克风初始化失败")
            microphone.startRecording()
            if (microphone.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IOException("麦克风无法录音")
            withContext(Dispatchers.Main.immediate) { onRecording() }

            val segments = mutableListOf<String>()
            val started = SystemClock.elapsedRealtime()
            while (!stop.get() && SystemClock.elapsedRealtime() - started < SESSION_LIMIT_MS) {
                currentCoroutineContext().ensureActive()
                val count = microphone.read(samples, 0, samples.size)
                if (count < 0) throw IOException("读取麦克风失败")
                if (count > 0 && recognizer.acceptWaveForm(samples, count)) {
                    readText(recognizer.result)?.let(segments::add)
                }
            }
            currentCoroutineContext().ensureActive()
            microphone.stop()
            readText(recognizer.finalResult)?.let(segments::add)
            return segments.joinToString("，")
        } finally {
            try {
                if (microphone.recordingState == AudioRecord.RECORDSTATE_RECORDING) microphone.stop()
            } finally {
                microphone.release()
            }
        }
    }

    private fun readText(json: String): String? =
        compactChineseSpeech(JSONObject(json).optString("text")).trim().takeIf { it.isNotEmpty() }
}
