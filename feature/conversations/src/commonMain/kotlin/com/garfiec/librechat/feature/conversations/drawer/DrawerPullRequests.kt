package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PullRequestEntry
import com.garfiec.librechat.core.data.repository.PullRequestRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest
import com.garfiec.librechat.core.model.pullrequest.PULL_REQUEST_ROW_FORCE_MS
import com.garfiec.librechat.core.model.pullrequest.PULL_REQUEST_ROW_STALE_MS
import com.garfiec.librechat.core.model.pullrequest.pullRequestBatchAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * The drawer's pull request marks (v0.8.9-rc1), upstream's sidebar row marks: while the drawer is
 * open, the shown rows that may be code chats and aren't running are asked (all of them: the
 * repository chunks by `PULL_REQUEST_BATCH_MAX` with a bounded number in flight), and again only
 * once their answer is a minute old. Answers live in [PullRequestRepository], which the chat screen
 * shares, so the open chat is not fetched twice.
 *
 * Rows never poll: a mark is brought up to date by an event, never a timer. Opening the drawer asks
 * rows whose answer is over a minute old; a pull-to-refresh ([refreshNow]) asks those over
 * [PULL_REQUEST_ROW_FORCE_MS], so repeated pulls cost nothing; a change of credential (an account
 * connected or disconnected, [PullRequestRepository.revision]) asks every shown row again while the
 * drawer is open, or at its next open. An account switch cancels the lookups still out.
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

    /** Bumped when the lookups out stop counting, so a cancelled one leaves [inFlight] alone. Main-confined. */
    private var generation = 0

    /** The credential revision the lookups out were asked under (Main-confined, see [start]). */
    private var askedRevision: Long? = null

    /** The running lookups, cancelled together on an account switch. */
    private val lookups = SupervisorJob(scope.coroutineContext[Job])

    /** Bumped by a pull-to-refresh. */
    private val refreshRequests = MutableStateFlow(0)

    /** The [refreshRequests] value the last lookup pass started from (Main-confined, see [start]). */
    private var honouredRefresh = 0

    private class Trigger(val asking: Boolean, val revision: Long, val refresh: Int)

    /** Asks for [shown]'s rows while [visible]; [running] rows are left out, as upstream's `!isGenerating`. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(visible: Flow<Boolean>, shown: Flow<List<Conversation>>, running: Flow<Set<String>>) {
        val askFor = combine(visible, gate, repository.revision, refreshRequests) { open, gate, revision, refresh ->
            Trigger(open && gate.enabled, revision, refresh)
        }
            .distinctUntilChanged { old, new ->
                old.asking == new.asking && old.revision == new.revision && old.refresh == new.refresh
            }
            .flatMapLatest { trigger ->
                // A pull asks what a minute's wait would not, down to a short floor; an open asks only
                // what is a minute old. A pull while the drawer is closed waits for no one.
                val pulled = trigger.refresh != honouredRefresh
                honouredRefresh = trigger.refresh
                // A change of credential: lookups still out went with the old one. Cancel them, as
                // upstream cancels before refetching, so their rows are asked again now.
                if (askedRevision != null && askedRevision != trigger.revision) dropLookups()
                askedRevision = trigger.revision
                val staleMs = if (pulled) PULL_REQUEST_ROW_FORCE_MS else PULL_REQUEST_ROW_STALE_MS
                if (!trigger.asking) {
                    emptyFlow()
                } else {
                    combine(shown, running) { rows, runningIds ->
                        pullRequestLookupIds(
                            conversations = rows,
                            runningIds = runningIds,
                            answeredAt = entries.value.mapValues { it.value.fetchedAt },
                            staleBefore = clock.now() - staleMs.milliseconds,
                        ).filterNot { it in inFlight }
                    }.filter { it.isNotEmpty() }
                }
            }
        scope.launch {
            askFor.collect { ids ->
                inFlight += ids
                val asked = generation
                scope.launch(lookups) {
                    try {
                        repository.refreshBatch(ids, gate.value.maxConcurrent)
                    } finally {
                        if (generation == asked) inFlight -= ids.toSet()
                    }
                }
            }
        }
    }

    /** The drawer's pull-to-refresh: re-asks shown rows whose answer is over [PULL_REQUEST_ROW_FORCE_MS] old. */
    fun refreshNow() {
        refreshRequests.update { it + 1 }
    }

    /**
     * The outgoing account's lookups are cancelled; the repository keeps answers per account and
     * drops any that land after the switch.
     */
    fun onAccountSwitched() {
        dropLookups()
    }

    private fun dropLookups() {
        generation++
        lookups.cancelChildren()
        inFlight.clear()
    }
}
