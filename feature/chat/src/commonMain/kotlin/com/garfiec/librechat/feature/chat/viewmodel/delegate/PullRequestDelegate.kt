package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.PullRequestEntry
import com.garfiec.librechat.core.data.repository.PullRequestRepository
import com.garfiec.librechat.core.model.isCodeConversation
import com.garfiec.librechat.core.model.pullrequest.PULL_REQUEST_CHAT_STALE_MS
import com.garfiec.librechat.core.model.pullrequest.PullRequestChecksLabel
import com.garfiec.librechat.core.model.pullrequest.PullRequestPresentation
import com.garfiec.librechat.core.model.pullrequest.PullRequestStateLabel
import com.garfiec.librechat.core.model.pullrequest.presentPullRequest
import com.garfiec.librechat.core.model.pullrequest.pullRequestRefetchIntervalMs
import com.garfiec.librechat.core.model.pullrequest.pullRequestsAvailable
import com.garfiec.librechat.feature.chat.viewmodel.PullRequestHandle
import com.garfiec.librechat.feature.chat.viewmodel.PullRequestUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * The open chat's pull request (v0.8.9-rc1, upstream `useConversationPullRequestQuery`).
 *
 * - **Who is asked:** a saved chat (not its first turn still streaming) that ran against an attached
 *   code workspace, on a server that announces `pullRequestsEnabled` and is rc1+. Nothing else is ever
 *   looked up. Whether the chat is a code chat comes from its cached row; a row cached before that was
 *   known is settled by the open's one-off conversation read (`ConversationLoadDelegate`).
 * - **Polling** ports upstream's `pullRequestRefetchInterval`, only while the chat screen is in front
 *   ([onForeground]). On return, an answer older than 15 s is asked again at once. No retries.
 * - **State** lives in [PullRequestRepository] (memory only, per account) and is mirrored here for
 *   the chat on screen; a refresh that fails after a success keeps the value and flags it.
 * - **A change of credential** (an account connected or disconnected, [PullRequestRepository.revision])
 *   restarts polling, which asks again at once: the repository has marked every answer stale.
 * - **Seen**: the composer chip spells out a state until the user has seen it (the sheet opened, or
 *   the chip stayed on screen, [markSeen]). Remembered per chat, state and checks, in memory only:
 *   after process death a state shows expanded once more, which is harmless.
 */
class PullRequestDelegate(
    private val handle: PullRequestHandle,
    private val configRepository: ConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val repository: PullRequestRepository,
    private val clock: Clock = Clock.System,
) {
    private val foreground = MutableStateFlow(true)
    private var started = false

    /** What the user has seen of a chat's pull request: its state and checks, as the chip says them. */
    private data class SeenKey(
        val conversationId: String,
        val state: PullRequestStateLabel,
        val checks: PullRequestChecksLabel,
    )

    private val seen = MutableStateFlow<Set<SeenKey>>(emptySet())
    private val sheetOpen = MutableStateFlow(false)

    private lateinit var entries: StateFlow<Map<String, PullRequestEntry>>

    /** The chat to look up, or null when nothing should be asked. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun eligibleConversation(): Flow<String?> {
        val gate = combine(configRepository.startupConfig, configRepository.detectedBackendVersion, ::pullRequestsAvailable)
            .distinctUntilChanged()
        val chat = handle.stateFlow
            .map { state -> state.conversationId?.takeIf { !state.isFirstTurnStreaming } }
            .distinctUntilChanged()
        return combine(gate, chat) { on, id -> id.takeIf { on } }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                if (id == null) {
                    flowOf(null)
                } else {
                    conversationRepository.observeConversation(id)
                        .map { conversation -> id.takeIf { conversation?.isCodeConversation == true } }
                }
            }
            .distinctUntilChanged()
    }

    fun start() {
        if (started) return
        started = true
        entries = repository.entries.onStart { emit(emptyMap()) }
            .stateIn(handle.scope, SharingStarted.Eagerly, emptyMap())
        val eligible = eligibleConversation().stateIn(handle.scope, SharingStarted.Eagerly, null)

        // A sheet left open on one chat must not reappear over the next one's pull request.
        handle.scope.launch { eligible.collect { sheetOpen.value = false } }

        handle.scope.launch {
            combine(eligible, entries, seen, sheetOpen) { id, all, seenKeys, sheet ->
                val entry = id?.let(all::get)
                val pr = entry?.pullRequest
                val presentation = pr?.let(::presentPullRequest)
                PullRequestUiState(
                    pullRequest = pr,
                    presentation = presentation,
                    refreshFailed = entry?.refreshFailed == true && pr != null,
                    unseen = id != null && presentation != null && seenKeyOf(id, presentation) !in seenKeys,
                    sheetOpen = sheet && pr != null,
                )
            }
                .distinctUntilChanged()
                .collect { ui -> handle.update { pullRequest = ui } }
        }

        handle.scope.launch {
            combine(eligible, foreground, repository.revision) { id, front, revision ->
                id?.takeIf { front }?.let { it to revision }
            }
                .distinctUntilChanged()
                .collectLatest { target -> if (target != null) poll(target.first) }
        }
    }

    private fun seenKeyOf(conversationId: String, presentation: PullRequestPresentation) =
        SeenKey(conversationId, presentation.state, presentation.checks)

    /** The user has seen the chat's pull request as it stands now; the composer chip folds to its glyph. */
    fun markSeen() {
        val id = handle.state.conversationId ?: return
        val presentation = handle.state.pullRequest.presentation ?: return
        seen.update { it + seenKeyOf(id, presentation) }
    }

    /** Opens the pull request sheet, which also counts as seeing it. */
    fun openSheet() {
        if (handle.state.pullRequest.pullRequest == null) return
        markSeen()
        sheetOpen.value = true
    }

    fun dismissSheet() {
        sheetOpen.value = false
    }

    private suspend fun poll(conversationId: String) {
        val known = latest(conversationId)
        if (known != null && clock.now() - known.fetchedAt < PULL_REQUEST_CHAT_STALE_MS.milliseconds) {
            val interval = pullRequestRefetchIntervalMs(known.pullRequest) ?: return
            val wait = interval - (clock.now() - known.fetchedAt).inWholeMilliseconds
            if (wait > 0) delay(wait)
        }
        while (true) {
            repository.refresh(conversationId)
            val interval = pullRequestRefetchIntervalMs(latest(conversationId)?.pullRequest) ?: return
            delay(interval)
        }
    }

    /** The repository's current answer, read at the source: [entries] lags it by a dispatch. */
    private suspend fun latest(conversationId: String): PullRequestEntry? = repository.entries.first()[conversationId]

    /** The sheet's Retry after a failed refresh. */
    fun retry() {
        val id = handle.state.conversationId ?: return
        if (handle.state.pullRequest.pullRequest == null) return
        handle.scope.launch { repository.refresh(id) }
    }

    /** The chat screen left (or returned to) the foreground: polling runs only while it is in front. */
    fun onForeground(inFront: Boolean) {
        foreground.value = inFront
    }
}
