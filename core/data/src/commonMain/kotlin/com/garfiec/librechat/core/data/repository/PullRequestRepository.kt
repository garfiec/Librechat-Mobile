package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.identity.flatMapAccountOrEmpty
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequestsEntry
import com.garfiec.librechat.core.model.pullrequest.PULL_REQUEST_BATCH_MAX
import com.garfiec.librechat.core.model.pullrequest.PULL_REQUEST_DEFAULT_CONCURRENCY
import com.garfiec.librechat.core.network.api.PullRequestsApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * What the session knows of one conversation's pull request.
 *
 * [pullRequest] is the last answer that succeeded (null: the chat has none, or nothing succeeded
 * yet); [answered] says whether one ever did. [refreshFailed] is set when the latest lookup failed
 * after an earlier one succeeded, so the value is kept and the chat can offer Retry.
 */
data class PullRequestEntry(
    val pullRequest: ConversationPullRequest?,
    val answered: Boolean,
    val refreshFailed: Boolean,
    val fetchedAt: Instant,
)

/**
 * Conversation pull requests (v0.8.9-rc1), shared by the chat screen and the drawer so the open
 * chat is never fetched twice. **In memory only, never Room**: the answer is volatile and depends on
 * the user's credential. Kept per account, so a switch shows nothing of another account's.
 */
interface PullRequestRepository {
    /** The active account's entries by conversation id. */
    val entries: Flow<Map<String, PullRequestEntry>>

    /** Asks for one conversation's pull request and records the answer. */
    suspend fun refresh(conversationId: String)

    /**
     * Asks for [conversationIds] through the batch route, in chunks of [PULL_REQUEST_BATCH_MAX] with
     * at most [maxConcurrent] chunks in flight. A server without the route (404) is asked one id at
     * a time with the same bound, as upstream's `createBatchFetcher` falls back.
     */
    suspend fun refreshBatch(conversationIds: List<String>, maxConcurrent: Int?)

    /**
     * Bumped when the active account's answers stop counting: the credential the server looks pull
     * requests up with changed (an account connected or disconnected). The chat and the drawer ask
     * again when it moves.
     */
    val revision: StateFlow<Long>

    /**
     * Marks the active account's answers stale (each value is kept until its re-ask lands) and bumps
     * [revision]. Upstream refetches every cached pull request on a change of credential.
     */
    fun invalidate()
}

class PullRequestRepositoryImpl(
    private val api: PullRequestsApi,
    private val activeAccountProvider: ActiveAccountProvider,
    private val clock: Clock = Clock.System,
) : PullRequestRepository {

    private val byAccount = MutableStateFlow<Map<String, Map<String, PullRequestEntry>>>(emptyMap())

    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()

    override fun invalidate() {
        val account = activeAccountProvider.currentAccountId()?.value ?: return
        byAccount.update { all ->
            val current = all[account] ?: return@update all
            all + (account to current.mapValues { (_, entry) -> entry.copy(fetchedAt = Instant.DISTANT_PAST) })
        }
        _revision.update { it + 1 }
    }

    override val entries: Flow<Map<String, PullRequestEntry>> =
        activeAccountProvider.flatMapAccountOrEmpty(emptyMap()) { account ->
            byAccount.map { it[account.value].orEmpty() }.distinctUntilChanged()
        }

    override suspend fun refresh(conversationId: String) {
        val asker = currentAsker() ?: return
        refresh(asker, conversationId)
    }

    private suspend fun refresh(asker: Asker, conversationId: String) {
        when (val result = safeApiCall { api.pullRequest(conversationId) }) {
            is Result.Success -> record(asker, conversationId, Answer.Found(result.data.pullRequest))
            is Result.Error -> record(asker, conversationId, failureOf(result))
            Result.Loading -> Unit
        }
    }

    /** Who a lookup was sent for: the account, and the credential revision it went out under. */
    private data class Asker(val account: String, val revision: Long)

    private fun currentAsker(): Asker? =
        activeAccountProvider.currentAccountId()?.value?.let { Asker(it, _revision.value) }

    override suspend fun refreshBatch(conversationIds: List<String>, maxConcurrent: Int?) {
        val asker = currentAsker() ?: return
        val ids = conversationIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return
        val gate = Semaphore((maxConcurrent ?: PULL_REQUEST_DEFAULT_CONCURRENCY).coerceAtLeast(1))
        // Each chunk answers the ids of its own that the server had no batch route for.
        val unrouted = coroutineScope {
            ids.chunked(PULL_REQUEST_BATCH_MAX).map { chunk ->
                async {
                    when (val result = gate.withPermit { safeApiCall { api.pullRequests(chunk) } }) {
                        is Result.Success -> {
                            recordBatch(asker, chunk, result.data.results)
                            emptyList()
                        }
                        is Result.Error ->
                            if ((result.exception as? ApiException)?.statusCode == HTTP_NOT_FOUND) {
                                chunk
                            } else {
                                val failure = failureOf(result)
                                chunk.forEach { record(asker, it, failure) }
                                emptyList()
                            }
                        Result.Loading -> emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
        if (unrouted.isNotEmpty()) {
            coroutineScope {
                unrouted.map { id -> async { gate.withPermit { refresh(asker, id) } } }.awaitAll()
            }
        }
    }

    private fun recordBatch(asker: Asker, asked: List<String>, results: List<ConversationPullRequestsEntry>) {
        val answered = results.associateBy { it.conversationId }
        asked.forEach { id ->
            val entry = answered[id]
            val answer = when {
                entry == null -> Answer.Failed
                entry.error != null -> {
                    Logger.w { "Pull request lookup failed: ${entry.error?.code}" }
                    Answer.Failed
                }
                else -> Answer.Found(entry.pullRequest)
            }
            record(asker, id, answer)
        }
    }

    private sealed interface Answer {
        data class Found(val pullRequest: ConversationPullRequest?) : Answer
        data object Failed : Answer

        /** No connection: nothing is learned and nothing is reported. */
        data object Offline : Answer
    }

    private fun failureOf(error: Result.Error): Answer {
        if (error.kind == FailureKind.Network) return Answer.Offline
        // Never shown: every code reads the same to the user. Logged for support.
        Logger.w { "Pull request lookup failed: ${(error.exception as? ApiException)?.statusCode ?: error.kind}" }
        return Answer.Failed
    }

    private fun record(asker: Asker, conversationId: String, answer: Answer) {
        if (answer == Answer.Offline) return
        val account = asker.account
        // An account switched while the request was out: the answer went out with one account's
        // token and would be filed under the other, so it is dropped rather than cached. Likewise
        // a change of credential: the answer was looked up under the old one, and the re-ask the
        // change started must not be overwritten by it.
        if (activeAccountProvider.currentAccountId()?.value != account || _revision.value != asker.revision) return
        val now = clock.now()
        byAccount.update { all ->
            val current = all[account].orEmpty()
            val previous = current[conversationId]
            val next = when (answer) {
                is Answer.Found -> PullRequestEntry(answer.pullRequest, answered = true, refreshFailed = false, fetchedAt = now)
                else -> PullRequestEntry(
                    pullRequest = previous?.pullRequest,
                    answered = previous?.answered == true,
                    refreshFailed = previous?.answered == true,
                    fetchedAt = now,
                )
            }
            all + (account to (current + (conversationId to next)))
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
