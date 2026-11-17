package com.zhuangxiu.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 轻账：蓝色品牌、浅灰背景，收入使用独立绿色。

private val LightColors = lightColorScheme(
    primary = Color(0xFF3568D4),            // 品牌蓝
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFE8FF),
    onPrimaryContainer = Color(0xFF132E66),
    secondary = Color(0xFF596985),          // 暖木色
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E8F4),
    onSecondaryContainer = Color(0xFF20314F),
    tertiary = Color(0xFF27765A),           // 橄榄绿，做少量点缀
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0EDD8),
    onTertiaryContainer = Color(0xFF20331B),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCDAD5),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF6F8FC),         // 浅灰白
    onBackground = Color(0xFF1C2536),
    surface = Color(0xFFF6F8FC),
    onSurface = Color(0xFF1C2536),
    surfaceVariant = Color(0xFFE5EAF3),
    onSurfaceVariant = Color(0xFF5C687D),
    outline = Color(0xFF8793A8),
    outlineVariant = Color(0xFFDEE4EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDF1F8),
    surfaceContainerHigh = Color(0xFFE6ECF6),
    surfaceContainerHighest = Color(0xFFDDE5F1),
)

/** 深色模式：深松绿底色与柔和的浅绿色。 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0C8FF),
    onPrimary = Color(0xFF0E2F68),
    primaryContainer = Color(0xFF26487E),
    onPrimaryContainer = Color(0xFFDFE8FF),
    secondary = Color(0xFFB7C6E2),
    onSecondary = Color(0xFF1E2C44),
    secondaryContainer = Color(0xFF36455F),
    onSecondaryContainer = Color(0xFFDDE7FA),
    tertiary = Color(0xFFBED5AB),
    onTertiary = Color(0xFF293C20),
    tertiaryContainer = Color(0xFF405636),
    onTertiaryContainer = Color(0xFFE0EDD8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121824),
    onBackground = Color(0xFFE3E9F4),
    surface = Color(0xFF121824),
    onSurface = Color(0xFFE3E9F4),
    surfaceVariant = Color(0xFF414B5E),
    onSurfaceVariant = Color(0xFFC3CDDF),
    outline = Color(0xFF8E9AB0),
    outlineVariant = Color(0xFF354156),
    surfaceContainerLowest = Color(0xFF0D121D),
    surfaceContainerLow = Color(0xFF1A2333),
    surfaceContainer = Color(0xFF222D40),
    surfaceContainerHigh = Color(0xFF2A364B),
    surfaceContainerHighest = Color(0xFF334158),
)

/** 圆角统一偏大，营造柔和的家居感 */
private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LedgerTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
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

private val LightExtra = ExtraColors(
    warning = Color(0xFFB7791F),
    success = Color(0xFF18734B),
    chart = listOf(
        Color(0xFF3568D4), Color(0xFFD9A05B), Color(0xFF596985), Color(0xFF7D8F5C), Color(0xFFB98A8A),
        Color(0xFF5F8A8B), Color(0xFFE0B8A0), Color(0xFF9C7BA8), Color(0xFFA8A17A), Color(0xFF6E5A4E),
    ),
)

private val DarkExtra = ExtraColors(
    warning = Color(0xFFF2C46D),
    success = Color(0xFF83D8AC),
    chart = listOf(
        Color(0xFFB0C8FF), Color(0xFFE8C07F), Color(0xFFC4A283), Color(0xFFA9BC87), Color(0xFFD9A9A9),
        Color(0xFF8DB8B9), Color(0xFFF0CDB8), Color(0xFFC0A3CB), Color(0xFFC9C39C), Color(0xFFA08B7E),
    ),
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtra }

/** 取补充颜色：`LedgerTheme.extra.warning` */
object LedgerTheme {
    val extra: ExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtraColors.current
}

@Composable
fun RenovationLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = LedgerShapes,
            typography = LedgerTypography,
            content = content,
        )
    }
}
