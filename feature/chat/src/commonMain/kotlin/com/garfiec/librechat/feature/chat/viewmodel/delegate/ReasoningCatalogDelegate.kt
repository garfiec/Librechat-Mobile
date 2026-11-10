package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ReasoningCapabilitiesRepository
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.ui.components.ReasoningSettingResolver
import com.garfiec.librechat.feature.chat.util.reasoningTarget
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.QueuedEditSession
import com.garfiec.librechat.feature.chat.viewmodel.ReasoningCatalogState
import com.garfiec.librechat.feature.chat.viewmodel.ReasoningHandle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Keeps the per-turn reasoning control's inputs current (v0.8.9-rc1):
 *
 * - **The catalog.** For an endpoint whose efforts come from OpenRouter's catalog
 *   ([ReasoningSettingResolver.usesCatalog]) it fetches `GET /api/endpoints/reasoning-capabilities`
 *   and refetches when the server's lifetime runs out, so a list is never held longer than the
 *   server holds it. While unknown (loading, failed, expired) the efforts are hidden. A failure is
 *   retried with backoff.
 * - **The staged choice.** A switch of endpoint, agent or model drops it (web's `Reasoning.tsx`), unless
 *   the same emission brings its own (a queued edit's). When the resolved setting changes, a pending
 *   choice it refuses is dropped. Not while the catalog is unknown: that says nothing about the
 *   choice, which is checked again once the catalog lands.
 */
class ReasoningCatalogDelegate(
    private val handle: ReasoningHandle,
    private val repository: ReasoningCapabilitiesRepository,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    fun start() {
        handle.scope.launch {
            handle.stateFlow
                // The version rides the key: the route exists from v0.8.9-rc1, and a version detected
                // after the chat opened must start the fetch the unknown one skipped.
                .map { catalogEndpoint(it) to it.gates.backendVersion }
                .distinctUntilChanged()
                .collectLatest { (endpoint, _) -> followCatalog(endpoint) }
        }
        handle.scope.launch {
            handle.stateFlow
                // Only what the setting is resolved from: the state also changes on every streamed token.
                .distinctUntilChangedBy { state ->
                    ReasoningInputs(
                        state.selection,
                        state.gates.backendVersion,
                        state.gates.parametersEnabled,
                        state.comparisonState.isEnabled,
                        state.composer.pendingReasoningOverride,
                    )
                }
                .map { state -> state.reasoningSetting to state.isCatalogPending() }
                .distinctUntilChanged()
                .collect { (_, pending) -> if (!pending) dropRefusedChoice() }
        }
        handle.scope.launch {
            var last: Triple<Pair<String, String?>, ReasoningOverride?, QueuedEditSession?>? = null
            handle.stateFlow
                .map { Triple(it.selectedEndpoint to it.selectedModel, it.composer.pendingReasoningOverride, it.editingQueuedItem) }
                .distinctUntilChanged()
                .collect { current ->
                    val previous = last
                    last = current
                    val (target, pending, session) = current
                    // A queued edit starting or ending swaps in a choice of its own, equal or not.
                    if (previous != null && previous.first != target && pending != null &&
                        pending == previous.second && session == previous.third
                    ) {
                        handle.update { if (pendingReasoningOverride == pending) pendingReasoningOverride = null }
                    }
                }
        }
    }

    private suspend fun followCatalog(endpoint: String?) {
        if (endpoint == null) {
            publish(null)
            return
        }
        // Unknown first: the previous endpoint's list must not narrow this one's efforts.
        publish(ReasoningCatalogState(endpoint, models = null))
        var retryMs = INITIAL_RETRY_MS
        while (true) {
            when (val result = repository.catalog(endpoint)) {
                null -> {
                    publish(null)
                    return
                }
                is Result.Success -> {
                    publish(ReasoningCatalogState(endpoint, result.data.models))
                    retryMs = INITIAL_RETRY_MS
                    delay((result.data.expiresAtMs - nowMs()).coerceAtLeast(MIN_REFRESH_MS))
                    // Past its lifetime the list is unknown until the refetch answers.
                    publish(ReasoningCatalogState(endpoint, models = null))
                }
                else -> {
                    publish(ReasoningCatalogState(endpoint, models = null))
                    delay(retryMs)
                    retryMs = (retryMs * 2).coerceAtMost(MAX_RETRY_MS)
                }
            }
        }
    }

    private fun publish(catalog: ReasoningCatalogState?) {
        if (handle.state.selection.reasoningCatalog == catalog) return
        handle.update { reasoningCatalog = catalog }
    }

    private fun dropRefusedChoice() {
        val pending = handle.state.composer.pendingReasoningOverride ?: return
        if (handle.state.acceptsReasoning(pending)) return
        handle.update { if (pendingReasoningOverride == pending) pendingReasoningOverride = null }
    }

    private companion object {
        const val INITIAL_RETRY_MS = 2_000L
        const val MAX_RETRY_MS = 60_000L
        const val MIN_REFRESH_MS = 1_000L
    }
}

/** The endpoint whose catalog the current selection needs, or null when it needs none. */
internal fun catalogEndpoint(state: ChatUiState): String? {
    val target = reasoningTarget(
        selectedEndpoint = state.selectedEndpoint,
        selectedModel = state.selectedModel,
        agentProvider = state.selection.selectedAgentProvider,
        agentModel = state.selection.selectedAgentModel,
    ) ?: return null
    val customParams = state.endpointConfigs[target.endpoint]?.customParams
    return target.endpoint.takeIf { ReasoningSettingResolver.usesCatalog(customParams) }
}

/** The selection needs a catalog that is not known yet. */
internal fun ChatUiState.isCatalogPending(): Boolean {
    val endpoint = catalogEndpoint(this) ?: return false
    val catalog = selection.reasoningCatalog
    return catalog?.endpoint != endpoint || catalog.models == null
}

/** What `ChatUiState.reasoningSetting` and the staged choice are read from. */
private data class ReasoningInputs(
    val selection: ModelSelectionState,
    val backendVersion: String?,
    val parametersEnabled: Boolean,
    val comparing: Boolean,
    val pending: ReasoningOverride?,
)
