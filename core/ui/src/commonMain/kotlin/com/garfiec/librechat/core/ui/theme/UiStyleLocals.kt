package com.garfiec.librechat.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.garfiec.librechat.core.model.ui.GlassCapability

/** `null` in Material, otherwise the device's [GlassCapability]. Most code only needs [isLiquidGlass]. */
internal val LocalGlassLevel = staticCompositionLocalOf<GlassCapability?> { null }

val isLiquidGlass: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalGlassLevel.current != null

/**
 * True when Liquid Glass is on and the device can blur (not the flat tier: below Android 12, or iOS
 * with Reduce Transparency). For a glass-only effect that blurs content itself.
 */
val glassBlurSupported: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalGlassLevel.current.let { it != null && it != GlassCapability.FLAT }

/**
 * The resolved dark flag [LibreChatTheme] was given. Native views need it explicitly: a user who
 * forces dark on a light system would otherwise get UIKit chrome that follows the system trait.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }
