package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyInvalidation
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.endpoint.resolveProviderKeyName
import com.garfiec.librechat.feature.chat.viewmodel.EndpointKeyHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Tracks per-endpoint user-provided-key state for the chat-side model selector
 * so groups whose key is missing/expired can render an "Add key" action instead
 * of letting the user pick a model that will fail at send time.
 *
 * A key that runs out while chat is open locks its endpoint then, not at the next failed send: a
 * timer at the earliest [KeyState.Set.expiresAt] marks it [KeyState.Expired] locally, and one a TTL
 * window later asks the server about that key alone, since the server has deleted it by then.
 *
 * That timer is a `delay`, which doesn't count time the device sleeps (Android's uptime clock) or
 * the app is suspended (iOS), so a key can run out while it is still pending. [resettle] checks
 * the states against the wall clock again whenever chat comes back or the selector opens.
 */
class EndpointKeyStatusDelegate(
    private val handle: EndpointKeyHandle,
    private val keyRepository: KeyRepository,
    private val clock: Clock = Clock.System,
) {

    private var lastKnownConfigs: Map<String, EndpointConfig> = emptyMap()
    private val mutex = Mutex()
    private var fetchJob: Job? = null

    /** The endpoints [fetchJob] fetches; null for every gated endpoint. */
    private var fetchNames: Set<String>? = null
    private var deadlineJob: Job? = null

    /** When each endpoint's key state was last fetched, by [clock]. */
    private val fetchedAt = mutableMapOf<String, Instant>()

    init {
        handle.scope.launch {
            keyRepository.keyInvalidations.collect { invalidation ->
                when (invalidation) {
                    is KeyInvalidation.All -> launchFetch(null)
                    is KeyInvalidation.ByName -> {
                        val names = gated(lastKnownConfigs)
                            .filter { (name, cfg) -> resolveProviderKeyName(name, cfg) == invalidation.name }
                            .keys
                        if (names.isNotEmpty()) launchFetch(names)
                    }
                }
            }
        }
    }

    /**
     * Re-fan-out the key-state fetch for every endpoint that requires a user-provided
     * key. Caches [configs] so a later invalidation-driven recompute can reuse them.
     *
     * Launches the fan-out on [handle.scope] so a slow `getKeyExpiry` GET
     * does not block the upstream `configRepository.endpointConfigs.collect`.
     */
    fun recomputeFor(configs: Map<String, EndpointConfig>) {
        lastKnownConfigs = configs
        launchFetch(null)
    }

    /**
     * Chat is back in front, or the model selector is opening: holds the key states against the
     * wall clock now. A key that ran out meanwhile locks at once, and one past its TTL window is
     * confirmed with the server unless it was fetched under [ResettleRefetchInterval] ago;
     * otherwise the pending timer is re-armed from the real time left.
     */
    fun resettle() = settleAndFetchDeleted(throttled = true)

    private fun settleAndFetchDeleted(throttled: Boolean) {
        val now = clock.now()
        handle.update { endpointKeyStates = endpointKeyStates.settledAt(now) }
        val states = handle.state.endpointKeyStates
        val due = states
            .filter { (name, state) ->
                state.isPastTtl(now) &&
                    !(throttled && fetchedAt[name]?.let { now - it < ResettleRefetchInterval } == true)
            }
            .keys
        if (due.isEmpty()) scheduleNextDeadline(states) else launchFetch(due)
    }

    /**
     * Fetches [names] (null: every gated endpoint), cancelling any in-flight fetch so a fresh
     * invalidation during a slow `getKeyExpiry` GET preempts it instead of serializing behind it.
     * The new fetch also covers what the cancelled one would have fetched.
     */
    private fun launchFetch(names: Set<String>?) {
        val inFlight = fetchJob?.takeIf { it.isActive }
        val merged = if (inFlight == null) names else names?.let { fetchNames?.plus(it) }
        inFlight?.cancel()
        fetchNames = merged
        fetchJob = handle.scope.launch {
            mutex.withLock { runFetch(merged) }
        }
    }

    private suspend fun runFetch(names: Set<String>?) {
        val targets = gated(lastKnownConfigs).filterKeys { names == null || it in names }

        if (targets.isEmpty()) {
            if (names == null && handle.state.endpointKeyStates.isNotEmpty()) {
                handle.update { endpointKeyStates = emptyMap() }
            }
            scheduleNextDeadline(handle.state.endpointKeyStates)
            return
        }

        // Snapshot last-known states BEFORE the optimistic Loading push so
        // resolveKeyState can preserve them when a transient error (e.g. a 401
        // mid-auth-token-refresh) breaks the per-endpoint fetch. Without this,
        // a single failed GET would demote every user-provide endpoint to greyed
        // even when the user has valid keys on file.
        val prior = handle.state.endpointKeyStates

        // Optimistic Loading push so the model sheet doesn't render an endpoint
        // as "active → greyed" while the fan-out is in flight. Already-resolved
        // values are preserved to avoid flicker on unrelated config tweaks.
        handle.update {
            val pending = targets.mapValues { (name, _) ->
                prior[name]?.takeIf { it != KeyState.Loading } ?: KeyState.Loading
            }
            endpointKeyStates = if (names == null) pending else endpointKeyStates + pending
        }

        val resolved: Map<String, KeyState> = coroutineScope {
            targets
                .map { (name, cfg) ->
                    async {
                        name to resolveKeyState(name, cfg, prior[name])
                    }
                }
                .map { it.await() }
                .toMap()
        }

        // A Set whose expiry has passed (kept from before a failed fetch, or read just before
        // it ran out) is expired now; the next deadline is then always in the future.
        val now = clock.now()
        if (names == null) fetchedAt.keys.retainAll(targets.keys)
        resolved.keys.forEach { fetchedAt[it] = now }
        handle.update {
            endpointKeyStates = (if (names == null) resolved else endpointKeyStates + resolved).settledAt(now)
        }
        scheduleNextDeadline(handle.state.endpointKeyStates)
    }

    /**
     * Replaces any pending timer with one at the next deadline in [states], or none when no key
     * has a deadline ahead. At an expiry the key is settled locally; at the end of a TTL window
     * the deleted key is fetched, and that fetch schedules the timer after.
     */
    private fun scheduleNextDeadline(states: Map<String, KeyState>) {
        deadlineJob?.cancel()
        val wait = nextKeyDeadline(states, clock.now())
        deadlineJob = wait?.let {
            handle.scope.launch {
                delay(it)
                settleAndFetchDeleted(throttled = false)
            }
        }
    }

    private suspend fun resolveKeyState(
        endpointName: String,
        config: EndpointConfig,
        priorState: KeyState?,
    ): KeyState {
        val keyName = resolveProviderKeyName(endpointName, config)
        return when (val state = keyRepository.fetchKeyState(keyName)) {
            is Result.Success -> state.data
            is Result.Error -> {
                Logger.w(throwable = state.exception) {
                    "fetchKeyState failed for $keyName, preserving prior state"
                }
                // Preserve the previously-resolved value so a transient network
                // blip does not flicker a known-good row to greyed. Brand-new
                // endpoints (no prior) and stale Loading entries fall through to
                // Unset so the CTA still surfaces for genuinely unknown rows.
                priorState?.takeIf { it != KeyState.Loading } ?: KeyState.Unset
            }
            Result.Loading -> priorState?.takeIf { it != KeyState.Loading } ?: KeyState.Unset
        }
    }
}

