package com.garfiec.librechat.core.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme
import com.garfiec.librechat.core.ui.theme.LocalFixedPalette

/**
 * Liquid Glass control colours, mapped onto the M3 scheme so the accent and dark mode carry over.
 * Grouped lists follow iOS: white cells on a grey page in light mode, lighter cells on near-black in dark.
 * [groupedBackground] and [cell] paint grouped settings pages in Material too.
 */
object GlassControlColors {
    val groupedBackground: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalFixedPalette.current) {
            // TCHAT: the fixed palette's page is `bg` in both themes (design tokens).
            MaterialTheme.colorScheme.background
        } else if (LocalDarkTheme.current) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }

    /** Rows inside a cell paint `adaptiveRowColor` (transparent) so this shows through. */
    val cell: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalFixedPalette.current) {
            // TCHAT: cells are `surface`, one step lighter than the page in dark, white in light.
            MaterialTheme.colorScheme.surfaceContainerLow
        } else if (LocalDarkTheme.current) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }

    /**
     * The fill for a filled control (an on switch). iOS tints stay saturated in dark mode, where the
     * M3 primary turns pastel, so dark mode takes the light scheme's primary tone instead.
     */
    val tint: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalDarkTheme.current && !LocalFixedPalette.current) {
            MaterialTheme.colorScheme.inversePrimary
        } else {
            // TCHAT: the fixed palette's dark accent is already saturated, so glass uses the one
            // accent in both themes (design/system/Audit.md: no third accent value).
            MaterialTheme.colorScheme.primary
        }

    val separator: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.outlineVariant

    /** An off switch's track, and the resting fill of secondary controls. */
    val fill: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    /** Dark M3 surface tones sit too close to lift the thumb off the track, so dark mode mixes its own. */
    val segmentThumb: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalDarkTheme.current) {
            lerp(fill, MaterialTheme.colorScheme.onSurface, DARK_THUMB_LIFT)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }

    val secondaryLabel: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant
}

private const val DARK_THUMB_LIFT = 0.2f
