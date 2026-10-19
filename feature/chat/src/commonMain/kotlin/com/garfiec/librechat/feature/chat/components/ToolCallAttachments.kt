package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.files.CODE_LANGUAGES
import com.garfiec.librechat.core.model.files.FileKind
import com.garfiec.librechat.core.model.files.isExpired
import com.garfiec.librechat.core.model.files.sandboxDisplayName
import com.garfiec.librechat.core.model.media.resolveAttachmentUrl
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactFileSource
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactType
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.attachment_fallback
import com.garfiec.librechat.feature.chat.resources.cd_open_pdf
import com.garfiec.librechat.feature.chat.resources.files_count_summary
import com.garfiec.librechat.feature.chat.resources.pdf_document
import com.garfiec.librechat.feature.chat.resources.plus_n_more
import com.garfiec.librechat.feature.chat.resources.state_collapsed
import com.garfiec.librechat.feature.chat.resources.state_expanded
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// ─── ToolCallAttachments ────────────────────────────────────────────
//
// Renders the files a tool call generated (code-interpreter output, etc.) below its card,
// matching the web client which passes each tool call's attachments to every tool component.
// The data already arrives via SSE `attachment` events / persisted `message.attachments`; this
// is the render layer.

/**
 * The ways a generated file is surfaced, after filtering to one tool call and skipping the
 * non-file pseudo-attachments. Order within a partition is the attachments' arrival order.
 */
internal sealed interface ToolAttachment {
    val attachment: Attachment

    /** What the artifact row slot holds: a preview, or the placeholder for one. */
    sealed interface PanelRow : ToolAttachment

    /** Bitmap output (e.g. a matplotlib PNG) — inline preview, tap for fullscreen. */
    data class Image(override val attachment: Attachment) : ToolAttachment

    /** Text-bearing source/doc the server extracted (`.py`, `.md`, `.html`, `.mmd`, `.log`, …) —
     *  opens readably in the artifacts panel. */
    data class ArtifactContent(override val attachment: Attachment, val artifact: Artifact) : PanelRow

    /**
     * A file whose preview is still rendering ([failed] false) or never rendered ([failed] true).
     * Holds the slot its preview will take, so the row stays put when it resolves. Web routes
     * only `pending` here and lets a failed file fall to its file chips; keeping both in the slot
     * means a row that fails doesn't jump to another section either.
     */
    data class Pending(override val attachment: Attachment, val failed: Boolean) : PanelRow

    /** A PDF (with a downloadable fileId) — a card that opens the native full-screen preview. */
    data class Pdf(override val attachment: Attachment) : ToolAttachment

    /** Anything else (CSV, binary) — a download chip that shares the bytes on tap. */
    data class File(override val attachment: Attachment) : ToolAttachment
}

/** Upstream `imageExtRegex` (`packages/data-provider/src/file-config.ts`), verbatim. Excluding
 *  `svg`/`bmp`/`avif` is deliberate: an `.svg` the server extracted text for belongs in
 *  [ToolAttachment.ArtifactContent], and "completing" this list silently moves it out. */
private val IMAGE_EXTENSIONS = Regex("""\.(?:jpg|jpeg|png|gif|webp|heic|heif)$""", RegexOption.IGNORE_CASE)

/**
 * Splits a message's [attachments] to the given [toolCallId] into renderable buckets, skipping
 * pseudo-types, office documents that render as their own inline card ([isInlineOfficePreview]),
 * and sandbox placeholders. Deduped by `fileId ?: filename`. Pure — unit-tested.
 */
internal fun partitionToolCallAttachments(
    attachments: List<Attachment>,
    toolCallId: String?,
    nowMs: Long = currentTimeMs(),
): List<ToolAttachment> {
    if (toolCallId == null) return emptyList()
    val seen = HashSet<String>()
    return attachments.asSequence()
        .filter { it.toolCallId == toolCallId }
        .filter { !isPseudoAttachment(it) }
        .filter { !isInlineOfficePreview(it) }
        .filter { !isSandboxPlaceholder(it) }
        .filter { att ->
            val key = att.fileId ?: attachmentName(att) ?: return@filter false
            seen.add(key)
        }
        .map { classifyToolAttachment(it, nowMs) }
        .toList()
}

