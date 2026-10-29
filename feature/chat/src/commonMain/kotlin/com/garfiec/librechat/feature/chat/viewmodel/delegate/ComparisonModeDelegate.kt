package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.request.AddedConversation
import com.garfiec.librechat.feature.chat.util.isEphemeralAgentId
import com.garfiec.librechat.feature.chat.util.parseEphemeralAgentId
import com.garfiec.librechat.feature.chat.util.primaryAgentId
import com.garfiec.librechat.feature.chat.util.secondaryAgentId
import com.garfiec.librechat.feature.chat.util.stripAgentIdSuffix
import com.garfiec.librechat.feature.chat.util.toSeed
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatScreenState
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonHandle
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonState
import com.garfiec.librechat.feature.chat.viewmodel.laneBSpec
import com.garfiec.librechat.feature.chat.viewmodel.resolveEndpointDispatch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns model-comparison mode end to end: selecting the secondary model, building its
 * [AddedConversation] request, and routing the dual SSE streams into the primary/secondary
 * panes of [ComparisonState]. Keeps the comparison's 3-way streaming fan-out out of
 * `ChatViewModel.handleStreamEvent` — callers funnel streaming events through [routeEvent],
 * which returns `true` once it has consumed an event for comparison mode.
 *
 * The two [StringBuilder] buffers accumulate each pane's text independently because the
 * server interleaves both agents' deltas on one stream (distinguished via [isSecondaryEvent]).
 */
