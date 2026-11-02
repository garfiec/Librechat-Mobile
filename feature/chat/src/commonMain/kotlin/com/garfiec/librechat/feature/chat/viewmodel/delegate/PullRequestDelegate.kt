package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.PullRequestEntry
import com.garfiec.librechat.core.data.repository.PullRequestRepository
import com.garfiec.librechat.core.model.isCodeConversation
import com.garfiec.librechat.core.model.pullrequest.PULL_REQUEST_CHAT_STALE_MS
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

        handle.scope.launch {
            combine(eligible, entries) { id, all -> id?.let(all::get) }
                .map { entry ->
                    PullRequestUiState(
                        pullRequest = entry?.pullRequest,
                        refreshFailed = entry?.refreshFailed == true && entry.pullRequest != null,
                    )
                }
                .distinctUntilChanged()
                .collect { ui -> handle.update { pullRequest = ui } }
        }

        handle.scope.launch {
            combine(eligible, foreground) { id, front -> id.takeIf { front } }
                .distinctUntilChanged()
                .collectLatest { id -> if (id != null) poll(id) }
        }
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
