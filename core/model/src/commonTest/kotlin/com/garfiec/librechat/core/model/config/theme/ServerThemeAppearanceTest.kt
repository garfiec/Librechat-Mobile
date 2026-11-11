package com.garfiec.librechat.core.model.config.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerThemeAppearanceTest {

    @Test
    fun lengthsReadTheWayTheWebAcceptsThem() {
        assertEquals(0f, cssLengthDp("0"))
        assertEquals(6f, cssLengthDp("6px"))
        assertEquals(4f, cssLengthDp("0.25rem"))
        assertEquals(12f, cssLengthDp(".75em"))
        assertEquals(4f, cssLengthDp("calc(0.5rem - 4px)"))
        assertEquals(10f, cssLengthDp("calc(0.5rem + 2px)"))
        assertEquals(9999f, cssLengthDp("9999px"))
        listOf("", "4", "4dp", "-2px", "50%", "calc(1rem * 2)", "var(--x)").forEach { assertNull(cssLengthDp(it), it) }
    }

    @Test
    fun aFontStackFallsToItsFirstGenericFamily() {
        assertEquals(GenericFontFamily.SERIF, genericFamily("Georgia, 'Times New Roman', serif"))
        assertEquals(GenericFontFamily.MONO, genericFamily("\"JetBrains Mono\", ui-monospace, monospace"))
        assertEquals(GenericFontFamily.SANS, genericFamily("\"Inter\", -apple-system, serif"))
        assertNull(genericFamily("Inter, Roboto"))
    }

    @Test
    fun onlyNamedSettingsArePresentAndNarrowOnesFollowTheirSource() {
        val appearance = readAppearance(
            mapOf(
                "radius2xl" to "0.5rem",
                "roundControlRadius" to "4px",
                "fontFamily" to "serif",
                "surfaceRadius" to "not a length",
                "tooltipRadius" to "2px",
            ),
        )
        assertEquals(
            mapOf(
                ThemeCorner.RADIUS_2XL to 8f,
                ThemeCorner.ROUND_CONTROL to 4f,
                ThemeCorner.COMPOSER_ACTION to 4f,
            ),
            appearance.corners,
        )
        assertEquals(
            mapOf(ThemeFont.BODY to GenericFontFamily.SERIF, ThemeFont.DISPLAY to GenericFontFamily.SERIF),
            appearance.fonts,
        )
        val named = readAppearance(mapOf("composerActionRadius" to "4px", "roundControlRadius" to "1rem"))
        assertEquals(4f, named.corners[ThemeCorner.COMPOSER_ACTION])
    }
}
