package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.core.ui.components.fileKindLabel
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactFileSource
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactType
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactWebContent
import com.garfiec.librechat.feature.chat.components.artifact.LocalOpenArtifact
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.attachment_fallback
import com.garfiec.librechat.feature.chat.resources.cd_open_file
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
    officePreviewMimeOf(attachment) != null &&
        attachment.status != FilePreviewResponse.STATUS_PENDING &&
        attachment.status != FilePreviewResponse.STATUS_FAILED

/**
 * The artifact an office preview attachment shows, or null when it has no text to show. Built
 * HERE, where textFormat is known, so the security gate is honoured: the document comes from
 * [ArtifactWebContent.buildOfficePreviewHtml] (HTML only when the server marks it html, escaped
 * otherwise) and [ArtifactWebContent.buildHtml] passes office content through unchanged. [inline]
 * picks the card's tighter padding; the panel copy is built without it. [Artifact.source] is what
 * gives the panel's overflow Open in / Save / Share for the original file.
 */
fun officePreviewArtifact(attachment: Attachment, isDarkTheme: Boolean, inline: Boolean): Artifact? {
    val previewText = attachment.text
    if (previewText.isNullOrBlank()) return null
    val filename = attachment.filename.orEmpty()
    return Artifact(
        identifier = attachment.fileId ?: filename,
        // The bucket, not the file's own MIME: a real openxml or csv type would classify as
        // CODE, and ArtifactWebContent would escape the document built here.
        type = officePreviewMimeOf(attachment) ?: ArtifactType.DEFAULT_OFFICE_PREVIEW_MIME,
        title = displayFilename(filename),
        language = null,
        content = ArtifactWebContent.buildOfficePreviewHtml(
            text = previewText,
            textFormat = attachment.textFormat,
            isDarkTheme = isDarkTheme,
            inline = inline,
        ),
        source = ArtifactFileSource(
            fileId = attachment.fileId,
            filepath = attachment.filepath,
            filename = filename,
            // A `-preview` bucket is ours, not a type any other app opens; without one, the file's
            // name decides.
            mime = attachment.type?.takeUnless(ArtifactType::isOfficePreviewMime),
        ),
    )
}

/**
 * Renders a ready office-doc preview attachment (v0.8.6):
 *  - with text → an [OfficePreviewHeader] over the document ([officePreviewArtifact]) in the
 *    SAME [InlineArtifactView] WebView other artifacts use, sized to the document up to the
 *    inline cap. The header is the way in: the WebView takes its own touches, to scroll and zoom
 *    a table, so a tap on the page never reaches the card.
 *  - with no text (binary/oversized) → a download-only chip.
 *
 * The live stream and a reloaded message reach it with the preview result already applied
 * (`withPreview`), so both render the same.
 */
@Composable
fun OfficePreviewCard(
    attachment: Attachment,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    onOpen: () -> Unit = {},
) {
    val filename = attachment.filename.orEmpty()
    val artifact = remember(attachment, isDarkTheme) {
        officePreviewArtifact(attachment, isDarkTheme, inline = true)
    }

    if (artifact != null) {
        val inlineView = LocalChatContentRenderers.current.inlineArtifact
        if (inlineView != null) {
            inlineView.content(
                artifact,
                onOpen,
                modifier.fillMaxWidth(),
                true,
                { OfficePreviewHeader(attachment = attachment, onOpen = onOpen) },
                emptySet(),
            )
        } else {
            OfficePreviewHeader(attachment = attachment, onOpen = onOpen)
        }
    } else {
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
 * The office card's title row and its open control: the file's tile, name and kind, and an open
 * glyph. The whole row is one 48dp button, read as "Open memo.docx".
 */
@Composable
fun OfficePreviewHeader(
    attachment: Attachment,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = attachmentName(attachment)?.let { displayFilename(it) }
        ?: stringResource(Res.string.attachment_fallback)
    val kind = fileKindOf(attachment.filename, attachment.type)
    val openLabel = stringResource(Res.string.cd_open_file, name)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = openLabel
                role = Role.Button
                onClick { onOpen(); true }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeTile(kind = kind, size = 32.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                text = fileKindLabel(kind, attachment.filename),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    .filter { officeBucketOf(it) != null && !isInlineOfficePreview(it) }
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
    val actions = rememberToolFileActions(baseUrl)
    val openArtifact = LocalOpenArtifact.current
    Column(modifier = modifier.fillMaxWidth()) {
        officeAttachments.forEach { attachment ->
            Spacer(modifier = Modifier.padding(top = 4.dp))
            // Opens the document's viewer, whose overflow reaches the file itself (Open in, Save,
            // Share). With no viewer in scope (previews, tests) it falls back to the file's actions.
            OfficePreviewCard(
                attachment = attachment,
                isDarkTheme = isDarkTheme,
                onOpen = {
                    val panel = officePreviewArtifact(attachment, isDarkTheme, inline = false)
                    val open = openArtifact
                    if (open != null && panel != null) open(panel, listOf(panel)) else actions.openToolFile(attachment)
                },
            )
        }
        if (unclaimed.isNotEmpty()) {
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
