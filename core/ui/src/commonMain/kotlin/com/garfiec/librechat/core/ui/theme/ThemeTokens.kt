package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.files.FileKind

/**
 * The colour roles the app paints beyond the M3 [ColorScheme], for one light or dark mode. Every
 * theme fills all of them, so a reader never keeps a palette of its own: read them through
 * [statusColors], [SeriesColors] and [FileKindColors], which take them from [LocalThemeTokens].
 * Names follow the LibreChat web roles they correspond to (`status-*`, `series-1..8`, `file-*`).
 */
@Immutable
data class ThemeTokens(
    val status: StatusColors,
    /** `series-1..8`, in slot order. */
    val series: List<Color>,
    /** A fill for every [FileKind], each carrying a white glyph at 4.5:1. */
    val fileKinds: Map<FileKind, Color>,
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
            )
        }

        private const val SUCCESS_SLOT = 7
        private const val WARNING_SLOT = 4
    }
}

/** Provided by [LibreChatTheme] and [ThemePreview]; the default only serves content drawn outside them. */
val LocalThemeTokens = staticCompositionLocalOf { ThemeTokens.switchboard(lightColorScheme(), dark = false) }
