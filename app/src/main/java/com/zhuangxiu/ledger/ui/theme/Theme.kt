package com.zhuangxiu.ledger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 系统动态配色不可用时的 Material 3 配色。 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF3E59AB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE5FF),
    onPrimaryContainer = Color(0xFF10266D),
    secondary = Color(0xFF565E78),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E5FA),
    onSecondaryContainer = Color(0xFF1B253A),
    tertiary = Color(0xFF128061),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6F3E8),
    onTertiaryContainer = Color(0xFF0A382A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF111C2D),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF111C2D),
    surfaceVariant = Color(0xFFDFE8F5),
    onSurfaceVariant = Color(0xFF4E5D75),
    outline = Color(0xFF7B8CA6),
    outlineVariant = Color(0xFFCFD9EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F2FA),
    surfaceContainer = Color(0xFFECEEF6),
    surfaceContainerHigh = Color(0xFFE5E7F1),
    surfaceContainerHighest = Color(0xFFDEE1EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC7FF),
    onPrimary = Color(0xFF1B327A),
    primaryContainer = Color(0xFF2D438F),
    onPrimaryContainer = Color(0xFFDEE5FF),
    inversePrimary = Color(0xFF555555),
    secondary = Color(0xFFCCCCCC),
    onSecondary = Color(0xFF252525),
    secondaryContainer = Color(0xFF292929),
    onSecondaryContainer = Color(0xFFE6E6E6),
    tertiary = Color(0xFF7CE0BD),
    onTertiary = Color(0xFF003828),
    tertiaryContainer = Color(0xFF0E513D),
    onTertiaryContainer = Color(0xFF9DF8D6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black,
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF101010),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceTint = Color(0xFFBDBDBD),
    inverseSurface = Color(0xFFE6E6E6),
    inverseOnSurface = Color(0xFF252525),
    outline = Color(0xFF818181),
    outlineVariant = Color(0xFF343434),
    surfaceBright = Color(0xFF353535),
    surfaceDim = Color(0xFF101010),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF141414),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF222222),
    surfaceContainerHighest = Color(0xFF2C2C2C),
)

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

/** Material 配色之外的账目、图表和预警颜色。 */
data class ExtraColors(
    val warning: Color,
    val income: Color,
    val expense: Color,
    val incomeContainer: Color,
    val expenseContainer: Color,
    val expenseChart: List<Color>,
) {
    fun transaction(income: Boolean): Color = if (income) this.income else expense

    fun balance(cents: Long, neutral: Color): Color = when {
        cents > 0 -> income
        cents < 0 -> expense
        else -> neutral
    }
}

private val LightExtra = ExtraColors(
    warning = Color(0xFFD97706),
    income = Color(0xFFB42324),
    expense = Color(0xFF12653E),
    incomeContainer = Color(0xFFFCE8E6),
    expenseContainer = Color(0xFFE0F3E6),
    expenseChart = listOf(
        Color(0xFF12653E), Color(0xFF439B69), Color(0xFF85BC48), Color(0xFF286B58), Color(0xFF9BCDB0),
        Color(0xFF547D33), Color(0xFF24A575), Color(0xFF456A43), Color(0xFF5EAC95), Color(0xFF739850),
    ),
)

private val DarkExtra = ExtraColors(
    warning = Color(0xFFFBBF24),
    income = Color(0xFFFF9D96),
    expense = Color(0xFF7CE0A6),
    incomeContainer = Color(0xFF482322),
    expenseContainer = Color(0xFF1B3A2C),
    expenseChart = listOf(
        Color(0xFF7CE0A6), Color(0xFF3DA778), Color(0xFFA5D76D), Color(0xFF50977E), Color(0xFFB3E7CD),
        Color(0xFF7A9C51), Color(0xFF56D1B0), Color(0xFF8EB384), Color(0xFF2C8869), Color(0xFF8AC164),
    ),
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtra }
private val LocalDarkTheme = staticCompositionLocalOf { false }

object LedgerTheme {
    val extra: ExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtraColors.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalDarkTheme.current
}

@Composable
fun RenovationLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context).copy(background = Color.Black, surfaceContainerLowest = Color.Black)
        else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        DarkColors
    } else {
        LightColors
    }
    CompositionLocalProvider(
        LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra,
        LocalDarkTheme provides darkTheme,
        LocalContentColor provides colors.onSurface,
    ) {
        MaterialTheme(colorScheme = colors, shapes = LedgerShapes, typography = LedgerTypography, content = content)
    }
}
