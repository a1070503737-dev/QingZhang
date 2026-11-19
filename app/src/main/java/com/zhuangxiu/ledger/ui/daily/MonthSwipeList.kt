package com.zhuangxiu.ledger.ui.daily

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.YearMonth
import kotlin.math.abs

@Composable
internal fun MonthSwipeList(
    month: YearMonth,
    onMonth: (Long) -> Unit,
    contentPadding: PaddingValues,
    verticalArrangement: Arrangement.Vertical,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentMonth by rememberUpdatedState(month)
    val changeMonth by rememberUpdatedState(onMonth)
    var position by remember { mutableFloatStateOf(0f) }
    var preview by remember { mutableStateOf<YearMonth?>(null) }
    var transition by remember { mutableStateOf<Job?>(null) }
    val colors = MaterialTheme.colorScheme

    Box(
        modifier.clipToBounds().background(colors.surfaceContainerLow).pointerInput(Unit) {
            var distance = 0f
            var startPosition = 0f
            val threshold = 48.dp.toPx()
            val previewWidth = 88.dp.toPx()
            val entryOffset = 28.dp.toPx()

            fun finishDrag(commit: Boolean) {
                val offset = if (distance < 0f) 1L else -1L
                val from = currentMonth
                val target = from.plusMonths(offset)
                transition = scope.launch {
                    if (commit && abs(distance) >= threshold && target.year in 1..9999) {
                        preview = target
                        animate(position, -offset * previewWidth, animationSpec = tween(85)) { value, _ -> position = value }
                        changeMonth(offset)
                        val changed = withTimeoutOrNull(300) {
                            snapshotFlow { currentMonth }.first { it != from }
                        }
                        if (changed == target) position = offset * entryOffset
                        preview = null
                    }
                    animate(position, 0f, animationSpec = tween(185, easing = FastOutSlowInEasing)) { value, _ -> position = value }
                    preview = null
                }
            }

            detectHorizontalDragGestures(
                onDragStart = {
                    transition?.cancel()
                    distance = 0f
                    startPosition = position
                },
                onHorizontalDrag = { _, delta ->
                    distance += delta
                    position = (startPosition + distance * 0.72f).coerceIn(-previewWidth, previewWidth)
                    preview = currentMonth.plusMonths(if (position < 0f) 1L else -1L)
                        .takeIf { position != 0f && it.year in 1..9999 }
                },
                onDragEnd = { finishDrag(commit = true) },
                onDragCancel = { finishDrag(commit = false) },
            )
        },
    ) {
        preview?.let { adjacent ->
            Column(
                modifier = Modifier
                    .align(if (adjacent > month) Alignment.CenterEnd else Alignment.CenterStart)
                    .width(88.dp)
                    .graphicsLayer { alpha = (abs(position) / 52.dp.toPx()).coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = colors.primary)
                Text("${adjacent.year} 年", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text("${adjacent.monthValue} 月", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { translationX = position }
                .background(colors.background),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
