package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.config.theme.ServerThemeDefinition
import com.garfiec.librechat.core.model.config.theme.ThemeRole
import com.garfiec.librechat.core.model.config.theme.resolve
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

    @Test
    fun serverTokensComeFromTheStatusSeriesAndFileRoles() {
        val colors = ServerThemeDefinition("stock", emptyMap(), emptyMap()).resolve(dark = true)
        val tokens = ThemeTokens.server(colors, dark = true)
        fun c(role: ThemeRole) = Color(0xFF000000 or role.libreChatDark.toLong())
        assertEquals(
            StatusColors(
                success = c(ThemeRole.STATUS_SUCCESS),
                info = c(ThemeRole.STATUS_INFO),
                warning = c(ThemeRole.STATUS_WARNING),
                neutral = c(ThemeRole.STATUS_NEUTRAL),
                error = c(ThemeRole.STATUS_ERROR),
            ),
            tokens.status,
        )
        assertEquals(c(ThemeRole.SERIES_1), tokens.series.first())
        assertEquals(c(ThemeRole.SERIES_8), tokens.series.last())
        assertEquals(c(ThemeRole.FILE_DOCUMENT), tokens.fileKinds.getValue(FileKind.PDF))
        assertEquals(c(ThemeRole.FILE_SHEET), tokens.fileKinds.getValue(FileKind.SPREADSHEET))
        assertEquals(c(ThemeRole.FILE_GENERIC), tokens.fileKinds.getValue(FileKind.FILE))
    }

    @Test
    fun serverTokensKeepSwitchboardFillsForKindsTheWebLacks() {
        val colors = ServerThemeDefinition("stock", emptyMap(), emptyMap()).resolve(dark = false)
        val tokens = ThemeTokens.server(colors, dark = false)
        for (kind in listOf(FileKind.DATA, FileKind.IMAGE, FileKind.PRESENTATION, FileKind.ARCHIVE)) {
            assertEquals(FileKindColors.light(kind), tokens.fileKinds.getValue(kind), "$kind")
        }
    }
}
