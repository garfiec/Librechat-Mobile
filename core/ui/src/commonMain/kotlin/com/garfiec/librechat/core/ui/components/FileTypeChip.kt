package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.files.FileKind

private val ChipShape = RoundedCornerShape(12.dp)
private val ChipMaxWidth = 240.dp

/** Large text widens the cap rather than ellipsizing the size off the second line. */
private const val MAX_WIDTH_FONT_SCALE = 1.5f
private const val DISABLED_ALPHA = 0.38f

/**
 * A file shown by name with its [FileTypeTile] in front and a second line under the name — the
 * type and size ([fileTypeSubtitle]) or a state such as "Expired". [isLoading] swaps the glyph
 * for a spinner while a tap's work is in flight and holds further taps; [enabled] = false dims
 * the chip and ignores taps. [dimmed] dims it but keeps it tappable, for a file that can no longer
 * be opened but can still say why; [glyph] replaces the kind's glyph on the tile (a clock for an
 * expired file). Tappable only with an [onClick], and then at least 48dp tall.
 */
@Composable
fun FileTypeChip(
    filename: String,
    kind: FileKind,
    subtitle: String?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    glyph: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .widthIn(max = ChipMaxWidth * LocalDensity.current.fontScale.coerceIn(1f, MAX_WIDTH_FONT_SCALE))
            .heightIn(min = 48.dp)
            .clip(ChipShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled && !isLoading, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .then(if (enabled && !dimmed) Modifier else Modifier.alpha(DISABLED_ALPHA))
            .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeTile(
            kind = kind,
            content = if (isLoading) {
                {
                    AdaptiveCircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                        trackColor = Color.Transparent,
                    )
                }
            } else if (glyph != null) {
                {
                    Icon(
                        imageVector = glyph,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                }
            } else {
                null
            },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = filename,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Wraps rather than cutting the size off when a large font fills the width.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
