package com.zhuangxiu.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ---------- 温暖家居风配色：奶油白、木色、陶土橙、浅棕 ----------

private val LightColors = lightColorScheme(
    primary = Color(0xFFC0603A),            // 陶土橙
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3A1505),
    secondary = Color(0xFF8B6B4A),          // 木色
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3DFC8),
    onSecondaryContainer = Color(0xFF2E1F0E),
    tertiary = Color(0xFF6F6A3A),           // 橄榄绿，做少量点缀
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1E9B8),
    onTertiaryContainer = Color(0xFF242105),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCDAD5),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFBF5EC),         // 奶油白
    onBackground = Color(0xFF2B211A),
    surface = Color(0xFFFBF5EC),
    onSurface = Color(0xFF2B211A),
    surfaceVariant = Color(0xFFF1E4D4),
    onSurfaceVariant = Color(0xFF6B5B4D),
    outline = Color(0xFFA39382),
    outlineVariant = Color(0xFFE2D4C2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFAF3),
    surfaceContainer = Color(0xFFF7EDE0),
    surfaceContainerHigh = Color(0xFFF2E6D7),
    surfaceContainerHighest = Color(0xFFECDFCE),
)

/** 暖色调的深色模式：深咖底色 + 柔和的陶土橙 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB595),
    onPrimary = Color(0xFF5A1F05),
    primaryContainer = Color(0xFF7A3414),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFDDC3A5),
    onSecondary = Color(0xFF3E2D18),
    secondaryContainer = Color(0xFF56432C),
    onSecondaryContainer = Color(0xFFF9DEC0),
    tertiary = Color(0xFFD5CD91),
    onTertiary = Color(0xFF39360F),
    tertiaryContainer = Color(0xFF504D24),
    onTertiaryContainer = Color(0xFFF1E9B8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1C1714),
    onBackground = Color(0xFFEFE0D2),
    surface = Color(0xFF1C1714),
    onSurface = Color(0xFFEFE0D2),
    surfaceVariant = Color(0xFF52443A),
    onSurfaceVariant = Color(0xFFD7C3B3),
    outline = Color(0xFF9F8D7F),
    outlineVariant = Color(0xFF4A3E35),
    surfaceContainerLowest = Color(0xFF16110E),
    surfaceContainerLow = Color(0xFF241E1A),
    surfaceContainer = Color(0xFF29221D),
    surfaceContainerHigh = Color(0xFF342C26),
    surfaceContainerHighest = Color(0xFF3F3630),
)

/** 圆角统一偏大，营造柔和的家居感 */
private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Material 配色之外的补充颜色：预警色和图表色板 */
data class ExtraColors(
    val warning: Color,
    val success: Color,
    val chart: List<Color>,
)

private val LightExtra = ExtraColors(
    warning = Color(0xFFB7791F),
    success = Color(0xFF5E7D4A),
    chart = listOf(
        Color(0xFFC0603A), Color(0xFFD9A05B), Color(0xFF8B6B4A), Color(0xFF7D8F5C), Color(0xFFB98A8A),
        Color(0xFF5F8A8B), Color(0xFFE0B8A0), Color(0xFF9C7BA8), Color(0xFFA8A17A), Color(0xFF6E5A4E),
    ),
)

private val DarkExtra = ExtraColors(
    warning = Color(0xFFF2C46D),
    success = Color(0xFFA9C993),
    chart = listOf(
        Color(0xFFFFB595), Color(0xFFE8C07F), Color(0xFFC4A283), Color(0xFFA9BC87), Color(0xFFD9A9A9),
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
            content = content,
        )
    }
}