/**
 * How one generated file is surfaced. Shared by the buckets and [ToolFileActions.openToolFile].
 * An expired file is a [ToolAttachment.File] whatever it is, so nothing tries to load or preview
 * what the sandbox no longer has. A file still waiting on its preview is [ToolAttachment.Pending]
 * before anything that reads its text could misjudge it, as upstream's `AttachmentGroup` checks
 * `status` before the artifact type.
 */
internal fun classifyToolAttachment(attachment: Attachment, nowMs: Long = currentTimeMs()): ToolAttachment = when {
    attachment.isExpired(nowMs) -> ToolAttachment.File(attachment)
    isImageAttachment(attachment) -> ToolAttachment.Image(attachment)
    attachment.status == FilePreviewResponse.STATUS_PENDING -> ToolAttachment.Pending(attachment, failed = false)
    attachment.status == FilePreviewResponse.STATUS_FAILED -> ToolAttachment.Pending(attachment, failed = true)
    // A PDF needs its downloadable fileId to preview; without one it stays a file chip.
    isPdf(attachment) && attachment.fileId != null -> ToolAttachment.Pdf(attachment)
    else -> attachmentToArtifact(attachment)?.let { ToolAttachment.ArtifactContent(attachment, it) }
        ?: ToolAttachment.File(attachment)
}

/**
 * Every renderable attachment produced by a run of tool calls, flattened in call order — the
 * hoisted counterpart of [partitionToolCallAttachments]. Mirrors upstream's per-group
 * `groupAttachments` (`ContentParts.tsx`) fed into `AttachmentGroup` (`Attachment.tsx`).
 *
 * Cross-call dedupe is by `fileId` **only**: two calls may each legitimately emit a `chart.png`
 * carrying no fileId, and collapsing those on filename drops real output. The per-call filename
 * dedupe inside [partitionToolCallAttachments] still applies within one call. Pure — unit-tested.
 */
internal fun collectGroupAttachments(
    attachments: List<Attachment>,
    toolCallIds: List<String>,
    nowMs: Long = currentTimeMs(),
): List<ToolAttachmentBucket> =
    bucketToolAttachments(
        toolCallIds
            .flatMap { partitionToolCallAttachments(attachments, it, nowMs) }
            .distinctBy { item ->
                item.attachment.fileId
                    ?: "${item.attachment.toolCallId}:${attachmentName(item.attachment)}"
            },
    )

/** One run of same-kind attachments, emitted in final render order; empty runs are omitted. */
internal sealed interface ToolAttachmentBucket {
    val items: List<ToolAttachment>

    data class Files(override val items: List<ToolAttachment.File>) : ToolAttachmentBucket

    /** Previews and pending placeholders together, so a row keeps its place as it resolves. */
    data class Artifacts(override val items: List<ToolAttachment.PanelRow>) : ToolAttachmentBucket
    data class Pdfs(override val items: List<ToolAttachment.Pdf>) : ToolAttachmentBucket
    data class Images(override val items: List<ToolAttachment.Image>) : ToolAttachmentBucket
}

/**
 * Buckets [items] into upstream `AttachmentGroup`'s render order: files → artifacts → previews →
 * **images last** (`Attachment.tsx`), each bucket stable-sorted by [attachmentSalience].
 *
 * The buckets do not line up 1:1 with upstream's: [ToolAttachment.ArtifactContent] covers its
 * separate mermaid and inline-text buckets (text opens in the panel), and [ToolAttachment.Pdf]
 * (no upstream counterpart) sits with the artifacts. Pure — unit-tested.
 */
