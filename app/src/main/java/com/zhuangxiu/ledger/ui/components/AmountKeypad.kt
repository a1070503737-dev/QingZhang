package com.zhuangxiu.ledger.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val KEYPAD_ROWS = listOf(
    listOf("1", "2", "3", "+"),
    listOf("4", "5", "6", "−"),
    listOf("7", "8", "9", "⌫"),
    listOf(".", "0", "0", "="),
)

private fun keyAt(position: Offset, size: IntSize): String? {
    if (size.width <= 0 || size.height <= 0) return null
    val row = (position.y / size.height * KEYPAD_ROWS.size).toInt().coerceIn(KEYPAD_ROWS.indices)
    val keys = KEYPAD_ROWS[row]
    val column = (position.x / size.width * keys.size).toInt().coerceIn(keys.indices)
    return keys[column]
}

@Composable
internal fun AmountKeypad(enabled: Boolean = true, onKey: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val enterKey by rememberUpdatedState(onKey)
    val pressTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    var pressedKey by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val start = down.position
                    pressedKey = keyAt(start, size)
                    var sliding = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) {
                                if (sliding) {
                                    pressedKey?.let { selected ->
                                        pointer.consume()
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        enterKey(selected)
                                    }
                                }
                                break
                            }
                            if (!sliding && (pointer.position - start).getDistance() > viewConfig.touchSlop * 0.55f) {
                                sliding = true
                            }
                            if (sliding) {
                                pressedKey = keyAt(pointer.position, size)
                                pointer.consume()
                            }
                        }
                    } finally {
                        pressedKey = null
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KEYPAD_ROWS.forEach { keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                keys.distinct().forEach { key ->
                    val description = when (key) {
                        "+" -> "加"
                        "−" -> "减"
                        "=" -> "等于"
                        "⌫" -> "退格"
                        else -> "数字键$key"
                    }
                    val variant = if (key == "+" || key == "−" || key == "=" || key == "⌫") {
                        GlassVariant.Tinted(MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        GlassVariant.Elevated
                    }
                    Box(
                        modifier = Modifier
                            .weight(if (key == "0") 2f else 1f)
                            .heightIn(min = 50.dp)
                            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                            .liquidGlass(RoundedCornerShape(18.dp), variant, cornerRadius = 18.dp, shadowElevation = 3.dp)
                            .drawBehind { if (enabled && pressedKey == key) drawRect(pressTint) }
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                enabled = enabled,
                                role = Role.Button,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    enterKey(key)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { contentDescription = description },
                        )
                    }
                }
            }
        }
    }
}
