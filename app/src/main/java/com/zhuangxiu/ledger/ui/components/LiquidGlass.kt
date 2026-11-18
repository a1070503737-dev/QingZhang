package com.zhuangxiu.ledger.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * 检测是否运行在 Robolectric 本地单元测试环境。
 * 测试环境下自动冻结无限流光动画并跳过原生 AGSL RuntimeShader，确保 ComposeTestRule 零阻塞秒过。
 */
internal val IsRobolectricRuntime: Boolean by lazy {
    val fp = Build.FINGERPRINT.orEmpty()
    val model = Build.MODEL.orEmpty()
    fp.contains("robolectric", ignoreCase = true) || model.contains("robolectric", ignoreCase = true)
}

/** 标记外层是否已挂载全局极光玻璃背景，避免嵌套页面重复绘制多层全屏背景 */
val LocalLiquidBackdropActive = staticCompositionLocalOf { false }

/** Liquid Glass 光学层级变体 */
@Immutable
sealed interface GlassVariant {
    /** 标准通透液态玻璃（用于账单卡片、设置分组、列表容器） */
    data object Regular : GlassVariant

    /** 悬浮高亮液态透镜（用于底部悬浮胶囊栏、顶部栏、弹窗、键盘按键） */
    data object Elevated : GlassVariant

    /** 轻薄水晶胶囊（用于搜索框、小标签、分段控制器槽位） */
    data object Clear : GlassVariant

    /** 有色折射液态玻璃（用于结余主卡、装修总预算卡、主操作按钮、告警卡） */
    data class Tinted(val tint: Color, val vivid: Boolean = false) : GlassVariant
}

/**
 * Android 13+ (API 33+) GPU AGSL 液态透镜着色器：
 * 基于圆角矩形 SDF 计算边缘曲率法线、左上高光反射、微棱镜色散与触控水波焦散。
 */
private const val LIQUID_GLASS_AGSL = """
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

    // 曲面边缘菲涅尔折射亮带（距离边缘 26px 内呈指数衰减）
    float fresnel = exp(max(d, -28.0) * 0.11);
    float catchlight = clamp((1.0 - uv.x) * 0.62 + (1.0 - uv.y) * 0.68 - 0.32, 0.0, 1.0);
    float counterGlow = clamp(uv.x * 0.52 + uv.y * 0.58 - 0.35, 0.0, 1.0);

    // 微色散冷暖边缘偏移
    half3 dispersion = half3(
        0.95 + 0.05 * catchlight,
        0.98 + 0.02 * counterGlow,
        1.0
    );

    // 手指按压位置的液态表面张力焦散扩散
    float distTouch = distance(fragCoord, uTouch);
    float caustic = uPress * exp(-distTouch / max(min(res.x, res.y) * 0.65, 56.0));

    half3 specularTint = mix(uTintColor.rgb, half3(1.0, 1.0, 1.0), half(uDark > 0.5 ? 0.58 : 0.78)) * dispersion;
    float sheen = fresnel * (0.14 + 0.24 * catchlight + 0.10 * counterGlow) + 0.18 * caustic;
    base.rgb = mix(base.rgb, specularTint, half(clamp(sheen, 0.0, 0.52)));
    base.a = clamp(base.a + half(fresnel * (uDark > 0.5 ? 0.08 : 0.14) + caustic * 0.09), half(0.0), half(0.97));
    return base;
}
"""

