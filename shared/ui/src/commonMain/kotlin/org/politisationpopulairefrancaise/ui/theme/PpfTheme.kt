package org.politisationpopulairefrancaise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Visual identity of the platform, as fixed by the owner's mock-ups of 2026-11-06 (decision D-025):
 * a deep blue brand colour and a carmine accent on cool off-white surfaces, one typeface (Atkinson
 * Hyperlegible), 6/10/16 dp corners, subtle shadows, and a dark theme with the same structure.
 * The names below are the design tokens of the mock-ups; every text/background pair meets WCAG 2.2
 * AA (4.5:1). The identity must never be mistaken for the State's: no tricolour, no official symbol.
 */

/** The design tokens of the visual identity, for one theme (light or dark). */
@Immutable
data class PpfPalette(
    /** Bars and navigation. */
    val surface0: Color,
    /** Page background and inset panels. */
    val surface1: Color,
    /** Cards. */
    val surface2: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkSubtle: Color,
    val border: Color,
    val borderStrong: Color,
    val brand: Color,
    val brandStrong: Color,
    val brandTint: Color,
    val onBrand: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentTint: Color,
    val accentInk: Color,
    val onAccent: Color,
    val success: Color,
    val successTint: Color,
    val warning: Color,
    val warningTint: Color,
    val focus: Color,
    val scrim: Color,
    /** Six avatar colours, chosen from the public key. */
    val avatars: List<Color>,
    val onAvatar: Color,
    val shadow: Color,
)

val LightPalette = PpfPalette(
    surface0 = Color(0xFFFFFFFF),
    surface1 = Color(0xFFF3F5FB),
    surface2 = Color(0xFFFFFFFF),
    ink = Color(0xFF14182B),
    inkMuted = Color(0xFF4A5272),
    inkSubtle = Color(0xFF5F6685),
    border = Color(0xFFDCE0EE),
    borderStrong = Color(0xFF8A91AD),
    brand = Color(0xFF2B3A8F),
    brandStrong = Color(0xFF1F2A6B),
    brandTint = Color(0xFFE9ECF8),
    onBrand = Color(0xFFFFFFFF),
    accent = Color(0xFFC8213A),
    accentStrong = Color(0xFFA51A30),
    accentTint = Color(0xFFFDE8EA),
    accentInk = Color(0xFF9E1B2B),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF1D6B45),
    successTint = Color(0xFFE3F4EA),
    warning = Color(0xFF7A4F00),
    warningTint = Color(0xFFFDF1D6),
    focus = Color(0xFF1F2A6B),
    scrim = Color(0x7A14182B),
    avatars = listOf(Color(0xFF2B3A8F), Color(0xFFA51A30), Color(0xFF1D6B45), Color(0xFF7A4F00), Color(0xFF0F5E66), Color(0xFF6B2F7A)),
    onAvatar = Color(0xFFFFFFFF),
    shadow = Color(0xFF14182B),
)

val DarkPalette = PpfPalette(
    surface0 = Color(0xFF0F1220),
    surface1 = Color(0xFF181C30),
    surface2 = Color(0xFF1E2339),
    ink = Color(0xFFEEF0F8),
    inkMuted = Color(0xFFB4B9D0),
    inkSubtle = Color(0xFF9AA0BD),
    border = Color(0xFF2E3452),
    borderStrong = Color(0xFF6A7194),
    brand = Color(0xFF9AA6F0),
    brandStrong = Color(0xFFC3CAF7),
    brandTint = Color(0xFF232A55),
    onBrand = Color(0xFF0F1220),
    accent = Color(0xFFFF7083),
    accentStrong = Color(0xFFFF9AA8),
    accentTint = Color(0xFF3A1C24),
    accentInk = Color(0xFFFFB3BD),
    onAccent = Color(0xFF0F1220),
    success = Color(0xFF7FDCAA),
    successTint = Color(0xFF14301F),
    warning = Color(0xFFF2C96B),
    warningTint = Color(0xFF33270D),
    focus = Color(0xFFC3CAF7),
    scrim = Color(0x99000000),
    avatars = listOf(Color(0xFF9AA6F0), Color(0xFFFF9AA8), Color(0xFF7FDCAA), Color(0xFFF2C96B), Color(0xFF6FD0D8), Color(0xFFD7A3E8)),
    onAvatar = Color(0xFF0F1220),
    shadow = Color(0xFF000000),
)

/** Colours of one kind of highlighted box (see [Callout]). */
@Immutable
data class CalloutColors(val container: Color, val accent: Color, val onContainer: Color)

/** The highlighted boxes used in the contents: a summary, what is legal, what is illegal, a warning, a tip. */
@Immutable
data class PpfExtendedColors(
    val enBref: CalloutColors,
    val legal: CalloutColors,
    val illegal: CalloutColors,
    val attention: CalloutColors,
    val conseil: CalloutColors,
)

private fun PpfPalette.callouts() = PpfExtendedColors(
    enBref = CalloutColors(container = brandTint, accent = brand, onContainer = ink),
    legal = CalloutColors(container = successTint, accent = success, onContainer = ink),
    illegal = CalloutColors(container = accentTint, accent = accentInk, onContainer = ink),
    attention = CalloutColors(container = warningTint, accent = warning, onContainer = ink),
    conseil = CalloutColors(container = surface1, accent = inkMuted, onContainer = ink),
)

/** The Material colour roles, derived from the tokens so that stock components follow the identity. */
private fun PpfPalette.toLightColorScheme(): ColorScheme = lightColorScheme(
    primary = brand,
    onPrimary = onBrand,
    primaryContainer = brandTint,
    onPrimaryContainer = brandStrong,
    inversePrimary = DarkPalette.brand,
    secondary = accent,
    onSecondary = onAccent,
    secondaryContainer = accentTint,
    onSecondaryContainer = accentInk,
    tertiary = success,
    onTertiary = onBrand,
    tertiaryContainer = successTint,
    onTertiaryContainer = success,
    background = surface1,
    onBackground = ink,
    surface = surface0,
    onSurface = ink,
    surfaceVariant = surface1,
    onSurfaceVariant = inkMuted,
    surfaceTint = brand,
    // Stock components: dialogs and menus on surface2, filled cards and text fields on surface1.
    surfaceContainerLowest = surface2,
    surfaceContainerLow = surface1,
    surfaceContainer = surface2,
    surfaceContainerHigh = surface2,
    surfaceContainerHighest = surface1,
    surfaceBright = surface0,
    surfaceDim = border,
    inverseSurface = ink,
    inverseOnSurface = surface0,
    outline = borderStrong,
    outlineVariant = border,
    error = accent,
    onError = onAccent,
    errorContainer = accentTint,
    onErrorContainer = accentInk,
    scrim = shadow,
)

private fun PpfPalette.toDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = brand,
    onPrimary = onBrand,
    primaryContainer = brandTint,
    onPrimaryContainer = brandStrong,
    inversePrimary = LightPalette.brand,
    secondary = accent,
    onSecondary = onAccent,
    secondaryContainer = accentTint,
    onSecondaryContainer = accentInk,
    tertiary = success,
    onTertiary = onBrand,
    tertiaryContainer = successTint,
    onTertiaryContainer = success,
    background = surface1,
    onBackground = ink,
    surface = surface0,
    onSurface = ink,
    surfaceVariant = surface1,
    onSurfaceVariant = inkMuted,
    surfaceTint = brand,
    surfaceContainerLowest = surface0,
    surfaceContainerLow = surface1,
    surfaceContainer = surface2,
    surfaceContainerHigh = surface2,
    surfaceContainerHighest = Color(0xFF262C47),
    surfaceBright = surface2,
    surfaceDim = surface0,
    inverseSurface = ink,
    inverseOnSurface = surface0,
    outline = borderStrong,
    outlineVariant = border,
    error = accent,
    onError = onAccent,
    errorContainer = accentTint,
    onErrorContainer = accentInk,
    scrim = shadow,
)

val LocalPpfPalette = staticCompositionLocalOf { LightPalette }
val LocalPpfExtendedColors = staticCompositionLocalOf { LightPalette.callouts() }

/** 6 dp for badges and small buttons, 10 dp for buttons and panels, 16 dp for cards and sheets. */
val PpfShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

object PpfTheme {
    /** The design tokens of the current theme. */
    val palette: PpfPalette
        @Composable get() = LocalPpfPalette.current

    val extended: PpfExtendedColors
        @Composable get() = LocalPpfExtendedColors.current
}

@Composable
fun PpfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(
        LocalPpfPalette provides palette,
        LocalPpfExtendedColors provides palette.callouts(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) palette.toDarkColorScheme() else palette.toLightColorScheme(),
            typography = ppfTypography(),
            shapes = PpfShapes,
            content = content,
        )
    }
}
