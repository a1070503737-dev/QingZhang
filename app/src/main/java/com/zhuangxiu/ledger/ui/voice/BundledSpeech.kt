package com.zhuangxiu.ledger.ui.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineZipformerCtcModelConfig
import com.zhuangxiu.ledger.domain.compactChineseSpeech
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
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
        stop: AtomicBoolean,
        onRecording: () -> Unit,
        onDecoding: () -> Unit,
    ): String = coroutineScope {
        val audio = CompletableDeferred<FloatArray>()
        val recognition = async(Dispatchers.Default, start = CoroutineStart.LAZY) {
            val recognizer = createRecognizer(context)
            try {
                decode(recognizer, audio.await())
            } finally {
                recognizer.release()
            }
        }
        val samples = withContext(Dispatchers.IO) {
            record(stop) {
                onRecording()
                recognition.start()
            }
        }
        if (samples.size < SAMPLE_RATE / 5) {
            recognition.cancel()
            return@coroutineScope ""
        }
        withContext(Dispatchers.Main.immediate) { onDecoding() }
        audio.complete(samples)
        recognition.await()
    }

    @SuppressLint("MissingPermission") // HomeVoiceEntry requests RECORD_AUDIO before starting.
    private suspend fun record(stop: AtomicBoolean, onRecording: () -> Unit): FloatArray {
        currentCoroutineContext().ensureActive()
        if (stop.get()) return FloatArray(0)

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
            while (!stop.get() && size < samples.size && SystemClock.elapsedRealtime() < deadline) {
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

    private fun createRecognizer(context: Context): OfflineRecognizer {
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                zipformerCtc = OfflineZipformerCtcModelConfig(model = "voice/model.int8.onnx"),
                tokens = "voice/tokens.txt",
                numThreads = 2,
            ),
        )
        return OfflineRecognizer(assetManager = context.assets, config = config)
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