internal fun bucketToolAttachments(items: List<ToolAttachment>): List<ToolAttachmentBucket> {
    fun <T : ToolAttachment> List<T>.bySalience(): List<T> = sortedBy { attachmentSalience(it.attachment) }
    return listOfNotNull(
        items.filterIsInstance<ToolAttachment.File>().bySalience()
            .takeIf { it.isNotEmpty() }?.let(ToolAttachmentBucket::Files),
        // Salience reads only `bytes`, which resolving a preview doesn't change: same slot.
        items.filterIsInstance<ToolAttachment.PanelRow>().bySalience()
            .takeIf { it.isNotEmpty() }?.let(ToolAttachmentBucket::Artifacts),
        items.filterIsInstance<ToolAttachment.Pdf>().bySalience()
            .takeIf { it.isNotEmpty() }?.let(ToolAttachmentBucket::Pdfs),
        items.filterIsInstance<ToolAttachment.Image>().bySalience()
            .takeIf { it.isNotEmpty() }?.let(ToolAttachmentBucket::Images),
    )
}

/** Upstream `attachmentSalience` (`attachmentTypes.ts`): a zero-byte file is an empty placeholder
 *  and sinks below real output within its bucket. An absent `bytes` is unreported, not zero, and
 *  must not sink with it. */
private fun attachmentSalience(attachment: Attachment): Int = if (attachment.bytes == 0L) 1 else 0

/**
 * Mirrors upstream `isImageAttachment` (`attachmentTypes.ts`) on the MIME and filename halves, and
 * deliberately drops its `width`/`height`/`filepath` requirements: upstream needs the dimensions
 * to reserve DOM layout space, and requiring them here would *hide* every image whose `attachment`
 * event omitted them. `resolveAttachmentUrl` falls back to `$baseUrl/api/files/$fileId`.
 */
private fun isImageAttachment(attachment: Attachment): Boolean =
    attachment.type?.startsWith("image/") == true ||
        attachmentName(attachment)?.let { IMAGE_EXTENSIONS.containsMatchIn(it) } == true

private fun isPdf(attachment: Attachment): Boolean =
    attachment.type == "application/pdf" ||
        attachmentName(attachment)?.endsWith(".pdf", ignoreCase = true) == true

/** How far into the bytes to scan for the PDF header; some servers prepend a BOM or whitespace. */
private const val PDF_HEADER_SCAN_LIMIT = 1024

/** The PDF magic number, `%PDF-`. */
private val PDF_MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)

/**
 * True if [bytes] contains the PDF header (`%PDF-`) within the first [PDF_HEADER_SCAN_LIMIT] bytes.
 * Guards the native parsers against non-PDF bodies that arrive with a 200 status (e.g. an HTML error
 * page), which would otherwise blank the Android surface or crash iOS's failable initializer.
 * Pure — unit-tested.
 */
internal fun looksLikePdf(bytes: ByteArray): Boolean {
    if (bytes.size < PDF_MAGIC.size) return false
    val lastStart = minOf(bytes.size, PDF_HEADER_SCAN_LIMIT) - PDF_MAGIC.size
    for (start in 0..lastStart) {
        var matched = true
        for (j in PDF_MAGIC.indices) {
            if (bytes[start + j] != PDF_MAGIC[j]) {
                matched = false
                break
            }
        }
        if (matched) return true
    }
    return false
}

internal fun attachmentName(attachment: Attachment): String? =
    attachment.filename ?: attachment.filepath?.substringAfterLast('/')

/**
 * Cleans a sandbox-sanitized filename for display: `_.config-abcdef.txt` → `.config.txt`,
 * `_.dirkeep-88b30b` → `.dirkeep`. Ordinary names (incl. `report-deadbe.csv`) pass through.
 * Upstream `getDisplayFileName`; see [sandboxDisplayName].
 */
internal fun displayFilename(name: String): String = sandboxDisplayName(name)

/**
 * Mobile equivalent of upstream `fileToArtifact`: maps a text-bearing attachment to an [Artifact]
 * the existing `ArtifactPanel` can render, or `null` to fall back to a download chip. Content is the
 * server-extracted `attachment.text`; a blank/absent text can't be rendered, so it falls back. Code
 * files are wrapped in a fenced block so the markdown artifact renderer highlights them.
 *
 * Any other text opens as plain text — a `.log`, a `.csv`, a `Makefile` — where upstream shows
 * an inline box this client doesn't have. Not an office document's or a PDF's: their text is an
 * extraction for their own viewers, and an office one is HTML. Pure — unit-tested.
 */
