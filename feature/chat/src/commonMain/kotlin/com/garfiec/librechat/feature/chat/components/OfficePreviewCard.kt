package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactType
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactWebContent
import com.garfiec.librechat.feature.chat.components.artifact.InlineArtifactView
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.office_preview_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * True for an office document that renders as its own inline card: its preview is ready, or it
 * predates the preview lifecycle. One still pending, or whose preview failed, is a
 * [PendingPreviewRow] in its tool call's artifact slot instead, like every other generated file —
 * this is the one predicate both sides read, so a document renders in exactly one of them. When a
 * pending document resolves it moves from the row to this card without a transition: the inline
 * card is shipped mobile behaviour the web's `ArtifactRow` does not have.
 */
internal fun isInlineOfficePreview(attachment: Attachment): Boolean =
    ArtifactType.isOfficePreviewMime(attachment.type) &&
        attachment.status != FilePreviewResponse.STATUS_PENDING &&
        attachment.status != FilePreviewResponse.STATUS_FAILED

/**
 * Renders a ready office-doc preview attachment (v0.8.6):
 *  - with text → the extracted document as an HTML artifact, built through
 *    [ArtifactWebContent.buildOfficePreviewHtml] (which enforces the
 *    textFormat==html security gate — HTML only when html, escaped otherwise)
 *    and rendered via the SAME [InlineArtifactView] WebView other artifacts use.
 *  - with no text (binary/oversized) → a download-only chip.
 *
 * The live stream and a reloaded message reach it with the preview result already applied
 * (`withPreview`), so both render the same.
 */
@Composable
internal fun OfficePreviewCard(
    attachment: Attachment,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
) {
    val filename = attachment.filename.orEmpty()
    val previewText = attachment.text

    when {
        !previewText.isNullOrBlank() -> {
            // Build the safe document HERE (where textFormat is known) so the
            // security gate is honored, then hand the complete document to the
            // shared artifact WebView (buildHtml passes office content unchanged).
            val artifact = Artifact(
                identifier = attachment.fileId ?: filename,
                type = attachment.type ?: ArtifactType.DEFAULT_OFFICE_PREVIEW_MIME,
                title = filename,
                language = null,
                content = ArtifactWebContent.buildOfficePreviewHtml(
                    text = previewText,
                    textFormat = attachment.textFormat,
                    isDarkTheme = isDarkTheme,
                    inline = true,
                ),
            )
            InlineArtifactView(
                artifact = artifact,
                onTap = { /* inline preview; full-screen open handled elsewhere if needed */ },
                modifier = modifier.fillMaxWidth(),
            )
        }

        else ->
            // ready but no inline content (binary/oversized) → download-only.
            OfficeStatusChip(
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                label = stringResource(Res.string.office_preview_unavailable, filename),
                modifier = modifier,
            )
    }
}

/**
 * The pending or failed office documents no rendered tool call claims. They'd show nowhere
 * otherwise: [partitionToolCallAttachments] puts one in its call's artifact slot only when that
 * call has a card. Pure — unit-tested.
 */
internal fun unclaimedOfficePreviewRows(
    attachments: List<Attachment>,
    renderedToolCallIds: Collection<String>,
    nowMs: Long = currentTimeMs(),
): List<ToolAttachment.Pending> = attachments.asSequence()
    .filter { ArtifactType.isOfficePreviewMime(it.type) && !isInlineOfficePreview(it) }
    .filter { it.toolCallId == null || it.toolCallId !in renderedToolCallIds }
    .distinctBy { it.fileId ?: it.filename }
    .mapNotNull { classifyToolAttachment(it, nowMs) as? ToolAttachment.Pending }
    .toList()

/**
 * Renders every inline office-doc preview in [attachments] ([isInlineOfficePreview]),
 * de-duplicated by file_id (a live record and a persisted record for the same file must not
 * double-render), then the pending or failed ones no tool call card claims
 * ([unclaimedOfficePreviewRows]) as preview rows. Everything else keeps its own render path.
 */
@Composable
internal fun OfficePreviewAttachments(
    attachments: List<Attachment>,
    renderedToolCallIds: Collection<String>,
    baseUrl: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
) {
    val officeAttachments = attachments
        .filter(::isInlineOfficePreview)
        .distinctBy { it.fileId ?: it.filename }
    val unclaimed = remember(attachments, renderedToolCallIds) {
        unclaimedOfficePreviewRows(attachments, renderedToolCallIds)
    }
    if (officeAttachments.isEmpty() && unclaimed.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        officeAttachments.forEach { attachment ->
            Spacer(modifier = Modifier.padding(top = 4.dp))
            OfficePreviewCard(attachment = attachment, isDarkTheme = isDarkTheme)
        }
        if (unclaimed.isNotEmpty()) {
            val actions = rememberToolFileActions(baseUrl)
            unclaimed.forEach { item ->
                key(item.attachment.fileId ?: item.attachment.filename) {
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    PendingPreviewRow(item = item, actions = actions)
                }
            }
        }
    }
}

@Composable
private fun OfficeStatusChip(
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            icon()
        }
    }
}
