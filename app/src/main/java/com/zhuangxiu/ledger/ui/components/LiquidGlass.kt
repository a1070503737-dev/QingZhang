package com.zhuangxiu.ledger.ui.components

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 检测是否运行在 Robolectric 单元测试环境。
 * 测试环境下自动冻结流光并跳过原生 GPU RenderEffect，确保所有 Compose UI 测试秒过且零阻塞。
 */
internal val IsRobolectricRuntime: Boolean by lazy {
    val fp = Build.FINGERPRINT.orEmpty()
    val model = Build.MODEL.orEmpty()
    fp.contains("robolectric", ignoreCase = true) || model.contains("robolectric", ignoreCase = true)
}

/** 全局场景背景 GraphicsLayer 句柄：录制极光底板与页面内容，供悬浮液态水滴透镜实时穿透折射 */
class LiquidBackdropHolder(
    val layer: GraphicsLayer,
    var rootOffset: Offset = Offset.Zero,
)

val LocalLiquidBackdropActive = staticCompositionLocalOf { false }
val LocalLiquidBackdropHolder = staticCompositionLocalOf<LiquidBackdropHolder?> { null }

/** Liquid Glass 静态材质层级 */
@Immutable
sealed interface GlassVariant {
    data object Regular : GlassVariant
    data object Elevated : GlassVariant
    data object Clear : GlassVariant
    data class Tinted(val tint: Color, val vivid: Boolean = false) : GlassVariant
}

/**
 * Android 13+ (API 33+) 真实凸透镜水滴背景折射 AGSL 着色器：
 * 仅作用于水滴下方的环境背景层（绝不二次扭曲前景文字与图标，彻底杜绝边缘拉丝残影）：
 * 1. 中心高透无雾、微距光学放大
 * 2. 外圈弯月面沿圆角矩形 SDF 法线平滑折射背景色彩与卡片边缘
 * 3. R/G/B 三通道波长分离，在曲面边缘形成清澈棱镜色散（Chromatic Dispersion）
 */
private const val LIQUID_DROPLET_LENS_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float uRadius;
uniform float uMagnification;
uniform float uDispersion;
uniform float uDark;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 fragCoord) {
    float2 safeSize = max(uSize, float2(2.0, 2.0));
    float2 halfSize = safeSize * 0.5;
    float r = min(uRadius, min(halfSize.x, halfSize.y));
    float2 p = fragCoord - halfSize;
    float d = sdRoundRect(p, halfSize, r);
    if (d > 0.5) {
        return half4(0.0);
    }

    float eps = 1.0;
    float dx = sdRoundRect(p + float2(eps, 0.0), halfSize, r) - sdRoundRect(p - float2(eps, 0.0), halfSize, r);
    float dy = sdRoundRect(p + float2(0.0, eps), halfSize, r) - sdRoundRect(p - float2(0.0, eps), halfSize, r);
    float2 normal = normalize(float2(dx, dy) + float2(1e-4, 1e-4));

    float bezel = min(22.0, min(halfSize.x, halfSize.y) * 0.58);
    float rimT = clamp(1.0 + d / max(bezel, 1.0), 0.0, 1.0);
    float meniscus = rimT * rimT * (3.0 - 2.0 * rimT);

    float2 centered = p / max(uMagnification, 1.0);
    float2 baseCoord = halfSize + centered - normal * (meniscus * meniscus * 9.5);
    float2 minCoord = float2(1.0, 1.0);
    float2 maxCoord = max(safeSize - float2(2.0, 2.0), minCoord);

    float2 disp = normal * (meniscus * uDispersion);
    half4 cR = content.eval(clamp(baseCoord + disp * 1.1, minCoord, maxCoord));
    half4 cG = content.eval(clamp(baseCoord, minCoord, maxCoord));
    half4 cB = content.eval(clamp(baseCoord - disp * 1.2, minCoord, maxCoord));

    half4 col = half4(cR.r, cG.g, cB.b, cG.a);

    float prismBand = pow(meniscus, 2.6) * (1.0 - smoothstep(-2.0, 0.5, d));
    half3 rainbow = half3(
        0.52 + 0.45 * normal.x,
        0.52 + 0.42 * normal.y,
        0.88 - 0.30 * normal.x
    );
    col.rgb = mix(col.rgb, rainbow, half(prismBand * 0.16));
    return col;
}
"""

/** 静态玻璃面板背景 AGSL 着色器 */
private const val LIQUID_PANEL_AGSL = """
uniform float2 uResolution;
uniform float2 uTouch;
uniform float uPress;
uniform float uCornerRadius;
uniform half4 uTopColor;
uniform half4 uBottomColor;
uniform half4 uTintColor;
uniform float uDark;

float sdRoundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 fragCoord) {
    float2 res = max(uResolution, float2(1.0, 1.0));
    float2 uv = fragCoord / res;
    float2 halfSize = res * 0.5;
    float r = min(uCornerRadius, min(halfSize.x, halfSize.y));
    float d = sdRoundedBox(fragCoord - halfSize, halfSize, r);

    float diag = clamp(uv.x * 0.45 + uv.y * 0.55, 0.0, 1.0);
    half4 base = mix(uTopColor, uBottomColor, half(diag));

    float fresnel = exp(max(d, -28.0) * 0.11);
    float catchlight = clamp((1.0 - uv.x) * 0.62 + (1.0 - uv.y) * 0.68 - 0.32, 0.0, 1.0);
    float counterGlow = clamp(uv.x * 0.52 + uv.y * 0.58 - 0.35, 0.0, 1.0);
    float distTouch = distance(fragCoord, uTouch);
    float caustic = uPress * exp(-distTouch / max(min(res.x, res.y) * 0.65, 56.0));

    half3 specularTint = mix(uTintColor.rgb, half3(1.0, 1.0, 1.0), half(uDark > 0.5 ? 0.58 : 0.78));
    float sheen = fresnel * (0.14 + 0.24 * catchlight + 0.10 * counterGlow) + 0.20 * caustic;
    base.rgb = mix(base.rgb, specularTint, half(clamp(sheen, 0.0, 0.50)));
    base.a = clamp(base.a + half(fresnel * (uDark > 0.5 ? 0.06 : 0.10) + caustic * 0.06), half(0.0), half(0.97));
    return base;
}
"""

@Composable
private fun fullGlassEffects(): Boolean {
    val context = LocalContext.current
    val lowRam = remember(context) { context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true }
    return !IsRobolectricRuntime && !lowRam && ValueAnimator.areAnimatorsEnabled()
}

/**
 * 全局流动极光与真实页面采样底板：
 * - `content`（极光 + 页面主体内容）统一录制进 `backdropLayer`，使悬浮在 `overlay` 层的持久化底栏水滴能够实时穿透毛玻璃、折射下方的真实滚动卡片与文字；
 * - `overlay` 独立绘制在 `backdropLayer` 之上，避免自引用循环。
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    if (LocalLiquidBackdropActive.current) {
        Box(modifier = modifier) {
            content()
            overlay()
        }
        return
    }
    val glass = LedgerTheme.glass
    val baseBg = MaterialTheme.colorScheme.background
    val auraLayer = rememberGraphicsLayer()
    val pageLayer = rememberGraphicsLayer()
    val auraHolder = remember(auraLayer) { LiquidBackdropHolder(auraLayer) }
    val pageHolder = remember(pageLayer) { LiquidBackdropHolder(pageLayer) }

    val phase = if (!fullGlassEffects()) {
        0.22f
    } else {
        val transition = rememberInfiniteTransition(label = "liquid-aurora")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2.0).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "aurora-phase",
        )
        animated
    }

    CompositionLocalProvider(LocalLiquidBackdropActive provides true) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val origin = it.positionInRoot()
                    auraHolder.rootOffset = origin
                    pageHolder.rootOffset = origin
                },
        ) {
            // 被采样层：包含流动极光与整个 NavHost 页面内容
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        pageLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(pageLayer)
                    },
            ) {
                Canvas(
                    Modifier.fillMaxSize().drawWithContent {
                        auraLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(auraLayer)
                    },
                ) {
                    drawRect(baseBg)
                    val w = size.width
                    val h = size.height
                    val rMax = maxOf(w, h)

                    val c1 = Offset(w * (0.18f + 0.10f * cos(phase)), h * (0.12f + 0.07f * sin(phase)))
                    drawCircle(
                        brush = Brush.radialGradient(listOf(glass.auraPrimary, Color.Transparent), c1, rMax * 0.58f),
                        radius = rMax * 0.58f,
                        center = c1,
                    )
                    val c2 = Offset(w * (0.85f - 0.11f * sin(phase * 0.8f)), h * (0.34f + 0.09f * cos(phase * 0.8f)))
                    drawCircle(
                        brush = Brush.radialGradient(listOf(glass.auraSecondary, Color.Transparent), c2, rMax * 0.52f),
                        radius = rMax * 0.52f,
                        center = c2,
                    )
                    val c3 = Offset(w * (0.28f + 0.12f * sin(phase * 0.65f + 1.1f)), h * (0.78f - 0.08f * cos(phase * 0.65f + 1.1f)))
                    drawCircle(
                        brush = Brush.radialGradient(listOf(glass.auraTertiary, Color.Transparent), c3, rMax * 0.56f),
                        radius = rMax * 0.56f,
                        center = c3,
                    )
                    val c4 = Offset(w * (0.76f + 0.07f * cos(phase + 2.2f)), h * (0.86f + 0.05f * sin(phase + 2.2f)))
                    drawCircle(
                        brush = Brush.radialGradient(listOf(glass.auraWarm, Color.Transparent), c4, rMax * 0.44f),
                        radius = rMax * 0.44f,
                        center = c4,
                    )
                }
                CompositionLocalProvider(LocalLiquidBackdropHolder provides auraHolder) {
                    content()
                }
            }
            // 页面内的透镜只采样极光，悬浮层才采样完整页面，避免图层自引用。
            CompositionLocalProvider(LocalLiquidBackdropHolder provides pageHolder) {
                overlay()
            }
        }
    }
}

/**
 * 为水滴背景采样层挂载 Android 13+ GPU 凸透镜折射与色散 RenderEffect。
 */
@Composable
internal fun Modifier.liquidDropletShaderEffect(
    cornerRadius: Dp,
    activeProgress: Float,
    magnification: Float = 1.14f,
    dispersionPx: Float = 4.2f,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !fullGlassEffects() || activeProgress <= 0.01f) {
        return this
    }
    val isDark = LedgerTheme.glass.isDark
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    return this.graphicsLayer {
        val w = size.width
        val h = size.height
        if (w > 2f && h > 2f) {
            renderEffect = runCatching {
                val shader = RuntimeShader(LIQUID_DROPLET_LENS_AGSL)
                shader.setFloatUniform("uSize", w, h)
                shader.setFloatUniform("uRadius", radiusPx)
                shader.setFloatUniform("uMagnification", 1f + (magnification - 1f) * activeProgress)
                shader.setFloatUniform("uDispersion", dispersionPx * activeProgress)
                shader.setFloatUniform("uDark", if (isDark) 1f else 0f)
                RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            }.getOrNull()
        }
    }
}

/**
 * 基础液态玻璃面板修饰符：
 * 支持 `clearMeltProgress` (0f..1f) 逐帧连续将磨砂雾面融化为清澈水滴态，彻底消除硬阈值跳变。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    variant: GlassVariant = GlassVariant.Regular,
    cornerRadius: Dp = 24.dp,
    shadowElevation: Dp = 10.dp,
    pressProgress: Float = 0f,
    clearMeltProgress: Float = 0f,
    touchOffset: Offset = Offset.Unspecified,
): Modifier {
    val glass = LedgerTheme.glass
    val primary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }

    val (rawTop, rawBottom, accentColor) = when (variant) {
        GlassVariant.Regular -> Triple(glass.surfaceTop, glass.surfaceBottom, primary)
        GlassVariant.Elevated -> Triple(glass.elevatedTop, glass.elevatedBottom, primary)
        GlassVariant.Clear -> Triple(
            glass.surfaceTop.copy(alpha = if (glass.isDark) 0.42f else 0.52f),
            glass.surfaceBottom.copy(alpha = if (glass.isDark) 0.26f else 0.34f),
            primary,
        )
        is GlassVariant.Tinted -> {
            if (variant.vivid) {
                Triple(
                    lerp(variant.tint, Color.White, if (glass.isDark) 0.08f else 0.14f).copy(alpha = 0.90f),
                    lerp(variant.tint, Color.Black, 0.16f).copy(alpha = 0.82f),
                    variant.tint,
                )
            } else {
                Triple(
                    lerp(glass.surfaceTop, variant.tint, if (glass.isDark) 0.44f else 0.36f)
                        .copy(alpha = if (glass.isDark) 0.78f else 0.84f),
                    lerp(glass.surfaceBottom, variant.tint, if (glass.isDark) 0.30f else 0.22f)
                        .copy(alpha = if (glass.isDark) 0.62f else 0.70f),
                    variant.tint,
                )
            }
        }
    }

    val melt = clearMeltProgress.coerceIn(0f, 1f)
    val crystalTop = if (glass.isDark) Color(0xFF132038).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.26f)
    val crystalBottom = if (glass.isDark) Color(0xFF0C1526).copy(alpha = 0.18f) else Color(0xFFEAF3FF).copy(alpha = 0.16f)
    val topColor = if (melt > 0.001f) lerp(rawTop, crystalTop, melt) else rawTop
    val bottomColor = if (melt > 0.001f) lerp(rawBottom, crystalBottom, melt) else rawBottom

    val specularBoost = (pressProgress * 0.28f).coerceIn(0f, 0.30f)
    val rimBright = glass.specularBright.copy(alpha = (glass.specularBright.alpha + specularBoost).coerceAtMost(1f))
    val rimMid = lerp(glass.specularMid, accentColor.copy(alpha = 0.34f), 0.42f)
    val rimCounter = glass.specularCounter.copy(alpha = (glass.specularCounter.alpha + specularBoost * 0.6f).coerceAtMost(1f))
    val innerTopGlow = glass.innerGlow.copy(alpha = ((glass.innerGlow.alpha + specularBoost * 0.4f) * (1f - melt * 0.55f)).coerceIn(0f, 0.95f))

    val enableShader = fullGlassEffects()
    val elevatedShadow = if (shadowElevation > 0.dp && !IsRobolectricRuntime) {
        Modifier.shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = glass.ambientShadow,
            spotColor = glass.ambientShadow,
        )
    } else {
        Modifier
    }

    return this
        .then(elevatedShadow)
        .clip(shape)
        .drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val actualTouch = if (touchOffset != Offset.Unspecified) touchOffset else Offset(w * 0.3f, h * 0.25f)

            val agslBrush: ShaderBrush? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && enableShader) {
                runCatching {
                    val shader = RuntimeShader(LIQUID_PANEL_AGSL)
                    shader.setFloatUniform("uResolution", w, h)
                    shader.setFloatUniform("uTouch", actualTouch.x, actualTouch.y)
                    shader.setFloatUniform("uPress", pressProgress)
                    shader.setFloatUniform("uCornerRadius", radiusPx)
                    shader.setFloatUniform("uTopColor", topColor.red, topColor.green, topColor.blue, topColor.alpha)
                    shader.setFloatUniform("uBottomColor", bottomColor.red, bottomColor.green, bottomColor.blue, bottomColor.alpha)
                    shader.setFloatUniform("uTintColor", accentColor.red, accentColor.green, accentColor.blue, 1f)
                    shader.setFloatUniform("uDark", if (glass.isDark) 1f else 0f)
                    ShaderBrush(shader)
                }.getOrNull()
            } else {
                null
            }

            val fallbackBodyBrush = Brush.linearGradient(listOf(topColor, bottomColor), Offset.Zero, Offset(w, h))
            val topSheenBrush = Brush.verticalGradient(listOf(innerTopGlow, Color.Transparent), 0f, (h * 0.42f).coerceAtLeast(12f))
            val rimBrush = Brush.linearGradient(
                colorStops = arrayOf(0.0f to rimBright, 0.46f to rimMid, 1.0f to rimCounter),
                start = Offset.Zero,
                end = Offset(w, h),
            )
            val strokePx = 1.15.dp.toPx()
            val halfStroke = strokePx / 2f
            val corner = CornerRadius((radiusPx - halfStroke).coerceAtLeast(0f))

            onDrawBehind {
                if (agslBrush != null) {
                    drawRect(brush = agslBrush)
                } else {
                    drawRect(brush = fallbackBodyBrush)
                }
                drawRect(brush = topSheenBrush)
                when (val outline = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Rounded -> drawRoundRect(
                        brush = rimBrush,
                        topLeft = Offset(halfStroke, halfStroke),
                        size = Size((w - strokePx).coerceAtLeast(0f), (h - strokePx).coerceAtLeast(0f)),
                        cornerRadius = corner,
                        style = Stroke(width = strokePx),
                    )
                    is Outline.Generic -> drawPath(outline.path, brush = rimBrush, style = Stroke(width = strokePx))
                    is Outline.Rectangle -> drawRect(brush = rimBrush, style = Stroke(width = strokePx))
                }
            }
        }
}

/**
 * 全局独立按钮连续液态水滴物理修饰符：
 * - 指数平滑速度滤波（消除 120Hz 触控采样抖动）
 * - 高阻尼无过冲弹簧（杜绝松手乱跳）
 * - 橡皮筋跟手拖拽 + 丝滑体积守恒拉伸形变
 */
fun Modifier.liquidInteractive(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 1.18f,
    enableTilt: Boolean = false,
    rubberBandRangeDp: Float = 12f,
    onTouchOffset: ((Offset) -> Unit)? = null,
    onActiveChange: ((Boolean) -> Unit)? = null,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    var pointerDown by remember { mutableStateOf(false) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var velocityStretch by remember { mutableFloatStateOf(0f) }
    var lastTouch by remember { mutableStateOf(Offset.Unspecified) }
    val density = LocalDensity.current
    val maxPullPx = with(density) { rubberBandRangeDp.dp.toPx() }

    val active = enabled && (isPressed || pointerDown)
    LaunchedEffect(active) {
        onActiveChange?.invoke(active)
    }

    val scale by animateFloatAsState(
        targetValue = if (active) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 520f),
        label = "droplet-scale",
    )
    val animatedPull by animateOffsetAsState(
        targetValue = if (active) dragDelta else Offset.Zero,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = 620f),
        label = "droplet-pull",
    )
    val stretchAnim by animateFloatAsState(
        targetValue = if (active) velocityStretch else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 540f),
        label = "droplet-stretch",
    )

    this
        .zIndex(if (scale > 1.005f) 12f else 0f)
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pointerDown = true
                val origin = down.position
                var prevPos = origin
                var smoothVel = 0f
                lastTouch = origin
                onTouchOffset?.invoke(origin)
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val cur = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (cur != null && cur.pressed) {
                            lastTouch = cur.position
                            onTouchOffset?.invoke(cur.position)
                            val raw = cur.position - origin
                            val rx = tanh(raw.x / (maxPullPx * 2.5f).coerceAtLeast(1f)) * maxPullPx
                            val ry = tanh(raw.y / (maxPullPx * 2.5f).coerceAtLeast(1f)) * maxPullPx
                            dragDelta = Offset(rx, ry)
                            val instVel = ((cur.position.x - prevPos.x) / 48f).coerceIn(-0.12f, 0.12f)
                            smoothVel = smoothVel * 0.72f + instVel * 0.28f
                            velocityStretch = smoothVel
                            prevPos = cur.position
                        }
                    } while (event.changes.any { it.pressed })
                } finally {
                    pointerDown = false
                    dragDelta = Offset.Zero
                    velocityStretch = 0f
                }
            }
        }
        .graphicsLayer {
            val stretchAbs = abs(stretchAnim)
            scaleX = scale * (1f + stretchAbs)
            scaleY = scale * (1f - stretchAbs * 0.42f)
            translationX = animatedPull.x
            translationY = animatedPull.y
            clip = false
            if (enableTilt && lastTouch != Offset.Unspecified && size.width > 0 && size.height > 0) {
                val nx = ((lastTouch.x / size.width) - 0.5f) * 2f
                val ny = ((lastTouch.y / size.height) - 0.5f) * 2f
                rotationY = nx * 2.2f * (if (active) 1f else 0f)
                rotationX = -ny * 1.8f * (if (active) 1f else 0f)
                cameraDistance = 14f * this.density
            }
        }
}

/**
 * 绘制水滴凸透镜表面张力高光亮边与微棱镜色散环（清澈透亮、零灰雾）。
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaterDropletRim(
    cornerRadiusPx: Float,
    isDark: Boolean,
    intensity: Float = 1f,
) {
    val w = size.width
    val h = size.height
    if (w <= 1f || h <= 1f || intensity <= 0.01f) return
    val r = CornerRadius(cornerRadiusPx, cornerRadiusPx)
    // 顶部微湿反光弧（中心完全透明，绝不遮挡透底画面）
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = (if (isDark) 0.16f else 0.30f) * intensity),
            0.28f to Color.Transparent,
            0.82f to Color.Transparent,
            1.0f to Color(0xFF60A5FA).copy(alpha = 0.10f * intensity),
        ),
        cornerRadius = r,
    )
    // 外圈曲面棱镜色散细环
    val prismStroke = 1.8.dp.toPx()
    drawRoundRect(
        brush = Brush.sweepGradient(
            0.00f to Color(0xFF60A5FA).copy(alpha = 0.44f * intensity),
            0.24f to Color(0xFFFBBF24).copy(alpha = 0.30f * intensity),
            0.50f to Color(0xFF34D399).copy(alpha = 0.32f * intensity),
            0.76f to Color(0xFFA78BFA).copy(alpha = 0.38f * intensity),
            1.00f to Color(0xFF60A5FA).copy(alpha = 0.44f * intensity),
        ),
        topLeft = Offset(prismStroke * 0.75f, prismStroke * 0.75f),
        size = Size((w - prismStroke * 1.5f).coerceAtLeast(1f), (h - prismStroke * 1.5f).coerceAtLeast(1f)),
        cornerRadius = CornerRadius((cornerRadiusPx - prismStroke * 0.75f).coerceAtLeast(0f)),
        style = Stroke(width = prismStroke),
    )
    // 最外层锋利水滴表面张力纯白亮边
    val rimStroke = 1.3.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (if (isDark) 0.88f else 0.98f) * intensity),
                Color.White.copy(alpha = 0.42f * intensity),
                Color.White.copy(alpha = (if (isDark) 0.72f else 0.90f) * intensity),
            ),
            start = Offset.Zero,
            end = Offset(w, h),
        ),
        topLeft = Offset(rimStroke / 2f, rimStroke / 2f),
        size = Size((w - rimStroke).coerceAtLeast(1f), (h - rimStroke).coerceAtLeast(1f)),
        cornerRadius = CornerRadius((cornerRadiusPx - rimStroke / 2f).coerceAtLeast(0f)),
        style = Stroke(width = rimStroke),
    )
}

/**
 * 连续无断层 iOS 26 液态水滴放大透镜轨道 (`LiquidLensTrack`)：
 * - 单一手势状态机统一处理点击与连续左右滑动（Scrubbing），自动让出纵向滚动；
 * - 乐观目标索引（`optimisticSelected`）保证松手瞬间原地丝滑凝结，彻底根除回弹旧 Tab 与边界过冲；
 * - 滑动过程支持实时切换（`liveSelectOnScrub = true`），配合持久化悬浮底栏实现全程跟手无断层；
 * - 水滴下方自动挖空底层毛玻璃轨道并实时折射页面内容，内部前景以 `saveLayer + SrcIn` 纯净染为品牌蓝并放大 1.22x，零灰雾、零边缘拉丝、零重复语义节点。
 */
@Composable
fun LiquidLensTrack(
    itemCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 62.dp,
    role: Role = Role.Tab,
    enabled: Boolean = true,
    liveSelectOnScrub: Boolean = true,
    itemContent: @Composable BoxScope.(index: Int, isSelected: Boolean) -> Unit,
) {
    if (itemCount <= 0) return
    val haptic = LocalHapticFeedback.current
    val glass = LedgerTheme.glass
    val primaryColor = MaterialTheme.colorScheme.primary
    val backdropHolder = LocalLiquidBackdropHolder.current
    val foregroundLayer = rememberGraphicsLayer()
    val viewConfig = LocalViewConfiguration.current
    val density = LocalDensity.current
    val selectItem by rememberUpdatedState(onSelect)

    var barSize by remember { mutableStateOf(IntSize.Zero) }
    var barRootPos by remember { mutableStateOf(Offset.Zero) }
    var isInteracting by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var dragCenterX by remember { mutableFloatStateOf(-1f) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var optimisticSelected by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        optimisticSelected = selectedIndex
    }

    val hasSelection = optimisticSelected in 0 until itemCount
    val slotWidthPx = if (barSize.width > 0) barSize.width.toFloat() / itemCount else 1f
    val restingCenterX = if (hasSelection) {
        (optimisticSelected + 0.5f) * slotWidthPx
    } else if (dragCenterX >= 0f) {
        dragCenterX
    } else {
        slotWidthPx * 0.5f
    }

    val targetCenterX = if (isInteracting && dragCenterX >= 0f) dragCenterX else restingCenterX
    val animatedCenterX by animateFloatAsState(
        targetValue = targetCenterX,
        animationSpec = when {
            isInteracting && isScrubbing -> spring(dampingRatio = 0.94f, stiffness = 2600f)
            isInteracting -> spring(dampingRatio = 0.86f, stiffness = 960f)
            else -> spring(dampingRatio = 0.85f, stiffness = 580f)
        },
        label = "lens-center-x",
    )
    val liftProgress by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "lens-lift",
    )
    val stretchAnim by animateFloatAsState(
        targetValue = if (isInteracting) dragVelocity.coerceIn(-0.18f, 0.18f) else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "lens-velocity-stretch",
    )

    val protrudePx = with(density) { 9.dp.toPx() } * liftProgress
    val padPx = with(density) { 4.5.dp.toPx() }
    val basePillW = (slotWidthPx - padPx * 2f).coerceAtLeast(24f)
    val basePillH = (barSize.height.toFloat() - padPx * 2f).coerceAtLeast(20f)
    val lensW = basePillW * (1f + 0.14f * liftProgress + abs(stretchAnim))
    val lensH = (basePillH + protrudePx * 2f) * (1f - abs(stretchAnim) * 0.32f)
    val lensLeft = animatedCenterX - lensW / 2f
    val lensTop = (barSize.height.toFloat() - lensH) / 2f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .zIndex(if (liftProgress > 0.01f) 20f else 1f)
            .onGloballyPositioned {
                barSize = it.size
                barRootPos = it.positionInRoot()
            }
            .graphicsLayer { clip = false }
            .pointerInput(enabled, itemCount, liveSelectOnScrub) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val slot = w / itemCount

                    fun clampThumbX(rawX: Float): Float {
                        val minX = slot * 0.46f
                        val maxX = w - slot * 0.46f
                        return when {
                            rawX < minX -> minX - tanh((minX - rawX) / slot) * (slot * 0.18f)
                            rawX > maxX -> maxX + tanh((rawX - maxX) / slot) * (slot * 0.18f)
                            else -> rawX
                        }
                    }

                    isInteracting = true
                    isScrubbing = false
                    var cancelledByVerticalScroll = false
                    var prevX = down.position.x
                    var lastPos = down.position
                    var smoothVel = 0f
                    dragCenterX = clampThumbX(down.position.x)
                    var currentDetent = (down.position.x / slot).toInt().coerceIn(0, itemCount - 1)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (ch != null && ch.pressed) {
                                lastPos = ch.position
                                val dx = abs(ch.position.x - down.position.x)
                                val dy = abs(ch.position.y - down.position.y)

                                if (!isScrubbing && dy > viewConfig.touchSlop && dy > dx * 1.25f) {
                                    // 用户正在上下滚动外层列表，立即无感让出手势
                                    cancelledByVerticalScroll = true
                                    isInteracting = false
                                    break
                                }
                                if (!isScrubbing && dx > viewConfig.touchSlop && dx >= dy) {
                                    isScrubbing = true
                                }

                                dragCenterX = clampThumbX(ch.position.x)
                                val instVel = ((ch.position.x - prevX) / 46f).coerceIn(-0.18f, 0.18f)
                                smoothVel = smoothVel * 0.70f + instVel * 0.30f
                                dragVelocity = smoothVel
                                prevX = ch.position.x

                                val hovered = (ch.position.x / slot).toInt().coerceIn(0, itemCount - 1)
                                if (hovered != currentDetent) {
                                    currentDetent = hovered
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (liveSelectOnScrub && isScrubbing && hovered != optimisticSelected) {
                                        optimisticSelected = hovered
                                        selectItem(hovered)
                                    }
                                }
                                if (isScrubbing) {
                                    ch.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (!cancelledByVerticalScroll) {
                            val releasedInside = lastPos.y in 0f..h && lastPos.x in 0f..w
                            if (isScrubbing || releasedInside) {
                                val finalIdx = (lastPos.x / slot).toInt().coerceIn(0, itemCount - 1)
                                val changed = finalIdx != optimisticSelected
                                optimisticSelected = finalIdx
                                if (changed || !isScrubbing) {
                                    selectItem(finalIdx)
                                }
                            }
                        }
                    } finally {
                        isInteracting = false
                        isScrubbing = false
                        dragVelocity = 0f
                    }
                }
            },
    ) {
        // 1. 底层毛玻璃胶囊轨道（当水滴升起时，水滴正下方自动挖空去雾，露出真实底层画面）
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (liftProgress > 0.02f && barSize.width > 0) {
                        val inset = 1.dp.toPx()
                        val holeLeft = lensLeft + inset
                        val holeTop = (lensTop + inset).coerceAtLeast(0f)
                        val holeRight = lensLeft + lensW - inset
                        val holeBottom = (lensTop + lensH - inset).coerceAtMost(size.height)
                        if (holeRight > holeLeft && holeBottom > holeTop) {
                            val holePath = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(holeLeft, holeTop, holeRight, holeBottom),
                                        cornerRadius = CornerRadius(lensH / 2f, lensH / 2f),
                                    ),
                                )
                            }
                            clipPath(holePath, clipOp = ClipOp.Difference) {
                                this@drawWithContent.drawContent()
                            }
                        } else {
                            this@drawWithContent.drawContent()
                        }
                    } else {
                        this@drawWithContent.drawContent()
                    }
                }
                .liquidGlass(
                    shape = CircleShape,
                    variant = GlassVariant.Elevated,
                    cornerRadius = height / 2,
                    shadowElevation = 12.dp,
                ),
        )

        // 2. 常态静止选中胶囊（严格限制在胶囊内壁范围内，绝不越界出框）
        if (barSize.width > 0 && hasSelection && liftProgress < 0.98f) {
            val maxLeft = (barSize.width.toFloat() - padPx - basePillW).coerceAtLeast(padPx)
            val clampedPillLeft = (animatedCenterX - basePillW / 2f).coerceIn(padPx, maxLeft)
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = clampedPillLeft.roundToInt(),
                            y = padPx.roundToInt(),
                        )
                    }
                    .size(
                        width = with(density) { basePillW.toDp() },
                        height = with(density) { basePillH.toDp() },
                    )
                    .graphicsLayer { alpha = (1f - liftProgress * 1.08f).coerceIn(0f, 1f) }
                    .liquidGlass(
                        shape = CircleShape,
                        variant = GlassVariant.Tinted(MaterialTheme.colorScheme.primaryContainer),
                        cornerRadius = height / 2,
                        shadowElevation = 2.dp,
                    ),
            )
        }

        // 3. 唯一语义前景行（水滴外部保持原尺寸墨色，水滴内部挖空交给上方凸透镜做 1.22x 纯蓝折射放大）
        Row(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    foregroundLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    if (liftProgress > 0.02f && barSize.width > 0) {
                        val dropPath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(lensLeft, lensTop, lensLeft + lensW, lensTop + lensH),
                                    cornerRadius = CornerRadius(lensH / 2f, lensH / 2f),
                                ),
                            )
                        }
                        clipPath(dropPath, clipOp = ClipOp.Difference) {
                            drawLayer(foregroundLayer)
                        }
                    } else {
                        drawLayer(foregroundLayer)
                    }
                },
        ) {
            for (index in 0 until itemCount) {
                val isSelected = index == optimisticSelected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics(mergeDescendants = true) {
                            this.role = role
                            this.selected = isSelected
                            if (enabled) {
                                onClick {
                                    optimisticSelected = index
                                    onSelect(index)
                                    true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // 当水滴完全升起时，前景底层统一渲染中性墨色，水滴覆盖部分由水滴层精准染成品牌蓝
                    itemContent(index, isSelected && liftProgress < 0.35f)
                }
            }
        }

        // 4. 升起的 3D 清澈凸透镜水滴层（分层架构：背景走 AGSL 曲面折射，前景走纯净矢量放大染色，零雾化零拉丝）
        if (liftProgress > 0.01f && barSize.width > 0) {
            val lensWidthDp = with(density) { lensW.toDp() }
            val lensHeightDp = with(density) { lensH.toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset(lensLeft.roundToInt(), lensTop.roundToInt()) }
                    .size(lensWidthDp, lensHeightDp)
                    .then(
                        if (!IsRobolectricRuntime) {
                            Modifier.shadow(
                                elevation = (14f * liftProgress).dp,
                                shape = CircleShape,
                                ambientColor = Color.Black.copy(alpha = 0.20f),
                                spotColor = primaryColor.copy(alpha = 0.24f),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clip(CircleShape),
            ) {
                // 4a. 底层真实页面 + 极光折射层（仅对背景施加 AGSL 水滴边缘弯月面色散）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .liquidDropletShaderEffect(
                            cornerRadius = lensHeightDp / 2,
                            activeProgress = liftProgress,
                            magnification = 1.12f,
                            dispersionPx = 4.2f,
                        )
                        .drawWithContent {
                            if (backdropHolder != null) {
                                val relX = (barRootPos.x + lensLeft) - backdropHolder.rootOffset.x
                                val relY = (barRootPos.y + lensTop) - backdropHolder.rootOffset.y
                                withTransform({
                                    translate(left = -relX, top = -relY)
                                }) {
                                    drawLayer(backdropHolder.layer)
                                }
                            }
                            // 极清透水晶光泽，绝不发灰发雾
                            drawRect(
                                color = if (glass.isDark) {
                                    Color(0xFF101C32).copy(alpha = 0.28f * liftProgress)
                                } else {
                                    Color.White.copy(alpha = 0.22f * liftProgress)
                                },
                            )
                        },
                )

                // 4b. 水滴内前景 1.22x 锋利蓝色放大层 + 水滴表面张力棱镜高光环
                Canvas(Modifier.fillMaxSize()) {
                    val mag = 1f + 0.22f * liftProgress
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val tintPaint = Paint().apply {
                        colorFilter = ColorFilter.tint(primaryColor, BlendMode.SrcIn)
                    }
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), tintPaint)
                        withTransform({
                            scale(mag, mag, pivot = Offset(cx, cy))
                            translate(left = -lensLeft, top = -lensTop)
                        }) {
                            drawLayer(foregroundLayer)
                        }
                        canvas.restore()
                    }
                    drawWaterDropletRim(
                        cornerRadiusPx = size.height / 2f,
                        isDark = glass.isDark,
                        intensity = liftProgress,
                    )
                }
            }
        }
    }
}

/**
 * 通用液态玻璃卡片/列表行容器：
 * 去除原生灰色涟漪，按住时在触点处平滑浮现清澈凸透镜放大水滴，且智能让出列表纵向滚动。
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    cornerRadius: Dp = 24.dp,
    variant: GlassVariant = GlassVariant.Regular,
    shadowElevation: Dp = 8.dp,
    pressedScale: Float = 0.986f,
    enableTilt: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    role: Role? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val glass = LedgerTheme.glass
    val cardContentLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current

    var fingerPos by remember { mutableStateOf(Offset.Unspecified) }
    var fingerDown by remember { mutableStateOf(false) }
    val showLoupe = onClick != null && enabled && (isPressed || fingerDown) && fingerPos != Offset.Unspecified

    val loupeProgress by animateFloatAsState(
        targetValue = if (showLoupe) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "card-loupe",
    )
    val animatedFinger by animateOffsetAsState(
        targetValue = if (fingerPos != Offset.Unspecified) fingerPos else Offset.Zero,
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 1800f),
        label = "card-loupe-pos",
    )
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && enabled && (isPressed || fingerDown)) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMedium),
        label = "card-scale",
    )

    val clickModifier = when {
        onClick != null && onLongClick != null -> Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClickLabel = onLongClickLabel,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
            onClick = onClick,
        )
        onClick != null -> Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
        else -> Modifier
    }

    val pointerTracker = if (onClick != null && enabled) {
        Modifier.pointerInput(enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                fingerDown = true
                fingerPos = down.position
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                        if (ch != null && ch.pressed) {
                            if (abs(ch.position.y - down.position.y) > viewConfig.touchSlop * 0.95f) {
                                fingerDown = false
                            } else {
                                fingerPos = ch.position
                            }
                        }
                    } while (event.changes.any { it.pressed })
                } finally {
                    fingerDown = false
                }
            }
        }
    } else {
        Modifier
    }

    val loupeW = with(density) { 124.dp.toPx() }
    val loupeH = with(density) { 56.dp.toPx() }

    Column(
        modifier = modifier
            .zIndex(if (loupeProgress > 0.02f) 8f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(pointerTracker)
            .liquidGlass(
                shape = shape,
                variant = variant,
                cornerRadius = cornerRadius,
                shadowElevation = shadowElevation,
                pressProgress = loupeProgress,
                touchOffset = fingerPos,
            )
            .then(clickModifier)
            .drawWithContent {
                if (loupeProgress > 0.02f && fingerPos != Offset.Unspecified) {
                    cardContentLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    val curW = loupeW * (0.82f + 0.18f * loupeProgress)
                    val curH = loupeH * (0.82f + 0.18f * loupeProgress)
                    val left = (animatedFinger.x - curW / 2f).coerceIn(0f, (size.width - curW).coerceAtLeast(0f))
                    val top = (animatedFinger.y - curH / 2f).coerceIn(0f, (size.height - curH).coerceAtLeast(0f))
                    val cx = left + curW / 2f
                    val cy = top + curH / 2f
                    val dropPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(left, top, left + curW, top + curH),
                                cornerRadius = CornerRadius(curH / 2f, curH / 2f),
                            ),
                        )
                    }
                    clipPath(dropPath, clipOp = ClipOp.Difference) {
                        drawLayer(cardContentLayer)
                    }
                    clipPath(dropPath) {
                        drawRect(
                            color = if (glass.isDark) Color(0xFF16243C).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.55f),
                        )
                        withTransform({
                            scale(1f + 0.20f * loupeProgress, 1f + 0.20f * loupeProgress, pivot = Offset(cx, cy))
                        }) {
                            drawLayer(cardContentLayer)
                        }
                    }
                    withTransform({
                        translate(left = left, top = top)
                    }) {
                        val subSize = Size(curW, curH)
                        val r = CornerRadius(curH / 2f, curH / 2f)
                        drawRoundRect(
                            brush = Brush.sweepGradient(
                                0f to Color(0xFF60A5FA).copy(alpha = 0.45f * loupeProgress),
                                0.3f to Color(0xFFFBBF24).copy(alpha = 0.32f * loupeProgress),
                                0.65f to Color(0xFF34D399).copy(alpha = 0.36f * loupeProgress),
                                1f to Color(0xFF60A5FA).copy(alpha = 0.45f * loupeProgress),
                                center = Offset(curW / 2f, curH / 2f),
                            ),
                            size = subSize,
                            cornerRadius = r,
                            style = Stroke(width = 1.8.dp.toPx()),
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.92f * loupeProgress),
                            size = subSize,
                            cornerRadius = r,
                            style = Stroke(width = 1.2.dp.toPx()),
                        )
                    }
                } else {
                    drawContent()
                }
            },
        content = content,
    )
}

/**
 * 全局液态凸透镜胶囊按钮：
 * 按下连续去雾融化（`clearMeltProgress = pressProgress`）、无灰色水波纹闪跳、橡皮筋丝滑跟随。
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = if (prominent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    shape: Shape = CircleShape,
    cornerRadius: Dp = 28.dp,
    minHeight: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val glass = LedgerTheme.glass
    var touchOffset by remember { mutableStateOf(Offset.Unspecified) }
    var active by remember { mutableStateOf(false) }

    val pressProgress by animateFloatAsState(
        targetValue = if (enabled && active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 520f),
        label = "btn-droplet-progress",
    )
    val variant = if (prominent) {
        GlassVariant.Tinted(tint = tint, vivid = true)
    } else {
        GlassVariant.Elevated
    }
    val activeContentColor = if (!prominent) {
        lerp(contentColor, MaterialTheme.colorScheme.primary, pressProgress)
    } else {
        contentColor
    }

    CompositionLocalProvider(LocalContentColor provides activeContentColor.copy(alpha = if (enabled) 1f else 0.55f)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                modifier = modifier
                    .defaultMinSize(minHeight = minHeight)
                    .graphicsLayer { alpha = if (enabled) 1f else 0.48f }
                    .liquidInteractive(
                        interactionSource = interactionSource,
                        enabled = enabled,
                        pressedScale = 1.16f,
                        rubberBandRangeDp = 12f,
                        onTouchOffset = { touchOffset = it },
                        onActiveChange = { active = it },
                    )
                    .liquidGlass(
                        shape = shape,
                        variant = variant,
                        cornerRadius = cornerRadius,
                        shadowElevation = if (active) 16.dp else if (prominent && enabled) 12.dp else 4.dp,
                        pressProgress = pressProgress,
                        clearMeltProgress = if (prominent) 0f else pressProgress * 0.85f,
                        touchOffset = touchOffset,
                    )
                    .drawWithContent {
                        drawContent()
                        if (pressProgress > 0.02f) {
                            drawWaterDropletRim(
                                cornerRadiusPx = size.height / 2f,
                                isDark = glass.isDark,
                                intensity = pressProgress,
                            )
                        }
                    }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        },
                    )
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/**
 * 全局液态水滴圆形图标按钮（连续融化放大 + 橡皮筋跟手 + 零灰色水波纹）。
 */
@Composable
fun LiquidLensIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val glass = LedgerTheme.glass
    var active by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(Offset.Unspecified) }

    val pressProgress by animateFloatAsState(
        targetValue = if (enabled && active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 540f),
        label = "icon-droplet",
    )
    val animatedTint = lerp(tint, MaterialTheme.colorScheme.primary, pressProgress)

    CompositionLocalProvider(LocalContentColor provides animatedTint.copy(alpha = if (enabled) 1f else 0.42f)) {
        Box(
            modifier = modifier
                .size(maxOf(48.dp, size))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                )
                .padding(((48.dp - size) / 2).coerceAtLeast(0.dp))
                .liquidInteractive(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    pressedScale = 1.26f,
                    rubberBandRangeDp = 13f,
                    onTouchOffset = { touchOffset = it },
                    onActiveChange = { active = it },
                )
                .liquidGlass(
                    shape = CircleShape,
                    variant = GlassVariant.Clear,
                    cornerRadius = size / 2,
                    shadowElevation = if (pressProgress > 0.1f) 12.dp else 2.dp,
                    pressProgress = pressProgress,
                    clearMeltProgress = pressProgress * 0.75f,
                    touchOffset = touchOffset,
                )
                .drawWithContent {
                    drawContent()
                    if (pressProgress > 0.02f) {
                        drawWaterDropletRim(
                            cornerRadiusPx = this.size.minDimension / 2f,
                            isDark = glass.isDark,
                            intensity = pressProgress,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * 全局液态水滴筛选胶囊（保留 Role.Checkbox 与 selected 语义，无灰色水波纹，连续融化水滴动画）。
 */
@Composable
fun LiquidLensChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val glass = LedgerTheme.glass
    var active by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(Offset.Unspecified) }

    val pressProgress by animateFloatAsState(
        targetValue = if (enabled && active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 520f),
        label = "chip-droplet",
    )
    val baseFg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val fgColor = lerp(baseFg, MaterialTheme.colorScheme.primary, pressProgress)

    CompositionLocalProvider(LocalContentColor provides fgColor.copy(alpha = if (enabled) 1f else 0.45f)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                modifier = modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .liquidInteractive(
                        interactionSource = interactionSource,
                        enabled = enabled,
                        pressedScale = 1.18f,
                        rubberBandRangeDp = 11f,
                        onTouchOffset = { touchOffset = it },
                        onActiveChange = { active = it },
                    )
                    .liquidGlass(
                        shape = CircleShape,
                        variant = if (selected) {
                            GlassVariant.Tinted(MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            GlassVariant.Regular
                        },
                        cornerRadius = 20.dp,
                        shadowElevation = if (pressProgress > 0.1f) 10.dp else if (selected) 4.dp else 1.dp,
                        pressProgress = pressProgress,
                        clearMeltProgress = pressProgress * 0.80f,
                        touchOffset = touchOffset,
                    )
                    .drawWithContent {
                        drawContent()
                        if (pressProgress > 0.02f) {
                            drawWaterDropletRim(
                                cornerRadiusPx = size.height / 2f,
                                isDark = glass.isDark,
                                intensity = pressProgress,
                            )
                        }
                    }
                    .toggleable(
                        value = selected,
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (leadingIcon != null) leadingIcon()
                label()
                if (trailingIcon != null) trailingIcon()
            }
        }
    }
}

/**
 * 悬浮通透玻璃顶栏。
 */
@Composable
fun LiquidGlassTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glass = LedgerTheme.glass
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                variant = GlassVariant.Elevated,
                cornerRadius = 24.dp,
                shadowElevation = 6.dp,
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (navigationIcon != null) {
            navigationIcon()
        }
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = if (navigationIcon == null) 8.dp else 2.dp),
        ) {
            ProvideTextStyle(
                MaterialTheme.typography.titleLarge.copy(
                    color = if (glass.isDark) Color(0xFFEAF2FF) else Color(0xFF0F1A2C),
                ),
            ) {
                title()
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
