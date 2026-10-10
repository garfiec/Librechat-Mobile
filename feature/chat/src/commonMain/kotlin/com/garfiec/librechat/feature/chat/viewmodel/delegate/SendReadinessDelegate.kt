package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.SendBlockReason
import com.garfiec.librechat.feature.chat.viewmodel.SendReadinessHandle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The pre-flight every send variant passes through: a live send, a queue drain, and the edit /
 * regenerate / continue / compact resubmits. A synchronous check fails fast on what the user can
 * act on (no model selected, agents denied with the role already loaded); otherwise a bounded wait
 * for [ChatUiState.isSendReady] closes the cold-start window where `startChat` would run before
 * the endpoint config has arrived. A refusal is a typed [SendBlockReason] handed to
 * [surfaceModelSheet]; this delegate writes no state of its own.
 */
class SendReadinessDelegate(
    private val handle: SendReadinessHandle,
    /** `ChatViewModel.surfaceModelSheet`: opens the selector with the reason the send was refused. */
    private val surfaceModelSheet: (SendBlockReason) -> Unit,
) {

    /**
     * Suspends until [ChatUiState.isSendReady] becomes true, up to [timeoutMs]; false on timeout.
     * Firing `chatRepository.startChat(...)` before the endpoint config has arrived produces a
     * mislabeled 403, which is what this wait prevents.
     */
    private suspend fun awaitSendReady(timeoutMs: Long = SEND_READY_TIMEOUT_MS): Boolean {
        if (handle.state.isSendReady) return true
        return withTimeoutOrNull(timeoutMs) {
            handle.stateFlow.map { it.isSendReady }.distinctUntilChanged().first { it }
        } != null
    }

    fun runWhenSendReady(action: () -> Unit) = runWhenSendReady(onRefused = {}, action = action)

    /**
     * Runs [action] once the gate passes. [onRefused] fires after the selector has been surfaced,
     * on either refusal: a synchronous pre-flight block or a readiness timeout.
     */
    fun runWhenSendReady(onRefused: () -> Unit, action: () -> Unit) {
        val current = handle.state
        preflightSendBlockReason(current)?.let { reason ->
            surfaceModelSheet(reason)
            onRefused()
            return
        }
        if (current.isSendReady) {
            action()
            return
        }
        handle.scope.launch {
            if (awaitSendReady()) {
                action()
            } else {
                surfaceModelSheet(sendReadinessTimeoutReason(handle.state))
                onRefused()
            }
        }
    }

    /**
     * Synchronous pre-flight. Returns a typed reason when sending is guaranteed
     * to fail regardless of outstanding async inits; null when we still need to wait
     * for the readiness signal. This keeps "no model selected" and "agents denied"
     * instantaneous instead of waiting out the readiness timeout.
     */
    private fun preflightSendBlockReason(state: ChatUiState): SendBlockReason? {
        if (state.selectedModel == null) {
            return if (state.selectedEndpoint == EndpointConstants.AGENTS) {
                SendBlockReason.SelectAgent
            } else {
                SendBlockReason.SelectModel
            }
        }
        if (state.selectedEndpoint == EndpointConstants.AGENTS && !state.agentsEnabled) {
            return SendBlockReason.AgentsUnavailable
        }
        return null
    }

    /**
     * Fallback for when readiness didn't resolve within the timeout. At this point the
     * async model list is most likely in its final shape, so we can confidently flag
     * stale selections that aren't in the available models.
     */
    private fun sendReadinessTimeoutReason(state: ChatUiState): SendBlockReason {
        if (state.selectedEndpoint == EndpointConstants.AGENTS) {
            return SendBlockReason.AgentNotAvailable
        }
        val modelsForEndpoint = state.availableModels[state.selectedEndpoint].orEmpty()
        val selectedModel = state.selectedModel
        return if (selectedModel != null && selectedModel !in modelsForEndpoint) {
            SendBlockReason.ModelNotAvailable
        } else {
            SendBlockReason.ModelLoadFailed
        }
    }

    private companion object {
        /** Timeout for the pre-send "is the endpoint/config ready" await. Snappier than the
         *  5 s role-load timeout because this only needs one of role OR availableModels to
         *  satisfy the check; usually both have landed by the time a human can tap send. */
        const val SEND_READY_TIMEOUT_MS = 3_000L
    }
}
