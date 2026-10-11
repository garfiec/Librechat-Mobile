package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Tchat's fixed palette, from design/system/tokens.json (see design/system/Audit.md for why it
 * replaced the scheme MaterialKolor generated from one seed). Every text token is checked at 4.5:1
 * or better on the surfaces it sits on, in both themes; control outlines at 3:1.
 *
 * Token → Material 3 role:
 * - `bg` → background, surface (the screen ground)
 * - `surface-sunken` → surfaceContainerLowest (dark) / surfaceVariant and surfaceContainerHigh (light wells)
 * - `surface` → surfaceContainerLow (cards, cells, the composer)
 * - `surface-raised` → surfaceContainer (menus)
 * - `surface-overlay` → surfaceContainerHigh in dark (dialogs, sheets)
 * - `ink` / `ink-muted` → onSurface / onSurfaceVariant
 * - `border` / `border-strong` → outlineVariant / outline
 * - `accent` family → primary family; `accent-container` also drives secondaryContainer, which
 *   M3 uses for selected chips and navigation indicators, so selection reads in one accent
 * - `danger` family → error family; `surface-inverse` → inverseSurface
 */

/** True while the fixed Tchat palette is in use (false under wallpaper dynamic color). */
val LocalFixedPalette = staticCompositionLocalOf { false }

/** The accent roles a user-chosen seed may change. Neutrals never take its hue. */
internal data class AccentRoles(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    /** Links and accent text on surfaces; also the snackbar action on the inverse surface. */
    val ink: Color,
)

internal val LightBrandAccent = AccentRoles(
    accent = Color(0xFF2463EB),
    onAccent = Color(0xFFFFFFFF),
    container = Color(0xFFE3ECFF),
    onContainer = Color(0xFF123A8C),
    ink = Color(0xFF1D55D0),
)

internal val DarkBrandAccent = AccentRoles(
    accent = Color(0xFF5C95FF),
    onAccent = Color(0xFF0C0F1A),
    container = Color(0xFF1C2D55),
    onContainer = Color(0xFFC9D9FF),
    ink = Color(0xFF86B0FF),
)

/**
 * The accent roles for a seed other than the brand's, taken from the MaterialKolor scheme for
 * that seed. M3's primary tones already keep onPrimary at 4.5:1, which is the clamp the design
 * asks for.
 */
internal fun accentRolesFrom(seedScheme: ColorScheme): AccentRoles = AccentRoles(
    accent = seedScheme.primary,
    onAccent = seedScheme.onPrimary,
    container = seedScheme.primaryContainer,
    onContainer = seedScheme.onPrimaryContainer,
    ink = seedScheme.primary,
)

/**
 * The full Tchat scheme for [dark]. The brand seed gets the token values exactly; any other seed
 * changes only the accent roles. [inversePrimary] is the accent on the inverse surface (snackbar
 * actions): the opposite theme's accent ink.
 */
internal fun tchatColorScheme(dark: Boolean, isBrandSeed: Boolean, seedScheme: ColorScheme): ColorScheme {
    val accent = when {
        !isBrandSeed -> accentRolesFrom(seedScheme)
        dark -> DarkBrandAccent
        else -> LightBrandAccent
    }
    val inversePrimary = when {
        !isBrandSeed -> seedScheme.inversePrimary
        dark -> LightBrandAccent.ink
        else -> DarkBrandAccent.ink
    }
    return if (dark) tchatDarkScheme(accent, inversePrimary) else tchatLightScheme(accent, inversePrimary)
}

private fun tchatLightScheme(accent: AccentRoles, inversePrimary: Color): ColorScheme = lightColorScheme(
    primary = accent.accent,
    onPrimary = accent.onAccent,
    primaryContainer = accent.container,
    onPrimaryContainer = accent.onContainer,
    inversePrimary = inversePrimary,
    secondary = accent.ink,
    onSecondary = accent.onAccent,
    secondaryContainer = accent.container,
    onSecondaryContainer = accent.onContainer,
    tertiary = accent.ink,
    onTertiary = accent.onAccent,
    tertiaryContainer = accent.container,
    onTertiaryContainer = accent.onContainer,
    background = Color(0xFFF6F7FA),
    onBackground = Color(0xFF11141C),
    surface = Color(0xFFF6F7FA),
    onSurface = Color(0xFF11141C),
    surfaceVariant = Color(0xFFEEF0F5),
    onSurfaceVariant = Color(0xFF4D5464),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF1D2130),
    inverseOnSurface = Color(0xFFF1F3F8),
    error = Color(0xFFC4271F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE8E6),
    onErrorContainer = Color(0xFF8A1610),
    outline = Color(0xFF878D9B),
    outlineVariant = Color(0xFFDDE1E9),
    scrim = Color(0xFF0C0F1A),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6E9F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEF0F5),
    surfaceContainerHighest = Color(0xFFE6E9F0),
)

