package com.garfiec.librechat.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Categorical series colours for charts and segmented meters, matching the LibreChat web client's
 * `--series-1..8` (client/src/style.css at v0.8.8). The slot order is the colour-vision-safety
 * mechanism, so a chart assigns slots in order rather than picking colours. Both styles read the
 * same palette; only light and dark differ.
 */
object SeriesColors {
    private val light = listOf(
        Color(0xFF056EBD), Color(0xFFE9560D), Color(0xFF00948E), Color(0xFFB67B05),
        Color(0xFFD85A8E), Color(0xFF7E23CD), Color(0xFF018301), Color(0xFF3F51B5),
    )
    private val dark = listOf(
        Color(0xFF098CEE), Color(0xFFD95723), Color(0xFF069E98), Color(0xFFC8850C),
        Color(0xFFD55282), Color(0xFFAB68FE), Color(0xFF50A731), Color(0xFF7882BE),
    )

    /** The colour of 1-based [slot]; out-of-range slots wrap, so nothing renders untinted. */
    @Composable
    @ReadOnlyComposable
    fun slot(slot: Int): Color {
        val palette = if (LocalDarkTheme.current) dark else light
        return palette[((slot - 1) % palette.size + palette.size) % palette.size]
    }
}
