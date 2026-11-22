package com.zhuangxiu.ledger.ui.navigation

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val surface = remember(colors, dark) {
        Brush.verticalGradient(
            if (dark) listOf(colors.surfaceContainerHighest.copy(alpha = 0.66f), colors.surface.copy(alpha = 0.79f))
            else listOf(colors.surfaceContainerLowest.copy(alpha = 0.76f), colors.surfaceContainerHigh.copy(alpha = 0.67f)),
        )
    }
    val rim = remember(colors, dark) {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (dark) 0.28f else 0.88f), colors.outline.copy(alpha = 0.25f)),
        )
    }
    val filteredLayer = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    val blur = remember(density) { BlurEffect(4.5f * density, 4.5f * density, TileMode.Clamp) }
    val refraction = remember(density, backdrop) {
        if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) DockRefraction(density) else null
    }
    var originInRoot by remember { mutableStateOf(Offset.Zero) }

    Canvas(modifier.onGloballyPositioned { originInRoot = it.positionInRoot() }) {
        if (size.isEmpty()) return@Canvas
        val slot = position().coerceIn(0f, TABS.lastIndex.toFloat())
        val raised = maxOf(lift(), 2f * abs(slot - slot.roundToInt()))
        val visualSlot = if (layoutDirection == LayoutDirection.Ltr) slot else TABS.lastIndex - slot
        val cellWidth = size.width / TABS.size
        val lensSize = Size(
            (cellWidth - (10.dp.toPx() - 6.dp.toPx() * raised)).coerceAtLeast(1f),
            (size.height - (10.dp.toPx() - 4.dp.toPx() * raised)).coerceAtLeast(1f),
        )
        val center = Offset((visualSlot + 0.5f) * cellWidth, size.height / 2f)
        val lensOrigin = center - Offset(lensSize.width / 2f, lensSize.height / 2f)
        val lensRadius = CornerRadius(lensSize.minDimension / 2f)

        if (backdrop != null && backdrop.layer.size != IntSize.Zero && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            // Filter the dock-sized area, with room for the blur and refracted pixels along its edges.
            val padding = 18.dp.toPx().roundToInt()
            val margin = Offset(padding.toFloat(), padding.toFloat())
            val shift = backdrop.originInRoot - originInRoot + margin
            filteredLayer.renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkNotNull(refraction).effect(center + margin, lensSize, raised)
            } else {
                blur
            }
            filteredLayer.record(size = IntSize(size.width.roundToInt() + padding * 2, size.height.roundToInt() + padding * 2)) {
                translate(shift.x, shift.y) { drawLayer(backdrop.layer) }
            }
            translate(-margin.x, -margin.y) { drawLayer(filteredLayer) }
        }

        drawRect(surface)
        val rimInset = 0.5.dp.toPx()
        drawRoundRect(
            brush = rim,
            topLeft = Offset(rimInset, rimInset),
            size = Size((size.width - 2 * rimInset).coerceAtLeast(0f), (size.height - 2 * rimInset).coerceAtLeast(0f)),
            cornerRadius = CornerRadius((size.minDimension / 2f - rimInset).coerceAtLeast(0f)),
            style = Stroke(1.dp.toPx()),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    colors.primary.copy(alpha = (if (dark) 0.33f else 0.25f) - 0.19f * raised),
                    colors.primary.copy(alpha = (if (dark) 0.24f else 0.17f) - 0.12f * raised),
                ),
                startY = lensOrigin.y,
                endY = lensOrigin.y + lensSize.height,
            ),
            topLeft = lensOrigin,
            size = lensSize,
            cornerRadius = lensRadius,
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = (if (dark) 0.32f else 0.73f) + 0.12f * raised),
                    colors.primary.copy(alpha = 0.14f + 0.18f * raised),
                    colors.tertiary.copy(alpha = 0.22f + 0.25f * raised),
                ),
                start = lensOrigin,
                end = lensOrigin + Offset(lensSize.width, lensSize.height),
            ),
            topLeft = lensOrigin,
            size = lensSize,
            cornerRadius = lensRadius,
            style = Stroke((0.8f + 0.4f * raised).dp.toPx()),
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class DockRefraction(private val density: Float) {
    private val shader by lazy { RuntimeShader(DockLensShader) }
    private val blur = RenderEffect.createBlurEffect(4.5f * density, 4.5f * density, Shader.TileMode.CLAMP)
    private var lastCenter = Offset.Unspecified
    private var lastSize = Size.Unspecified
    private var lastLift = -1f
    private var cachedEffect: ComposeRenderEffect? = null

    fun effect(center: Offset, size: Size, lift: Float): ComposeRenderEffect {
        if (center != lastCenter || size != lastSize || lift != lastLift) {
            shader.setFloatUniform("center", center.x, center.y)
            shader.setFloatUniform("halfExtent", size.width / 2f, size.height / 2f)
            shader.setFloatUniform("density", density)
            shader.setFloatUniform("lift", lift)
            val lens = RenderEffect.createRuntimeShaderEffect(shader, "scene")
            cachedEffect = RenderEffect.createChainEffect(lens, blur).asComposeRenderEffect()
            lastCenter = center
            lastSize = size
            lastLift = lift
        }
        return checkNotNull(cachedEffect)
    }
}

private const val DockLensShader = """
    uniform shader scene;
    uniform float2 center;
    uniform float2 halfExtent;
    uniform float density;
    uniform float lift;

    half4 main(float2 pixel) {
        float radius = min(halfExtent.x, halfExtent.y);
        float straight = halfExtent.x - radius;
        float2 relative = pixel - center;
        float2 rim = float2(relative.x - clamp(relative.x, -straight, straight), relative.y);
        float rimDistance = length(rim);
        float inset = radius - rimDistance;
        if (inset <= 0.0 || inset >= 12.0 * density || rimDistance < 0.01) {
            return scene.eval(pixel);
        }
        float rimStrength = smoothstep(0.0, 2.0 * density, inset)
            * (1.0 - smoothstep(3.0 * density, 12.0 * density, inset));
        float2 displacement = (rim / rimDistance) * rimStrength * (3.0 + 6.0 * lift) * density;
        half4 throughGlass = scene.eval(pixel + displacement);
        half red = scene.eval(pixel + displacement * (1.1 + lift * 0.2)).r;
        half blue = scene.eval(pixel + displacement * (0.9 - lift * 0.2)).b;
        return half4(min(red, throughGlass.a), throughGlass.g, min(blue, throughGlass.a), throughGlass.a);
    }
"""
