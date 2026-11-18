package com.zhuangxiu.ledger.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import kotlin.math.hypot
import kotlin.math.roundToInt

internal fun applyAmountKey(amount: String, key: String): String = when {
    key == "⌫" -> amount.dropLast(1)
    key == "." && amount.contains('.') -> amount
    key == "." && amount.isEmpty() -> "0."
    else -> amount + key
}

private val KEYPAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", "⌫"),
)

/**
 * 2D 连续滑动水滴凸透镜数字键盘：
 * - 单点或按住在 12 键间连续漫游时，高透凸透镜水滴以高阻尼弹簧平滑跟随拇指；
 * - 水滴下方自动挖空原按键以消除重影，内部穿透毛玻璃折射底层画面，并将字符以 1.26x 纯净品牌蓝锋利放大，松手精准输入目标键。
 */
@Composable
internal fun AmountKeypad(enabled: Boolean = true, onKey: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val glass = LedgerTheme.glass
    val primaryColor = MaterialTheme.colorScheme.primary
    val backdropHolder = LocalLiquidBackdropHolder.current
    val keysForegroundLayer = rememberGraphicsLayer()
    val viewConfig = LocalViewConfiguration.current
    val density = LocalDensity.current

    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var gridRootPos by remember { mutableStateOf(Offset.Zero) }
    var activePointer by remember { mutableStateOf(false) }
    var pointerOffset by remember { mutableStateOf(Offset.Zero) }
    var hoveredFlatIdx by remember { mutableIntStateOf(-1) }

    val liftProgress by animateFloatAsState(
        targetValue = if (enabled && activePointer) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 540f),
        label = "keypad-droplet",
    )
    val animatedOffset by animateOffsetAsState(
        targetValue = pointerOffset,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 2400f),
        label = "keypad-droplet-pos",
    )

    fun keyIndexAt(pos: Offset, size: IntSize): Int {
        if (size.width <= 0 || size.height <= 0) return -1
        val col = (pos.x / (size.width / 3f)).toInt().coerceIn(0, 2)
        val row = (pos.y / (size.height / 4f)).toInt().coerceIn(0, 3)
        return row * 3 + col
    }

    val lensW = if (gridSize.width > 0) (gridSize.width / 3f) * (1.02f + 0.14f * liftProgress) else 0f
    val lensH = with(density) { 60.dp.toPx() } * (0.88f + 0.12f * liftProgress)
    val lensLeft = if (gridSize.width > 0) {
        (animatedOffset.x - lensW / 2f).coerceIn(-lensW * 0.12f, gridSize.width - lensW * 0.88f)
    } else {
        0f
    }
    val lensTop = if (gridSize.height > 0) {
        (animatedOffset.y - lensH / 2f).coerceIn(-lensH * 0.22f, gridSize.height - lensH * 0.78f)
    } else {
        0f
    }
    val cornerPx = with(density) { 24.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (liftProgress > 0.01f) 18f else 0f)
            .onGloballyPositioned {
                gridSize = it.size
                gridRootPos = it.positionInRoot()
            }
            .graphicsLayer { clip = false }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    activePointer = true
                    pointerOffset = down.position
                    var scrubbing = false
                    hoveredFlatIdx = keyIndexAt(down.position, size)

                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (ch != null && ch.pressed) {
                                pointerOffset = ch.position
                                val dist = hypot(ch.position.x - down.position.x, ch.position.y - down.position.y)
                                if (!scrubbing && dist > viewConfig.touchSlop * 0.55f) {
                                    scrubbing = true
                                }
                                val idx = keyIndexAt(ch.position, size)
                                if (idx != hoveredFlatIdx && idx in 0..11) {
                                    hoveredFlatIdx = idx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                if (scrubbing) {
                                    ch.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (scrubbing && hoveredFlatIdx in 0..11) {
                            val key = KEYPAD_ROWS[hoveredFlatIdx / 3][hoveredFlatIdx % 3]
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onKey(key)
                        }
                    } finally {
                        activePointer = false
                        hoveredFlatIdx = -1
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    keysForegroundLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    if (liftProgress > 0.02f && gridSize.width > 0) {
                        val dropPath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(lensLeft, lensTop, lensLeft + lensW, lensTop + lensH),
                                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                                ),
                            )
                        }
                        clipPath(dropPath, clipOp = ClipOp.Difference) {
                            drawLayer(keysForegroundLayer)
                        }
                    } else {
                        drawLayer(keysForegroundLayer)
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KEYPAD_ROWS.forEach { keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    keys.forEach { key ->
                        val isBackspace = key == "⌫"
                        val interactionSource = remember(key) { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                                .liquidGlass(
                                    shape = RoundedCornerShape(18.dp),
                                    variant = if (isBackspace) {
                                        GlassVariant.Tinted(MaterialTheme.colorScheme.secondaryContainer)
                                    } else {
                                        GlassVariant.Elevated
                                    },
                                    cornerRadius = 18.dp,
                                    shadowElevation = 3.dp,
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    enabled = enabled,
                                    role = Role.Button,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onKey(key)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = key,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics {
                                    contentDescription = if (isBackspace) "退格" else "数字键$key"
                                },
                            )
                        }
                    }
                }
            }
        }

        // 升起跟随拇指漫游的清澈 3D 凸透镜水滴放大镜
        if (liftProgress > 0.02f && gridSize.width > 0) {
            val lensShape = RoundedCornerShape(24.dp)
            val lensWDp = with(density) { lensW.toDp() }
            val lensHDp = with(density) { lensH.toDp() }

            Box(
                modifier = Modifier
                    .offset { IntOffset(lensLeft.roundToInt(), lensTop.roundToInt()) }
                    .size(lensWDp, lensHDp)
                    .then(
                        if (!IsRobolectricRuntime) {
                            Modifier.shadow((14f * liftProgress).dp, lensShape)
                        } else {
                            Modifier
                        },
                    )
                    .clip(lensShape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .liquidDropletShaderEffect(
                            cornerRadius = 24.dp,
                            activeProgress = liftProgress,
                            magnification = 1.14f,
                            dispersionPx = 4.5f,
                        )
                        .drawWithContent {
                            if (backdropHolder != null) {
                                val relX = (gridRootPos.x + lensLeft) - backdropHolder.rootOffset.x
                                val relY = (gridRootPos.y + lensTop) - backdropHolder.rootOffset.y
                                withTransform({
                                    translate(-relX, -relY)
                                }) {
                                    drawLayer(backdropHolder.layer)
                                }
                            }
                            drawRect(
                                color = if (glass.isDark) {
                                    Color(0xFF101C32).copy(alpha = 0.32f * liftProgress)
                                } else {
                                    Color.White.copy(alpha = 0.28f * liftProgress)
                                },
                            )
                        },
                )
                Canvas(Modifier.fillMaxSize()) {
                    val mag = 1f + 0.28f * liftProgress
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val tintPaint = Paint().apply {
                        colorFilter = ColorFilter.tint(primaryColor, BlendMode.SrcIn)
                    }
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), tintPaint)
                        withTransform({
                            scale(mag, mag, pivot = Offset(cx, cy))
                            translate(-lensLeft, -lensTop)
                        }) {
                            drawLayer(keysForegroundLayer)
                        }
                        canvas.restore()
                    }
                    drawWaterDropletRim(
                        cornerRadiusPx = cornerPx,
                        isDark = glass.isDark,
                        intensity = liftProgress,
                    )
                }
            }
        }
    }
}
