package app.navi.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.navi.Store

// Generated with Material Color Utilities (SchemeTonalSpot, spec 2025) from the logo's seeds:
// teal route #1FA39A for primary/secondary/neutrals, coral pin #F0603F for tertiary.
// TonalSpot keeps surfaces low-chroma, so nothing glares; coral only accents.
private val Dark = darkColorScheme(
    primary = Color(0xFF9CD1CA),
    onPrimary = Color(0xFF0E4844),
    primaryContainer = Color(0xFF265B56),
    onPrimaryContainer = Color(0xFFB8EEE7),
    inversePrimary = Color(0xFF346763),
    secondary = Color(0xFFB0CCC8),
    onSecondary = Color(0xFF2C4542),
    secondaryContainer = Color(0xFF27403D),
    onSecondaryContainer = Color(0xFFA9C5C1),
    error = Color(0xFFFA746F),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF871F21),
    onErrorContainer = Color(0xFFFF9993),
    background = Color(0xFF0A0F0E),
    onBackground = Color(0xFFDCE8E5),
    surface = Color(0xFF0A0F0E),
    onSurface = Color(0xFFDCE8E5),
    surfaceVariant = Color(0xFF1D2826),
    onSurfaceVariant = Color(0xFFA2ADAB),
    surfaceTint = Color(0xFF9CD1CA),
    surfaceDim = Color(0xFF0A0F0E),
    surfaceBright = Color(0xFF242E2D),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0E1514),
    surfaceContainer = Color(0xFF131B1A),
    surfaceContainerHigh = Color(0xFF192120),
    surfaceContainerHighest = Color(0xFF1D2826),
    inverseSurface = Color(0xFFF6FAF8),
    inverseOnSurface = Color(0xFF515655),
    outline = Color(0xFF6D7876),
    outlineVariant = Color(0xFF3F4A48),
    scrim = Color(0xFF000000),
    tertiary = Color(0xFFF9B7A7),
    onTertiary = Color(0xFF613429),
    tertiaryContainer = Color(0xFF76463A),
    onTertiaryContainer = Color(0xFFFFDBD3),
)

private val Light = lightColorScheme(
    primary = Color(0xFF1D6A64),
    onPrimary = Color(0xFFE1FFFB),
    primaryContainer = Color(0xFFA8F0E8),
    onPrimaryContainer = Color(0xFF035C56),
    inversePrimary = Color(0xFFB3FBF3),
    secondary = Color(0xFF4A6461),
    onSecondary = Color(0xFFE2FFFA),
    secondaryContainer = Color(0xFFCCE8E4),
    onSecondaryContainer = Color(0xFF3D5654),
    error = Color(0xFFA83836),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFA746F),
    onErrorContainer = Color(0xFF6E0A12),
    background = Color(0xFFF6FAF8),
    onBackground = Color(0xFF2A3433),
    surface = Color(0xFFF6FAF8),
    onSurface = Color(0xFF2A3433),
    surfaceVariant = Color(0xFFD9E5E2),
    onSurfaceVariant = Color(0xFF566160),
    surfaceTint = Color(0xFF1D6A64),
    surfaceDim = Color(0xFFD1DCDA),
    surfaceBright = Color(0xFFF6FAF8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF5F3),
    surfaceContainer = Color(0xFFE7F0ED),
    surfaceContainerHigh = Color(0xFFE1EAE8),
    surfaceContainerHighest = Color(0xFFD9E5E2),
    inverseSurface = Color(0xFF0A0F0E),
    inverseOnSurface = Color(0xFF999E9C),
    outline = Color(0xFF727D7B),
    outlineVariant = Color(0xFFA9B4B2),
    scrim = Color(0xFF000000),
    tertiary = Color(0xFF8C4E40),
    onTertiary = Color(0xFFFFF7F6),
    tertiaryContainer = Color(0xFFFDAD9B),
    onTertiaryContainer = Color(0xFF622D20),
)

private fun naviScheme(dark: Boolean): ColorScheme = if (dark) Dark else Light

@Composable
fun isAppDark(): Boolean = when (Store.theme) {
    "light" -> false
    "system" -> isSystemInDarkTheme()
    else -> true
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NaviTheme(content: @Composable () -> Unit) {
    val dark = isAppDark()
    val ctx = LocalContext.current
    val dynamic = Store.dynamicColor && Build.VERSION.SDK_INT >= 31
    val scheme = remember(dark, dynamic) {
        if (dynamic) (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else naviScheme(dark)
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp),
        ),
        typography = expressiveType(),
        content = content,
    )
}

/** Expressive leans on heavier, tighter display and headline type. */
private fun expressiveType(): Typography {
    val t = Typography()
    val tight = (-0.5).sp
    return t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = tight),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = tight),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = tight),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = tight),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = tight),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

/** True when the current colour scheme is dark (follows the in-app theme, not the system). */
val darkScheme: Boolean
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Status colours that stay readable in light and dark mode, independent of the wallpaper palette. */
object StatusColors {
    val onTime @Composable @ReadOnlyComposable get() = if (darkScheme) Color(0xFF8BD8A0) else Color(0xFF1B7A3A)
    val late @Composable @ReadOnlyComposable get() = if (darkScheme) Color(0xFFF2C27A) else Color(0xFF8A5B00)
    val veryLate @Composable @ReadOnlyComposable get() = if (darkScheme) Color(0xFFFFA49A) else Color(0xFFC62828)
}