class ComparisonModeDelegate(
    private val handle: ComparisonHandle,
    private val messageRepository: MessageRepository,
    /** Reloads the conversation from the server after branching (VM-owned Room observer). */
    private val reloadConversation: (String) -> Unit,
    /**
     * The server's spec config as last fetched, read directly: a reopen can land before the
     * selection's copy is filled in.
     */
    private val currentModelSpecs: () -> ModelSpecs?,
) {

    private val primaryBuffer = StringBuilder()
    private val secondaryBuffer = StringBuilder()

    /** Accumulated primary-pane text — used as the auto-read source on Final. */
    fun primaryContent(): String = primaryBuffer.toString()

    // ── Selection ────────────────────────────────────────────────────

    /**
     * Turning Compare on starts a setup: the user picks the second model before anything is
     * enabled (a sheet on phones, the empty second pane on wide screens). Nothing is sent and the
     * screen does not leave LANDING until [startComparison].
     */
    fun beginComparisonSetup() {
        if (handle.state.comparisonState.isEnabled) return
        handle.update { comparisonState = ComparisonState(isSettingUp = true) }
    }

    /** Ends a setup without enabling comparison. */
    fun cancelComparisonSetup() {
        if (!handle.state.comparisonState.isSettingUp) return
        handle.update { comparisonState = ComparisonState() }
    }

    /**
     * Enables comparison against [endpoint]/[model] ([spec] when it is a spec), ending any setup.
     * Refused while a reply streams: [routeEvent] reads [ComparisonState.isEnabled] per event, so
     * the rest of a single-model reply would land in the comparison buffers and its streamed text
     * would vanish.
     */
    fun startComparison(endpoint: String, model: String, spec: String? = null) {
        if (handle.state.isStreaming) return
        handle.update {
            comparisonState = ComparisonState(
                isEnabled = true,
                secondaryEndpoint = endpoint,
                secondaryModel = model,
                secondarySpec = spec,
            )
            // When enabling on LANDING, switch to ACTIVE so comparison tabs render
            content = content.copy(
                screenState = if (content.screenState == ChatScreenState.LANDING) {
                    ChatScreenState.ACTIVE
                } else {
                    content.screenState
                },
            )
        }
    }

    /**
     * Turns comparison mode off. Refused while a reply streams, for [startComparison]'s reason in
     * reverse: the rest of both agents' deltas would fall through [routeEvent] into the single
     * stream and finalize as one interleaved reply.
     *
     * A new chat with nothing sent goes back to LANDING, undoing [startComparison]'s switch: ACTIVE
     * with no messages is a blank list. An opened conversation stays ACTIVE even when empty (its
     * load failed, or it has no messages): LANDING would greet it as a new chat and hide the retry.
     */
    fun stopComparison() {
        if (handle.state.isStreaming) return
        primaryBuffer.clear()
        secondaryBuffer.clear()
        handle.update {
            comparisonState = ComparisonState()
            if (conversation.conversationId == null &&
                content.screenState == ChatScreenState.ACTIVE &&
                content.messages.isEmpty()
            ) {
                content = content.copy(screenState = ChatScreenState.LANDING)
            }
        }
    }

    /** Sets the secondary selection to a named spec, carrying its name with the comparison's sends. */
    fun setSecondarySpec(spec: ModelSpec) {
        val seed = spec.toSeed() ?: return
        val comparison = handle.state.comparisonState
        if (!comparison.isEnabled) return
        handle.update {
            comparisonState = comparison.copy(
                secondaryEndpoint = seed.endpoint,
                secondaryModel = seed.model,
                secondarySpec = seed.spec,
                reopenedSecondaryId = null,
            )
        }
    }

    /** Updates the secondary model selection for comparison mode. */
    fun setSecondaryModel(endpoint: String, model: String) {
        val comparison = handle.state.comparisonState
        if (!comparison.isEnabled) return
        handle.update {
            comparisonState = comparison.copy(
                secondaryEndpoint = endpoint,
                secondaryModel = model,
                secondarySpec = null,
                reopenedSecondaryId = null,
            )
        }
    }

    /**
     * Builds an [AddedConversation] for the secondary agent/model in comparison mode.
     * Returns null if comparison is not enabled or secondary selection is incomplete.
     *
     * Reads the per-endpoint user-provided-key state out of `ChatUiState.endpointKeyStates`
     * (populated by `EndpointKeyStatusDelegate`) instead of issuing a per-call
     * `getKeyExpiry` GET — keeps the chat-send hot path off the network.
     *
     * A spec lane carries its preset's sampling settings (see [AddedConversation]). Not its
     * prompt: the served config leaves a spec's `promptPrefix` out, and the server applies it to
     * the primary only.
     * It must send no `modelLabel`: the server would save that as the lane's sender in place of
     * the spec's label, and [rehydrateFromMessage] reads the spec back from it.
     */
    fun buildAddedConvo(parentMessageId: String? = null): AddedConversation? {
        val state = handle.state
        val comparison = state.comparisonState
        if (!comparison.isEnabled) return null
        val endpoint = comparison.secondaryEndpoint ?: return null
        val model = comparison.secondaryModel ?: return null
        val isAgent = endpoint == EndpointConstants.AGENTS
        val dispatch = resolveEndpointDispatch(
            endpointName = endpoint,
            endpointConfigs = state.endpointConfigs,
            endpointKeyStates = state.endpointKeyStates,
        )
        val specs = currentModelSpecs()
        val spec = comparison.laneBSpec(specs)
        val preset = spec?.let { name -> specs?.list?.firstOrNull { it.name == name }?.preset }
        return AddedConversation(
            conversationId = state.conversationId,
            parentMessageId = parentMessageId,
            endpoint = endpoint,
            endpointType = dispatch.endpointType,
            modelDisplayLabel = dispatch.modelDisplayLabel,
            key = dispatch.key,
            agentId = if (isAgent) model else null,
            model = if (isAgent) null else model,
            spec = spec,
            temperature = preset?.temperature,
            topP = preset?.topP,
            topK = preset?.topK,
            presencePenalty = preset?.presencePenalty,
            frequencyPenalty = preset?.frequencyPenalty,
            maxOutputTokens = preset?.maxOutputTokens,
            maxTokens = preset?.maxTokens,
        )
    }

    /**
     * Rebuilds comparison mode from a persisted parallel response [message] on reopen.
     * v0.8.7 stores a comparison as one message whose parts carry per-agent ids (the added
     * agent suffixed `____N`); nothing else records that a conversation was a comparison, so
     * we derive the secondary endpoint/model back out of the added agent's id — a real
     * `agent_…____1` maps to the AGENTS endpoint with the stripped id as the model, an
     * ephemeral `endpoint__model___sender____1` decodes to its endpoint/model. This restores
     * the dual-pane view, re-enables Keep this answer, and lets a follow-up send keep
     * comparing via [buildAddedConvo]. No-op if the message has no added-agent part.
     *
     * The server saves no spec for the added lane, so an ephemeral lane's spec is read back from
     * the id ([laneBSpec]). The id is kept too, so a reopen that lands before the spec config
     * still finds the spec once it has loaded.
     */
    fun rehydrateFromMessage(message: Message) {
        val secondaryId = secondaryAgentId(message) ?: return
        val (endpoint, model) = resolveSecondarySelection(secondaryId)
        val reopened = ComparisonState(
            isEnabled = true,
            secondaryEndpoint = endpoint,
            secondaryModel = model,
            reopenedSecondaryId = secondaryId.takeIf { isEphemeralAgentId(it) },
        )
        handle.update {
            comparisonState = reopened.copy(
                secondarySpec = reopened.laneBSpec(currentModelSpecs()),
                primaryAgentId = primaryAgentId(message),
                secondaryAgentId = secondaryId,
                parallelMessageId = message.messageId,
            )
            content = content.copy(
                screenState = if (content.screenState == ChatScreenState.LANDING) {
                    ChatScreenState.ACTIVE
                } else {
                    content.screenState
                },
            )
        }
    }

    /** Decodes the secondary agent's id into the (endpoint, model) selection [buildAddedConvo] expects. */
    private fun resolveSecondarySelection(secondaryAgentId: String): Pair<String, String> {
        val stripped = stripAgentIdSuffix(secondaryAgentId)
        if (!isEphemeralAgentId(secondaryAgentId)) {
            // Real agent id (agent_…): AGENTS endpoint, the agent id is the "model".
            return EndpointConstants.AGENTS to stripped
        }
        val parsed = parseEphemeralAgentId(secondaryAgentId)
        return if (parsed != null) parsed.endpoint to parsed.model else EndpointConstants.AGENTS to stripped
    }

    // ── Streaming lifecycle ──────────────────────────────────────────

    /**
     * Resets both panes' buffers and streaming state at the start of a comparison send.
     * No-ops when comparison is disabled, so callers can invoke it unconditionally.
     */
    fun onSendStart() {
        if (!handle.state.comparisonState.isEnabled) return
        primaryBuffer.clear()
        secondaryBuffer.clear()
        handle.update {
            comparisonState = comparisonState.copy(
                primaryIsStreaming = true,
                secondaryIsStreaming = true,
                primaryStreamingContent = "",
                secondaryStreamingContent = "",
                primaryActiveToolCalls = emptyList(),
                secondaryActiveToolCalls = emptyList(),
                primaryAgentId = null,
                secondaryAgentId = null,
                parallelMessageId = null,
                primaryFinalContent = null,
                secondaryFinalContent = null,
            )
        }
    }

    private fun routeToolCallClosed(event: StreamEvent.ToolCallClosed) {
        if (isSecondaryEvent(event.agentId)) {
            handle.update {
                val updated = comparisonState.secondaryActiveToolCalls.map { tc ->
                    if (tc.id == event.toolCallId) tc.closedBy(event) else tc
                }
                comparisonState = comparisonState.copy(secondaryActiveToolCalls = updated)
            }
        } else {
            handle.update {
                val updated = comparisonState.primaryActiveToolCalls.map { tc ->
                    if (tc.id == event.toolCallId) tc.closedBy(event) else tc
                }
                comparisonState = comparisonState.copy(primaryActiveToolCalls = updated)
            }
        }
    }

    /**
     * Routes a streaming event into the comparison panes. Returns `true` if the event
     * was consumed for comparison mode (so the caller skips its normal handling), `false`
     * otherwise — including when comparison is disabled, leaving the standard path intact.
     *
     * A tool-call event with no branch here falls through to the standard path, which writes the
     * non-comparison `activeToolCalls` the panes never read. That is why the closure needs one:
     * without it an aborted comparison run leaves both panes' cards spinning.
     */
    fun routeEvent(event: StreamEvent): Boolean {
        if (!handle.state.comparisonState.isEnabled) return false
        return when (event) {
            // Thinking and content deltas both feed the same pane buffer in comparison mode.
            is StreamEvent.ContentDelta -> { routeTextDelta(event.agentId, event.chunk); true }
            is StreamEvent.ThinkingDelta -> { routeTextDelta(event.agentId, event.chunk); true }
            is StreamEvent.ToolCallStart -> { routeToolCallStart(event); true }
            is StreamEvent.ToolCallComplete -> { routeToolCallComplete(event); clearSandboxStarting(event.toolCallId); true }
            is StreamEvent.ToolCallClosed -> { routeToolCallClosed(event); clearSandboxStarting(event.toolCallId); true }
            else -> false
        }
    }

    /**
     * `on_sandbox_starting` is not routed here: it carries no agent, and the flag set it feeds is
     * keyed by tool_call id, which is unique across both panes. But the completion that ends the
     * boot is consumed here, so this is where the flag comes off.
     */
    private fun clearSandboxStarting(toolCallId: String) {
        if (toolCallId !in handle.state.sandboxStartingToolCallIds) return
        handle.update { content = content.withoutSandboxStarting(toolCallId) }
    }

    private fun routeTextDelta(agentId: String?, chunk: String) {
        if (isSecondaryEvent(agentId)) {
            secondaryBuffer.append(chunk)
            handle.update {
                comparisonState = comparisonState.copy(
                    secondaryStreamingContent = secondaryBuffer.toString(),
                    secondaryIsStreaming = true,
                    secondaryAgentId = comparisonState.secondaryAgentId ?: agentId,
                )
            }
        } else {
            primaryBuffer.append(chunk)
            handle.update {
                comparisonState = comparisonState.copy(
                    primaryStreamingContent = primaryBuffer.toString(),
                    primaryIsStreaming = true,
                    primaryAgentId = comparisonState.primaryAgentId ?: agentId,
                )
                content = content.copy(streamingContent = primaryBuffer.toString())
            }
        }
    }

    private fun routeToolCallStart(event: StreamEvent.ToolCallStart) {
        val newToolCall = ActiveToolCall(id = event.toolCallId, name = event.toolName, input = event.input)
        if (isSecondaryEvent(event.agentId)) {
            handle.update {
                comparisonState = comparisonState.copy(
                    secondaryActiveToolCalls = comparisonState.secondaryActiveToolCalls + newToolCall,
                )
            }
        } else {
            handle.update {
                comparisonState = comparisonState.copy(
                    primaryActiveToolCalls = comparisonState.primaryActiveToolCalls + newToolCall,
                )
            }
        }
    }

    private fun routeToolCallComplete(event: StreamEvent.ToolCallComplete) {
        if (isSecondaryEvent(event.agentId)) {
            handle.update {
                val updated = comparisonState.secondaryActiveToolCalls.map { tc ->
                    if (tc.id == event.toolCallId) tc.completedBy(event) else tc
                }
                comparisonState = comparisonState.copy(secondaryActiveToolCalls = updated)
            }
        } else {
            handle.update {
                val updated = comparisonState.primaryActiveToolCalls.map { tc ->
                    if (tc.id == event.toolCallId) tc.completedBy(event) else tc
                }
                comparisonState = comparisonState.copy(primaryActiveToolCalls = updated)
            }
        }
    }

    /**
     * Clears both panes' streaming flags and active tool calls. No-ops when comparison
     * is disabled. Pass [clearContent] = true to also wipe the in-progress pane text
     * (stream stop / abort); the default preserves it so an errored stream's partial
     * panes stay readable.
     */
    fun endStreaming(clearContent: Boolean = false) {
        if (!handle.state.comparisonState.isEnabled) return
        handle.update {
            comparisonState = comparisonState.copy(
                primaryIsStreaming = false,
                secondaryIsStreaming = false,
                primaryActiveToolCalls = emptyList(),
                secondaryActiveToolCalls = emptyList(),
                primaryStreamingContent = if (clearContent) "" else comparisonState.primaryStreamingContent,
                secondaryStreamingContent = if (clearContent) "" else comparisonState.secondaryStreamingContent,
            )
        }
    }

    /**
     * Finalizes both panes on a successful Final: freezes each buffer as the pane's final
     * content and records the parallel response message id for branch-from-comparison.
     */
    fun onFinal(parallelMessageId: String?) {
        handle.update {
            comparisonState = comparisonState.copy(
                primaryIsStreaming = false,
                secondaryIsStreaming = false,
                primaryStreamingContent = "",
                secondaryStreamingContent = "",
                primaryActiveToolCalls = emptyList(),
                secondaryActiveToolCalls = emptyList(),
                parallelMessageId = parallelMessageId,
                primaryFinalContent = primaryBuffer.toString(),
                secondaryFinalContent = secondaryBuffer.toString(),
            )
        }
    }

    /**
     * Branches the conversation onto the chosen agent's response, disables comparison,
     * and reloads so the branched message surfaces. Triggers deferred navigation for new
     * chats that skipped it during comparison.
     */
    fun branchFromComparison(agentId: String) {
        branch(agentId) { armDeferredNavigation() }
    }

    /**
     * "Keep and continue" on a comparison reply that was cut short: keeps [agentId]'s answer
     * ([branchFromComparison]), then [continueReply]s it once the branched reply is the one shown.
     *
     * A comparison can't be continued as one: on v0.8.8 a Continue carrying both lanes makes the
     * server rewrite both from scratch, and one without the second lane saves the primary's
     * answer alone. A single-model Continue extends the reply, so the lane is kept first.
     *
     * A new chat's move to its own screen is held back until then: the move replaces this
     * ViewModel, which would drop the pending Continue. The Continue's `created` event moves it
     * instead, handing the live stream over as a new chat's first send does. When the reply never
     * shows, or the Continue never starts, the chat moves as a plain branch would.
     */
    fun keepAndContinue(agentId: String, continueReply: () -> Unit) {
        branch(agentId) { branchedId ->
            handle.scope.launch {
                val shown = withTimeoutOrNull(KEEP_SETTLE_TIMEOUT_MS) {
                    handle.stateFlow.first { state ->
                        !state.isStreaming && state.displayMessages.lastOrNull()?.message?.messageId == branchedId
                    }
                }
                if (shown == null) {
                    handle.setError(ChatNotice.CONTINUE_RESPONSE_FAILED.marker())
                    armDeferredNavigation()
                    return@launch
                }
                continueReply()
                val started = withTimeoutOrNull(KEEP_SETTLE_TIMEOUT_MS) { handle.stateFlow.first { it.isStreaming } }
                if (started == null) armDeferredNavigation()
            }
        }
    }

    /** Keeps [agentId]'s lane as the conversation's reply, then [onBranched] with the kept message's id. */
    private fun branch(agentId: String, onBranched: (String) -> Unit) {
        val messageId = handle.state.comparisonState.parallelMessageId ?: return
        val conversationId = handle.state.conversationId ?: return
        handle.scope.launch {
            val result = messageRepository.branchMessage(
                conversationId = conversationId,
                messageId = messageId,
                agentId = agentId,
            )
            if (result !is Result.Success) {
                Logger.e((result as? Result.Error)?.exception) { "Failed to branch comparison message" }
                handle.setError(ChatNotice.CONTINUE_RESPONSE_FAILED.marker())
                return@launch
            }
            handle.update { comparisonState = ComparisonState() }
            reloadConversation(conversationId)
            onBranched(result.data.messageId)
        }
    }

    /** Moves a new chat that stayed on its landing while comparing to its own screen. */
    private fun armDeferredNavigation() {
        val cid = handle.state.conversationId
        if (cid != null && handle.state.pendingNavigationConversationId == null) {
            handle.update { conversation = conversation.copy(pendingNavigationConversationId = cid) }
        }
    }

    /**
     * Determines whether a stream event belongs to the secondary (added) agent.
     *
     * The server gives both agents the same `groupId` (they share a parallel
     * execution group), so groupId alone cannot distinguish them. Instead, the
     * server suffixes added-agent IDs with `"____N"` (e.g. `openAI__gpt-5.2____1`).
     * The primary never has this suffix.
     */
    fun isSecondaryEvent(agentId: String?): Boolean {
        if (agentId == null) return false
        val comparison = handle.state.comparisonState
        // If we've already resolved the secondary agentId from earlier SSE events, use that
        if (comparison.secondaryAgentId != null) {
            return agentId == comparison.secondaryAgentId
        }
        // The "____N" suffix identifies the addedConvo (added/secondary) agent.
        return agentId.contains("____")
    }

    private companion object {
        /** How long Keep and continue waits for the branched reply to load before giving up. */
        const val KEEP_SETTLE_TIMEOUT_MS = 10_000L
    }
}
