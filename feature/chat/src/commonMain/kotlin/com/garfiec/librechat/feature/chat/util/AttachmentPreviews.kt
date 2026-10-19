package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.response.FilePreviewResponse

/**
 * [this] with a preview result the client learned after the attachment was recorded (a poll of
 * `GET /api/files/:id/preview`, or an SSE update the Final's snapshot predates). Applied only over
 * a `pending` record: a terminal one is already the server's answer, so a result can fill a gap but
 * never replace it. Text falls back to the record's own, as upstream's write-back does.
 */
internal fun Attachment.withPreview(preview: FilePreviewResponse?): Attachment {
    if (preview == null || status != FilePreviewResponse.STATUS_PENDING) return this
    if (!preview.isTerminal) return this
    return copy(
        status = preview.status,
        text = preview.text ?: text,
        textFormat = preview.textFormat ?: textFormat,
        previewError = preview.previewError ?: previewError,
    )
}

/**
 * Every attachment with its preview result applied. Returns the same list instance when nothing
 * changes, so callers keyed on it (`remember(attachments)`) don't recompute for every message
 * whenever any one preview resolves.
 */
internal fun List<Attachment>.withPreviews(overrides: Map<String, FilePreviewResponse>): List<Attachment> {
    if (overrides.isEmpty() || none { it.status == FilePreviewResponse.STATUS_PENDING }) return this
    var changed = false
    val applied = map { attachment ->
        attachment.withPreview(attachment.previewKey()?.let(overrides::get)).also { if (it !== attachment) changed = true }
    }
    return if (changed) applied else this
}

/** The status an attachment renders with once its known preview result is applied. */
internal fun Attachment.effectivePreviewStatus(overrides: Map<String, FilePreviewResponse>): String? =
    withPreview(previewKey()?.let(overrides::get)).status

/**
 * What a preview result is filed under: the file in the reply that produced it. A stateful session
 * that rewrites a file keeps its `file_id`, so a later turn's result for that id must not land on
 * an earlier turn's copy. A record without a `messageId` (an older server) falls back to the id.
 */
internal fun Attachment.previewKey(): String? = fileId?.let { previewKey(messageId, it) }

internal fun previewKey(messageId: String?, fileId: String): String = messageId?.let { "$it:$fileId" } ?: fileId
