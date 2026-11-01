package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.network.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Asks a failed run status check again: after a backoff (2s, doubling to 60s), for a server that
 * failed while the device stayed online, and at once when the device comes back online. Each
 * [schedule] arms one retry; [stop] cancels it and resets the backoff.
 */
internal class RunStatusRetry(
    connectivityObserver: ConnectivityObserver,
    private val scope: CoroutineScope,
    private val recheck: () -> Unit,
) {
    private val onReconnect = NetworkRecoveryTrigger(connectivityObserver, scope) {
        cancelBackoff()
        fire()
    }
    private var backoff: Job? = null
    private var delayMs = INITIAL_DELAY_MS

    fun schedule() {
        onReconnect.arm()
        cancelBackoff()
        val wait = delayMs
        delayMs = (wait * 2).coerceAtMost(MAX_DELAY_MS)
        backoff = scope.launch {
            delay(wait)
            backoff = null
            fire()
        }
    }

    fun stop() {
        onReconnect.disarm()
        cancelBackoff()
        delayMs = INITIAL_DELAY_MS
    }

    private fun fire() {
        onReconnect.disarm()
        recheck()
    }

    private fun cancelBackoff() {
        backoff?.cancel()
        backoff = null
    }

    private companion object {
        const val INITIAL_DELAY_MS = 2_000L
        const val MAX_DELAY_MS = 60_000L
    }
}
