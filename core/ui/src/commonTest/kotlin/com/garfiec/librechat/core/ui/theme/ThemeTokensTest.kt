package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.files.FileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ThemeTokensTest {

    private val lightScheme =
        lightColorScheme(primary = Color(0xFF123456), outline = Color(0xFF654321), error = Color(0xFFAA0000))
    private val darkScheme =
        darkColorScheme(primary = Color(0xFFABCDEF), outline = Color(0xFF777777), error = Color(0xFFFF5555))

    @Test
    fun switchboardStatusKeepsSeriesGreenAndAmberAndSchemeRoles() {
        for ((scheme, dark) in listOf(lightScheme to false, darkScheme to true)) {
            val tokens = ThemeTokens.switchboard(scheme, dark)
            val series = SeriesColors.switchboard(dark)
            assertEquals(
                StatusColors(
                    success = series[6],
                    info = scheme.primary,
                    warning = series[3],
                    neutral = scheme.outline,
                    error = scheme.error,
                ),
                tokens.status,
                "dark=$dark",
            )
        }
    }

    @Test
    fun switchboardPalettesFollowTheMode() {
        val light = ThemeTokens.switchboard(lightScheme, dark = false)
        val dark = ThemeTokens.switchboard(darkScheme, dark = true)
        assertEquals(SeriesColors.switchboard(dark = false), light.series)
        assertEquals(SeriesColors.switchboard(dark = true), dark.series)
        for (kind in FileKind.entries) {
            assertEquals(FileKindColors.light(kind), light.fileKinds.getValue(kind), "$kind light")
            assertEquals(FileKindColors.dark(kind), dark.fileKinds.getValue(kind), "$kind dark")
        }
    }

    @Test
    fun rejectsAnIncompletePalette() {
        val tokens = ThemeTokens.switchboard(lightScheme, dark = false)
        assertFailsWith<IllegalArgumentException> { tokens.copy(series = tokens.series.drop(1)) }
        assertFailsWith<IllegalArgumentException> { tokens.copy(fileKinds = tokens.fileKinds - FileKind.PDF) }
    }
}
