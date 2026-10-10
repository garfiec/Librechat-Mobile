package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.theme.LocalGlassLevel
import com.garfiec.librechat.core.ui.theme.glassCapability

/**
 * Draws [content] as the app would look in [style], whatever style the app is in now: the
 * `Adaptive*` components inside render that style's variant, on that style's page background.
 * For a style picker's previews only. Bars inside stay in Compose (never the system's), and the
 * window's insets are taken as used, so a bar or scaffold in the preview doesn't pad for the status
 * bar. The content is hidden from accessibility, since it is a picture of controls rather than
 * controls; the caller labels the preview itself.
 */
@Composable
fun UiStylePreview(
    style: UiStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val glass = style == UiStyle.LIQUID_GLASS
    // The device's real tier, so a reduced device previews the reduced glass it would get.
    val capability = remember { glassCapability() }
    CompositionLocalProvider(
        LocalGlassLevel provides capability.takeIf { glass },
        LocalInSeparateWindow provides true,
    ) {
        Box(
            modifier = modifier
                .consumeWindowInsets(WindowInsets.safeDrawing)
                .background(if (glass) GlassControlColors.groupedBackground else MaterialTheme.colorScheme.background)
                .clearAndSetSemantics {},
        ) {
            content()
        }
    }
}
