package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactType
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.RetryInfo
import com.garfiec.librechat.feature.chat.viewmodel.StreamingHandle

/**
 * Projects the stream events that only describe the reply in progress — tool calls, attachments,
 * usage, the title, retry progress, subagent traces — onto UI state. None of them starts, ends, or
 * resumes a session; that lifecycle stays in [StreamingManagerDelegate], which routes these here.
 */
class LiveReplyDelegate(
    private val handle: StreamingHandle,
    private val subagentTraceDelegate: SubagentTraceDelegate,
    private val officePreviewDelegate: OfficePreviewDelegate,
) {

    /** A new turn: the previous one's traces and preview polls must not carry into it. */
    fun onTurnStarted() {
        subagentTraceDelegate.reset()
        officePreviewDelegate.reset()
    }

    /**
     * The stream has ended: any office-doc attachment still `pending` (its `ready` SSE update may
     * never arrive once the run closes) now falls back to polling GET /api/files/:id/preview.
     * De-duped + bounded in the delegate.
     */
    fun onStreamEnded() = officePreviewDelegate.onStreamEnded()

    /** Applies one of the events [StreamingManagerDelegate] routes here; anything else is ignored. */
    fun apply(event: StreamEvent) {
        when (event) {
            is StreamEvent.Retrying -> handle.update {
                content = content.copy(retryInfo = RetryInfo(attempt = event.attempt, maxAttempts = event.maxAttempts))
            }
            is StreamEvent.ToolCallStart -> {
                val newToolCall = ActiveToolCall(id = event.toolCallId, name = event.toolName, input = event.input)
                handle.update { content = content.copy(activeToolCalls = content.activeToolCalls + newToolCall) }
            }
            is StreamEvent.ToolCallComplete -> {
                handle.update {
                    content = content.updateToolCall(event.toolCallId) { it.copy(isComplete = true, output = event.output) }
                }
                // If this was a `subagent` tool_call, freeze its live trace —
                // the child run is done; stop accumulating for that key.
                subagentTraceDelegate.onParentToolCallResolved(event.toolCallId)
            }
            is StreamEvent.ToolCallClosed -> {
                handle.update {
                    content = content.updateToolCall(event.toolCallId) {
                        it.copy(isComplete = true, closedStatus = event.status)
                    }
                }
                // An aborted run closes its steps without ever completing them, so this is also
                // where a subagent trace stops accumulating on that path.
                subagentTraceDelegate.onParentToolCallResolved(event.toolCallId)
            }
            is StreamEvent.AttachmentCreated -> onAttachment(event.toAttachment())
            is StreamEvent.Sync -> onSync(event)
            is StreamEvent.SubagentUpdate -> subagentTraceDelegate.onUpdate(event)
            is StreamEvent.TitleUpdate -> onTitleUpdate(event)
            is StreamEvent.ContextUsageUpdate -> {
                // Latest context-window snapshot drives the gauge. In-memory only.
                handle.update { content = content.copy(contextUsage = event.usage) }
            }
            is StreamEvent.TokenUsageUpdate -> {
                // Per-call provider usage; the gauge denominator comes from the context
                // snapshot, but the breakdown sheet shows Input/Output from this. In-memory only.
                //
                // A non-null `usageType` marks a non-primary bucket — a summary pass, an
                // isolated subagent run, a hidden sequential-agent call, or an activity-label
                // header. Those are separate model calls, so letting one through would overwrite
                // the turn's own figures: this handler is last-write-wins. Activity labels make
                // that acute, emitting one usage event per tool batch (default up to 20 per run)
                // from a cheap fast model, so the sheet would end up showing the label model's
                // counts rather than the turn's.
                if (event.usage.usageType == null) {
                    handle.update { content = content.copy(tokenUsage = event.usage) }
                }
            }
            else -> Unit
        }
    }

    private fun onAttachment(attachment: Attachment) {
        // Office-doc previews (v0.8.6) arrive twice per file_id (pending →
        // ready/failed) — route through the delegate for upsert-by-file_id +
        // poll-while-pending. Ordinary attachments keep the simple append path.
        if (ArtifactType.isOfficePreviewMime(attachment.type)) {
            officePreviewDelegate.onAttachment(attachment)
        } else if (attachment.webSearch != null && attachment.toolCallId != null) {
            // Web-search re-emits an accumulating superset per source processed —
            // upsert by toolCallId so we keep only the latest (fullest) one rather
            // than piling up near-duplicate copies for the stream's duration.
            handle.update {
                val kept = content.streamingAttachments.filterNot {
                    it.type == ToolConstants.WEB_SEARCH && it.toolCallId == attachment.toolCallId
                }
                content = content.copy(streamingAttachments = kept + attachment)
            }
        } else {
            handle.update {
                content = content.copy(streamingAttachments = content.streamingAttachments + attachment)
            }
        }
    }

    /**
     * Resume snapshot: `aggregatedContent` is the authoritative state of the response so far, so
     * the tool-call list is REPLACED from it, not appended to (the text buffers are replaced by the
     * caller). Any pendingEvents in the same frame arrive as their own StreamEvents after this and
     * fold on top via the normal handlers.
     *
     * Rebuilding active tool calls from the snapshot's tool_call parts lets an in-progress image
     * gen (or any tool call) started before we resumed still render its live card. The same
     * ActiveToolCall the live path produces, so the existing StreamingToolCallCard / ImageGenCard
     * render it identically. A part with a non-blank output is already complete.
     */
    private fun onSync(event: StreamEvent.Sync) {
        val syncedToolCalls = event.aggregatedContent
            .mapNotNull { part -> part.toolCall?.takeIf { !it.id.isNullOrBlank() } }
            .map { tc ->
                ActiveToolCall(
                    id = tc.id.orEmpty(),
                    name = tc.name.orEmpty(),
                    input = tc.args?.toString(),
                    isComplete = !tc.output.isNullOrBlank(),
                    output = tc.output,
                )
            }
        handle.update { content = content.copy(retryInfo = null, activeToolCalls = syncedToolCalls) }
    }

    /**
     * Eager mid-stream title reveal (v0.8.7 `titleTiming: immediate`). Updates the
     * in-memory title only — writing to Room mid-stream would re-emit the
     * loadConversation observer and clobber the in-place streaming view (see the
     * streaming-anchor invariant). The post-stream title refetch persists it.
     */
    private fun onTitleUpdate(event: StreamEvent.TitleUpdate) {
        val current = handle.state.conversationId
        if (current != null && current != event.conversationId) return
        handle.update { conversation = conversation.copy(conversationTitle = event.title) }
    }
}

/**
 * The live reply's fields, which always move together: [isStreaming] plus the streamed text,
 * reasoning, tool calls and attachments. An ending that keeps the partial on screen passes it as
 * [text] / [thinking]; everything else starts from empty.
 */
internal fun MessagesState.resetLiveReply(
    isStreaming: Boolean,
    text: String = "",
    thinking: String = "",
): MessagesState = copy(
    isStreaming = isStreaming,
    streamingContent = text,
    streamingThinking = thinking,
    activeToolCalls = emptyList(),
    streamingAttachments = emptyList(),
)

private fun MessagesState.updateToolCall(id: String, transform: (ActiveToolCall) -> ActiveToolCall) =
    copy(activeToolCalls = activeToolCalls.map { if (it.id == id) transform(it) else it })

private fun StreamEvent.AttachmentCreated.toAttachment() = Attachment(
    fileId = fileId,
    filename = filename,
    filepath = filepath,
    type = type,
    toolCallId = toolCallId,
    width = width,
    height = height,
    status = status,
    text = text,
    textFormat = textFormat,
    previewError = previewError,
    webSearch = webSearch,
    fileSearch = fileSearch,
    memory = memory,
    uiResources = uiResources,
)