internal fun attachmentToArtifact(attachment: Attachment): Artifact? {
    val text = attachment.text
    if (text.isNullOrBlank()) return null
    if (ArtifactType.isOfficePreviewMime(attachment.type) || isPdf(attachment)) return null
    val name = attachmentName(attachment) ?: return null
    val ext = name.substringAfterLast('.', "").lowercase()

    val type: String
    val content: String
    val language: String?
    when {
        ext == "html" || ext == "htm" -> {
            type = "text/html"; content = text; language = "html"
        }
        ext == "md" || ext == "mdx" || ext == "markdown" -> {
            type = "text/markdown"; content = text; language = "markdown"
        }
        ext == "mmd" || ext == "mermaid" -> {
            type = "application/vnd.mermaid"; content = text; language = "mermaid"
        }
        ext == "svg" -> {
            type = "image/svg+xml"; content = text; language = "svg"
        }
        ext in CODE_LANGUAGES -> {
            type = "text/markdown"; content = "```$ext\n$text\n```"; language = ext
        }
        else -> {
            type = "text/plain"; content = text; language = null
        }
    }
    return Artifact(
        identifier = attachment.fileId ?: name,
        type = type,
        title = displayFilename(name),
        language = language,
        content = content,
        source = ArtifactFileSource(
            fileId = attachment.fileId,
            filepath = attachment.filepath,
            filename = name,
            mime = attachment.type,
        ),
    )
}

@Composable
internal fun ToolCallAttachments(
    attachments: List<Attachment>,
    toolCallId: String?,
    baseUrl: String,
    modifier: Modifier = Modifier,
) {
    val buckets = remember(attachments, toolCallId) {
        bucketToolAttachments(partitionToolCallAttachments(attachments, toolCallId))
    }
    ToolAttachmentBuckets(buckets = buckets, baseUrl = baseUrl, modifier = modifier)
}

/**
 * Renders pre-bucketed attachments in the order [bucketToolAttachments] returned them. Shared by
 * the per-tool-call path and the hoisted-out-of-an-activity-group path.
 *
 * Emits no searchable text of its own — no section headers. `SearchMatchEnumeration` counts zero
 * occurrences for tool calls and images, and the renderer walks the same function, so a heading
 * here would desync the two walks and send prev/next to the wrong match.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolAttachmentBuckets(
    buckets: List<ToolAttachmentBucket>,
    baseUrl: String,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return

    val actions = rememberToolFileActions(baseUrl)
    val fallbackName = stringResource(Res.string.attachment_fallback)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        buckets.forEach { bucket ->
            when (bucket) {
                is ToolAttachmentBucket.Files -> FileChipGroup(items = bucket.items, actions = actions)

                is ToolAttachmentBucket.Artifacts -> bucket.items.forEach { item ->
                    // Keyed by file, so a pending row and its resolved self are one composition
                    // and the row can animate the change rather than being replaced.
                    key(item.attachment.fileId ?: attachmentName(item.attachment)) {
                        PendingPreviewRow(item = item, actions = actions)
                    }
                }

                is ToolAttachmentBucket.Pdfs -> bucket.items.forEach { item ->
                    if (item.attachment.fileId == null) return@forEach
                    PdfAttachmentCard(
                        title = attachmentName(item.attachment)?.let { displayFilename(it) } ?: fallbackName,
                        onClick = { actions.openToolFile(item.attachment) },
                    )
                }

                is ToolAttachmentBucket.Images -> bucket.items.forEach { item ->
                    val url = resolveAttachmentUrl(item.attachment, baseUrl)
                    if (url != null) {
                        MessageImagePreview(
                            imageUrl = url,
                            altText = item.attachment.filename?.let { displayFilename(it) } ?: fallbackName,
                        )
                    } else {
                        // Classification is by MIME *or* extension, so an entry can reach this
                        // bucket with nothing resolvable to load. Degrade to a chip rather than
                        // rendering nothing, which loses the file silently.
                        AttachmentDownloadChip(attachment = item.attachment, actions = actions)
                    }
                }
            }
        }
    }
}

/** Cached per core/ui Compose perf rule 13 (don't allocate `RoundedCornerShape` per composition). */
private val PDF_CARD_SHAPE = RoundedCornerShape(12.dp)

