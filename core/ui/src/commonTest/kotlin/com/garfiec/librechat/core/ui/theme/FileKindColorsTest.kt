package com.garfiec.librechat.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.garfiec.librechat.core.model.files.FileKind
import kotlin.test.Test
import kotlin.test.assertTrue

class FileKindColorsTest {

    private fun contrastWithWhite(fill: Color): Double = 1.05 / (fill.luminance() + 0.05)

    @Test
    fun whiteGlyphReachesAaOnEveryTile() {
        val failures = mutableListOf<String>()
        for (kind in FileKind.entries) {
            for ((theme, fill) in listOf("light" to FileKindColors.light(kind), "dark" to FileKindColors.dark(kind))) {
                val ratio = contrastWithWhite(fill)
                // Doubles as the contrast table for review.
                println("$kind $theme #${(fill.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0')} ${(ratio * 100).toInt() / 100.0}")
                if (ratio < 4.5) failures += "$kind $theme $ratio"
            }
        }
        assertTrue(failures.isEmpty(), "under 4.5:1: $failures")
    }
}