/**
 * 全局流动极光环境底板：
 * 为半透明液态玻璃提供真实可透射的多层柔和色彩光场，使滚动与切换时呈现灵动的折射层次。
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (LocalLiquidBackdropActive.current) {
        Box(modifier = modifier, content = content)
        return
    }
    val glass = LedgerTheme.glass
    val baseBg = MaterialTheme.colorScheme.background
    val phase = if (IsRobolectricRuntime) {
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
        Box(modifier = modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(baseBg)
                val w = size.width
                val h = size.height
                val rMax = maxOf(w, h)

                val c1 = Offset(
                    x = w * (0.18f + 0.10f * cos(phase)),
                    y = h * (0.12f + 0.07f * sin(phase)),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.auraPrimary, Color.Transparent),
                        center = c1,
                        radius = rMax * 0.58f,
                    ),
                    radius = rMax * 0.58f,
                    center = c1,
                )

                val c2 = Offset(
                    x = w * (0.85f - 0.11f * sin(phase * 0.8f)),
                    y = h * (0.34f + 0.09f * cos(phase * 0.8f)),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.auraSecondary, Color.Transparent),
                        center = c2,
                        radius = rMax * 0.52f,
                    ),
                    radius = rMax * 0.52f,
                    center = c2,
                )

                val c3 = Offset(
                    x = w * (0.28f + 0.12f * sin(phase * 0.65f + 1.1f)),
                    y = h * (0.78f - 0.08f * cos(phase * 0.65f + 1.1f)),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.auraTertiary, Color.Transparent),
                        center = c3,
                        radius = rMax * 0.56f,
                    ),
                    radius = rMax * 0.56f,
                    center = c3,
                )

                val c4 = Offset(
                    x = w * (0.76f + 0.07f * cos(phase + 2.2f)),
                    y = h * (0.86f + 0.05f * sin(phase + 2.2f)),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.auraWarm, Color.Transparent),
                        center = c4,
                        radius = rMax * 0.44f,
                    ),
                    radius = rMax * 0.44f,
                    center = c4,
                )
            }
            content()
        }
    }
}

/**
 * 核心液态玻璃光学表面修饰符：
 * 叠加柔和空间阴影、AGSL/多层折射渐变基底、内部菲涅尔弯月面亮边与 360° 镜面高光描边。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    variant: GlassVariant = GlassVariant.Regular,
    cornerRadius: Dp = 24.dp,
    shadowElevation: Dp = 10.dp,
    pressProgress: Float = 0f,
    touchOffset: Offset = Offset.Unspecified,
): Modifier {
    val glass = LedgerTheme.glass
    val primary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }

    val (topColor, bottomColor, accentColor) = when (variant) {
        GlassVariant.Regular -> Triple(glass.surfaceTop, glass.surfaceBottom, primary)
        GlassVariant.Elevated -> Triple(glass.elevatedTop, glass.elevatedBottom, primary)
        GlassVariant.Clear -> Triple(
            glass.surfaceTop.copy(alpha = if (glass.isDark) 0.46f else 0.56f),
            glass.surfaceBottom.copy(alpha = if (glass.isDark) 0.30f else 0.38f),
            primary,
        )
        is GlassVariant.Tinted -> {
            if (variant.vivid) {
                Triple(
                    lerp(variant.tint, Color.White, if (glass.isDark) 0.08f else 0.14f).copy(alpha = 0.88f),
                    lerp(variant.tint, Color.Black, 0.18f).copy(alpha = 0.80f),
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

    val specularBoost = (pressProgress * 0.28f).coerceIn(0f, 0.3f)
    val rimBright = glass.specularBright.copy(
        alpha = (glass.specularBright.alpha + specularBoost).coerceAtMost(1f),
    )
    val rimMid = lerp(glass.specularMid, accentColor.copy(alpha = 0.32f), 0.4f)
    val rimCounter = glass.specularCounter.copy(
        alpha = (glass.specularCounter.alpha + specularBoost * 0.6f).coerceAtMost(1f),
    )
    val innerTopGlow = glass.innerGlow.copy(
        alpha = (glass.innerGlow.alpha + specularBoost * 0.5f).coerceAtMost(0.95f),
    )

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

            val agslBrush: ShaderBrush? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !IsRobolectricRuntime) {
                runCatching {
                    val shader = RuntimeShader(LIQUID_GLASS_AGSL)
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

            val fallbackBodyBrush = Brush.linearGradient(
                colors = listOf(topColor, bottomColor),
                start = Offset.Zero,
                end = Offset(w, h),
            )
            val topSheenBrush = Brush.verticalGradient(
                colors = listOf(innerTopGlow, Color.Transparent),
                startY = 0f,
                endY = (h * 0.42f).coerceAtLeast(12f),
            )
            val rimBrush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to rimBright,
                    0.46f to rimMid,
                    1.0f to rimCounter,
                ),
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
                    if (pressProgress > 0.01f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = (if (glass.isDark) 0.16f else 0.28f) * pressProgress),
                                    Color.Transparent,
                                ),
                                center = actualTouch,
                                radius = maxOf(w, h) * 0.7f,
                            ),
                            radius = maxOf(w, h) * 0.7f,
                            center = actualTouch,
                        )
                    }
                }
                // 顶部弯月面光学亮膜
                drawRect(brush = topSheenBrush)
                // 360° 镜面高光折射描边
                when (val outline = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Rounded -> drawRoundRect(
                        brush = rimBrush,
                        topLeft = Offset(halfStroke, halfStroke),
                        size = Size((w - strokePx).coerceAtLeast(0f), (h - strokePx).coerceAtLeast(0f)),
                        cornerRadius = corner,
                        style = Stroke(width = strokePx),
                    )
                    is Outline.Generic -> drawPath(
                        path = outline.path,
                        brush = rimBrush,
                        style = Stroke(width = strokePx),
                    )
                    is Outline.Rectangle -> drawRect(
                        brush = rimBrush,
                        style = Stroke(width = strokePx),
                    )
                }
            }
        }
}

/**
 * 液态凝胶弹性按压物理反馈：
 * 支持小控件按压膨胀（pressedScale > 1f，模拟水滴受压散开发光）或大卡片微缩回弹（pressedScale < 1f）与轻微立体倾角。
 */
fun Modifier.liquidInteractive(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.972f,
    enableTilt: Boolean = false,
    onTouchOffset: ((Offset) -> Unit)? = null,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    var lastTouch by remember { mutableStateOf(Offset.Unspecified) }
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.56f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "liquid-scale",
    )
    val tiltProgress by animateFloatAsState(
        targetValue = if (enabled && isPressed && enableTilt) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "liquid-tilt",
    )

    this
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                lastTouch = down.position
                onTouchOffset?.invoke(down.position)
                waitForUpOrCancellation()
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            if (enableTilt && lastTouch != Offset.Unspecified && size.width > 0 && size.height > 0) {
                val nx = ((lastTouch.x / size.width) - 0.5f) * 2f
                val ny = ((lastTouch.y / size.height) - 0.5f) * 2f
                rotationY = nx * 2.2f * tiltProgress
                rotationX = -ny * 2.0f * tiltProgress
                cameraDistance = 14f * density
            }
        }
}

/**
 * 通用液态玻璃容器：支持点击/长按弹性形变、水波焦散追踪与触觉微反馈。
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    cornerRadius: Dp = 24.dp,
    variant: GlassVariant = GlassVariant.Regular,
    shadowElevation: Dp = 8.dp,
    pressedScale: Float = 0.974f,
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
    var touchOffset by remember { mutableStateOf(Offset.Unspecified) }
    val pressProgress by animateFloatAsState(
        targetValue = if (onClick != null && enabled && isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "liquid-press-sheen",
    )

    val clickModifier = when {
        onClick != null && onLongClick != null -> Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
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
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
        else -> Modifier
    }

    val interactiveModifier = if (onClick != null) {
        Modifier.liquidInteractive(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedScale = pressedScale,
            enableTilt = enableTilt,
            onTouchOffset = { touchOffset = it },
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .then(interactiveModifier)
            .liquidGlass(
                shape = shape,
                variant = variant,
                cornerRadius = cornerRadius,
                shadowElevation = shadowElevation,
                pressProgress = pressProgress,
                touchOffset = touchOffset,
            )
            .then(clickModifier),
        content = content,
    )
}

/**
 * 液态玻璃胶囊主按钮 / 次级玻璃按钮：具备果冻膨胀按压反馈与镜面高光边缘。
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
    val isPressed by interactionSource.collectIsPressedAsState()
    var touchOffset by remember { mutableStateOf(Offset.Unspecified) }
    val pressProgress by animateFloatAsState(
        targetValue = if (enabled && isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium),
        label = "btn-press",
    )
    val variant = if (prominent) {
        GlassVariant.Tinted(tint = tint, vivid = true)
    } else {
        GlassVariant.Elevated
    }
    val effectiveAlpha = if (enabled) 1f else 0.48f

    CompositionLocalProvider(LocalContentColor provides contentColor.copy(alpha = if (enabled) 1f else 0.6f)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Row(
                modifier = modifier
                    .defaultMinSize(minHeight = minHeight)
                    .graphicsLayer { alpha = effectiveAlpha }
                    .liquidInteractive(
                        interactionSource = interactionSource,
                        enabled = enabled,
                        pressedScale = 1.03f,
                        onTouchOffset = { touchOffset = it },
                    )
                    .liquidGlass(
                        shape = shape,
                        variant = variant,
                        cornerRadius = cornerRadius,
                        shadowElevation = if (prominent && enabled) 12.dp else 4.dp,
                        pressProgress = pressProgress,
                        touchOffset = touchOffset,
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
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
 * 悬浮通透玻璃顶栏：滚动内容在下方柔和透出，底部带有一道微弱镜面分割光线。
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
        Box(Modifier.weight(1f).padding(horizontal = if (navigationIcon == null) 8.dp else 2.dp)) {
            ProvideTextStyle(
                MaterialTheme.typography.titleLarge.copy(
                    color = if (glass.isDark) Color(0xFFEAF2FF) else Color(0xFF0F1A2C),
                ),
            ) {
                title()
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
