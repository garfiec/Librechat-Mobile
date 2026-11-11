package com.garfiec.librechat.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.config.theme.ServerThemeDefinition
import com.garfiec.librechat.core.model.config.theme.resolve
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.topbar.LocalNativeOverlayGate
import com.garfiec.librechat.core.ui.components.topbar.NativeOverlayGate
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

/**
 * Returns a platform-specific dynamic color scheme if available, or null to fall back
 * to the LibreChat color scheme. On Android 12+, returns wallpaper-based Material You colors.
 */
@Composable
expect fun platformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme?

/**
 * Whether the platform supports wallpaper-based Material You dynamic color.
 * Android 12+ returns true; iOS returns false. Used to gate the "use wallpaper
 * colors" setting and the [LibreChatTheme] precedence branch.
 */
expect fun supportsDynamicColor(): Boolean

/** The platform's own idiom, used when the user has not picked a [UiStyle]. */
expect fun platformDefaultUiStyle(): UiStyle

/** How faithfully this device can render [UiStyle.LIQUID_GLASS]. */
expect fun glassCapability(): GlassCapability

/**
 * Applies the LibreChat Material 3 theme.
 *
 * Color resolution precedence:
 * 1. [serverTheme] set -> the server's colours, resolved as the web resolves them; accent and
 *    wallpaper colours don't apply.
 * 2. [useDynamicColor] on **and** the platform supports it -> wallpaper-based scheme.
 * 3. Otherwise the full scheme is generated from [accentColor] via MaterialKolor
 *    ([rememberDynamicColorScheme], remembered/keyed on its inputs).
 *
 * [PaletteStyle.TonalSpot] keeps only the seed's hue — every role is re-derived at a fixed
 * chroma and a fixed per-role tone, so no role in the generated scheme resolves to the seed hex.
 * The hue feeds the neutral palette as well as the accent one, which is why it tints surfaces
 * app-wide.
 *
 * [uiStyle] is the user's stored choice; null (nothing stored) means [platformDefaultUiStyle].
 */
@Composable
fun LibreChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: Color = DefaultAccentSeed,
    useDynamicColor: Boolean = false,
    uiStyle: UiStyle? = null,
    serverTheme: ServerThemeDefinition? = null,
    content: @Composable () -> Unit,
) {
    val colors = rememberThemeColors(darkTheme, accentColor, useDynamicColor, serverTheme)
    val capability = remember { glassCapability() }
    CompositionLocalProvider(
        LocalGlassLevel provides capability.takeIf { (uiStyle ?: platformDefaultUiStyle()) == UiStyle.LIQUID_GLASS },
        LocalDarkTheme provides darkTheme,
        LocalThemeTokens provides colors.tokens,
        // One gate for the whole app: covering surfaces and native bars must see the same instance.
        LocalNativeOverlayGate provides remember { NativeOverlayGate() },
    ) {
        MaterialTheme(
            colorScheme = colors.scheme,
            typography = colors.typography,
            shapes = libreChatShapes,
            content = content,
        )
    }
}

/**
 * Draws [content] in the light or dark theme the app would have with these settings, whatever theme
 * the app is in now: the colours [LibreChatTheme] would build, and the dark flag glass colours read.
 * For a theme picker's previews only; the interface style is left as it is.
 */
@Composable
fun ThemePreview(
    darkTheme: Boolean,
    accentColor: Color,
    useDynamicColor: Boolean,
    serverTheme: ServerThemeDefinition? = null,
    content: @Composable () -> Unit,
) {
    val colors = rememberThemeColors(darkTheme, accentColor, useDynamicColor, serverTheme)
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalThemeTokens provides colors.tokens,
    ) {
        MaterialTheme(
            colorScheme = colors.scheme,
            typography = colors.typography,
            shapes = libreChatShapes,
            content = content,
        )
    }
}

private class ThemeColors(val scheme: ColorScheme, val tokens: ThemeTokens, val typography: Typography = libreChatTypography)

/** The scheme and tokens for these settings, by the precedence [LibreChatTheme] documents. */
@Composable
private fun rememberThemeColors(
    darkTheme: Boolean,
    accentColor: Color,
    useDynamicColor: Boolean,
    serverTheme: ServerThemeDefinition?,
): ThemeColors {
    if (serverTheme != null) {
        return remember(serverTheme, darkTheme) {
            val colors = serverTheme.resolve(darkTheme)
            val appearance = serverTheme.appearance(darkTheme)
            ThemeColors(
                scheme = serverColorScheme(colors, darkTheme),
                tokens = ThemeTokens.server(colors, darkTheme, appearance),
                typography = libreChatTypography.withFonts(appearance),
            )
        }
    }
    val scheme = rememberLibreChatColorScheme(darkTheme, accentColor, useDynamicColor)
    return remember(scheme, darkTheme) { ThemeColors(scheme, ThemeTokens.switchboard(scheme, darkTheme)) }
}

/** Switchboard's own scheme: wallpaper colours where on and supported, else the accent's. */
@Composable
private fun rememberLibreChatColorScheme(darkTheme: Boolean, accentColor: Color, useDynamicColor: Boolean): ColorScheme {
    val seedScheme = rememberDynamicColorScheme(accentColor, darkTheme, style = PaletteStyle.TonalSpot)
    return if (useDynamicColor && supportsDynamicColor()) {
        platformColorScheme(darkTheme, dynamicColor = true) ?: seedScheme
    } else {
        seedScheme
    }
}
