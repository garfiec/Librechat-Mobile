package com.garfiec.librechat.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.files.FileKind

/**
 * File-type tile fills. Most keep the web's hue for their kind (`fileTypes` in
 * `client/src/utils/files.ts`). Kinds the web doesn't have, and two whose web hue clashes or glares
 * here (video's violet beside image, file's pure blue), take the mobile design's hue instead. Every
 * fill was darkened in OKLCH (hue kept) until the white glyph reaches 4.5:1, and dark mode also drops
 * 20% of the chroma so tiles don't glare on dark surfaces. `FileKindColorsTest` holds both tables to
 * that ratio and keeps the kinds apart. Both styles read the same tables.
 */
object FileKindColors {
    /** Source fill (web, or "mobile" for the design's) → light → dark. */
    private val table: Map<FileKind, Pair<Color, Color>> = mapOf(
        FileKind.SPREADSHEET to (Color(0xFF068567) to Color(0xFF218468)), // #10A37F
        FileKind.DOCUMENT to (Color(0xFFD92E69) to Color(0xFFC7466C)), // #FF5588
        FileKind.PDF to (Color(0xFFD92E69) to Color(0xFFC7466C)), // #FF5588
        FileKind.TEXT to (Color(0xFFD92E69) to Color(0xFFC7466C)), // #FF5588
        FileKind.CODE to (Color(0xFFD14503) to Color(0xFFC1532D)), // #FF6E3C
        FileKind.DATA to (Color(0xFFA26903) to Color(0xFF996C2E)), // mobile #A86F12
        FileKind.IMAGE to (Color(0xFF7356D6) to Color(0xFF715FC3)), // mobile #7356D6
        FileKind.PRESENTATION to (Color(0xFF0E7490) to Color(0xFF307288)), // mobile #0E7490
        FileKind.ARCHIVE to (Color(0xFF64748B) to Color(0xFF677486)), // mobile #64748B
        FileKind.AUDIO to (Color(0xFFD04603) to Color(0xFFC3522A)), // #FF6B35
        FileKind.VIDEO to (Color(0xFFA21CAF) to Color(0xFF9838A1)), // mobile #A21CAF (web #8B5CF6)
        FileKind.FILE to (Color(0xFF3A64C8) to Color(0xFF4467B6)), // mobile #3A64C8 (web #0000FF)
    )

    fun light(kind: FileKind): Color = table.getValue(kind).first

    fun dark(kind: FileKind): Color = table.getValue(kind).second

    @Composable
    @ReadOnlyComposable
    fun of(kind: FileKind): Color = if (LocalDarkTheme.current) dark(kind) else light(kind)
}
