package com.garfiec.librechat.feature.settings.state

/** Why prefetching is not running, in the order the user can act on. */
enum class PrefetchPauseReason {

    /** No connection at all. Distinct from [NETWORK], which is a connection we decline to use. */
    OFFLINE,

    /** Metered connection, and the override is off. */
    NETWORK,
    POWER,

    /** A user-initiated request is in flight — the prefetcher yields to the foreground. */
    BUSY,
    BACKGROUND,
}

/**
 * The single line the readout leads with.
 *
 * Deliberately one state rather than a list of flags: a user asking "is this working" wants one
 * answer, and the checklist on the activity screen is where the full picture lives.
 */
sealed interface PrefetchDisplayStatus {

    data object Off : PrefetchDisplayStatus

    /** A pass is running, but not on message bodies — list sync, reference data, or pruning. */
    data object Working : PrefetchDisplayStatus

    data class Warming(val completed: Int, val total: Int) : PrefetchDisplayStatus

    data object RateLimited : PrefetchDisplayStatus

    /** Repeated failures stopped the prefetcher for this account; it will not retry on its own. */
    data object Stopped : PrefetchDisplayStatus

    data class Paused(val reason: PrefetchPauseReason) : PrefetchDisplayStatus

    /** Conditions are met and nothing is stale — the steady state, and what success looks like. */
    data object UpToDate : PrefetchDisplayStatus

    /**
     * Conditions are met, work is outstanding, and no pass is running.
     *
     * Reachable because passes start on a *rising edge* of the gate and there is no timer: if
     * conversations went stale while the gate was already open, nothing re-triggers a pass until
     * some condition flips. This is the state the manual run exists for.
     */
    data class Waiting(val pending: Int) : PrefetchDisplayStatus
}
