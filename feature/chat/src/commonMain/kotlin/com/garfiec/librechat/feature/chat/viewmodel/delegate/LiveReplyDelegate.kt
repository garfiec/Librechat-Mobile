package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.data.repository.contextOverheadKey
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.TokenUsage
import com.garfiec.librechat.core.model.usage.UsageAmount
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactType
import com.garfiec.librechat.feature.chat.util.PendingUsage
import com.garfiec.librechat.feature.chat.util.fold
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.garfiec.librechat.feature.chat.viewmodel.ContextUsageSource
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
    private val endpointTokenRepository: EndpointTokenRepository,
) {

    /** A new turn: the previous one's traces, preview polls and usage must not carry into it. */
    fun onTurnStarted() {
        subagentTraceDelegate.reset()
        officePreviewDelegate.reset()
        // Its own emission, after the atomic begin-stream reset: safe, because on v0.8.8 the
        // previous turn's pending usage is already represented by its reply's metadata.usage, so
        // dropping it changes no total (older servers lose that share; see ContextProjectionDelegate).
        // Skipped when there is nothing to drop.
        if (!handle.state.pendingUsage.isEmpty) {
            handle.update { content = content.copy(pendingUsage = PendingUsage.EMPTY) }
        }
    }

    /**
     * The final frame arrived for [responseId]: the run's usage now belongs to that reply, and is
     * counted until the reply carries its own `metadata.usage` (usually in this same frame). Its
     * subagent share moves to the session figure. Without a reply (an early abort, or a response
     * the server never saved) there is nothing to attribute it to, so it is dropped.
     *
     * Called before the reply is swapped into the branch, while the stream still counts as live,
     * so the swap and the hand-over land in the same emission.
     */
    fun onFinal(responseId: String?) {
        handle.update {
            val pending = content.pendingUsage
            content = if (responseId == null) {
                content.copy(pendingUsage = PendingUsage.EMPTY)
            } else {
                content.copy(
                    pendingUsage = pending.copy(subagent = UsageAmount.EMPTY, anchorResponseId = responseId),
                    sessionSubagentUsage = content.sessionSubagentUsage + pending.subagent,
                )
            }
        }
    }

    /** A run that ended without a final frame left no reply to attribute its usage to. */
    fun discardPendingUsage() {
        handle.update { content = content.copy(pendingUsage = PendingUsage.EMPTY) }
    }

    private fun foldUsage(events: List<TokenUsage>) {
        handle.update {
            content = content.copy(pendingUsage = events.fold(content.pendingUsage) { pending, event -> pending.fold(event) })
        }
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
            is StreamEvent.ContextUsageUpdate -> onContextUsage(event.usage)
            // Every billed call counts toward the breakdown's Totals, buckets included (summary
            // passes, subagent runs, activity labels), exactly once: a resume can replay calls
            // already seen live, and its backfill repeats them.
            is StreamEvent.TokenUsageUpdate -> foldUsage(listOf(event.usage))
            is StreamEvent.UsageBackfill -> foldUsage(event.usages)
            else -> Unit
        }
    }

    /**
     * A live context reading drives the gauge (in memory only), and its instruction + tool-schema
     * share is remembered for this config so a branch with no saved snapshot can count it in its
     * estimate. Keyed by the event's own agent, as upstream does: a comparison run emits a reading
     * per agent, and keying by the current selection would file the second agent's overhead
     * under the first.
     */
    private fun onContextUsage(usage: ContextUsage) {
        handle.update { content = content.copy(contextUsage = usage, contextUsageSource = ContextUsageSource.LIVE) }
        val state = handle.state
        val agentId = usage.agentId
            ?: state.selectedModel.takeIf { state.selectedEndpoint == EndpointConstants.AGENTS }
        endpointTokenRepository.recordContextOverhead(
            contextOverheadKey(state.selectedEndpoint, state.selectedModel, agentId),
            usage.effectiveInstructionTokens ?: usage.breakdown.instructionTokens,
        )
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
