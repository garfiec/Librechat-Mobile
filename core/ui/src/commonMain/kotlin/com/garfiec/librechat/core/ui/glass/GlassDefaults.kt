package com.garfiec.librechat.core.ui.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object GlassDefaults {
    /** How far a disabled control fades (iOS dims rather than greys). */
    const val DISABLED_ALPHA = 0.4f

    val Hairline = 0.5.dp

    val MenuShape = RoundedCornerShape(16.dp)

    /** Extra wash for a panel of text (sheet, menu), so rows stay legible over a busy screen. */
    fun panelThickening(surface: Color, dark: Boolean): Color =
        surface.copy(alpha = if (dark) DARK_THICKENING else LIGHT_THICKENING)

    private const val DARK_THICKENING = 0.5f
    private const val LIGHT_THICKENING = 0.35f
}
