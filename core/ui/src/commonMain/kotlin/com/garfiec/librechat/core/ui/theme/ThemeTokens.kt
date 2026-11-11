package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.config.theme.ServerThemeAppearance
import com.garfiec.librechat.core.model.config.theme.ThemeRole
import com.garfiec.librechat.core.model.files.FileKind

/**
 * The colour roles the app paints beyond the M3 [ColorScheme], for one light or dark mode. Every
 * theme fills all of them, so a reader never keeps a palette of its own: read them through
 * [statusColors], [SeriesColors], [FileKindColors], [chatColors] and [chromeColors], which take them
 * from [LocalThemeTokens]. Names follow the LibreChat web roles they correspond to (`status-*`,
 * `series-1..8`, `file-*`).
 */
@Immutable
data class ThemeTokens(
    val status: StatusColors,
    /** `series-1..8`, in slot order. */
    val series: List<Color>,
    /** A fill for every [FileKind], under a white glyph: Switchboard's at 4.5:1, a server's as it gives them, like the web. */
    val fileKinds: Map<FileKind, Color>,
    val chat: ChatColors,
    val chrome: ChromeColors,
    val page: PageColors,
    val shapes: ThemeShapes,
) {
    init {
        require(series.size == SERIES_SLOTS) { "series needs $SERIES_SLOTS colours, got ${series.size}" }
        require(fileKinds.keys == FileKind.entries.toSet()) { "fileKinds must cover every FileKind" }
    }

    companion object {
        const val SERIES_SLOTS = 8

        /** Switchboard's own tokens for [scheme]: fixed palettes per mode, status roles from the scheme. */
        fun switchboard(scheme: ColorScheme, dark: Boolean): ThemeTokens {
            val series = SeriesColors.switchboard(dark)
            return ThemeTokens(
                status = StatusColors(
                    success = series[SUCCESS_SLOT - 1],
                    info = scheme.primary,
                    warning = series[WARNING_SLOT - 1],
                    neutral = scheme.outline,
                    error = scheme.error,
                ),
                series = series,
                fileKinds = FileKind.entries.associateWith { FileKindColors.switchboard(it, dark) },
                chat = ChatColors.switchboard(scheme, dark),
                chrome = ChromeColors.switchboard(scheme),
                page = PageColors.switchboard(scheme),
                shapes = ThemeShapes.SWITCHBOARD,
            )
        }

        /**
         * A server theme's tokens, from its colours resolved for one mode (24-bit RGB for every role).
         * File kinds the web has no role for keep Switchboard's fill.
         */
        fun server(
            colors: Map<ThemeRole, Int>,
            dark: Boolean,
            appearance: ServerThemeAppearance = ServerThemeAppearance.NONE,
        ): ThemeTokens = ThemeTokens(
            status = StatusColors(
                success = colors.color(ThemeRole.STATUS_SUCCESS),
                info = colors.color(ThemeRole.STATUS_INFO),
                warning = colors.color(ThemeRole.STATUS_WARNING),
                neutral = colors.color(ThemeRole.STATUS_NEUTRAL),
                error = colors.color(ThemeRole.STATUS_ERROR),
            ),
            series = SERVER_SERIES.map(colors::color),
            fileKinds = FileKind.entries.associateWith { kind ->
                SERVER_FILE_ROLES[kind]?.let(colors::color) ?: FileKindColors.switchboard(kind, dark)
            },
            chat = ChatColors.server(colors, dark),
            chrome = ChromeColors.server(colors),
            page = PageColors.server(colors),
            shapes = ThemeShapes.of(appearance.corners),
        )

        private val SERVER_SERIES = listOf(
            ThemeRole.SERIES_1,
            ThemeRole.SERIES_2,
            ThemeRole.SERIES_3,
            ThemeRole.SERIES_4,
            ThemeRole.SERIES_5,
            ThemeRole.SERIES_6,
            ThemeRole.SERIES_7,
            ThemeRole.SERIES_8,
        )

        private val SERVER_FILE_ROLES = mapOf(
            FileKind.DOCUMENT to ThemeRole.FILE_DOCUMENT,
            FileKind.PDF to ThemeRole.FILE_DOCUMENT,
            FileKind.TEXT to ThemeRole.FILE_DOCUMENT,
            FileKind.SPREADSHEET to ThemeRole.FILE_SHEET,
            FileKind.CODE to ThemeRole.FILE_CODE,
            FileKind.AUDIO to ThemeRole.FILE_AUDIO,
            FileKind.VIDEO to ThemeRole.FILE_VIDEO,
            FileKind.FILE to ThemeRole.FILE_GENERIC,
        )

        private const val SUCCESS_SLOT = 7
        private const val WARNING_SLOT = 4
    }
}

/** Provided by [LibreChatTheme] and [ThemePreview]; the default only serves content drawn outside them. */
val LocalThemeTokens = staticCompositionLocalOf { ThemeTokens.switchboard(lightColorScheme(), dark = false) }
