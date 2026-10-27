package com.garfiec.librechat.feature.settings.viewmodel.delegate

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.feature.settings.state.providerkeys.fetchProviderKeyState
import com.garfiec.librechat.feature.settings.state.providerkeys.isLiveAt
import com.garfiec.librechat.feature.settings.state.providerkeys.listedProviderKeyEndpoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/** What the Settings row for Provider Keys shows. */
@Immutable
sealed interface ProviderKeysSummary {
    /** No reachable endpoint takes a user-provided value: the page would be empty. */
    data object Hidden : ProviderKeysSummary

    /**
     * The general subtitle: not every listed endpoint has answered yet, or the endpoint configs
     * haven't loaded (offline at launch), when the row still shows so the page's Retry is reachable.
     */
    data object General : ProviderKeysSummary

    /** [setCount] of the [totalCount] listed endpoints hold a live key. */
    data class Count(val setCount: Int, val totalCount: Int) : ProviderKeysSummary
}

/**
 * The Provider Keys row's summary, from the same list the page shows ([listedProviderKeyEndpoints]),
 * so the row is hidden exactly when the page would be empty. Until the endpoint configs load it is
 * [ProviderKeysSummary.General], and [refresh] retries the load.
 *
 * Key states are re-read by [refresh] and whenever the listed endpoints change. A key counts as
 * set by the page's rule ([isLiveAt]), at the time of the last read: one past its expiry counts as
 * not set.
 */
internal class ProviderKeysSummaryDelegate(
    private val configRepository: ConfigRepository,
    roleRepository: RoleRepository,
    private val keyRepository: KeyRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val endpoints = listedProviderKeyEndpoints(configRepository, roleRepository)

    /** Last answer per endpoint name, and when it was read; a failed GET keeps the earlier answer. */
    private val keyStates = MutableStateFlow(KeyStates(emptyMap(), clock.now()))

    val summary: StateFlow<ProviderKeysSummary> = combine(endpoints, keyStates) { listed, read ->
        when {
            listed == null -> ProviderKeysSummary.General
            listed.isEmpty() -> ProviderKeysSummary.Hidden
            listed.any { it.name !in read.states } -> ProviderKeysSummary.General
            else -> ProviderKeysSummary.Count(
                setCount = listed.count { read.states.getValue(it.name).isLiveAt(read.at) },
                totalCount = listed.size,
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, ProviderKeysSummary.Hidden)

    private var refreshJob: Job? = null

    init {
        scope.launch { endpoints.drop(1).collect { refresh() } }
    }

    /**
     * Re-reads every listed endpoint's key state, or retries the endpoint configs while none has
     * loaded (their arrival re-reads the keys). The latest call wins.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val listed = endpoints.first()
            if (listed == null) {
                configRepository.fetchEndpoints()
                return@launch
            }
            val fetched = coroutineScope {
                listed.map { (name, config) -> async { name to keyRepository.fetchProviderKeyState(name, config) } }
                    .awaitAll()
            }
            keyStates.update { previous ->
                KeyStates(
                    states = buildMap {
                        fetched.forEach { (name, state) -> (state ?: previous.states[name])?.let { put(name, it) } }
                    },
                    at = clock.now(),
                )
            }
        }
    }

    private data class KeyStates(val states: Map<String, KeyState>, val at: Instant)
}
