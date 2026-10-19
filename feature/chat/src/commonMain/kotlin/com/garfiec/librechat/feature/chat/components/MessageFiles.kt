package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.model.media.resolveFileReferenceUrl
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.FileTypeChip
import com.garfiec.librechat.core.ui.components.fileTypeSubtitle
import com.garfiec.librechat.core.ui.media.mediaThumbnail
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Renders files attached to a message. Splits files into images (shown as
 * inline previews) and non-image files (shown as file chips with icon and name).
 *
 * This mirrors the official LibreChat web app's `Files` component which renders
 * `message.files` above the message text for user messages.
 *
 * [alignImagesEnd] puts the image previews at the end edge while the file chips stay at the start,
 * as upstream does for a user turn (`Image`'s `alignRight`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageFiles(
    files: List<FileReference>,
    modifier: Modifier = Modifier,
    baseUrl: String = "",
    alignImagesEnd: Boolean = false,
) {
    if (files.isEmpty()) return

    val imageFiles = remember(files) {
        files.filter { it.type?.startsWith("image/") == true }
    }
    val otherFiles = remember(files) {
        files.filter { it.type?.startsWith("image/") != true }
    }

    Column(modifier = modifier) {
        // Non-image file chips
        if (otherFiles.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                otherFiles.forEach { file ->
                    FileChip(
                        filename = file.filename ?: file.filepath?.substringAfterLast('/') ?: stringResource(Res.string.file_fallback),
                        type = file.type,
                        bytes = file.bytes,
                    )
                }
            }
            if (imageFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Image previews
        imageFiles.forEach { file ->
            val imageUrl = resolveFileReferenceUrl(file, baseUrl)
            if (imageUrl != null) {
                MessageImagePreview(
                    imageUrl = imageUrl,
                    altText = file.filename ?: stringResource(Res.string.cd_attached_image),
                    modifier = if (alignImagesEnd) Modifier.align(Alignment.End) else Modifier,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Inline image preview that can be tapped to open fullscreen.
 */
@Composable
internal fun MessageImagePreview(
    imageUrl: String,
    altText: String,
    modifier: Modifier = Modifier,
) {
    val openMedia = LocalChatMediaViewer.current

    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = altText,
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .widthIn(max = 300.dp)
            .heightIn(max = 300.dp)
            .mediaThumbnail(imageUrl, RoundedCornerShape(12.dp))
            .clickable { openMedia(imageUrl) }
            .semantics {
                role = Role.Image
                contentDescription = altText
            },
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = stringResource(Res.string.cd_failed_to_load_image),
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * A non-image file as a [FileTypeChip]: its type tile, its name, and its type and size.
 *
 * When [onClick] is non-null the chip is tappable (e.g. to download + share a generated file);
 * [isLoading] swaps the tile's glyph for a spinner while that action is in flight. An [expired]
 * file is dimmed, shows a clock and says "Expired", and stays tappable to explain why.
 */
@Composable
internal fun FileChip(
    filename: String,
    type: String?,
    modifier: Modifier = Modifier,
    bytes: Long? = null,
    isLoading: Boolean = false,
    expired: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val kind = remember(filename, type) { fileKindOf(filename, type) }
    FileTypeChip(
        filename = filename,
        kind = kind,
        subtitle = if (expired) stringResource(Res.string.expired) else fileTypeSubtitle(kind, filename, bytes),
        modifier = modifier,
        isLoading = isLoading,
        dimmed = expired,
        glyph = if (expired) Icons.Outlined.Schedule else null,
        onClick = onClick,
    )
}
