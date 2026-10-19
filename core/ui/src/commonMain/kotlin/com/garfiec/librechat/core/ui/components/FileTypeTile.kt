package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.files.FileKind
import com.garfiec.librechat.core.model.files.codeLanguageOf
import com.garfiec.librechat.core.ui.glass.GlassDefaults
import com.garfiec.librechat.core.ui.platform.formatFileSize
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.file_kind_archive
import com.garfiec.librechat.core.ui.resources.file_kind_audio
import com.garfiec.librechat.core.ui.resources.file_kind_code
import com.garfiec.librechat.core.ui.resources.file_kind_data
import com.garfiec.librechat.core.ui.resources.file_kind_document
import com.garfiec.librechat.core.ui.resources.file_kind_file
import com.garfiec.librechat.core.ui.resources.file_kind_image
import com.garfiec.librechat.core.ui.resources.file_kind_pdf
import com.garfiec.librechat.core.ui.resources.file_kind_presentation
import com.garfiec.librechat.core.ui.resources.file_kind_spreadsheet
import com.garfiec.librechat.core.ui.resources.file_kind_text
import com.garfiec.librechat.core.ui.resources.file_kind_video
import com.garfiec.librechat.core.ui.resources.file_type_and_size
import com.garfiec.librechat.core.ui.theme.FileKindColors
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

object FileTypeTileDefaults {
    val Size = 36.dp
}

/** Glyph-to-tile ratio, from the web's 20px glyph on a 36px tile. */
private const val GLYPH_RATIO = 20f / 36f

/** Corner-to-tile ratio: 8dp at the default 36dp. */
private const val CORNER_RATIO = 8f / 36f

/**
 * How far down the glass sheen reaches. The glyph's ink starts below 26% of the tile (its 22%
 * inset plus the icon's own padding), so the sheen never lightens what the 4.5:1 contrast is
 * measured against.
 */
private const val SHEEN_DEPTH = 0.25f
private const val SHEEN_ALPHA = 0.28f
private const val RIM_ALPHA = 0.35f

/**
 * A colored rounded square with a white glyph saying what kind of file sits beside it, as the web
 * puts in front of every file name (`FileIcon`). Decorative: the file's type is also written next
 * to it, so it has no content description. [content] replaces the glyph (a spinner, a name);
 * [shape] overrides the corner that scales with [size], for a tile that must match its neighbours.
 *
 * In Liquid Glass the tile stays opaque, so the glyph keeps its contrast whatever is behind it, and
 * takes a specular sheen along its top edge and a light rim — the tinted-icon look of iOS 26,
 * rather than a translucent pane: a 36dp tile can't sample a backdrop (chat content has none) and
 * a see-through fill would drop the white glyph below 4.5:1 on a light page.
 */
@Composable
fun FileTypeTile(
    kind: FileKind,
    modifier: Modifier = Modifier,
    size: Dp = FileTypeTileDefaults.Size,
    shape: Shape? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val defaultShape = remember(size) { RoundedCornerShape(size * CORNER_RATIO) }
    val tileShape = shape ?: defaultShape
    val glass = isLiquidGlass
    Box(
        modifier = modifier
            .size(size)
            .background(FileKindColors.of(kind), tileShape)
            .then(
                if (glass) {
                    Modifier
                        .drawWithCache {
                            val outline = tileShape.createOutline(this.size, layoutDirection, this)
                            val sheen = Brush.verticalGradient(
                                0f to Color.White.copy(alpha = SHEEN_ALPHA),
                                1f to Color.Transparent,
                                endY = this.size.height * SHEEN_DEPTH,
                            )
                            onDrawBehind { drawOutline(outline, sheen) }
                        }
                        .border(GlassDefaults.Hairline, Color.White.copy(alpha = RIM_ALPHA), tileShape)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            Icon(
                imageVector = fileKindGlyph(kind),
                contentDescription = null,
                modifier = Modifier.size(size * GLYPH_RATIO),
                tint = Color.White,
            )
        }
    }
}

fun fileKindGlyph(kind: FileKind): ImageVector = when (kind) {
    FileKind.SPREADSHEET -> Icons.Default.TableChart
    FileKind.DOCUMENT, FileKind.TEXT -> Icons.Default.Description
    FileKind.PDF -> Icons.Default.PictureAsPdf
    FileKind.CODE -> Icons.Default.Code
    FileKind.DATA -> Icons.Default.DataObject
    FileKind.IMAGE -> Icons.Default.Image
    FileKind.PRESENTATION -> Icons.Default.Slideshow
    FileKind.ARCHIVE -> Icons.Default.FolderZip
    FileKind.AUDIO -> Icons.Default.AudioFile
    FileKind.VIDEO -> Icons.Default.VideoFile
    FileKind.FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun fileKindLabelRes(kind: FileKind): StringResource = when (kind) {
    FileKind.SPREADSHEET -> Res.string.file_kind_spreadsheet
    FileKind.DOCUMENT -> Res.string.file_kind_document
    FileKind.PDF -> Res.string.file_kind_pdf
    FileKind.TEXT -> Res.string.file_kind_text
    FileKind.CODE -> Res.string.file_kind_code
    FileKind.DATA -> Res.string.file_kind_data
    FileKind.IMAGE -> Res.string.file_kind_image
    FileKind.PRESENTATION -> Res.string.file_kind_presentation
    FileKind.ARCHIVE -> Res.string.file_kind_archive
    FileKind.AUDIO -> Res.string.file_kind_audio
    FileKind.VIDEO -> Res.string.file_kind_video
    FileKind.FILE -> Res.string.file_kind_file
}

/** "Spreadsheet", "PDF", …; a code file names its language ("Python") when the extension says. */
@Composable
fun fileKindLabel(kind: FileKind, filename: String?): String =
    (if (kind == FileKind.CODE) codeLanguageOf(filename) else null) ?: stringResource(fileKindLabelRes(kind))

/** A file chip's second line: "Spreadsheet · 1.2 KB", or the type alone when the size is unknown. */
@Composable
fun fileTypeSubtitle(kind: FileKind, filename: String?, bytes: Long?): String {
    val label = fileKindLabel(kind, filename)
    if (bytes == null) return label
    // A no-break space keeps "1.2 KB" whole when the line wraps.
    return stringResource(Res.string.file_type_and_size, label, formatFileSize(bytes).replace(' ', '\u00A0'))
}
