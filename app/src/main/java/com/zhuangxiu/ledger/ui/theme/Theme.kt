package com.zhuangxiu.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Liquid Glass 主题系统：通透冰晶浅色 + 深海极光深色。
 * 保留全部原有 MaterialTheme 与 LedgerTheme.extra 接口，确保全工程与测试 100% 兼容。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),            // 光学冰晶蓝
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0D275C),
    secondary = Color(0xFF476282),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE6F5),
    onSecondaryContainer = Color(0xFF182A44),
    tertiary = Color(0xFF128061),           // 翡翠液态绿
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6F3E8),
    onTertiaryContainer = Color(0xFF0A382A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFEFF4FC),
    onBackground = Color(0xFF111C2D),
    surface = Color(0xFFF4F8FF),
    onSurface = Color(0xFF111C2D),
    surfaceVariant = Color(0xFFDFE8F5),
    onSurfaceVariant = Color(0xFF4E5D75),
    outline = Color(0xFF7B8CA6),
    outlineVariant = Color(0xFFCFD9EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAFF),
    surfaceContainer = Color(0xFFEAF1FB),
    surfaceContainerHigh = Color(0xFFE1EBF8),
    surfaceContainerHighest = Color(0xFFD6E3F4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003166),
    primaryContainer = Color(0xFF1B4484),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFFB3C7E6),
    onSecondary = Color(0xFF1C304A),
    secondaryContainer = Color(0xFF2E4361),
    onSecondaryContainer = Color(0xFFD9E6FC),
    tertiary = Color(0xFF7CE0BD),
    onTertiary = Color(0xFF003828),
    tertiaryContainer = Color(0xFF0E513D),
    onTertiaryContainer = Color(0xFF9DF8D6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B111E),
    onBackground = Color(0xFFE5EEFC),
    surface = Color(0xFF0E1626),
    onSurface = Color(0xFFE5EEFC),
    surfaceVariant = Color(0xFF28354C),
    onSurfaceVariant = Color(0xFFB7C5DD),
    outline = Color(0xFF7F90AC),
    outlineVariant = Color(0xFF2D3C56),
    surfaceContainerLowest = Color(0xFF080D18),
    surfaceContainerLow = Color(0xFF131D31),
    surfaceContainer = Color(0xFF19263E),
    surfaceContainerHigh = Color(0xFF21304C),
    surfaceContainerHighest = Color(0xFF2A3B5B),
)

/** Liquid Glass 同心大曲率圆角几何规范 */
private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val LedgerTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Material 配色之外的补充颜色：预警色和图表色板 */
data class ExtraColors(
    val warning: Color,
    val success: Color,
    val chart: List<Color>,
)

/** Liquid Glass 光学材质参数令牌 */
data class LiquidGlassPalette(
    val isDark: Boolean,
    val surfaceTop: Color,
    val surfaceBottom: Color,
    val elevatedTop: Color,
    val elevatedBottom: Color,
    val dockTop: Color,
    val dockBottom: Color,
    val specularBright: Color,
    val specularMid: Color,
    val specularCounter: Color,
    val innerGlow: Color,
    val ambientShadow: Color,
    val auraPrimary: Color,
    val auraSecondary: Color,
    val auraTertiary: Color,
    val auraWarm: Color,
)

private val LightExtra = ExtraColors(
    warning = Color(0xFFD97706),
    success = Color(0xFF0D8A5E),
    chart = listOf(
        Color(0xFF2563EB), Color(0xFF0EA5E9), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6),
        Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFF6366F1), Color(0xFFF97316), Color(0xFF64748B),
    ),
)

private val DarkExtra = ExtraColors(
    warning = Color(0xFFFBBF24),
    success = Color(0xFF5EEAD4),
    chart = listOf(
        Color(0xFF93C5FD), Color(0xFF67E8F9), Color(0xFF6EE7B7), Color(0xFFFCD34D), Color(0xFFC4B5FD),
        Color(0xFFF9A8D4), Color(0xFF5EEAD4), Color(0xFFA5B4FC), Color(0xFFFDBA74), Color(0xFF94A3B8),
    ),
)

private val LightGlassPalette = LiquidGlassPalette(
    isDark = false,
    surfaceTop = Color(0xBDFFFFFF),        // 74% 纯白折射层
    surfaceBottom = Color(0x8AF2F7FF),     // 54% 冰蓝透射层
    elevatedTop = Color(0xDBFFFFFF),       // 86% 悬浮高亮层
    elevatedBottom = Color(0xB8EEF5FF),    // 72% 悬浮底透层
    dockTop = Color(0xD6FFFFFF),           // 84% 液态胶囊顶层
    dockBottom = Color(0xADEAF3FF),        // 68% 液态胶囊底层
    specularBright = Color(0xF2FFFFFF),    // 左上高光反射边缘
    specularMid = Color(0x3860A5FA),       // 棱镜微色散过渡
    specularCounter = Color(0x99FFFFFF),   // 右下反向菲涅尔亮边
    innerGlow = Color(0x75FFFFFF),
    ambientShadow = Color(0x221E40AF),
    auraPrimary = Color(0x4760A5FA),       // 天青光斑
    auraSecondary = Color(0x3834D399),     // 薄荷极光
    auraTertiary = Color(0x33A78BFA),      // 鸢尾紫雾
    auraWarm = Color(0x2EFBBF24),          // 暖金微光
)

private val DarkGlassPalette = LiquidGlassPalette(
    isDark = true,
    surfaceTop = Color(0xB31B2842),        // 70% 深海蓝晶顶层
    surfaceBottom = Color(0x85111B2E),     // 52% 深海透射底层
    elevatedTop = Color(0xCC223252),
    elevatedBottom = Color(0xA6162238),
    dockTop = Color(0xCC1E2D4A),
    dockBottom = Color(0xA3121C30),
    specularBright = Color(0x85E2EEFF),
    specularMid = Color(0x3360A5FA),
    specularCounter = Color(0x4793C5FD),
    innerGlow = Color(0x339ECAFF),
    ambientShadow = Color(0x66020617),
    auraPrimary = Color(0x422563EB),
    auraSecondary = Color(0x3310B981),
    auraTertiary = Color(0x337C3AED),
    auraWarm = Color(0x24F59E0B),
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtra }
private val LocalLiquidGlassPalette = staticCompositionLocalOf { LightGlassPalette }

/** 取补充颜色与液态玻璃光学令牌：`LedgerTheme.extra` / `LedgerTheme.glass` */
object LedgerTheme {
    val extra: ExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtraColors.current

    val glass: LiquidGlassPalette
        @Composable @ReadOnlyComposable get() = LocalLiquidGlassPalette.current
}

@Composable
fun RenovationLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(
        LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra,
        LocalLiquidGlassPalette provides if (darkTheme) DarkGlassPalette else LightGlassPalette,
        LocalContentColor provides colors.onSurface,
    ) {
        MaterialTheme(
            colorScheme = colors,
            shapes = LedgerShapes,
            typography = LedgerTypography,
            content = content,
        )
    }
}
