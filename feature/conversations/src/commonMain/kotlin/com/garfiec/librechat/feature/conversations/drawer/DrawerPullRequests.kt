package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PullRequestEntry
import com.garfiec.librechat.core.data.repository.PullRequestRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest
import com.garfiec.librechat.core.model.pullrequest.PULL_REQUEST_BATCH_MAX
import com.garfiec.librechat.core.model.pullrequest.PULL_REQUEST_ROW_STALE_MS
import com.garfiec.librechat.core.model.pullrequest.pullRequestBatchAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * The drawer's pull request marks (v0.8.9-rc1), upstream's sidebar row marks: while the drawer is
 * open, the shown rows that may be code chats and aren't running are asked in one batch, and again
 * only once their answer is a minute old. Rows never poll. Answers live in [PullRequestRepository],
 * which the chat screen shares, so the open chat is not fetched twice.
 */
internal class DrawerPullRequests(
    configRepository: ConfigRepository,
    private val repository: PullRequestRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    /** On when the server has the batch route this client speaks (and is rc1+). */
    private class Gate(val enabled: Boolean, val maxConcurrent: Int?)

    private val gate: StateFlow<Gate> = combine(
        configRepository.startupConfig,
        configRepository.detectedBackendVersion,
    ) { config, version ->
        Gate(pullRequestBatchAvailable(config, version), config?.pullRequestsMaxConcurrentLookups)
    }.stateIn(scope, SharingStarted.Eagerly, Gate(enabled = false, maxConcurrent = null))

    private val entries: StateFlow<Map<String, PullRequestEntry>> =
        combine(gate, repository.entries.onStart { emit(emptyMap()) }) { gate, entries ->
            if (gate.enabled) entries else emptyMap()
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Each answered row's pull request, for the row mapping. */
    val marks: Flow<Map<String, ConversationPullRequest>> = entries
        .map { all -> all.mapNotNull { (id, entry) -> entry.pullRequest?.let { id to it } }.toMap() }
        .distinctUntilChanged()

    /** Ids whose lookup is out, so a re-emission while it runs doesn't ask again. Main-confined. */
    private val inFlight = mutableSetOf<String>()

    /** Asks for [shown]'s rows while [visible]; [running] rows are left out, as upstream's `!isGenerating`. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(visible: Flow<Boolean>, shown: Flow<List<Conversation>>, running: Flow<Set<String>>) {
        val lookups = combine(visible, gate) { open, gate -> open && gate.enabled }
            .distinctUntilChanged()
            .flatMapLatest { asking ->
                if (!asking) {
                    emptyFlow()
                } else {
                    combine(shown, running) { rows, runningIds ->
                        pullRequestLookupIds(
                            conversations = rows,
                            runningIds = runningIds,
                            answeredAt = entries.value.mapValues { it.value.fetchedAt },
                            staleBefore = clock.now() - PULL_REQUEST_ROW_STALE_MS.milliseconds,
                        ).filterNot { it in inFlight }.take(PULL_REQUEST_BATCH_MAX)
                    }.filter { it.isNotEmpty() }
                }
            }
        scope.launch {
            lookups.collect { ids ->
                inFlight += ids
                scope.launch {
                    try {
                        repository.refreshBatch(ids, gate.value.maxConcurrent)
                    } finally {
                        inFlight -= ids.toSet()
                    }
                }
            }
        }
    }

    /** The outgoing account's lookups stop counting; the repository keeps answers per account. */
    fun onAccountSwitched() {
        inFlight.clear()
    }
}
