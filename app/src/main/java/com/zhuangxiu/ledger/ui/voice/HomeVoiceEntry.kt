package com.zhuangxiu.ledger.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuangxiu.ledger.data.db.BookEntity
import com.zhuangxiu.ledger.ui.components.LedgerPanel
import com.zhuangxiu.ledger.ui.components.SurfaceTone
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal enum class VoiceCapture { Idle, Starting, Recording, Recognizing }

@Composable
internal fun HomeVoiceEntry(
    book: BookEntity?,
    canRecord: Boolean,
    pickBook: () -> Unit,
    pickImageBook: () -> Unit,
    showImageAfterBookChoice: Boolean,
    onImageBookChoiceHandled: () -> Unit,
    model: VoiceEntryViewModel = hiltViewModel(),
    content: @Composable ((@Composable () -> Unit)?, (@Composable () -> Unit)?) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val review by model.state.collectAsStateWithLifecycle()
    var capture by remember { mutableStateOf(VoiceCapture.Idle) }
    var task by remember { mutableStateOf<Job?>(null) }
    val stop = remember { AtomicBoolean() }
    val cancelSlide = remember { AtomicBoolean() }
    val currentBook by rememberUpdatedState(book)
    val chooseBook by rememberUpdatedState(pickBook)
    fun message(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message(if (granted) "已授权，长按「ai 记账」开始录音" else "麦克风未授权，轻点可输入文字")
    }

    fun begin() {
        if (task != null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val target = currentBook
        if (target == null) {
            chooseBook()
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        stop.set(false)
        cancelSlide.set(false)
        capture = VoiceCapture.Starting
        task = scope.launch {
            try {
                val text = BundledSpeech.listen(
                    context, model.speechModels, stop,
                    cancelled = cancelSlide,
                    onRecording = {
                        capture = VoiceCapture.Recording
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDecoding = { capture = VoiceCapture.Recognizing },
                    onFallback = ::message,
                )
                if (text.isBlank()) message("没有听清，请长按说完再松手")
                else model.open(target, text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                message("轻账无法访问麦克风，轻点可输入文字")
            } catch (_: LinkageError) {
                message("这台设备无法加载离线语音引擎，轻点可输入文字")
            } catch (_: Exception) {
                message("离线识别未完成，可以重试或输入文字")
            } finally {
                task = null
                capture = VoiceCapture.Idle
            }
        }
    }

    fun finish(released: Boolean) {
        val activeTask = task
        if (activeTask == null || !activeTask.isActive || capture == VoiceCapture.Recognizing) return
        if (released) {
            stop.set(true)
        } else {
            cancelSlide.set(true)
            activeTask.cancel()
            stop.set(true)
            message("已取消语音识别")
        }
    }

    fun openManual() {
        if (task == null) {
            val target = currentBook
            if (target != null) model.open(target) else chooseBook()
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) task?.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            task?.cancel()
        }
    }

    if (canRecord) {
        content(
            {
                HoldToTalkButton(
                    capture = capture,
                    onHold = ::begin,
                    onEnd = ::finish,
                    onTap = ::openManual,
                    onCancelPreviewChange = cancelSlide::set,
                )
            },
            {
                ReceiptImageAction(
                    book, pickImageBook, showImageAfterBookChoice, onImageBookChoiceHandled,
                    enabled = capture == VoiceCapture.Idle && review.bookName == null,
                    onImage = model::openImage,
                )
            },
        )
    } else content(null, null)

    if (review.bookName != null) VoiceReviewSheet(review, model)
}

private enum class InitialPressResult { Tap, Cancelled }

internal fun isSlideCancelling(
    position: Offset,
    size: IntSize,
    currentlyCancelling: Boolean,
    margin: Float,
): Boolean {
    val padding = if (currentlyCancelling) 0f else margin
    return position.x < -padding ||
        position.x > size.width + padding ||
        position.y < -padding ||
        position.y > size.height + padding
}

@Composable
internal fun HoldToTalkButton(
    capture: VoiceCapture,
    onHold: () -> Unit,
    onEnd: (Boolean) -> Unit,
    onTap: () -> Unit,
    onCancelPreviewChange: (Boolean) -> Unit = {},
) {
    val start by rememberUpdatedState(onHold)
    val end by rememberUpdatedState(onEnd)
    val tap by rememberUpdatedState(onTap)
    val cancelPreview by rememberUpdatedState(onCancelPreviewChange)
    val currentCapture by rememberUpdatedState(capture)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val colors = MaterialTheme.colorScheme
    var pressed by remember { mutableStateOf(false) }
    var cancelPending by remember { mutableStateOf(false) }
    val active = pressed && capture != VoiceCapture.Recognizing
    val cancelling = cancelPending && (capture == VoiceCapture.Starting || capture == VoiceCapture.Recording)
    val containerColor = when {
        cancelling -> colors.errorContainer
        active -> colors.secondaryContainer
        else -> colors.primaryContainer
    }
    val contentColor = when {
        cancelling -> colors.onErrorContainer
        active -> colors.onSecondaryContainer
        else -> colors.onPrimaryContainer
    }
    val scale by animateFloatAsState(
        targetValue = when {
            !active -> 1f
            cancelling -> 0.98f
            capture == VoiceCapture.Recording -> 1.09f
            else -> 1.055f
        },
        animationSpec = tween(if (active) 115 else 155, easing = FastOutSlowInEasing),
        label = "Voice button press",
    )
    val labelWidth = with(LocalDensity.current) { 80.sp.toDp() }
    LedgerPanel(
        modifier = Modifier
            .heightIn(min = 54.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label = "输入文字记账") { tap(); true }
            }
            .pointerInput(Unit) {
                val cancelMargin = 12.dp.toPx()
                coroutineScope {
                    var activePresses = 0
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val pointerId = down.id
                        var longPressed = false
                        var completed = false
                        var slidingOut = false
                        val release = CompletableDeferred<Unit>()
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            activePresses++
                            pressed = true
                            try {
                                release.await()
                            } finally {
                                activePresses--
                                if (activePresses == 0) {
                                    pressed = false
                                    cancelPending = false
                                }
                            }
                        }
                        try {
                            val beforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                        ?: return@withTimeoutOrNull InitialPressResult.Cancelled
                                    if (change.isConsumed) {
                                        return@withTimeoutOrNull InitialPressResult.Cancelled
                                    }
                                    if (!change.pressed) {
                                        return@withTimeoutOrNull if (change.isOutOfBounds(size, extendedTouchPadding)) {
                                            InitialPressResult.Cancelled
                                        } else {
                                            change.consume()
                                            InitialPressResult.Tap
                                        }
                                    }
                                    if (
                                        change.isOutOfBounds(size, extendedTouchPadding) ||
                                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    ) {
                                        return@withTimeoutOrNull InitialPressResult.Cancelled
                                    }
                                }
                                @Suppress("UNREACHABLE_CODE")
                                InitialPressResult.Cancelled
                            }

                            when (beforeLongPress) {
                                InitialPressResult.Tap -> {
                                    tap()
                                    return@awaitEachGesture
                                }
                                InitialPressResult.Cancelled -> return@awaitEachGesture
                                null -> {
                                    longPressed = true
                                    start()
                                }
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                val nextSlidingOut = isSlideCancelling(
                                    position = change.position,
                                    size = size,
                                    currentlyCancelling = slidingOut,
                                    margin = cancelMargin,
                                )
                                if (!change.pressed) {
                                    if (change.changedToUp()) {
                                        change.consume()
                                        completed = !nextSlidingOut
                                    }
                                    break
                                }
                                change.consume()
                                if (nextSlidingOut != slidingOut) {
                                    slidingOut = nextSlidingOut
                                    cancelPending = nextSlidingOut
                                    cancelPreview(nextSlidingOut)
                                    if (currentCapture == VoiceCapture.Starting || currentCapture == VoiceCapture.Recording) {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                }
                            }
                        } finally {
                            cancelPending = false
                            cancelPreview(false)
                            release.complete(Unit)
                            if (longPressed) end(completed)
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(16.dp),
        variant = SurfaceTone.Accent(containerColor),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (cancelling) Icons.Outlined.Close else Icons.Outlined.Mic, null, tint = contentColor)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    cancelling -> "松手取消"
                    capture == VoiceCapture.Idle -> "ai 记账"
                    capture == VoiceCapture.Starting -> "准备录音…"
                    capture == VoiceCapture.Recording -> "松手识别"
                    else -> "正在识别…"
                },
                modifier = Modifier.widthIn(min = labelWidth),
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