/**
 * A card for a generated PDF, styled like [ArtifactButton] (icon + title + "PDF Document" subtitle
 * + open affordance). Tapping opens the native full-screen preview via [LocalOpenPdf] — the
 * download-on-open + rendering happen there, mirroring how the web client opens PDFs in a viewer
 * rather than forcing a download.
 */
@Composable
private fun PdfAttachmentCard(title: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = PDF_CARD_SHAPE,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.pdf_document),
                    style = MaterialTheme.typography.bodySmall,
                    // Opaque theme role rather than copy(alpha=…) per core/ui Compose perf rule 15.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(Res.string.cd_open_pdf),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A file chip that opens the file on tap through [ToolFileActions.openToolFile], showing a
 * spinner while a download is in flight. An expired file is dimmed and a tap says why.
 */
@Composable
private fun AttachmentDownloadChip(attachment: Attachment, actions: ToolFileActions) {
    val isDownloading = actions.isDownloading(attachment)
    val expired = actions.isExpired(attachment)
    FileChip(
        filename = attachmentName(attachment)?.let { displayFilename(it) }
            ?: stringResource(Res.string.attachment_fallback),
        type = attachment.type,
        bytes = attachment.bytes,
        isLoading = isDownloading,
        expired = expired,
        onClick = if (expired || (actions.canDownload(attachment) && !isDownloading)) {
            { actions.openToolFile(attachment) }
        } else {
            null
        },
    )
}

/**
 * More downloadable chips than this collapse behind a summary row. A deliberate divergence: the
 * web groups at more than one, but a phone shows three chips without crowding the reply.
 */
internal const val FILE_GROUP_THRESHOLD = 3

/** How many names the collapsed summary row spells out before "+N more". */
private const val SUMMARY_NAMES = 2

/** Whether [items] collapse behind a summary row: only downloadable chips count. Pure. */
internal fun shouldGroupFiles(items: List<ToolAttachment.File>, nowMs: Long): Boolean =
    items.count { isDownloadable(it.attachment, nowMs) } > FILE_GROUP_THRESHOLD

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileChipGroup(items: List<ToolAttachment.File>, actions: ToolFileActions) {
    val grouped = remember(items) { shouldGroupFiles(items, currentTimeMs()) }
    // Saveable so a group the user opened stays open when the reply scrolls out and back.
    var expanded by rememberSaveable(items.firstOrNull()?.attachment?.let(::downloadKey)) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (grouped) {
            val fallbackName = stringResource(Res.string.attachment_fallback)
            FileGroupSummaryRow(
                names = items.map { item -> attachmentName(item.attachment)?.let(::displayFilename) ?: fallbackName },
                expanded = expanded,
                onToggle = { expanded = !expanded },
            )
        }
        if (!grouped || expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    AttachmentDownloadChip(attachment = item.attachment, actions = actions)
                }
            }
        }
    }
}

/** Cached per core/ui Compose perf rule 13. */
private val SUMMARY_ROW_SHAPE = RoundedCornerShape(12.dp)

/** "N files — a, b +N more", tapping to show or hide the chips beneath it. */
@Composable
internal fun FileGroupSummaryRow(names: List<String>, expanded: Boolean, onToggle: () -> Unit) {
    val count = pluralStringResource(Res.plurals.files_count_summary, names.size, names.size)
    val shown = names.take(SUMMARY_NAMES).joinToString(", ")
    val rest = names.size - SUMMARY_NAMES
    val more = if (rest > 0) " " + pluralStringResource(Res.plurals.plus_n_more, rest, rest) else ""
    val state = stringResource(if (expanded) Res.string.state_expanded else Res.string.state_collapsed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, SUMMARY_ROW_SHAPE)
            .clip(SUMMARY_ROW_SHAPE)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state }
            .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The archive kind's slate: a neutral fill for a group of mixed kinds.
        FileTypeTile(kind = FileKind.ARCHIVE) {
            Icon(
                imageVector = Icons.Outlined.FolderCopy,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = count,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = shown + more,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
