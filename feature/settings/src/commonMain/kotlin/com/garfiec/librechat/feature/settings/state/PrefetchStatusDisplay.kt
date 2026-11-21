package com.garfiec.librechat.feature.settings.state

import com.garfiec.librechat.core.data.prefetch.PrefetchConditions
import com.garfiec.librechat.core.data.prefetch.PrefetchRunState
import com.garfiec.librechat.core.data.prefetch.PrefetchStatus

/**
 * Reduces the gate's conditions and the engine's run state to the one status line.
 *
 * Order is the whole content of this function. Being switched off is checked first: the breaker is
 * cleared only by the manual run, which lives behind an entry point that is itself disabled while
 * prefetching is off, so reporting a stale failure over "Off" would leave a permanent error banner
 * for a feature the user has turned off and no in-app way to clear it. After that, run state is read
 * before the remaining conditions so a pass still winding down is not reported as paused by the
 * condition that is cancelling it.
 */
fun PrefetchStatus.toDisplayStatus(): PrefetchDisplayStatus {
    val state = runState
    return when {
        !conditions.enabled -> PrefetchDisplayStatus.Off
        state is PrefetchRunState.Stopped -> PrefetchDisplayStatus.Stopped
        state is PrefetchRunState.WarmingMessages ->
            PrefetchDisplayStatus.Warming(state.completed, state.total)
        state is PrefetchRunState.RateLimited -> PrefetchDisplayStatus.RateLimited
        state != PrefetchRunState.Idle -> PrefetchDisplayStatus.Working
        else -> conditions.pauseReason()?.let(PrefetchDisplayStatus::Paused)
            ?: if (pending.isEmpty()) {
                PrefetchDisplayStatus.UpToDate
            } else {
                PrefetchDisplayStatus.Waiting(pending.size)
            }
    }
}

/**
 * The first unmet condition, or null when the gate is open.
 *
 * Network before power before busy: that is the order in which a user can do something about them,
 * and reporting "app is busy" — which resolves by itself a second later — over "waiting for Wi-Fi"
 * would send them looking for a problem that is not the one holding prefetching back.
 *
 * Offline is separated from metered ahead of both. An unmetered check answers false for a device
 * with no radio on exactly as it does for one on mobile data, so collapsing them tells a user in
 * airplane mode to wait for Wi-Fi — naming a cause that is not theirs, on the screen whose only job
 * is to name the right one.
 */
fun PrefetchConditions.pauseReason(): PrefetchPauseReason? = when {
    !connected -> PrefetchPauseReason.OFFLINE
    !networkAllowed -> PrefetchPauseReason.NETWORK
    !powerAvailable -> PrefetchPauseReason.POWER
    !appAvailable -> PrefetchPauseReason.BACKGROUND
    !appIdle -> PrefetchPauseReason.BUSY
    else -> null
}
