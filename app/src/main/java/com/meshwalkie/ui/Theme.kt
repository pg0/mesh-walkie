package com.meshwalkie.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshwalkie.R
import com.meshwalkie.core.AppTheme

// Bundled pixel/dot-matrix fonts (OFL-licensed, see docs/font-licenses/). Built
// once at file scope rather than per-call so FIELD/CORRUPTION typography
// resolution stays cheap.
@OptIn(ExperimentalTextApi::class)
private val DotoFamily = FontFamily(
    Font(
        R.font.doto_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)
private val Vt323Family = FontFamily(Font(R.font.vt323_regular))
// silkscreen_regular.ttf is bundled alongside the bold weight (see
// docs/font-licenses/) but the CORRUPTION type scale only calls for the
// bold cut on headers - no unused FontFamily val for it here.
private val SilkscreenBoldFamily = FontFamily(Font(R.font.silkscreen_bold))

/**
 * Composable-free theme resolution: colors, shapes and type scale per
 * [AppTheme]. Kept out of MainActivity so PttButton and other composables
 * can resolve theme-derived values (e.g. night-mode red) without pulling in
 * the whole MaterialTheme composition.
 */

/** True for the light themes (FIELD, CORRUPTION, RADIO); false for DARK/NIGHT. */
fun themeIsLight(t: AppTheme): Boolean = when (t) {
    AppTheme.FIELD, AppTheme.CORRUPTION, AppTheme.RADIO -> true
    AppTheme.DARK, AppTheme.NIGHT -> false
}

fun themeColorScheme(t: AppTheme): ColorScheme = when (t) {
    // Paper field-recorder: ink on warm off-white, thin technical feel.
    AppTheme.FIELD -> lightColorScheme(
        background = Color(0xFFEFEDE8),
        surface = Color(0xFFEFEDE8),
        surfaceVariant = Color(0xFFDDDAD3),
        onBackground = Color(0xFF1B1B1B),
        onSurface = Color(0xFF1B1B1B),
        onSurfaceVariant = Color(0xFF3C3C3C),
        primary = Color(0xFF1B1B1B),
        onPrimary = Color(0xFFEFEDE8),
        secondary = Color(0xFF4A4A4A),
        onSecondary = Color(0xFFEFEDE8),
        outline = Color(0xFF1B1B1B),
        error = Color(0xFFE23B2E)
    )
    // Bitcrusher plugin: same paper family, brutalist, muted color chips.
    AppTheme.CORRUPTION -> lightColorScheme(
        background = Color(0xFFF5F4F0),
        surface = Color(0xFFF5F4F0),
        surfaceVariant = Color(0xFFDDDBD3),
        onBackground = Color(0xFF101010),
        onSurface = Color(0xFF101010),
        onSurfaceVariant = Color(0xFF2E2E2E),
        primary = Color(0xFF101010),
        onPrimary = Color(0xFFF1EFE9),
        primaryContainer = Color(0xFFABA5C7),
        onPrimaryContainer = Color(0xFF101010),
        secondary = Color(0xFF5A5A5A),
        secondaryContainer = Color(0xFFC09291),
        onSecondaryContainer = Color(0xFF101010),
        tertiary = Color(0xFF8E9A88),
        outline = Color(0xFF101010),
        inverseSurface = Color(0xFF3A3A3A),
        inverseOnSurface = Color(0xFFF1EFE9),
        error = Color(0xFFB3403E)
    )
    // Swiss radio app: pure white, black pills, signal-red accent.
    AppTheme.RADIO -> lightColorScheme(
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEDEDED),
        onBackground = Color(0xFF141414),
        onSurface = Color(0xFF141414),
        onSurfaceVariant = Color(0xFF9E9E9E),
        primary = Color(0xFF141414),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE8E8E8),
        onPrimaryContainer = Color(0xFF141414),
        secondary = Color(0xFF9E9E9E),
        tertiary = Color(0xFFF5372B),
        error = Color(0xFFF5372B),
        outline = Color(0xFFD6D6D6)
    )
    // Current OLED scheme, unchanged.
    AppTheme.DARK -> darkColorScheme(background = Color.Black, surface = Color.Black)
    // Current night-vision scheme (red on black), unchanged.
    AppTheme.NIGHT -> {
        val red = Color(0xFFD0342C)
        darkColorScheme(
            background = Color.Black, surface = Color.Black,
            onBackground = red, onSurface = red,
            primary = red, onPrimary = Color.Black,
            surfaceVariant = Color(0xFF2A0000), onSurfaceVariant = red
        )
    }
}

