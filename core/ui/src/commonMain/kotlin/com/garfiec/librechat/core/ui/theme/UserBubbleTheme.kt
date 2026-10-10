package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * TCHAT: the user's own message bubble, filled per the design tokens `bubble-user` /
 * `on-bubble-user`: the accent in light mode, a deep navy in dark so long messages don't glare.
 */

/** Space kept left of the user's bubble in the two-sided layout, so it never spans the screen. */
val UserBubbleLeadingInset: Dp = 48.dp

/** The bubble's fill and the colour of everything drawn on it. */
data class UserBubbleColors(val container: Color, val content: Color)

private val DarkBrandBubble = UserBubbleColors(container = Color(0xFF1F3B78), content = Color(0xFFEEF3FF))

/**
 * `bubble-user` for the current theme. The brand accent gets the token values; another accent
 * (or wallpaper colour) uses its own primary in light mode and its primary container in dark.
 */
@Composable
@ReadOnlyComposable
fun userBubbleColors(): UserBubbleColors {
    val scheme = MaterialTheme.colorScheme
    return when {
        !LocalDarkTheme.current -> UserBubbleColors(container = scheme.primary, content = scheme.onPrimary)
        scheme.primary == DarkBrandAccent.accent -> DarkBrandBubble
        else -> UserBubbleColors(container = scheme.primaryContainer, content = scheme.onPrimaryContainer)
    }
}

/**
 * Draws [content] in colours that read on the filled user bubble: text, icons, links and actions
 * take the bubble's content colour, and code blocks and other wells a shade off the fill. Message
 * renderers read `MaterialTheme.colorScheme` directly, so recolouring the scheme is what reaches
 * them all. With [active] false the content is drawn unchanged.
 */
@Composable
fun UserBubbleTheme(active: Boolean, content: @Composable () -> Unit) {
    if (!active) {
        content()
        return
    }
    val bubble = userBubbleColors()
    val dark = LocalDarkTheme.current
    val base = MaterialTheme.colorScheme
    val scheme = remember(base, bubble, dark) { base.onUserBubble(bubble, dark) }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides bubble.content, content = content)
    }
}

private fun ColorScheme.onUserBubble(bubble: UserBubbleColors, dark: Boolean): ColorScheme {
    val on = bubble.content
    val fill = bubble.container
    // A well (code block, quote, chip) sits a step darker than the fill, in both themes.
    val well = lerp(fill, Color.Black, if (dark) 0.35f else 0.22f)
    val wellHigh = lerp(fill, Color.Black, if (dark) 0.45f else 0.3f)
    // Secondary text (timestamps, actions) keeps 4.5:1 on the fill in both themes.
    val muted = lerp(on, fill, if (dark) 0.18f else 0.06f)
    return copy(
        primary = on,
        onPrimary = fill,
        primaryContainer = well,
        onPrimaryContainer = on,
        secondary = on,
        onSecondary = fill,
        secondaryContainer = well,
        onSecondaryContainer = on,
        tertiary = on,
        onTertiary = fill,
        background = fill,
        onBackground = on,
        surface = fill,
        onSurface = on,
        surfaceVariant = well,
        onSurfaceVariant = muted,
        surfaceContainerLowest = well,
        surfaceContainerLow = well,
        surfaceContainer = well,
        surfaceContainerHigh = well,
        surfaceContainerHighest = wellHigh,
        outline = lerp(on, fill, 0.4f),
        outlineVariant = lerp(on, fill, 0.65f),
    )
}