private fun tchatDarkScheme(accent: AccentRoles, inversePrimary: Color): ColorScheme = darkColorScheme(
    primary = accent.accent,
    onPrimary = accent.onAccent,
    primaryContainer = accent.container,
    onPrimaryContainer = accent.onContainer,
    inversePrimary = inversePrimary,
    secondary = accent.ink,
    onSecondary = accent.onAccent,
    secondaryContainer = accent.container,
    onSecondaryContainer = accent.onContainer,
    tertiary = accent.ink,
    onTertiary = accent.onAccent,
    tertiaryContainer = accent.container,
    onTertiaryContainer = accent.onContainer,
    background = Color(0xFF0C0F1A),
    onBackground = Color(0xFFE9ECF4),
    surface = Color(0xFF0C0F1A),
    onSurface = Color(0xFFE9ECF4),
    surfaceVariant = Color(0xFF1A1F30),
    onSurfaceVariant = Color(0xFFA7AEBF),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFE9ECF4),
    inverseOnSurface = Color(0xFF11141C),
    error = Color(0xFFFF7A6E),
    onError = Color(0xFF1A0503),
    errorContainer = Color(0xFF3D1414),
    onErrorContainer = Color(0xFFFFC2BB),
    outline = Color(0xFF6B7388),
    outlineVariant = Color(0xFF2A3043),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF222839),
    surfaceDim = Color(0xFF090B13),
    surfaceContainerLowest = Color(0xFF090B13),
    surfaceContainerLow = Color(0xFF131726),
    surfaceContainer = Color(0xFF1A1F30),
    surfaceContainerHigh = Color(0xFF222839),
    surfaceContainerHighest = Color(0xFF2A3043),
)

/** The status colours M3 has no role for (design tokens `success*` / `warning*`). */
data class StatusColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val LightStatusColors = StatusColors(
    success = Color(0xFF00785F),
    successContainer = Color(0xFFDCF4EC),
    onSuccessContainer = Color(0xFF00513F),
    warning = Color(0xFF9A5B00),
    warningContainer = Color(0xFFFDF0D8),
    onWarningContainer = Color(0xFF6B3F00),
)

internal val DarkStatusColors = StatusColors(
    success = Color(0xFF3CCFA4),
    successContainer = Color(0xFF0C3127),
    onSuccessContainer = Color(0xFFA6ECD5),
    warning = Color(0xFFF2B04A),
    warningContainer = Color(0xFF36260A),
    onWarningContainer = Color(0xFFFBD99C),
)

/** The design's status colours for the current theme. Provided by [LibreChatTheme]. */
val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/**
 * `surface-overlay`: dialogs and bottom sheets. White in light mode, the top surface step in dark.
 * Under wallpaper colours it stays M3's dialog container.
 */
val overlaySurfaceColor: Color
    @Composable @ReadOnlyComposable
    get() {
        val scheme = MaterialTheme.colorScheme
        return when {
            !LocalFixedPalette.current -> scheme.surfaceContainerHigh
            LocalDarkTheme.current -> scheme.surfaceContainerHigh
            else -> scheme.surfaceContainerLowest
        }
    }

/** `surface-raised`: menus, popovers and the solid fallback for glass. */
val raisedSurfaceColor: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceContainer

/**
 * `accent-ink`: links and accent-coloured text on surfaces (≥6:1 on bg, surface and surface-raised).
 * The fixed palette carries it as `secondary`; under wallpaper colours it is the primary.
 */
val accentInkColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalFixedPalette.current) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

/** `focus-ring`: the accent in light mode, the lighter accent ink in dark (≥4.8:1 on every surface). */
val focusRingColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalFixedPalette.current && LocalDarkTheme.current) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }
