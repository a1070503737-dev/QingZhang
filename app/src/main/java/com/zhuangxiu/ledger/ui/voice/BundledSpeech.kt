package com.zhuangxiu.ledger.ui.voice

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineZipformerCtcModelConfig
import com.zhuangxiu.ledger.domain.compactChineseSpeech
import com.zhuangxiu.ledger.voice.OfflineSpeechModels
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val SAMPLE_RATE = 16_000
private const val MAX_RECORDING_SECONDS = 45

internal object BundledSpeech {
    suspend fun listen(
        context: Context,
        models: OfflineSpeechModels,
        confirmed: Deferred<Unit>,
        onRecording: () -> Unit,
        onDecoding: () -> Unit,
        onFallback: (String) -> Unit,
    ): String = coroutineScope {
        val audio = CompletableDeferred<FloatArray>()
        val recognition = async(Dispatchers.Default, start = CoroutineStart.LAZY) {
            val recognizer = createRecognizer(context, models, onFallback)
            try {
                decode(recognizer, audio.await())
            } finally {
                recognizer.release()
            }
        }
        val samples = withContext(Dispatchers.IO) {
            record(confirmed) {
                onRecording()
                recognition.start()
            }
        }
        // The recording limit stops the microphone, but only a release inside the button accepts the audio.
        confirmed.await()
        if (samples.size < SAMPLE_RATE / 5) {
            recognition.cancel()
            return@coroutineScope ""
        }
        withContext(Dispatchers.Main.immediate) { onDecoding() }
        audio.complete(samples)
        recognition.await()
    }

    @SuppressLint("MissingPermission") // HomeVoiceEntry requests RECORD_AUDIO before starting.
    private suspend fun record(confirmed: Deferred<Unit>, onRecording: () -> Unit): FloatArray {
        currentCoroutineContext().ensureActive()
        if (confirmed.isCompleted) return FloatArray(0)

        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, channel, encoding)
        if (minimum <= 0) throw IOException("设备无法录制所需格式的音频")
        val buffer = ShortArray(1024)
        val samples = FloatArray(SAMPLE_RATE * MAX_RECORDING_SECONDS)
        var size = 0
        val microphone = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            channel,
            encoding,
            maxOf(minimum, buffer.size * Short.SIZE_BYTES * 2),
        )
        try {
            if (microphone.state != AudioRecord.STATE_INITIALIZED) throw IOException("麦克风初始化失败")
            microphone.startRecording()
            if (microphone.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IOException("麦克风无法录音")
            withContext(Dispatchers.Main.immediate) { onRecording() }
            val deadline = SystemClock.elapsedRealtime() + MAX_RECORDING_SECONDS * 1000L
            while (!confirmed.isCompleted && size < samples.size && SystemClock.elapsedRealtime() < deadline) {
                currentCoroutineContext().ensureActive()
                val count = microphone.read(buffer, 0, minOf(buffer.size, samples.size - size))
                if (count < 0) throw IOException("读取麦克风失败")
                for (index in 0 until count) samples[size + index] = buffer[index] / 32768f
                size += count
            }
        } finally {
            try {
                if (microphone.recordingState == AudioRecord.RECORDSTATE_RECORDING) microphone.stop()
            } finally {
                microphone.release()
            }
        }
        return samples.copyOf(size)
    }

    private suspend fun createRecognizer(
        context: Context,
        models: OfflineSpeechModels,
        onFallback: (String) -> Unit,
    ): OfflineRecognizer {
        if (models.wantsSenseVoice) {
            if (!hasMemoryForSenseVoice(context)) {
                withContext(Dispatchers.Main.immediate) { onFallback("可用内存较少，本次使用内置模型") }
            } else {
                val recognizer = try {
                    models.withSenseVoice { files ->
                        OfflineRecognizer(assetManager = null, config = OfflineRecognizerConfig(
                            modelConfig = OfflineModelConfig(
                                senseVoice = OfflineSenseVoiceModelConfig(
                                    model = files.model.absolutePath,
                                    language = "auto",
                                    useInverseTextNormalization = true,
                                ),
                                tokens = files.tokens.absolutePath,
                                numThreads = 2,
                            ),
                        ))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } catch (_: LinkageError) {
                    null
                }
                if (recognizer != null) return recognizer
                withContext(Dispatchers.Main.immediate) { onFallback("SenseVoice 暂不可用，本次使用内置模型") }
            }
        }
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                zipformerCtc = OfflineZipformerCtcModelConfig(model = "voice/model.int8.onnx"),
                tokens = "voice/tokens.txt",
                numThreads = 2,
            ),
        )
        return OfflineRecognizer(assetManager = context.assets, config = config)
    }

    private fun hasMemoryForSenseVoice(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return true
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        // Long recordings can use over 400 MiB during inference.
        return !info.lowMemory && info.availMem >= 640L * 1024 * 1024
    }

    private fun decode(recognizer: OfflineRecognizer, samples: FloatArray): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            return compactChineseSpeech(recognizer.getResult(stream).text).trim()
        } finally {
            stream.release()
        }
    }
}
