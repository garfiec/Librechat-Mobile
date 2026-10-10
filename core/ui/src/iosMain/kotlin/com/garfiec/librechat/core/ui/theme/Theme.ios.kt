package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.glass.iosGlassCapability
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled

/**
 * Kill switch for native UIKit glass bars. Off, an iOS 26 device draws the simulated bar instead,
 * which is the fallback if the overlay approach regresses on a future iOS or CMP release.
 */
internal const val NATIVE_GLASS_BARS_ENABLED = true

@Composable
actual fun platformColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme? = null

actual fun supportsDynamicColor(): Boolean = false

actual fun platformDefaultUiStyle(): UiStyle = UiStyle.LIQUID_GLASS

@OptIn(ExperimentalForeignApi::class)
actual fun glassCapability(): GlassCapability = iosGlassCapability(
    osMajor = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion.toInt() },
    nativeBarsEnabled = NATIVE_GLASS_BARS_ENABLED,
    reduceTransparency = UIAccessibilityIsReduceTransparencyEnabled(),
)
