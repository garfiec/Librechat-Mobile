package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.network.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Armed when a stream ends on a network error: calls [onRecovered] on every offline → online
 * transition until disarmed. Observing starts only when armed, so a healthy session never
 * collects connectivity at all.
 */
internal class NetworkRecoveryTrigger(
    private val connectivityObserver: ConnectivityObserver,
    private val scope: CoroutineScope,
    private val onRecovered: () -> Unit,
) {
    /** Whether the last stream failure was a network error, i.e. a recovery is owed. */
    var isArmed = false
        private set

    private var job: Job? = null

    fun arm() {
        isArmed = true
        job?.cancel()
        job = scope.launch {
            var wasConnected = true
            connectivityObserver.isConnected.collect { connected ->
                val recovered = !wasConnected && connected
                wasConnected = connected
                if (recovered) onRecovered()
            }
        }
    }

    fun disarm() {
        isArmed = false
        job?.cancel()
        job = null
    }
}
