package com.garfiec.librechat.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.files.FileKind

/**
 * File-type tile fills. Each keeps the web's hue for its kind (`fileTypes` in
 * `client/src/utils/files.ts`); kinds the web doesn't have take a hue no other kind uses. The web's
 * fills are too light for the white glyph, so each was darkened in OKLCH (hue kept) until white
 * reaches 4.5:1, and dark mode also drops 20% of the chroma so tiles don't glare on dark surfaces.
 * `FileKindColorsTest` holds both tables to that ratio. Both styles read the same tables.
 */
object FileKindColors {
    /** Web fill → light → dark. */
    private val table: Map<FileKind, Pair<Color, Color>> = mapOf(
        FileKind.SPREADSHEET to (Color(0xFF068567) to Color(0xFF218468)), // #10A37F
        FileKind.DOCUMENT to (Color(0xFFD92E69) to Color(0xFFC7466C)), // #FF5588
        FileKind.PDF to (Color(0xFFD92E69) to Color(0xFFC7466C)), // #FF5588
        FileKind.TEXT to (Color(0xFFD92E69) to Color(0xFFC7466C)), // #FF5588
        FileKind.CODE to (Color(0xFFD14503) to Color(0xFFC1532D)), // #FF6E3C
        FileKind.DATA to (Color(0xFFC026D3) to Color(0xFFB345C1)), // mobile only
        FileKind.IMAGE to (Color(0xFF057F9C) to Color(0xFF287E98)), // mobile only
        FileKind.PRESENTATION to (Color(0xFFB16003) to Color(0xFFAD621A)), // mobile only
        FileKind.ARCHIVE to (Color(0xFF64748B) to Color(0xFF677486)), // mobile only
        FileKind.AUDIO to (Color(0xFFD04603) to Color(0xFFC3522A)), // #FF6B35
        FileKind.VIDEO to (Color(0xFF8555EF) to Color(0xFF805FD6)), // #8B5CF6
        FileKind.FILE to (Color(0xFF0000FF) to Color(0xFF0437DD)), // #0000FF
    )

    fun light(kind: FileKind): Color = table.getValue(kind).first

    fun dark(kind: FileKind): Color = table.getValue(kind).second

    @Composable
    @ReadOnlyComposable
    fun of(kind: FileKind): Color = if (LocalDarkTheme.current) dark(kind) else light(kind)
}
