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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.navi.R
import app.navi.Store

// Generated with Material Color Utilities (SchemeTonalSpot, spec 2025). Neutrals and secondary come from
// a blue-graphite seed #3D6FA8 (the glass of a train's passenger display), primary from amber #E9A23B
// (the old LED text), tertiary is the blue seed's primary and marks "next". Low chroma, nothing glares.
private val Dark = darkColorScheme(
    primary = Color(0xFFEBBF89),
    onPrimary = Color(0xFF573A10),
    primaryContainer = Color(0xFF6C4C21),
    onPrimaryContainer = Color(0xFFFFDEB8),
    inversePrimary = Color(0xFF7A592C),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF354151),
    secondaryContainer = Color(0xFF313C4C),
    onSecondaryContainer = Color(0xFFB4C0D4),
    tertiary = Color(0xFFB0C8EC),
    onTertiary = Color(0xFF2A415F),
    tertiaryContainer = Color(0xFF3C5472),
    onTertiaryContainer = Color(0xFFD4E4FF),
    error = Color(0xFFFA746F),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF871F21),
    onErrorContainer = Color(0xFFFF9993),
    background = Color(0xFF0C0E12),
    onBackground = Color(0xFFE2E5EF),
    surface = Color(0xFF0C0E12),
    onSurface = Color(0xFFE2E5EF),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFFA7ABB4),
    surfaceTint = Color(0xFFEBBF89),
    surfaceDim = Color(0xFF0C0E12),
    surfaceBright = Color(0xFF282C34),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF111418),
    surfaceContainer = Color(0xFF161A1F),
    surfaceContainerHigh = Color(0xFF1C2026),
    surfaceContainerHighest = Color(0xFF21262D),
    inverseSurface = Color(0xFFF9F9FE),
    inverseOnSurface = Color(0xFF535559),
    outline = Color(0xFF71767E),
    outlineVariant = Color(0xFF444850),
    scrim = Color(0xFF000000),
)

private val Light = lightColorScheme(
    primary = Color(0xFF7F571E),
    onPrimary = Color(0xFFFFF7F3),
    primaryContainer = Color(0xFFFEC884),
    onPrimaryContainer = Color(0xFF644006),
    inversePrimary = Color(0xFFFEC884),
    secondary = Color(0xFF546071),
    onSecondary = Color(0xFFF8F8FF),
    secondaryContainer = Color(0xFFD7E3F8),
    onSecondaryContainer = Color(0xFF475263),
    tertiary = Color(0xFF406189),
    onTertiary = Color(0xFFF8F8FF),
    tertiaryContainer = Color(0xFFA3C3F1),
    onTertiaryContainer = Color(0xFF1A3E64),
    error = Color(0xFFA83836),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFA746F),
    onErrorContainer = Color(0xFF6E0A12),
    background = Color(0xFFF9F9FE),
    onBackground = Color(0xFF2E333A),
    surface = Color(0xFFF9F9FE),
    onSurface = Color(0xFF2E333A),
    surfaceVariant = Color(0xFFDFE2EC),
    onSurfaceVariant = Color(0xFF5B5F67),
    surfaceTint = Color(0xFF7F571E),
    surfaceDim = Color(0xFFD6DAE4),
    surfaceBright = Color(0xFFF9F9FE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF5),
    surfaceContainerHigh = Color(0xFFE5E8F0),
    surfaceContainerHighest = Color(0xFFDFE2EC),
    inverseSurface = Color(0xFF0C0E12),
    inverseOnSurface = Color(0xFF9C9DA1),
    outline = Color(0xFF767B83),
    outlineVariant = Color(0xFFAEB2BB),
    scrim = Color(0xFF000000),
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

/** Atkinson Hyperlegible Next everywhere: made for people who struggle to read, which is everyone at a stop in the rain. */
val Atkinson = FontFamily(
    Font(R.font.atkinson_400, FontWeight.Normal),
    Font(R.font.atkinson_500, FontWeight.Medium),
    Font(R.font.atkinson_700, FontWeight.SemiBold),
    Font(R.font.atkinson_700, FontWeight.Bold),
    Font(R.font.atkinson_800, FontWeight.ExtraBold),
)

private fun expressiveType(): Typography {
    val t = Typography()
    fun TextStyle.a(w: FontWeight? = null, ls: Float? = null) = copy(fontFamily = Atkinson, fontWeight = w ?: fontWeight, letterSpacing = ls?.sp ?: letterSpacing)
    return Typography(
        displayLarge = t.displayLarge.a(FontWeight.ExtraBold, -0.5f),
        displayMedium = t.displayMedium.a(FontWeight.ExtraBold, -0.5f),
        displaySmall = t.displaySmall.a(FontWeight.ExtraBold, -0.25f),
        headlineLarge = t.headlineLarge.a(FontWeight.ExtraBold, -0.25f),
        headlineMedium = t.headlineMedium.a(FontWeight.ExtraBold, -0.25f),
        headlineSmall = t.headlineSmall.a(FontWeight.Bold),
        titleLarge = t.titleLarge.a(FontWeight.Bold),
        titleMedium = t.titleMedium.a(FontWeight.SemiBold),
        titleSmall = t.titleSmall.a(FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.a(),
        bodyMedium = t.bodyMedium.a(),
        bodySmall = t.bodySmall.a(),
        labelLarge = t.labelLarge.a(FontWeight.Bold),
        labelMedium = t.labelMedium.a(FontWeight.SemiBold),
        labelSmall = t.labelSmall.a(FontWeight.SemiBold),
    )
}

/** True when the current colour scheme is dark (follows the in-app theme, not the system). */
val darkScheme: Boolean
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Status colours that stay readable in light and dark mode, independent of the wallpaper palette. */
object StatusColors {
    val onTime @Composable @ReadOnlyComposable get() = if (darkScheme) Color(0xFF8BD8A0) else Color(0xFF1B7A3A)
    val late @Composable @ReadOnlyComposable get() = if (darkScheme) Color(0xFFF5A06E) else Color(0xFF9C4A14)
    val veryLate @Composable @ReadOnlyComposable get() = if (darkScheme) Color(0xFFFFA49A) else Color(0xFFC62828)
}
