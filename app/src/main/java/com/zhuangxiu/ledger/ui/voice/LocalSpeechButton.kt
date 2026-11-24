package com.zhuangxiu.ledger.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhuangxiu.ledger.ui.components.LedgerButton
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class SpeechState { Idle, Preparing, Recording, Finishing }

@Composable
internal fun LocalSpeechButton(enabled: Boolean, onText: (String) -> Unit, onError: (String?) -> Unit) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onText)
    val error by rememberUpdatedState(onError)
    var state by remember { mutableStateOf(SpeechState.Idle) }
    var task by remember { mutableStateOf<Job?>(null) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var openedFromHome by rememberSaveable { mutableStateOf(true) }
    val stop = remember { AtomicBoolean() }

    fun start() {
        if (task != null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        stop.set(false)
        error(null)
        state = SpeechState.Preparing
        task = scope.launch {
            try {
                val text = BundledSpeech.listen(context, stop) { state = SpeechState.Recording }
                if (text.isBlank()) error("没有听清，可以再说一次或直接输入文字。") else result(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                error("轻账无法访问麦克风，可以检查权限或直接输入。")
            } catch (_: LinkageError) {
                error("这台设备无法加载轻账的语音引擎，可以直接输入。")
            } catch (_: Exception) {
                error("本地识别未完成，可以重试或直接输入。")
            } finally {
                state = SpeechState.Idle
                task = null
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) error("未授予麦克风权限，可以直接输入文字。")
        else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        else startAfterPermission = true
    }
    fun startWithPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(lifecycle) {
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        if (openedFromHome) {
            openedFromHome = false
            startWithPermission()
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when {
                event == Lifecycle.Event.ON_STOP -> task?.cancel()
                event == Lifecycle.Event.ON_RESUME && startAfterPermission -> {
                    startAfterPermission = false
                    start()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            task?.cancel()
        }
    }

    LedgerButton(
        enabled = enabled && (state == SpeechState.Idle || state == SpeechState.Recording),
        onClick = {
            if (state == SpeechState.Recording) {
                stop.set(true)
                state = SpeechState.Finishing
            } else if (state == SpeechState.Idle) startWithPermission()
        },
    ) {
        Icon(if (state == SpeechState.Recording) Icons.Outlined.Stop else Icons.Outlined.Mic, null)
        Spacer(Modifier.width(8.dp))
        Text(when (state) {
            SpeechState.Idle -> "开始录音"
            SpeechState.Preparing -> "正在加载本地模型…"
            SpeechState.Recording -> "结束录音"
            SpeechState.Finishing -> "正在识别…"
        })
    }
}
