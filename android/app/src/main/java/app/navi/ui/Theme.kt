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

// Neutrals from Material Color Utilities (TonalSpot, blue-graphite seed #3D6FA8). Primary is a muted sand
// (old LED amber, turned way down), tertiary a dusty blue that marks "next". Kept low chroma on purpose.
private val Dark = darkColorScheme(
    primary = Color(0xFFD6C3A5),
    onPrimary = Color(0xFF3A2E1C),
    primaryContainer = Color(0xFF4A4030),
    onPrimaryContainer = Color(0xFFEFE2CC),
    inversePrimary = Color(0xFF6B5A40),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF354151),
    secondaryContainer = Color(0xFF2C323B),
    onSecondaryContainer = Color(0xFFB4C0D4),
    tertiary = Color(0xFFAFBCCF),
    onTertiary = Color(0xFF26313F),
    tertiaryContainer = Color(0xFF384352),
    onTertiaryContainer = Color(0xFFD5DEEA),
    error = Color(0xFFE59A95),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF5E2B2A),
    onErrorContainer = Color(0xFFF2C9C5),
    background = Color(0xFF0C0E12),
    onBackground = Color(0xFFE2E5EF),
    surface = Color(0xFF0C0E12),
    onSurface = Color(0xFFE2E5EF),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFFA7ABB4),
    surfaceTint = Color(0xFFD6C3A5),
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
    primary = Color(0xFF6B5A40),
    onPrimary = Color(0xFFFFF7F3),
    primaryContainer = Color(0xFFEDE2CF),
    onPrimaryContainer = Color(0xFF3F3322),
    inversePrimary = Color(0xFFD6C3A5),
    secondary = Color(0xFF546071),
    onSecondary = Color(0xFFF8F8FF),
    secondaryContainer = Color(0xFFE0E4EB),
    onSecondaryContainer = Color(0xFF475263),
    tertiary = Color(0xFF4E5D72),
    onTertiary = Color(0xFFF8F8FF),
    tertiaryContainer = Color(0xFFD9E0EA),
    onTertiaryContainer = Color(0xFF283445),
    error = Color(0xFF9A4440),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFF0D2CF),
    onErrorContainer = Color(0xFF5A1F1C),
    background = Color(0xFFF9F9FE),
    onBackground = Color(0xFF2E333A),
    surface = Color(0xFFF9F9FE),
    onSurface = Color(0xFF2E333A),
    surfaceVariant = Color(0xFFDFE2EC),
    onSurfaceVariant = Color(0xFF5B5F67),
    surfaceTint = Color(0xFF6B5A40),
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

/** Google Sans Flex: Material 3 Expressive's own typeface (OFL). Round, calm, with clear numbers. */
val AppFont = FontFamily(
    Font(R.font.gsans_400, FontWeight.Normal),
    Font(R.font.gsans_500, FontWeight.Medium),
    Font(R.font.gsans_600, FontWeight.SemiBold),
    Font(R.font.gsans_700, FontWeight.Bold),
    Font(R.font.gsans_700, FontWeight.ExtraBold),
)

private fun expressiveType(): Typography {
    val t = Typography()
    fun TextStyle.a(w: FontWeight? = null, ls: Float? = null) = copy(fontFamily = AppFont, fontWeight = w ?: fontWeight, letterSpacing = ls?.sp ?: letterSpacing)
    return Typography(
        displayLarge = t.displayLarge.a(FontWeight.Bold, -0.25f),
        displayMedium = t.displayMedium.a(FontWeight.Bold, -0.25f),
        displaySmall = t.displaySmall.a(FontWeight.Bold, 0f),
        headlineLarge = t.headlineLarge.a(FontWeight.Bold, 0f),
        headlineMedium = t.headlineMedium.a(FontWeight.Bold, 0f),
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
    val onTime @Composable @ReadOnlyComposable get() = if (darkScheme) Color(0xFF9DC7A8) else Color(0xFF2F6B40)
    val late @Composable @ReadOnlyComposable get() = if (darkScheme) Color(0xFFDDAD8A) else Color(0xFF8A4E22)
    val veryLate @Composable @ReadOnlyComposable get() = if (darkScheme) Color(0xFFE8A19B) else Color(0xFFA33A33)
}

/** Operator colours are often pure signal colours; take the edge off so a list of chips doesn't shout. */
fun Color.calm(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.rgb((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()), hsv)
    hsv[1] = hsv[1].coerceAtMost(0.5f) * 0.85f
    hsv[2] = hsv[2].coerceIn(0.35f, 0.52f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
