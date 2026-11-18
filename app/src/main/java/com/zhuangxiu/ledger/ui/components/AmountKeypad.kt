package com.zhuangxiu.ledger.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun applyAmountKey(amount: String, key: String): String = when {
    key == "⌫" -> amount.dropLast(1)
    key == "." && amount.contains('.') -> amount
    key == "." && amount.isEmpty() -> "0."
    else -> amount + key
}

/**
 * 液态水滴数字键盘：
 * 按下时按键呈凝胶水滴微膨胀（1.06x）并点亮镜面边缘高光与轻触觉脉冲，保留完整无障碍语义标签。
 */
@Composable
internal fun AmountKeypad(enabled: Boolean = true, onKey: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val keyRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    keyRows.forEach { keys ->
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            keys.forEach { key ->
                val interactionSource = remember(key) { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                var touchOffset by remember(key) { mutableStateOf(Offset.Unspecified) }
                val pressProgress by animateFloatAsState(
                    targetValue = if (enabled && isPressed) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMedium),
                    label = "key-sheen-$key",
                )
                val isBackspace = key == "⌫"
                val shape = RoundedCornerShape(18.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                        .liquidInteractive(
                            interactionSource = interactionSource,
                            enabled = enabled,
                            pressedScale = 1.06f,
                            onTouchOffset = { touchOffset = it },
                        )
                        .liquidGlass(
                            shape = shape,
                            variant = if (isBackspace) {
                                GlassVariant.Tinted(MaterialTheme.colorScheme.secondaryContainer)
                            } else {
                                GlassVariant.Elevated
                            },
                            cornerRadius = 18.dp,
                            shadowElevation = 4.dp,
                            pressProgress = pressProgress,
                            touchOffset = touchOffset,
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
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
