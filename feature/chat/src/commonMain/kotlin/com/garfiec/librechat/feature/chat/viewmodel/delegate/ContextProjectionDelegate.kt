package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.getOrNull
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.data.repository.contextOverheadKey
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.request.ContextProjectionRequest
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.ContextUsageTotals
import com.garfiec.librechat.core.model.usage.UsageAmount
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.PendingUsage
import com.garfiec.librechat.feature.chat.util.deepestPersistedSnapshot
import com.garfiec.librechat.feature.chat.util.estimateContextUsage
import com.garfiec.librechat.feature.chat.util.estimateDetail
import com.garfiec.librechat.feature.chat.util.latestExchangeTokens
import com.garfiec.librechat.feature.chat.util.persistedContextUsage
import com.garfiec.librechat.feature.chat.util.usageOver
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ContextProjectionHandle
import com.garfiec.librechat.feature.chat.viewmodel.ContextUsageSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Owns the context-usage gauge outside a stream. While a reply streams, the live
 * `on_context_usage` reading ([LiveReplyDelegate]) owns it and this delegate writes nothing.
 * Otherwise it resolves the gauge from the displayed branch, in this order:
 *
 * 1. **The just-finished turn's saved snapshot.** When a stream ends with a live reading, the tail
 *    at that moment is the anchor. A snapshot saved on the anchor or below it is that same turn's
 *    reading, reconciled with the final call's output, so it replaces the live one.
 * 2. **The live reading**, kept while its anchor stays on the branch and nothing at or below it
 *    has a saved snapshot yet. A value this delegate wrote is never treated as live.
 * 3. **The deepest saved snapshot on the branch** (`metadata.contextUsage`, v0.8.8).
 * 4. **The projection endpoint**, on servers before v0.8.8-rc1. Later servers removed it and
 *    [EndpointTokenRepository.getContextProjection] answers null there without a request.
 * 5. **An estimate** from the branch's messages, stamped [ContextUsageSource.ESTIMATE].
 *
 * It re-runs on every change to the branch, including a network refresh that keeps the tail id
 * and only fills in `metadata`: the branch itself is part of the key, so a cached reading is
 * corrected in place once the server's copy lands. Room persists `metadata`, so the snapshot
 * also works offline; the estimate's window falls back to the token-config saved for the server.
 * An agent's real model resolves only through the network, so offline an agent's branch has no
 * estimate unless a `maxContextTokens` override supplies the window.
 *
 * Rule 1 diverges from upstream on purpose. Web keeps the finalized live snapshot over the saved
 * one, which works because web reconciles the live reading with the output streamed after it.
 * Mobile does not, so the saved copy is the more accurate figure, and the gauge steps once when
 * a reply completes.
 *
 * It also derives the breakdown's branch-wide figures ([ContextUsageTotals]): Totals and Cost from
 * each reply's `metadata.usage` plus the live run's pending usage, the compaction reclaim, and the
 * estimate rows. Those re-run while streaming too, so Totals grow live. Divergences from web:
 * the Cached / Cache write rows appear only once the reply is saved (web reconciles the live
 * reading with each usage event; mobile doesn't), and a reply that never got `metadata.usage`
 * (servers before v0.8.8) loses its live share at the next turn, where web keeps a sticky index.
 *
 * Read-only over chat state apart from the gauge: it never touches messages, branches or Room,
 * so it cannot disturb the streaming-anchor invariant.
 */
class ContextProjectionDelegate(
    private val handle: ContextProjectionHandle,
    private val agentRepository: AgentRepository,
    private val endpointTokenRepository: EndpointTokenRepository,
) {

    private var previousInputs: GaugeInputs? = null

    /**
     * The tail when the last stream ended with a live reading, or null. Captured synchronously on
     * every emission, ahead of the cancellable resolve, so a newer emission can't drop the edge.
     */
    private var liveAnchorId: String? = null

    /** The gauge's value when the current stream started; a reading this run delivered replaces it. */
    private var readingAtStreamStart: ContextUsage? = null

    /** The projection last requested, so an unchanged branch doesn't re-request it. */
    private var lastProjectionKey: ProjectionKey? = null

    /**
     * Resolved agent config per agentId, cached for the session so a refresh doesn't re-hit the
     * network each time. The list that fills [ChatUiState.agents] comes from `GET /api/agents`,
     * which strips `provider`/`model`/`model_parameters`, so the agent's real model is only
     * obtainable from the per-agent detail endpoint.
     */
    private val resolvedAgents = mutableMapOf<String, ResolvedAgent>()

    /** Starts the gauge and totals observers on [ContextProjectionHandle.scope]. */
    fun start() {
        handle.scope.launch {
            handle.stateFlow
                .map { GaugeInputs(it) }
                .distinctUntilChanged()
                .onEach(::trackStreamEnd)
                .collectLatest(::resolve)
        }
        // Separate from the resolve, which stands down while a reply streams: Totals has to grow
        // live. Keyed on the lists by reference and on the pending usage, so the 50ms text flush
        // passes through distinctUntilChanged without recomputing anything.
        handle.scope.launch {
            handle.stateFlow
                .map { TotalsInputs(it) }
                .distinctUntilChanged()
                .collect { inputs ->
                    val totals = deriveTotals(inputs)
                    if (handle.state.contextUsageTotals != totals) handle.update { contextUsageTotals = totals }
                }
        }
    }

    private fun deriveTotals(inputs: TotalsInputs): ContextUsageTotals {
        val branch = inputs.branch.map { it.message }
        val usage = inputs.usage
        val isEstimate = inputs.source == ContextUsageSource.ESTIMATE
        val reclaim = if (usage != null && !isEstimate) {
            val breakdown = usage.breakdown
            val completedOutput = usage.completedOutputTokens ?: usage.resumedOutputTokens ?: 0
            val kept = latestExchangeTokens(branch, inputs.isStreaming, breakdown.summaryTokens)
            (breakdown.messageTokens + completedOutput + (usage.retainedToolTokens ?: 0) - kept).coerceAtLeast(0)
        } else {
            0
        }
        return ContextUsageTotals(
            branch = usageOver(branch, inputs.messages, inputs.pending, inputs.isStreaming),
            total = usageOver(inputs.messages, inputs.messages, inputs.pending, inputs.isStreaming),
            subagent = inputs.sessionSubagent + inputs.pending.subagent,
            compactionReclaim = reclaim,
            // The estimate reading stores its overhead as the instruction share.
            estimate = if (usage != null && isEstimate) {
                estimateDetail(branch, usage.windowTokens, usage.breakdown.instructionTokens)
            } else {
                null
            },
        )
    }

    private fun trackStreamEnd(inputs: GaugeInputs) {
        val previous = previousInputs
        previousInputs = inputs
        if (previous != null && previous.conversationId != inputs.conversationId) {
            liveAnchorId = null
            lastProjectionKey = null
        }
        if (previous?.isStreaming != true && inputs.isStreaming) {
            readingAtStreamStart = handle.state.contextUsage
        }
        if (previous?.isStreaming == true && !inputs.isStreaming) {
            // Only a reading delivered during this run counts. The LIVE stamp alone can't tell:
            // the previous run's reading keeps it until something replaces it, so a run that
            // never reported one (an interrupted final call) would carry that stale value over.
            val state = handle.state
            val reportedThisRun = state.contextUsageSource == ContextUsageSource.LIVE &&
                state.contextUsage != null &&
                state.contextUsage !== readingAtStreamStart
            liveAnchorId = if (reportedThisRun) inputs.tailId else null
            readingAtStreamStart = null
        }
    }

    private suspend fun resolve(inputs: GaugeInputs) {
        if (!inputs.enabled || inputs.isStreaming) return
        val branch = inputs.branch.map { it.message }

        val anchor = liveAnchorId?.takeIf { id -> branch.any { it.messageId == id } }
        if (anchor == null) liveAnchorId = null

        if (anchor != null) {
            snapshotAtOrBelow(branch, anchor)?.let { return write(it, ContextUsageSource.SNAPSHOT) }
            val state = handle.state
            if (state.contextUsageSource == ContextUsageSource.LIVE && state.contextUsage != null) return
        }

        deepestPersistedSnapshot(branch)?.let { return write(it, ContextUsageSource.SNAPSHOT) }

        val state = handle.state
        val (lookupEndpoint, lookupModel) = resolveModel(state)
        val window = resolveWindow(state, lookupEndpoint, lookupModel)
        // Every lookup above can suspend, and a stream may have started meanwhile.
        if (handle.state.isStreaming) return

        when (val projection = project(inputs, state, lookupModel, window)) {
            ProjectionOutcome.Keep -> return
            is ProjectionOutcome.Value -> return write(projection.usage, ContextUsageSource.PROJECTION)
            ProjectionOutcome.None -> Unit
        }

        val estimate = estimateContextUsage(branch, window, endpointTokenRepository.contextOverhead(overheadKey(state)))
        if (estimate == null && window == null) {
            // A custom, proxy or self-hosted model missing from token-config has no denominator,
            // so there is no ratio to show. Logged because the gauge then silently stays hidden.
            Logger.d { "Context gauge skipped: no known context window for $lookupEndpoint/$lookupModel" }
        }
        write(estimate, estimate?.let { ContextUsageSource.ESTIMATE })
    }

    /** The deepest snapshot saved on [anchorId] or a message below it on the branch. */
    private fun snapshotAtOrBelow(branch: List<Message>, anchorId: String): ContextUsage? {
        val anchorIndex = branch.indexOfLast { it.messageId == anchorId }
        if (anchorIndex < 0) return null
        for (index in branch.lastIndex downTo anchorIndex) {
            branch[index].persistedContextUsage()?.let { return it }
        }
        return null
    }

    /**
     * Asks the projection endpoint (servers before v0.8.8-rc1) once per branch tail and config.
     * [ProjectionOutcome.Keep] holds the current projection when nothing it depends on changed,
     * or when a refresh failed over one; [ProjectionOutcome.None] lets the estimate take over.
     */
    private suspend fun project(
        inputs: GaugeInputs,
        state: ChatUiState,
        lookupModel: String?,
        window: Int?,
    ): ProjectionOutcome {
        val conversationId = inputs.conversationId ?: return ProjectionOutcome.None
        val tailId = inputs.tailId ?: return ProjectionOutcome.None
        if (window == null) return ProjectionOutcome.None
        val key = ProjectionKey(conversationId, tailId, inputs.endpoint, inputs.model, window)
        val holdingProjection = state.contextUsageSource == ContextUsageSource.PROJECTION
        if (key == lastProjectionKey && holdingProjection) return ProjectionOutcome.Keep
        lastProjectionKey = key

        // For agents, address the projection by agentId (the server resolves the agent's config)
        // and send the resolved real model; otherwise pass the selected model directly.
        val isAgent = state.selectedEndpoint == EndpointConstants.AGENTS
        val result = endpointTokenRepository.getContextProjection(
            ContextProjectionRequest(
                conversationId = conversationId,
                messageId = tailId,
                endpoint = state.selectedEndpoint,
                model = lookupModel,
                agentId = if (isAgent) state.selectedModel else null,
                maxContextTokens = window,
            ),
        )
        if (handle.state.isStreaming) return ProjectionOutcome.Keep
        (result as? Result.Success)?.let { success ->
            return success.data?.let(ProjectionOutcome::Value) ?: ProjectionOutcome.None
        }
        // A failed refresh leaves a projection already on the gauge rather than swapping it for an
        // estimate of the same branch.
        return if (result is Result.Error && holdingProjection) ProjectionOutcome.Keep else ProjectionOutcome.None
    }

    private fun write(usage: ContextUsage?, source: ContextUsageSource?) {
        val state = handle.state
        if (state.contextUsage == usage && state.contextUsageSource == source) return
        handle.update {
            contextUsage = usage
            contextUsageSource = source
        }
    }

    /** Key for the overhead the live reading recorded; built as [LiveReplyDelegate] builds it. */
    private fun overheadKey(state: ChatUiState): String = contextOverheadKey(
        state.selectedEndpoint,
        state.selectedModel,
        state.selectedModel.takeIf { state.selectedEndpoint == EndpointConstants.AGENTS },
    )

    /**
     * The gauge's denominator: an explicit per-conversation override, then the agent's own
     * `model_parameters.maxContextTokens`, then token-config (web's `useTokenLimits` order).
     */
    private suspend fun resolveWindow(state: ChatUiState, endpoint: String, model: String?): Int? {
        state.modelParameters.maxContextTokens?.takeIf { it > 0 }?.let { return it }
        agentFor(state)?.maxContextTokens?.let { return it }
        if (model == null) return null
        // token-config is memoized on the singleton repository, so this hits the network at most
        // once per session even though each chat gets its own ViewModel.
        val config = endpointTokenRepository.getTokenConfig().getOrNull() ?: return null
        return config[endpoint]?.get(model)?.context?.takeIf { it > 0 }
    }

    /**
     * The (endpoint, model) used to look up the window and address the projection. For the agents
     * endpoint [ChatUiState.selectedModel] is the agentId, not a token-config key, so substitute
     * the agent's real provider/model (web does the same via useTokenLimits).
     */
    private suspend fun resolveModel(state: ChatUiState): Pair<String, String?> {
        if (state.selectedEndpoint != EndpointConstants.AGENTS) {
            return state.selectedEndpoint to state.selectedModel
        }
        val agent = agentFor(state) ?: return state.selectedEndpoint to null
        return (agent.provider ?: state.selectedEndpoint) to agent.model
    }

    private suspend fun agentFor(state: ChatUiState): ResolvedAgent? {
        if (state.selectedEndpoint != EndpointConstants.AGENTS) return null
        val agentId = state.selectedModel ?: return null
        // Honor a fully-populated list entry if one ever has a model; otherwise use the cache.
        state.agents.firstOrNull { it.id == agentId && it.model != null }?.let {
            return ResolvedAgent(it.provider, it.model, it.modelParameters.maxContextTokens())
        }
        resolvedAgents[agentId]?.let { return it }
        // getAgent short-circuits to the stripped list cache, so fetch the detail explicitly.
        val detail = (agentRepository.getAgentForEditing(agentId) as? Result.Success)?.data ?: return null
        val resolved = ResolvedAgent(detail.provider, detail.model, detail.modelParameters.maxContextTokens())
        // Only cache a real resolution; a transient failure should be retryable next time.
        if (detail.model != null) resolvedAgents[agentId] = resolved
        return resolved
    }

    private fun JsonElement?.maxContextTokens(): Int? =
        ((this as? JsonObject)?.get("maxContextTokens") as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }

    private data class ResolvedAgent(val provider: String?, val model: String?, val maxContextTokens: Int?)

    /** Everything the resolve reads; a change to any of it re-runs the resolve. */
    private data class GaugeInputs(
        val conversationId: String?,
        val branch: List<MessageNode>,
        val isStreaming: Boolean,
        val endpoint: String,
        val model: String?,
        val enabled: Boolean,
        val maxContextOverride: Int?,
    ) {
        constructor(state: ChatUiState) : this(
            conversationId = state.conversationId,
            branch = state.displayMessages,
            isStreaming = state.isStreaming,
            endpoint = state.selectedEndpoint,
            model = state.selectedModel,
            enabled = state.contextUsageEnabled,
            maxContextOverride = state.modelParameters.maxContextTokens,
        )

        val tailId: String? get() = branch.lastOrNull()?.message?.messageId
    }

    /** Everything the totals read. */
    private data class TotalsInputs(
        val branch: List<MessageNode>,
        val messages: List<Message>,
        val pending: PendingUsage,
        val sessionSubagent: UsageAmount,
        val isStreaming: Boolean,
        val usage: ContextUsage?,
        val source: ContextUsageSource?,
    ) {
        constructor(state: ChatUiState) : this(
            branch = state.displayMessages,
            messages = state.messages,
            pending = state.content.pendingUsage,
            sessionSubagent = state.content.sessionSubagentUsage,
            isStreaming = state.isStreaming,
            usage = state.contextUsage,
            source = state.contextUsageSource,
        )
    }

    private data class ProjectionKey(
        val conversationId: String,
        val tailId: String,
        val endpoint: String,
        val model: String?,
        val window: Int,
    )

    private sealed interface ProjectionOutcome {
        data object Keep : ProjectionOutcome
        data object None : ProjectionOutcome
        data class Value(val usage: ContextUsage) : ProjectionOutcome
    }
}