private fun gated(configs: Map<String, EndpointConfig>): Map<String, EndpointConfig> =
    configs.filterValues { it.userProvide == true || it.userProvideURL == true }

/**
 * How long after its expiry the server still holds a key. The key collection's TTL index
 * (`schema/key.ts`, `expireAfterSeconds: 0`) deletes a document once `expiresAt` passes, on
 * MongoDB's TTL pass, which runs every 60 seconds. Until then the key reads as expired; after
 * it, as not set.
 */
internal val ExpiredKeyTtlGrace: Duration = 60.seconds

/**
 * How far past a key's expiry its timer runs, so [settledAt] (strictly after the expiry, as
 * [KeyState.Companion.fromWire] counts the boundary instant as live) reads it as passed.
 */
internal val KeyExpiryMargin: Duration = 1.seconds

/** How recently a key past its TTL window may have been fetched for [EndpointKeyStatusDelegate.resettle] to skip it. */
internal val ResettleRefetchInterval: Duration = 60.seconds

/** [this] with every [KeyState.Set] whose expiry is before [now] as [KeyState.Expired]. */
internal fun Map<String, KeyState>.settledAt(now: Instant): Map<String, KeyState> = mapValues { (_, state) ->
    val expiresAt = (state as? KeyState.Set)?.expiresAt
    if (state is KeyState.Set && !state.neverExpires && expiresAt != null && expiresAt < now) {
        KeyState.Expired(expiresAt = expiresAt, wire = state.wire)
    } else {
        state
    }
}

/** Whether this is an expired key past the TTL window, so the server has deleted it by [now]. */
private fun KeyState.isPastTtl(now: Instant): Boolean =
    this is KeyState.Expired && expiresAt + ExpiredKeyTtlGrace <= now

/**
 * How long from [now] until the next key in [states] changes on the server: a [KeyState.Set]
 * runs out at its expiry, and a [KeyState.Expired] is deleted [ExpiredKeyTtlGrace] after it.
 * Null when nothing is due ahead of [now], so a key the server keeps past its deadline never
 * schedules a recompute loop.
 */
internal fun nextKeyDeadline(states: Map<String, KeyState>, now: Instant): Duration? = states.values
    .mapNotNull { state ->
        when (state) {
            is KeyState.Set -> state.expiresAt?.takeUnless { state.neverExpires }?.plus(KeyExpiryMargin)
            is KeyState.Expired -> state.expiresAt + ExpiredKeyTtlGrace
            KeyState.Loading, KeyState.Unset -> null
        }
    }
    .filter { it > now }
    .minOrNull()
    ?.let { it - now }
