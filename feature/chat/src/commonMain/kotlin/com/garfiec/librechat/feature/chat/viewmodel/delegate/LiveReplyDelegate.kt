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
import com.garfiec.librechat.feature.chat.util.PendingUsage
import com.garfiec.librechat.feature.chat.util.PtcTrace
import com.garfiec.librechat.feature.chat.util.fold
import com.garfiec.librechat.feature.chat.util.interrupted
import com.garfiec.librechat.feature.chat.util.withoutLivePartial
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.garfiec.librechat.feature.chat.viewmodel.ContextUsageSource
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.RetryInfo
import com.garfiec.librechat.feature.chat.viewmodel.StreamingHandle
import com.garfiec.librechat.feature.chat.viewmodel.SubagentTrace

/**
 * Projects the stream events that only describe the reply in progress — tool calls, attachments,
 * usage, the title, retry progress, subagent traces — onto UI state. None of them starts, ends, or
 * resumes a session; that lifecycle stays in [StreamingManagerDelegate], which routes these here.
 */
class LiveReplyDelegate(
    private val handle: StreamingHandle,
    private val subagentTraceDelegate: SubagentTraceDelegate,
    private val attachmentPreviewDelegate: AttachmentPreviewDelegate,
    private val endpointTokenRepository: EndpointTokenRepository,
) {

    /**
     * A new turn: the previous one's traces and usage must not carry into it. Its preview polls
     * do: they resolve files of a reply that is already on screen, and the new turn's stream will
     * never report on them.
     */
    fun onTurnStarted() {
        subagentTraceDelegate.reset()
        if (handle.state.ptcTraces.isNotEmpty()) {
            handle.update { content = content.copy(ptcTraces = emptyMap()) }
        }
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
     * The stream has ended: any attachment still `pending` (its `ready` SSE update may never
     * arrive once the run closes) now falls back to polling GET /api/files/:id/preview. Reads the
     * streamed list, so it runs before the finalize retires it; the results land in
     * `previewOverrides`, which the finalize leaves alone. [finalAttachments] are the Final's own,
     * which can name a file the stream never did (a resume whose sync frame predates it).
     */
    fun onStreamEnded(finalAttachments: List<Attachment> = emptyList()) =
        attachmentPreviewDelegate.onStreamEnded(finalAttachments)

    /** Applies one of the events [StreamingManagerDelegate] routes here; anything else is ignored. */
    fun apply(event: StreamEvent) {
        when (event) {
            is StreamEvent.Retrying -> handle.update {
                content = content.copy(retryInfo = RetryInfo(attempt = event.attempt, maxAttempts = event.maxAttempts))
            }
            is StreamEvent.ToolCallStart -> {
                val newToolCall = ActiveToolCall(
                    id = event.toolCallId,
                    name = event.toolName,
                    input = event.input,
                    preparationStarted = event.preparing,
                )
                handle.update { content = content.copy(activeToolCalls = content.activeToolCalls + newToolCall) }
            }
            is StreamEvent.ToolCallComplete -> {
                handle.update {
                    content = content.updateToolCall(event.toolCallId) { it.completedBy(event) }
                        .withoutSandboxStarting(event.toolCallId)
                        .withoutSubagentAuthFor(event.toolCallId)
                }
                // If this was a `subagent` tool_call, freeze its live trace —
                // the child run is done; stop accumulating for that key.
                subagentTraceDelegate.onParentToolCallResolved(event.toolCallId)
            }
            is StreamEvent.ToolCallClosed -> {
                handle.update {
                    content = content.updateToolCall(event.toolCallId) { it.closedBy(event) }
                        .withoutSandboxStarting(event.toolCallId)
                        .withoutSubagentAuthFor(event.toolCallId)
                }
                // An aborted run closes its steps without ever completing them, so this is also
                // where a subagent trace stops accumulating on that path.
                subagentTraceDelegate.onParentToolCallResolved(event.toolCallId)
            }
            is StreamEvent.ToolCallAuthRequired -> onAuthRequired(event, parentToolCallId = null)
            is StreamEvent.ToolCallPreparation -> handle.update {
                content = content.copy(activeToolCalls = content.activeToolCalls.preparedBy(event))
            }
            is StreamEvent.SandboxStarting -> handle.update {
                content = content.copy(sandboxStartingToolCallIds = content.sandboxStartingToolCallIds + event.toolCallId)
            }
            is StreamEvent.PtcToolCall -> onPtcToolCall(event)
            is StreamEvent.AttachmentCreated -> onAttachment(event.toAttachment())
            is StreamEvent.Sync -> onSync(event)
            is StreamEvent.SubagentUpdate -> onSubagentUpdate(event)
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
        // A file with a deferred preview (v0.8.6) arrives twice per file_id (pending →
        // ready/failed) — route through the delegate for upsert-by-file_id +
        // poll-while-pending. Ordinary attachments keep the simple append path.
        if (attachment.status != null) {
            attachmentPreviewDelegate.onAttachment(attachment)
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
                    inputValidationError = tc.inputValidationError == true,
                    executor = tc.executor,
                )
            }
        // A boot flag survives only on a call the snapshot still shows running: the snapshot
        // carries no boot state, and a call it reports complete (or no longer has) is past it.
        val running = syncedToolCalls.filterNot { it.isComplete }.mapTo(HashSet()) { it.id }
        handle.update {
            content = content.copy(
                retryInfo = null,
                resumedReplyId = event.responseMessageId,
                // The partial may already be on screen: the load can read it before the frame lands.
                displayMessages = content.displayMessages.withoutLivePartial(event.responseMessageId),
                activeToolCalls = syncedToolCalls,
                sandboxStartingToolCallIds = content.sandboxStartingToolCallIds.filterTo(HashSet()) { it in running },
                // Inner calls are not content parts, so the snapshot cannot rebuild them and a
                // settle lost in the gap is never replayed: what was running reads as interrupted.
                ptcTraces = content.ptcTraces.mapValues { (_, trace) -> trace.interrupted() },
            )
        }
    }

    private fun onPtcToolCall(event: StreamEvent.PtcToolCall) {
        val traces = handle.state.ptcTraces
        val current = traces[event.toolCallId] ?: PtcTrace.EMPTY
        val next = current.fold(event)
        if (next === current) return
        handle.update { content = content.copy(ptcTraces = content.ptcTraces + (event.toolCallId to next)) }
    }

    private fun onSubagentUpdate(event: StreamEvent.SubagentUpdate) {
        subagentTraceDelegate.onUpdate(event)
        when (val inner = event.inner) {
            is StreamEvent.ToolCallAuthRequired -> onAuthRequired(inner, event.parentToolCallId)
            is StreamEvent.ToolCallComplete -> clearSubagentAuthFor(inner.toolCallId)
            is StreamEvent.ToolCallClosed -> clearSubagentAuthFor(inner.toolCallId)
            else -> Unit
        }
    }

    /**
     * Puts an MCP sign-in on the call it holds. A subagent's tool runs with the parent's stream, so
     * its prompt arrives naming a call that lives in the subagent's trace, not in [ActiveToolCall]s:
     * then it goes on the `subagent` call whose trace holds it (else the envelope's parent), which
     * is the card the user sees; otherwise the run would wait on a prompt nobody is shown.
     */
    private fun onAuthRequired(event: StreamEvent.ToolCallAuthRequired, parentToolCallId: String?) {
        val state = handle.state
        val updated = state.activeToolCalls.withAuthRequired(event, state.subagentProgress, parentToolCallId)
        if (updated === state.activeToolCalls) return
        handle.update { content = content.copy(activeToolCalls = updated) }
    }

    private fun clearSubagentAuthFor(childToolCallId: String) {
        if (handle.state.activeToolCalls.none { it.authToolCallId == childToolCallId }) return
        handle.update { content = content.withoutSubagentAuthFor(childToolCallId) }
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
        if (handle.state.conversation.titleSetByUser) return
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
    attachedRun = null,
    streamingContent = text,
    streamingThinking = thinking,
    activeToolCalls = emptyList(),
    sandboxStartingToolCallIds = emptySet(),
    streamingAttachments = emptyList(),
)

private fun MessagesState.updateToolCall(id: String, transform: (ActiveToolCall) -> ActiveToolCall) =
    copy(activeToolCalls = activeToolCalls.map { if (it.id == id) transform(it) else it })

/** A subagent's child call finished, so the parent card stops offering its sign-in. */
private fun MessagesState.withoutSubagentAuthFor(childToolCallId: String): MessagesState {
    val updated = activeToolCalls.withoutSubagentAuthFor(childToolCallId)
    return if (updated === activeToolCalls) this else copy(activeToolCalls = updated)
}

/**
 * Puts an MCP sign-in on the call in this list that holds it: the call it names, else the
 * `subagent` call whose trace in [subagentProgress] holds that call (else [envelopeParentId], the
 * `subagent_update` envelope's parent), recording the child's id in `authToolCallId`. A subagent's
 * tool runs with the parent's stream, so its prompt names a call that is only in the trace. Returns
 * this same list when no call here holds it. Shared with the comparison panes, which hold the calls
 * in their own lists.
 */
internal fun List<ActiveToolCall>.withAuthRequired(
    event: StreamEvent.ToolCallAuthRequired,
    subagentProgress: Map<String, SubagentTrace>,
    envelopeParentId: String?,
): List<ActiveToolCall> {
    if (any { it.id == event.toolCallId }) {
        return map { if (it.id == event.toolCallId) it.authRequiredBy(event) else it }
    }
    val parentId = subagentProgress.entries.firstOrNull { (_, trace) ->
        trace.parts.any { it.toolCall?.id == event.toolCallId }
    }?.key ?: envelopeParentId ?: return this
    if (none { it.id == parentId }) return this
    return map { if (it.id == parentId) it.authRequiredBy(event).copy(authToolCallId = event.toolCallId) else it }
}

/**
 * Takes a subagent child's sign-in off its parent card once the child call ends. Returns this same
 * list when no card carries it. Shared with the comparison panes.
 */
internal fun List<ActiveToolCall>.withoutSubagentAuthFor(childToolCallId: String): List<ActiveToolCall> =
    if (none { it.authToolCallId == childToolCallId }) {
        this
    } else {
        map {
            if (it.authToolCallId == childToolCallId) {
                it.copy(authUrl = null, authServerName = null, authExpiresAt = null, authToolCallId = null)
            } else {
                it
            }
        }
    }

/** A completed or closed call is past its sandbox boot. Shared with the comparison panes. */
internal fun MessagesState.withoutSandboxStarting(toolCallId: String): MessagesState =
    if (toolCallId in sandboxStartingToolCallIds) {
        copy(sandboxStartingToolCallIds = sandboxStartingToolCallIds - toolCallId)
    } else {
        this
    }

/** Shared with the comparison panes, which keep their own copies of the same list. */
internal fun ActiveToolCall.completedBy(event: StreamEvent.ToolCallComplete) =
    copy(
        isComplete = true,
        output = event.output,
        input = event.input ?: input,
        inputValidationError = event.inputValidationError,
        executor = event.executor ?: executor,
    )

/** A preparation marker or a dispatch, applied to the calls it names. Shared with the comparison panes. */
internal fun List<ActiveToolCall>.preparedBy(event: StreamEvent.ToolCallPreparation): List<ActiveToolCall> {
    if (none { it.id in event.toolCallIds }) return this
    return map { call ->
        when {
            call.id !in event.toolCallIds -> call
            event.dispatched -> call.copy(dispatched = true)
            else -> call.copy(preparationStarted = true)
        }
    }
}

/** The run is waiting on the user to sign in to an MCP server. Shared with the comparison panes. */
internal fun ActiveToolCall.authRequiredBy(event: StreamEvent.ToolCallAuthRequired) =
    copy(authUrl = event.authUrl, authServerName = event.serverName, authExpiresAt = event.expiresAt, authToolCallId = null)

internal fun ActiveToolCall.closedBy(event: StreamEvent.ToolCallClosed) =
    copy(isComplete = true, closedStatus = event.status, durationMs = event.durationMs ?: durationMs)

private fun StreamEvent.AttachmentCreated.toAttachment() = Attachment(
    fileId = fileId,
    filename = filename,
    filepath = filepath,
    type = type,
    conversationId = conversationId,
    messageId = messageId,
    toolCallId = toolCallId,
    expiresAt = expiresAt,
    agentId = agentId,
    stepId = stepId,
    workspaceChange = workspaceChange,
    bytes = bytes,
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
