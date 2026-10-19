package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.effectivePreviewStatus
import com.garfiec.librechat.feature.chat.util.previewKey
import com.garfiec.librechat.feature.chat.viewmodel.AttachmentPreviewHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Resolves deferred previews (v0.8.6) for files a tool generated: a file whose extracted text was
 * still rendering when its `attachment` event went out arrives with `status: "pending"`, and later
 * either as a second event for the same `file_id` (`ready` + text, or `failed` + previewError) or,
 * once the stream has closed, only through `GET /api/files/:id/preview`. Upstream's
 * `useAttachmentPreviewSync`, for every file type rather than office documents only.
 *
 * What the client learns is kept in
 * [MessagesState.previewOverrides][com.garfiec.librechat.feature.chat.viewmodel.MessagesState.previewOverrides],
 * keyed by message and `file_id` ([previewKey]), and applied at render time (`withPreview`). Never
 * written into a message or Room: a poll lands whenever it lands, including mid-stream of a later turn, where a Room write
 * or a tree mutation would un-truncate the streaming anchor. Being apart from the message is also
 * what lets a result outlive the finalize that retires the streamed copy, and apply to a reloaded
 * message whose persisted record still says `pending`.
 *
 *  - [onAttachment]: the live event. Upserts the stream's copy by `file_id`, never downgrading a
 *    terminal record to a late `pending`; a terminal event is also recorded as an override,
 *    because the Final's own attachments are the immediate-persist snapshot and still say
 *    `pending`.
 *  - [onStreamEnded]: polls each attachment of the finished reply still pending.
 *  - [onMessagesSettled]: polls each pending attachment of the displayed messages, once the
 *    conversation's fetch has settled, for a reply that finished while nobody was watching.
 *
 * Polls are de-duplicated per key and cancelled only by [reset] (the conversation changed)
 * and [cancelPolls] (the ViewModel is cleared). A new turn does not cancel them: its own stream
 * says nothing about the previous turn's files.
 */
class AttachmentPreviewDelegate(
    private val handle: AttachmentPreviewHandle,
    private val fileRepository: FileRepository,
) {

    /** In-flight preview polls keyed by [previewKey] (de-dup + cancellation). */
    private val activePolls = mutableMapOf<String, Job>()

    fun onAttachment(attachment: Attachment) {
        val fileId = attachment.fileId
        val key = attachment.previewKey()
        if (fileId == null || key == null) {
            handle.update { content = content.copy(streamingAttachments = content.streamingAttachments + attachment) }
            return
        }
        val streamedTerminal = handle.state.streamingAttachments
            .any { it.fileId == fileId && it.status.isTerminal() }
        handle.update {
            val overrides = when {
                attachment.status.isTerminal() -> content.previewOverrides + (key to attachment.toPreview(fileId))
                // A fresh `pending` for a file this reply hasn't resolved is the same file
                // re-rendered (a rewrite in a stateful session): its earlier result is stale. An
                // earlier turn's copy is filed under its own message and keeps its result.
                !streamedTerminal -> content.previewOverrides - key
                else -> content.previewOverrides
            }
            content = content.copy(
                streamingAttachments = upsertByFileId(content.streamingAttachments, attachment),
                previewOverrides = overrides,
            )
        }
        if (attachment.status.isTerminal() || !streamedTerminal) activePolls.remove(key)?.cancel()
    }

    /** The stream has ended: a `ready` event may now never arrive, so poll for it. */
    fun onStreamEnded(finalAttachments: List<Attachment> = emptyList()) =
        pollPending(handle.state.streamingAttachments + finalAttachments)

    /**
     * The conversation's fetch has settled on [displayMessages]. Re-runs on every later emission,
     * which is why the gate reads the status with the known results applied: Room keeps saying
     * `pending` for good, and gating on that would re-poll a resolved file on every emission.
     */
    fun onMessagesSettled(displayMessages: List<MessageNode>) =
        pollPending(displayMessages.flatMap { it.message.pendingAttachments() })

    /** The conversation changed: nothing learned about the previous one applies. */
    fun reset() {
        cancelPolls()
        handle.update { content = content.copy(previewOverrides = emptyMap()) }
    }

    /** Cancels all in-flight polls (ViewModel clear). */
    fun cancelPolls() {
        activePolls.values.forEach { it.cancel() }
        activePolls.clear()
    }

    private fun pollPending(attachments: List<Attachment>) {
        val overrides = handle.state.previewOverrides
        for (attachment in attachments) {
            val fileId = attachment.fileId ?: continue
            val key = previewKey(attachment.messageId, fileId)
            if (attachment.effectivePreviewStatus(overrides) != FilePreviewResponse.STATUS_PENDING) continue
            if (activePolls.containsKey(key)) continue
            activePolls[key] = handle.scope.launch {
                try {
                    val preview = when (val result = fileRepository.pollFilePreview(fileId)) {
                        is Result.Success -> result.data
                        is Result.Error -> {
                            Logger.w(result.exception) { "Preview poll gave up for $fileId" }
                            null
                        }
                        is Result.Loading -> null
                    }
                    record(key, fileId, preview)
                } finally {
                    activePolls.remove(key)
                }
            }
        }
    }

    /**
     * A poll that ended without a terminal answer — a run of failed requests, or a budget spent
     * while the server still said `pending` — is shown as failed: the row then offers the
     * download, which a spinner that never stops does not. It is a local verdict over a `pending`
     * record only, so a later fetch that carries the real result still wins (`withPreview`), and
     * reopening the conversation polls again.
     */
    private fun record(key: String, fileId: String, preview: FilePreviewResponse?) {
        val resolved = preview?.takeIf { it.isTerminal }
            ?: FilePreviewResponse(fileId = fileId, status = FilePreviewResponse.STATUS_FAILED)
        handle.update {
            // A terminal SSE update that landed while the poll was in flight already holds the slot.
            if (content.previewOverrides[key]?.isTerminal == true) return@update
            content = content.copy(previewOverrides = content.previewOverrides + (key to resolved))
        }
    }

    private fun String?.isTerminal(): Boolean = FilePreviewResponse.isTerminalStatus(this)

    private fun Attachment.toPreview(fileId: String) = FilePreviewResponse(
        fileId = fileId,
        status = status.orEmpty(),
        text = text,
        textFormat = textFormat,
        previewError = previewError,
    )

    private fun Message.pendingAttachments(): List<Attachment> =
        attachments.orEmpty().filter { it.status == FilePreviewResponse.STATUS_PENDING }

    /**
     * Merge-over upsert by `file_id`. Replaces an existing record with the same
     * `file_id`; appends when new. NO-REGRESS: if the existing record is terminal
     * and the incoming is `pending`, the existing terminal record is kept (a
     * stray late `pending` from cross-turn file_id reuse must not blank out a
     * resolved preview).
     */
    internal fun upsertByFileId(
        existing: List<Attachment>,
        incoming: Attachment,
    ): List<Attachment> {
        val fileId = incoming.fileId ?: return existing + incoming
        val index = existing.indexOfFirst { it.fileId == fileId }
        if (index < 0) return existing + incoming
        val current = existing[index]
        val resolved = if (current.status.isTerminal() && !incoming.status.isTerminal()) {
            current
        } else {
            incoming
        }
        return existing.toMutableList().also { it[index] = resolved }
    }
}
