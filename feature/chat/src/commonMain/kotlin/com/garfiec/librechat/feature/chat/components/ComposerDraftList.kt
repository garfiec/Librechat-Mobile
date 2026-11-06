package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.fileTypeSubtitle
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_cancel_upload
import com.garfiec.librechat.feature.chat.resources.cd_remove_file
import com.garfiec.librechat.feature.chat.resources.cd_remove_quote_named
import com.garfiec.librechat.feature.chat.resources.cd_remove_skill
import com.garfiec.librechat.feature.chat.resources.cd_retry_upload
import com.garfiec.librechat.feature.chat.resources.cd_upload_route_code
import com.garfiec.librechat.feature.chat.resources.cd_upload_route_text
import com.garfiec.librechat.feature.chat.resources.draft_list_header
import com.garfiec.librechat.feature.chat.resources.draft_row_failed
import com.garfiec.librechat.feature.chat.resources.draft_row_quote
import com.garfiec.librechat.feature.chat.resources.draft_row_skill
import com.garfiec.librechat.feature.chat.resources.draft_row_uploading
import com.garfiec.librechat.feature.chat.resources.draft_subtitle_join
import com.garfiec.librechat.feature.chat.util.DraftItem
import com.garfiec.librechat.feature.chat.util.DraftItems
import com.garfiec.librechat.feature.chat.util.DraftUploadProgress
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The list the attachment stack opens, over the thread just above the composer: everything that goes
 * with the message (failed files first, then images, files, skills and quotes), one 52dp row each
 * with a 48dp × that removes it. Past four and a half rows it scrolls.
 *
 * Only a failed file's row acts on a tap (it retries); the others aren't tappable. Opening a picked,
 * unsent image isn't offered yet: the media viewer only knows the conversation's own media, so a
 * local preview of a draft image is a follow-up.
 *
 * Opaque over the thread in Material (`surfaceContainerHigh`), a glass panel in Liquid Glass.
 *
 * [focusHeader] moves focus to the header as it opens, for a screen reader that opened it: drawn
 * above the composer, the panel comes before the stack in traversal order, so without it the next
 * swipe from the stack goes on to the field.
 */
@Composable
internal fun ComposerDraftListPanel(
    items: DraftItems,
    onRemove: (DraftItem) -> Unit,
    onRetry: (AttachedFile) -> Unit,
    modifier: Modifier = Modifier,
    focusHeader: Boolean = false,
    /** The rows' upload rings' progress, read as they draw. */
    uploadProgress: DraftUploadProgress = DraftUploadProgress.NONE,
) {
    val count = items.items.size
    val header = pluralStringResource(Res.plurals.draft_list_header, count, count)
    val headerFocus = remember { FocusRequester() }
    LaunchedEffect(focusHeader) { if (focusHeader) headerFocus.requestFocus() }
    AdaptiveCard(
        modifier = modifier.semantics { paneTitle = header },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Text(
            text = header,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                .focusRequester(headerFocus)
                .focusable()
                .semantics { heading() },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = DRAFT_ROW_HEIGHT * 4 + DRAFT_ROW_HEIGHT / 2)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
        ) {
            items.items.forEach { item ->
                key(item.key) {
                    DraftItemRow(item = item, onRemove = { onRemove(item) }, onRetry = onRetry, uploadProgress = uploadProgress)
                }
            }
        }
    }
}

@Composable
private fun DraftItemRow(
    item: DraftItem,
    onRemove: () -> Unit,
    onRetry: (AttachedFile) -> Unit,
    uploadProgress: DraftUploadProgress,
) {
    val attachment = item as? DraftItem.Attachment
    val failedFile = attachment?.takeIf { it.isFailed }?.file
    val retryLabel = failedFile?.let { stringResource(Res.string.cd_retry_upload, it.name) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DRAFT_ROW_HEIGHT)
            .then(
                if (failedFile != null) {
                    Modifier.clickable(onClickLabel = retryLabel) { onRetry(failedFile) }
                } else {
                    Modifier
                },
            )
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            DraftItemTile(item = item, size = ROW_TILE)
            if (attachment != null && attachment.isUploading) {
                Box(
                    modifier = Modifier
                        .size(ROW_TILE)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                )
                UploadRing({ uploadProgress.of(attachment.file) }, 20.dp, MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                text = item.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = draftItemSubtitle(item),
                style = MaterialTheme.typography.bodySmall,
                color = if (failedFile != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = removeDescription(item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A row's second line: the file's type and route, its upload state, or what kind of item it is. */
@Composable
private fun draftItemSubtitle(item: DraftItem): String = when (item) {
    is DraftItem.Attachment -> when {
        item.isFailed -> stringResource(Res.string.draft_row_failed)
        item.isUploading -> stringResource(Res.string.draft_row_uploading)
        else -> {
            val file = item.file
            val kind = remember(file.name, file.type) { fileKindOf(file.name, file.type) }
            val type = fileTypeSubtitle(kind, file.name, null)
            when (file.route) {
                UploadRoute.TEXT -> stringResource(Res.string.draft_subtitle_join, type, stringResource(Res.string.cd_upload_route_text))
                UploadRoute.CODE -> stringResource(Res.string.draft_subtitle_join, type, stringResource(Res.string.cd_upload_route_code))
                UploadRoute.PROVIDER, null -> type
            }
        }
    }

    is DraftItem.Skill -> stringResource(Res.string.draft_row_skill)
    is DraftItem.Quote -> stringResource(Res.string.draft_row_quote)
}

@Composable
private fun removeDescription(item: DraftItem): String = when (item) {
    is DraftItem.Attachment -> if (item.isUploading) {
        stringResource(Res.string.cd_cancel_upload, item.file.name)
    } else {
        stringResource(Res.string.cd_remove_file, item.file.name)
    }

    is DraftItem.Skill -> stringResource(Res.string.cd_remove_skill, item.name)
    is DraftItem.Quote -> stringResource(Res.string.cd_remove_quote_named, item.text.take(QUOTE_CD_CHARS))
}

/**
 * [draftListMinHeight] for the panel as it draws now: the header's, a row's name and its subtitle's
 * text styles, at the font size in effect.
 */
@Composable
@ReadOnlyComposable
internal fun draftListMinHeight(): Dp {
    val type = MaterialTheme.typography
    return LocalDensity.current.draftListMinHeight(header = type.titleSmall, name = type.bodyMedium, subtitle = type.bodySmall)
}

internal val DRAFT_ROW_HEIGHT = 52.dp
private val ROW_TILE = 36.dp
private const val QUOTE_CD_CHARS = 60
