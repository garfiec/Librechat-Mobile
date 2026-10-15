package com.garfiec.librechat.core.data.prefetch

import com.garfiec.librechat.core.common.lifecycle.DeferredWorkWindow
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.network.NetworkConditionObserver
import com.garfiec.librechat.core.common.network.RequestActivityTracker
import com.garfiec.librechat.core.common.power.PowerStateObserver
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Every condition the gate weighs, named individually so the settings readout can say *which* one is
 * holding prefetching back rather than only that something is.
 *
 * [isOpen] is the gate's actual verdict, so the checklist a user reads and the boolean the engine
 * obeys cannot disagree — there is one expression, not two that have to be kept in step.
 */
data class PrefetchConditions(
    val enabled: Boolean,
    /**
     * Whether the app is in a state that permits deferred work — see [DeferredWorkWindow], which
     * answers "the UI has started, or a background run is under way" on platforms that can keep a
     * backgrounded process alive, and plain "on screen" on those that cannot.
     */
    val appAvailable: Boolean,
    val networkAllowed: Boolean,
    val powerAvailable: Boolean,
    val appIdle: Boolean,
    /**
     * Whether the device has any connection at all.
     *
     * Diagnostic only — deliberately **not** part of [isOpen], which is already closed by
     * [networkAllowed] when there is no network. It exists because an unmetered check answers false
     * both for "on mobile data" and for "no radio at all", and a readout that reports the second as
     * the first names the wrong cause — the one thing it exists not to do.
     */
    val connected: Boolean,
) {
    val isOpen: Boolean get() = enabled && appAvailable && networkAllowed && powerAvailable && appIdle
}

/**
 * The single answer to "may background prefetching run right now".
 *
 * Everything that should stop the prefetcher is folded into one boolean, so the engine has no
 * conditions of its own to check and `PrefetchController` needs no per-condition teardown.
 */
class PrefetchGate(
    private val deferredWorkWindow: DeferredWorkWindow,
    private val preferenceStore: PreferenceStore,
    private val networkConditionObserver: NetworkConditionObserver,
    private val connectivityObserver: ConnectivityObserver,
    private val powerStateObserver: PowerStateObserver,
    private val requestActivityTracker: RequestActivityTracker,
) {

    /** The verdict on the connection, plus the reachability that explains it. */
    private data class NetworkState(val allowed: Boolean, val connected: Boolean)

    /**
     * Nested rather than flat because Kotlin's [combine] takes at most five flows and there are more
     * inputs than that. The network sources nest naturally: the override only ever modifies the
     * metering answer, and connectivity only ever explains it.
     */
    private val network: Flow<NetworkState> = combine(
        networkConditionObserver.isUnmetered,
        preferenceStore.observe(AppPreferences.PrefetchOnMetered),
        connectivityObserver.isConnected,
    ) { unmetered, allowMetered, connected ->
        NetworkState(allowed = unmetered || allowMetered, connected = connected)
    }

    fun conditions(): Flow<PrefetchConditions> = combine(
        deferredWorkWindow.isOpen,
        preferenceStore.observe(AppPreferences.PrefetchEnabled),
        network,
        powerStateObserver.isPowerConstrained.map { !it },
        // No debounce and no quiet-period timer: the rule is "not while a request is in flight", not
        // "not near one". Adding a settle delay here would also delay resuming after every tap.
        requestActivityTracker.userInFlight.map { it == 0 },
    ) { appAvailable, enabled, networkState, powerOk, idle ->
        PrefetchConditions(
            enabled = enabled,
            appAvailable = appAvailable,
            networkAllowed = networkState.allowed,
            powerAvailable = powerOk,
            appIdle = idle,
            connected = networkState.connected,
        )
    }.distinctUntilChanged()

    fun isOpen(): Flow<Boolean> = conditions().map { it.isOpen }.distinctUntilChanged()
}
