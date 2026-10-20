package com.garfiec.librechat.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.garfiec.librechat.core.model.files.FileKind
import kotlin.math.pow
import kotlin.math.sqrt
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

    /** OKLab distance; 0.06 separates violet image from the web's violet video (0.048). */
    private fun distance(a: Color, b: Color): Float {
        val x = a.convert(ColorSpaces.Oklab)
        val y = b.convert(ColorSpaces.Oklab)
        return sqrt((x.red - y.red).pow(2) + (x.green - y.green).pow(2) + (x.blue - y.blue).pow(2))
    }

    @Test
    fun kindsWithTheirOwnHueStayApart() {
        // These share a fill on purpose: documents take the web's one pink, and the web gives code
        // and audio the same orange.
        val sharing = listOf(
            setOf(FileKind.DOCUMENT, FileKind.PDF, FileKind.TEXT),
            setOf(FileKind.CODE, FileKind.AUDIO),
        )
        val failures = mutableListOf<String>()
        for ((theme, fill) in listOf<Pair<String, (FileKind) -> Color>>("light" to FileKindColors::light, "dark" to FileKindColors::dark)) {
            val kinds = FileKind.entries
            for (i in kinds.indices) {
                for (j in i + 1 until kinds.size) {
                    val pair = setOf(kinds[i], kinds[j])
                    if (sharing.any { it.containsAll(pair) }) continue
                    val d = distance(fill(kinds[i]), fill(kinds[j]))
                    if (d < MIN_DISTANCE) failures += "${kinds[i]}/${kinds[j]} $theme $d"
                }
            }
        }
        assertTrue(failures.isEmpty(), "tiles too alike: $failures")
    }

    private companion object {
        const val MIN_DISTANCE = 0.06f
    }
}