fun themeShapes(t: AppTheme): Shapes = when (t) {
    AppTheme.FIELD -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(14.dp)
    )
    // Fully square, brutalist.
    AppTheme.CORRUPTION -> Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(0.dp)
    )
    // Soft pill look.
    AppTheme.RADIO -> Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )
    AppTheme.DARK, AppTheme.NIGHT -> Shapes()
}

fun themeTypography(t: AppTheme): Typography = when (t) {
    // Field recorder: dot-matrix Doto on the big display/headline/titleLarge
    // styles ("TUESDAY", the timer), VT323 pixel-mono on titleMedium/label
    // styles and bodyMedium/bodySmall (file-list rows, chip labels). VT323's
    // x-height runs small at equal sp, so those styles get a +2sp bump to
    // match visual weight. titleSmall/bodyLarge are unused by this scale and
    // stay default.
    AppTheme.FIELD -> {
        val base = Typography()
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = DotoFamily, letterSpacing = 1.sp),
            displayMedium = base.displayMedium.copy(fontFamily = DotoFamily, letterSpacing = 1.sp),
            displaySmall = base.displaySmall.copy(fontFamily = DotoFamily, letterSpacing = 1.sp),
            headlineLarge = base.headlineLarge.copy(fontFamily = DotoFamily, letterSpacing = 1.sp),
            headlineMedium = base.headlineMedium.copy(fontFamily = DotoFamily, letterSpacing = 1.sp),
            headlineSmall = base.headlineSmall.copy(fontFamily = DotoFamily, letterSpacing = 1.sp),
            titleLarge = base.titleLarge.copy(fontFamily = DotoFamily, letterSpacing = 1.sp),
            titleMedium = base.titleMedium.copy(
                fontFamily = Vt323Family,
                fontSize = 18.sp,   // M3 default 16sp; VT323 needs +2sp
                letterSpacing = 1.sp
            ),
            bodyMedium = base.bodyMedium.copy(fontFamily = Vt323Family, fontSize = 16.sp),
            bodySmall = base.bodySmall.copy(fontFamily = Vt323Family, fontSize = 14.sp),
            labelLarge = base.labelLarge.copy(
                fontFamily = Vt323Family,
                fontSize = 16.sp,
                letterSpacing = 1.sp
            ),
            labelMedium = base.labelMedium.copy(fontFamily = Vt323Family, fontSize = 14.sp),
            labelSmall = base.labelSmall.copy(fontFamily = Vt323Family, fontSize = 13.sp)
        )
    }
    // Bitcrusher plugin: VT323 pixel-mono across the whole scale (as
    // Monospace was), then Silkscreen Bold overrides titleLarge/headline*
    // and the labelLarge style SectionHeader renders its "TITLE ——" rule
    // with, for the chunky ALL-CAPS masthead look of the webp. Every
    // VT323-carrying style gets the same +2sp compensation used in FIELD.
    AppTheme.CORRUPTION -> {
        val base = Typography()
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = Vt323Family, fontSize = 59.sp),   // M3 defaults +2sp
            displayMedium = base.displayMedium.copy(fontFamily = Vt323Family, fontSize = 47.sp),
            displaySmall = base.displaySmall.copy(fontFamily = Vt323Family, fontSize = 38.sp),
            headlineLarge = base.headlineLarge.copy(fontFamily = SilkscreenBoldFamily, letterSpacing = 2.sp),
            headlineMedium = base.headlineMedium.copy(fontFamily = SilkscreenBoldFamily, letterSpacing = 2.sp),
            headlineSmall = base.headlineSmall.copy(fontFamily = SilkscreenBoldFamily, letterSpacing = 2.sp),
            titleLarge = base.titleLarge.copy(fontFamily = SilkscreenBoldFamily, letterSpacing = 1.sp),
            titleMedium = base.titleMedium.copy(fontFamily = Vt323Family, fontSize = 18.sp),
            titleSmall = base.titleSmall.copy(fontFamily = Vt323Family, fontSize = 16.sp),
            bodyLarge = base.bodyLarge.copy(fontFamily = Vt323Family, fontSize = 18.sp),
            bodyMedium = base.bodyMedium.copy(fontFamily = Vt323Family, fontSize = 16.sp),
            bodySmall = base.bodySmall.copy(fontFamily = Vt323Family, fontSize = 14.sp),
            labelLarge = base.labelLarge.copy(fontFamily = SilkscreenBoldFamily, letterSpacing = 1.sp),
            labelMedium = base.labelMedium.copy(fontFamily = Vt323Family, fontSize = 14.sp),
            labelSmall = base.labelSmall.copy(fontFamily = Vt323Family, fontSize = 13.sp)
        )
    }
    // Clean sans, default Material3 type scale.
    AppTheme.RADIO, AppTheme.DARK, AppTheme.NIGHT -> Typography()
}
