package com.zhuangxiu.ledger.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import java.io.File

/** 图片查看页：全屏、左右滑动切换、双指缩放、双击放大、删除 */
@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val images = state.images
    var showDelete by remember { mutableStateOf(false) }

    // 图片删光了就返回
    LaunchedEffect(state.loading, images.isEmpty()) { if (!state.loading && images.isEmpty()) onBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (images.isNotEmpty()) {
            val pagerState = rememberPagerState(
                initialPage = viewModel.startIndex.coerceIn(0, images.size - 1),
                pageCount = { images.size },
            )
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { images[it].id }) { page ->
                ZoomableImage(viewModel.imageFile(images[page].fileName))
            }
            val current = images.getOrNull(pagerState.currentPage)

            // 顶部操作栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text(
                    "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                // 从凭证库进入时，可以跳到这张图片所属的账单
                if (viewModel.expenseId <= 0 && current != null) {
                    IconButton(onClick = { onOpenExpense(current.expenseId) }) {
                        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = "查看账单", tint = Color.White)
                    }
                }
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = Color.White)
                }
            }

            if (showDelete && current != null) {
                ConfirmDialog(
                    title = "删除这张凭证？",
                    text = "图片删除后无法恢复。",
                    confirmLabel = "删除",
                    destructive = true,
                    onConfirm = {
                        viewModel.delete(current)
                        showDelete = false
                    },
                    onDismiss = { showDelete = false },
                )
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/**
 * 可缩放的图片：双指缩放（1x ~ 5x）、放大后单指拖动、双击在 1x 和 2.5x 之间切换。
 * 没放大时不消费单指滑动，这样外层的 Pager 仍然可以左右翻页。
 */
@Composable
private fun ZoomableImage(file: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // 拖动范围限制在放大后超出屏幕的部分
    fun clamp(value: Offset, scale: Float): Offset {
        val maxX = size.width * (scale - 1) / 2
        val maxY = size.height * (scale - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    AsyncImage(
        model = file,
        contentDescription = "凭证图片",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zooming = event.changes.size > 1
                        if (zooming || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else clamp(offset + event.calculatePan(), scale)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
