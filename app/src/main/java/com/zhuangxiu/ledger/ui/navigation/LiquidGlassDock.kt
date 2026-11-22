package com.zhuangxiu.ledger.ui.navigation

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import kotlin.math.abs
import kotlin.math.roundToInt

internal class DockBackdrop(val layer: GraphicsLayer) {
    var originInRoot by mutableStateOf(Offset.Zero)
}

@Composable
internal fun rememberDockBackdrop(): DockBackdrop? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val layer = rememberGraphicsLayer()
    return remember(layer) { DockBackdrop(layer) }
}

internal fun Modifier.recordDockBackdrop(backdrop: DockBackdrop?): Modifier {
    if (backdrop == null) return this
    return onGloballyPositioned { backdrop.originInRoot = it.positionInRoot() }.drawWithContent {
        if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        } else {
            drawContent()
        }
    }
}

@Composable
internal fun LiquidGlassDock(
    backdrop: DockBackdrop?,
    position: () -> Float,
    lift: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = LedgerTheme.isDark
    val glassTint = remember(colors, dark) {
        Brush.verticalGradient(
            0f to colors.surfaceContainerHighest.copy(alpha = if (dark) 0.49f else 0.37f),
            0.48f to colors.surface.copy(alpha = if (dark) 0.36f else 0.24f),
            1f to colors.surfaceContainerHigh.copy(alpha = if (dark) 0.43f else 0.32f),
        )
    }
    val glassRim = remember(colors, dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.40f else 0.89f),
            0.53f to colors.outline.copy(alpha = 0.19f),
            1f to Color.White.copy(alpha = if (dark) 0.25f else 0.62f),
        )
    }
    val edgeLayer = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    val blur = remember(density) { BlurEffect(0.85f * density, 0.85f * density, TileMode.Clamp) }
    var originInRoot by remember { mutableStateOf(Offset.Zero) }

    Canvas(modifier.onGloballyPositioned { originInRoot = it.positionInRoot() }) {
        if (size.isEmpty()) return@Canvas
        val slot = position().coerceIn(0f, TABS.lastIndex.toFloat())
        val raised = maxOf(lift().coerceIn(0f, 1f), 1.5f * abs(slot - slot.roundToInt()))
        val visualSlot = if (layoutDirection == LayoutDirection.Ltr) slot else TABS.lastIndex - slot
        val cellWidth = size.width / TABS.size
        val lensSize = Size(
            (cellWidth - 10.dp.toPx() + 18.dp.toPx() * raised).coerceAtLeast(1f),
            (size.height - 10.dp.toPx() + 15.dp.toPx() * raised).coerceAtLeast(1f),
        )
        val lensCenter = Offset((visualSlot + 0.5f) * cellWidth, size.height / 2f)
        val lensBounds = Rect(lensCenter - Offset(lensSize.width / 2f, lensSize.height / 2f), lensSize)
        val dockBounds = Rect(Offset.Zero, size)
        val padding = 14.dp.roundToPx()
        val margin = Offset(padding.toFloat(), padding.toFloat())
        val hasBackdrop = backdrop != null && backdrop.layer.size != IntSize.Zero &&
            drawContext.canvas.nativeCanvas.isHardwareAccelerated
        if (hasBackdrop) {
            val shift = backdrop.originInRoot - originInRoot + margin
            edgeLayer.renderEffect = blur
            edgeLayer.record(
                size = IntSize(size.width.roundToInt() + padding * 2, size.height.roundToInt() + padding * 2),
            ) {
                translate(shift.x, shift.y) { drawLayer(backdrop.layer) }
            }
            // Sample the page only along the glass edges; the center shows the page directly.
            drawGlassEdge(edgeLayer, margin, dockBounds, 3.dp.toPx(), scale = 1.016f)
            drawGlassEdge(edgeLayer, margin, lensBounds, (3.5f + raised).dp.toPx(), scale = 1.04f + 0.02f * raised)
        }

        clipPath(capsulePath(dockBounds)) { drawRect(glassTint) }
        drawGlassOutline(dockBounds, glassRim, 1.dp.toPx())
        val lensWash = Brush.verticalGradient(
            0f to Color.White.copy(alpha = (if (dark) 0.12f else 0.22f) + 0.04f * raised),
            0.5f to colors.primary.copy(alpha = (if (dark) 0.20f else 0.16f) - 0.09f * raised),
            1f to colors.primary.copy(alpha = if (dark) 0.21f else 0.13f),
            startY = lensBounds.top,
            endY = lensBounds.bottom,
        )
        drawRoundRect(
            brush = lensWash,
            topLeft = lensBounds.topLeft,
            size = lensSize,
            cornerRadius = CornerRadius(lensSize.minDimension / 2f),
        )
        drawGlassOutline(
            lensBounds,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = (if (dark) 0.50f else 0.84f) + 0.12f * raised),
                0.35f to Color.White.copy(alpha = if (dark) 0.25f else 0.52f),
                0.63f to colors.outline.copy(alpha = 0.24f),
                0.84f to colors.primary.copy(alpha = 0.24f + 0.12f * raised),
                1f to Color.White.copy(alpha = if (dark) 0.37f else 0.77f),
                start = lensBounds.topLeft,
                end = lensBounds.bottomRight,
            ),
            (0.9f + 0.3f * raised).dp.toPx(),
        )
    }
}

private fun DrawScope.drawGlassEdge(layer: GraphicsLayer, margin: Offset, bounds: Rect, depth: Float, scale: Float) {
    val innerBounds = bounds.deflate(depth.coerceAtMost(bounds.minDimension / 3f))
    clipPath(capsulePath(bounds)) {
        clipPath(capsulePath(innerBounds), ClipOp.Difference) {
            scale(scale, bounds.center) {
                translate(-margin.x, -margin.y) { drawLayer(layer) }
            }
        }
    }
}

private fun capsulePath(bounds: Rect): Path = Path().apply {
    addRoundRect(RoundRect(bounds, CornerRadius(minOf(bounds.width, bounds.height) / 2f)))
}

private fun DrawScope.drawGlassOutline(bounds: Rect, brush: Brush, width: Float) {
    val inset = width / 2f
    drawRoundRect(
        brush = brush,
        topLeft = bounds.topLeft + Offset(inset, inset),
        size = Size((bounds.width - width).coerceAtLeast(0f), (bounds.height - width).coerceAtLeast(0f)),
        cornerRadius = CornerRadius((minOf(bounds.width, bounds.height) / 2f - inset).coerceAtLeast(0f)),
        style = Stroke(width),
    )
}
