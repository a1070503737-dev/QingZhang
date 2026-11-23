package com.zhuangxiu.ledger.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhuangxiu.ledger.ui.components.LedgerIconButton

@Composable
internal fun DeviceSpeechButton(enabled: Boolean, onText: (String) -> Unit, onError: (String?) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val result by rememberUpdatedState(onText)
    val error by rememberUpdatedState(onError)
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var startAfterPermission by remember { mutableStateOf(false) }

    fun release() {
        val engine = recognizer
        recognizer = null
        finishing = false
        engine?.destroy()
    }

    fun start() {
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            error("这台设备未提供离线语音识别，可以手动输入。")
            return
        }
        if (recognizer != null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        try {
            val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = engine
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { if (recognizer === engine) finishing = true }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onError(errorCode: Int) {
                    if (recognizer !== engine) return
                    release()
                    error(if (errorCode == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                        errorCode == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                    ) "设备缺少中文离线语音模型，可以手动输入。"
                    else "没能识别这段语音，可以重试或手动输入。")
                }
                override fun onResults(results: Bundle?) {
                    if (recognizer !== engine) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                    release()
                    if (text == null) error("没有听清，可以手动输入。") else result(text)
                }
            })
            error(null)
            engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        } catch (_: RuntimeException) {
            release()
            error("无法启动离线语音识别，可以手动输入。")
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) error("未授予麦克风权限，可以手动输入。")
        else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        else startAfterPermission = true
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) release()
            if (event == Lifecycle.Event.ON_RESUME && startAfterPermission) {
                startAfterPermission = false
                start()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            release()
        }
    }

    LedgerIconButton(
        enabled = enabled && !finishing,
        onClick = {
            val engine = recognizer
            if (engine != null) {
                finishing = true
                engine.stopListening()
            } else if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                error("这台设备未提供离线语音识别，可以手动输入。")
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                start()
            } else permission.launch(Manifest.permission.RECORD_AUDIO)
        },
    ) {
        val listening = recognizer != null
        Icon(if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic, if (listening) "结束录音" else "开始中文语音识别")
    }
    if (recognizer != null) {
        Text(if (finishing) "正在识别" else "正在听…", style = MaterialTheme.typography.labelSmall)
    }
}
