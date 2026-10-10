package com.garfiec.librechat.core.data.prefetch

import com.garfiec.librechat.core.common.conversation.OpenConversationRegistry
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.lifecycle.ForegroundSignal
import com.garfiec.librechat.core.common.network.PrefetchMarker
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.MessageDao
import com.garfiec.librechat.core.data.db.dao.PrefetchCandidate
import com.garfiec.librechat.core.data.db.dao.PrefetchWatermarkDao
import com.garfiec.librechat.core.data.db.entity.PrefetchWatermarkEntity
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.media.resolveAttachmentUrl
import com.garfiec.librechat.core.model.media.resolveFileReferenceUrl
import com.garfiec.librechat.core.model.media.resolveImageFilePartUrl
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Warms the cache for the conversations the user is likeliest to open next, one request at a time,
 * only while [PrefetchGate] is open.
 *
 * A pass runs in four stages, and the order is load-bearing:
 *
 * 1. **Refresh the conversation list.** Freshness is decided by comparing each conversation's
 *    `updatedAt` against the watermark from its last warm — and that `updatedAt` is read from Room.
 *    Without syncing the list first the engine compares watermarks against the same stale timestamps
 *    it wrote them from, concludes nothing has changed, and never warms anything again. Skipped when
 *    a previous pass finished paging the list within the last few minutes — the one time-based rule
 *    in this feature, and it covers this stage only.
 * 2. **Warm messages** for whatever that reveals as stale.
 * 3. **Warm ancillary reference data** — endpoints, models, agents — once per account per process.
 * 4. **Prune** message rows for conversations that have aged out of the warm set.
 */
@Suppress("LongParameterList") // debt: constructor dependencies
class PrefetchEngine(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val watermarkDao: PrefetchWatermarkDao,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val configRepository: ConfigRepository,
    private val agentRepository: AgentRepository,
    private val policy: PrefetchPolicy,
    private val openConversationRegistry: OpenConversationRegistry,
    private val attachmentWarmer: AttachmentWarmer,
    private val settingsDataStore: SettingsDataStore,
    private val serverUrlProvider: ServerUrlProvider,
    private val foregroundSignal: ForegroundSignal,
    private val ioDispatcher: CoroutineDispatcher,
    // Test seams; keep them defaulted rather than injected. Supplying them from the module would
    // need `Function0` whitelisted in the graph verification, which would then stop catching
    // genuinely unresolvable dependencies.
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /**
     * The account whose pass gave up, if any. Must stay keyed by account, not a flag: this engine is
     * a singleton outliving any one session, so a flag would either survive a switch or reset on
     * every gate flip and keep retrying a dead server.
     */
    private var trippedForAccountId: String? = null

    /** When the breaker tripped, so [BREAKER_COOLDOWN] can expire it rather than it lasting forever. */
    private var breakerTrippedAtMillis: Long? = null

    /** Accounts whose slow-moving reference data has been warmed once this process. */
    private val ancillaryWarmed = mutableSetOf<String>()

    private val _runState = MutableStateFlow(
        PrefetchAccountRunState(accountId = null, state = PrefetchRunState.Idle),
    )

    /** What this engine is doing right now, for the settings readout. See [PrefetchRunState]. */
    val runState: StateFlow<PrefetchAccountRunState> = _runState.asStateFlow()

    private fun publish(accountId: AccountId, state: PrefetchRunState) {
        _runState.value = PrefetchAccountRunState(accountId.value, state)
    }

    suspend fun run(accountId: AccountId) {
        if (breakerHolds(accountId)) {
            publish(accountId, PrefetchRunState.Stopped)
            return
        }

        try {
            // Everything below is marked, so none of these requests count as user activity. Without
            // it the engine's own first request closes the gate that permits it and the pass
            // deadlocks — silently, since a closed gate is indistinguishable from a busy user.
            withContext(PrefetchMarker) {
                // Read once: a depth re-read mid-pass would select rows the list refresh never fetched.
                val depth = settingsDataStore.prefetchDepth.first()
                val pass = Pass(accountId)

                if (!pass.warmConversationList(depth)) return@withContext
                val eligible = pass.eligible(depth)
                if (!pass.warmMessages(eligible)) return@withContext
                pass.warmAncillary()
                pass.prune(eligible)
            }
        } finally {
            // Runs on cancellation too — the gate closing mid-pass is the ordinary way a pass ends,
            // and leaving the last in-progress state published would report work that has stopped.
            publish(
                accountId,
                if (trippedForAccountId == accountId.value) {
                    PrefetchRunState.Stopped
                } else {
                    PrefetchRunState.Idle
                },
            )
        }
    }

    /**
     * Whether the breaker still stops this account, expiring it once [BREAKER_COOLDOWN] has passed.
     *
     * The cooldown is load-bearing now that passes run unattended: without it a trip is permanent for
     * the life of the process, so a night-time outage leaves prefetching dead all the next day in a
     * process Android never got round to killing, with every condition on the readout green.
     */
    private fun breakerHolds(accountId: AccountId): Boolean {
        if (trippedForAccountId != accountId.value) return false
        val trippedAt = breakerTrippedAtMillis ?: return true
        if (nowMillis() - trippedAt < BREAKER_COOLDOWN.inWholeMilliseconds) return true
        Diag.d("Prefetch") { "breaker cooled off; allowing another attempt" }
        clearBreaker()
        return false
    }

    private fun clearBreaker() {
        trippedForAccountId = null
        breakerTrippedAtMillis = null
    }

    /**
     * Clears everything that would make the next pass a no-op, so a manual run is a genuine retry.
     *
     * Three pieces of state survive a pass, and all are otherwise durable: the breaker, which makes a
     * briefly-unreachable server indistinguishable from one that is down for good; the once-per-process
     * reference-data mark; and the recorded list refresh, which would otherwise let the manual run skip
     * the very stage that discovers new conversations. Clearing only some of them leaves "Warm now"
     * unable to recover the thing it advertises. Reached only from the manual run, so a retry is
     * always a deliberate user action rather than an automatic loop against a failing server.
     */
    suspend fun resetForManualRun(accountId: AccountId) {
        resetBreaker(accountId)
        ancillaryWarmed.remove(accountId.value)
        settingsDataStore.clearPrefetchListRefreshed(accountId.value)
    }

    /**
     * Clears only the breaker, for a scheduled run — without this a long-lived process makes every
     * later scheduled run return instantly having done nothing.
     *
     * Deliberately not [resetForManualRun]: clearing the reference-data mark as well would re-fetch
     * endpoints, models and agents on every scheduled pass, which for an account with nothing stale
     * is the entire cost of the pass.
     */
    fun resetBreaker(accountId: AccountId) {
        if (trippedForAccountId == accountId.value) {
            clearBreaker()
            publish(accountId, PrefetchRunState.Idle)
        }
    }

    /**
     * One pass's mutable bookkeeping. Held here rather than on the engine so a cancelled pass leaves
     * nothing behind — only the breaker and the ancillary set outlive a pass, and both are keyed by
     * account deliberately.
     */
    private inner class Pass(private val accountId: AccountId) {

        private var consecutiveFailures = 0

        /** When this pass last found itself off screen, or null while the app is visible. */
        private var offScreenSince: kotlin.time.TimeMark? = null

        /**
         * Whether the pass has been running unattended for longer than [OFF_SCREEN_BUDGET].
         *
         * A pass started by the gate's rising edge carries no budget of its own — only the scheduled
         * path passes one — and since the window latches, leaving the app no longer stops it. This is
         * the only bound on such a pass, which at the deepest depth with attachments on is hundreds
         * of requests at four a second against a server nobody is watching. Resets whenever the user
         * comes back, so an attended pass is never cut short.
         */
        fun outstayedWelcome(): Boolean {
            if (foregroundSignal.isForeground.value) {
                offScreenSince = null
                return false
            }
            val since = offScreenSince ?: timeSource.markNow().also { offScreenSince = it }
            if (since.elapsedNow() < OFF_SCREEN_BUDGET) return false
            Diag.d("Prefetch") { "off-screen budget spent; ending the pass" }
            return true
        }

        /** Returns false when the pass should stop because the breaker tripped. */
        suspend fun warmConversationList(depth: Int): Boolean {
            if (listRefreshedRecently()) {
                Diag.d("Prefetch") { "conversation list refreshed recently; skipping" }
                return true
            }
            publish(accountId, PrefetchRunState.RefreshingList)
            val pages = policy.listPagesFor(depth)
            var cursor: String? = null
            var page = 0
            do {
                if (outstayedWelcome()) return true
                val start = timeSource.markNow()
                val result = conversationRepository.loadNextPage(cursor = cursor)
                val elapsed = start.elapsedNow()

                cursor = when (val outcome = outcomeOf(result)) {
                    is StepOutcome.Ok -> {
                        consecutiveFailures = 0
                        (result as Result.Success).data
                    }
                    is StepOutcome.RateLimited -> {
                        publish(
                            accountId,
                            PrefetchRunState.RateLimited(outcome.backoff.inWholeMilliseconds),
                        )
                        delay(outcome.backoff)
                        return true // Stop paging; messages will still be warmed on stale data.
                    }
                    StepOutcome.Failed -> return !tripBreaker()
                }
                page++
                delay(paceAfter(elapsed))
            } while (cursor != null && page < pages)

            // Only here — every early exit above left the list partly paged, and recording those
            // would let the skip suppress the refresh that was never finished.
            settingsDataStore.recordPrefetchListRefreshed(accountId.value, nowMillis())
            return true
        }

        /**
         * Whether the list was fully paged recently enough to reuse.
         *
         * The prefetcher is edge-triggered and passes are short, so at the deepest settings a user
         * who idles in bursts can re-pay an eight-page prologue every time and never reach the
         * message stage. This is scoped to the list stage only: message freshness is still decided
         * by watermark-versus-`updatedAt` with no TTL anywhere near it.
         *
         * The cost is bounded and worth naming: a conversation created on another device inside the
         * window is invisible to selection until it expires.
         */
        private suspend fun listRefreshedRecently(): Boolean {
            val last = settingsDataStore.prefetchListRefreshedAt(accountId.value) ?: return false
            val age = nowMillis() - last
            // A clock that moved backwards (timezone change, NTP correction) reads as a huge or
            // negative age; refresh rather than trust it.
            return age in 0 until LIST_REFRESH_TTL.inWholeMilliseconds
        }

        suspend fun eligible(depth: Int): List<PrefetchCandidate> = withContext(ioDispatcher) {
            policy.eligible(
                recent = conversationDao.recentForPrefetch(accountId.value, depth),
                pinned = conversationDao.pinnedForPrefetch(accountId.value),
            )
        }

        /** Returns false when the breaker tripped partway through. */
        suspend fun warmMessages(eligible: List<PrefetchCandidate>): Boolean {
            val watermarks = withContext(ioDispatcher) {
                watermarkDao.allForAccount(accountId.value)
                    .associate { it.conversationId to it.warmedConversationUpdatedAt }
            }
            val work = policy.selectWork(
                eligible = eligible,
                watermarks = watermarks,
                openConversationId = openConversationRegistry.openConversationId.value,
            )
            Diag.d(
                "Prefetch",
                attrs = mapOf("eligible" to eligible.size.toString(), "stale" to work.size.toString()),
            ) { "warming messages" }

            work.forEachIndexed { index, candidate ->
                if (outstayedWelcome()) return true
                publish(
                    accountId,
                    PrefetchRunState.WarmingMessages(completed = index, total = work.size),
                )
                val start = timeSource.markNow()
                val result = messageRepository.refreshMessages(
                    candidate.conversationId,
                    originAccount = accountId,
                )
                val elapsed = start.elapsedNow()

                when (val outcome = outcomeOf(result)) {
                    is StepOutcome.Ok -> {
                        consecutiveFailures = 0
                        recordWatermark(candidate)
                        warmAttachments((result as Result.Success).data)
                    }
                    // Rate limiting means "slower", not "broken", so it deliberately does not count
                    // toward the breaker — the server is answering, it is just asking us to wait.
                    is StepOutcome.RateLimited -> {
                        Diag.d(
                            "Prefetch",
                            attrs = mapOf("backoffMs" to outcome.backoff.inWholeMilliseconds.toString()),
                        ) { "rate limited" }
                        publish(
                            accountId,
                            PrefetchRunState.RateLimited(outcome.backoff.inWholeMilliseconds),
                        )
                        delay(outcome.backoff)
                        // This candidate was not warmed; the next pass will find it stale again.
                        return@forEachIndexed
                    }
                    StepOutcome.Failed -> if (tripBreaker()) return false
                }
                delay(paceAfter(elapsed))
            }
            return true
        }

        /**
         * Endpoints, models and the agent list: small, slow-moving, and needed by the first screen
         * the user opens. Warmed once per account per process rather than every pass — a pass runs
         * whenever the user goes idle, and re-fetching reference data that rarely changes on each of
         * those would be most of the prefetcher's traffic.
         */
        suspend fun warmAncillary() {
            if (accountId.value in ancillaryWarmed) return
            publish(accountId, PrefetchRunState.WarmingReferenceData)

            // Marked only once all three have actually landed. Marking up front makes "warmed once
            // per process" mean "attempted once per process": a pass cancelled here — which is the
            // ordinary way a pass ends — or any failed request would retire the stage for the life
            // of the process, leaving the model picker and agent list permanently cold on a device
            // whose readout says everything is up to date.
            val outcomes = listOf(
                runStep { configRepository.fetchEndpoints() },
                runStep { configRepository.fetchModels() },
                runStep { agentRepository.getAgents() },
            )
            if (outcomes.all { it }) ancillaryWarmed.add(accountId.value)
        }

        /**
         * Drops cached messages for conversations that have fallen out of the warm set and have not
         * been touched in [PRUNE_AGE]. Conversation rows themselves are kept, so the list stays
         * complete and reopening a pruned thread simply fetches it again.
         */
        suspend fun prune(eligible: List<PrefetchCandidate>) = withContext(ioDispatcher) {
            val cutoff = nowMillis() - PRUNE_AGE.inWholeMilliseconds
            val stale = conversationDao.conversationIdsOlderThan(accountId.value, cutoff)
            val protectedIds = policy.protectedFromPruning(
                eligible = eligible,
                openConversationId = openConversationRegistry.openConversationId.value,
            )
            val prunable = stale.filterNot { it in protectedIds }
            if (prunable.isEmpty()) return@withContext
            publish(accountId, PrefetchRunState.Pruning)

            // Chunked because SQLite binds at most ~999 variables per statement, and the first prune
            // on a long-lived install is exactly where that limit is met.
            prunable.chunked(PRUNE_CHUNK).forEach { chunk ->
                withContext(NonCancellable) {
                    messageDao.deleteForConversations(accountId.value, chunk)
                    // Together, always: a watermark left behind reports its conversation as warm, so
                    // the rows just deleted would never be fetched again.
                    watermarkDao.deleteFor(accountId.value, chunk)
                }
            }
            Diag.d("Prefetch", attrs = mapOf("pruned" to prunable.size.toString())) { "pruned stale message cache" }
        }

        /**
         * Pulls a thread's images into the image cache, behind its own opt-in. Each image is a
         * request and stays paced like every other one — images are megabytes where the text is
         * kilobytes, so a burst here is what would flood the connection.
         */
        private suspend fun warmAttachments(messages: List<Message>) {
            if (!attachmentWarmer.isSupported) return
            if (!settingsDataStore.prefetchAttachmentsEnabled.first()) return

            val baseUrl = serverUrlProvider.getBaseUrl()
            if (baseUrl.isBlank()) return

            messages.asSequence()
                .flatMap { imageUrlsOf(it, baseUrl) }
                .distinct()
                .take(MAX_ATTACHMENTS_PER_CONVERSATION)
                .toList()
                .forEach { url ->
                    val start = timeSource.markNow()
                    attachmentWarmer.warm(url)
                    delay(paceAfter(start.elapsedNow()))
                }
        }

        private suspend fun recordWatermark(candidate: PrefetchCandidate) {
            // Watermark the value fetched against, not "now": the question this answers is "has the
            // server changed since?", which a wall-clock time cannot.
            withContext(ioDispatcher + NonCancellable) {
                watermarkDao.upsert(
                    PrefetchWatermarkEntity(
                        accountId = accountId.value,
                        conversationId = candidate.conversationId,
                        warmedConversationUpdatedAt = candidate.updatedAt,
                        warmedAt = nowMillis(),
                    ),
                )
            }
        }

        /** Returns whether the step landed, so the caller can decide what to record. */
        private suspend fun runStep(block: suspend () -> Result<*>): Boolean {
            val start = timeSource.markNow()
            val result = block()
            val elapsed = start.elapsedNow()
            val ok = when (val outcome = outcomeOf(result)) {
                is StepOutcome.Ok -> {
                    consecutiveFailures = 0
                    true
                }
                is StepOutcome.RateLimited -> {
                    publish(accountId, PrefetchRunState.RateLimited(outcome.backoff.inWholeMilliseconds))
                    delay(outcome.backoff)
                    false
                }
                StepOutcome.Failed -> {
                    tripBreaker()
                    false
                }
            }
            delay(paceAfter(elapsed))
            return ok
        }

        /** Counts a failure; returns true once the pass should stop. */
        private fun tripBreaker(): Boolean {
            consecutiveFailures++
            if (consecutiveFailures < MAX_CONSECUTIVE_FAILURES) return false
            // Give up for this account rather than working through the whole list against a server
            // that is plainly not answering.
            trippedForAccountId = accountId.value
            breakerTrippedAtMillis = nowMillis()
            Diag.w("Prefetch") { "prefetch stopped after $MAX_CONSECUTIVE_FAILURES consecutive failures" }
            return true
        }
    }

    /**
     * Every image URL a message would render, resolved exactly the way the UI resolves them — via
     * the shared resolver in `:core:model`, so a warmed entry is a cache hit for the composable that
     * later asks for it rather than a near-miss under a slightly different URL.
     */
    private fun imageUrlsOf(message: Message, baseUrl: String): Sequence<String> = sequence {
        message.files?.forEach { file -> resolveFileReferenceUrl(file, baseUrl)?.let { yield(it) } }
        message.attachments?.forEach { attachment ->
            resolveAttachmentUrl(attachment, baseUrl)?.let { yield(it) }
        }
        message.content?.forEach { part -> resolveImageFilePartUrl(part, baseUrl)?.let { yield(it) } }
    }.filterNot { it.startsWith(DATA_URI_PREFIX) } // Already inline; there is nothing to fetch.

    private fun outcomeOf(result: Result<*>): StepOutcome = when (result) {
        is Result.Success -> StepOutcome.Ok
        is Result.Loading -> StepOutcome.Failed
        is Result.Error -> {
            val api = result.exception as? ApiException
            if (api?.statusCode == HTTP_TOO_MANY_REQUESTS) {
                // Honour the server's own number when it sent one; otherwise wait a fixed period
                // rather than not at all, since the one thing a 429 rules out is retrying now.
                StepOutcome.RateLimited(api.retryAfterSeconds?.seconds ?: DEFAULT_RATE_LIMIT_BACKOFF)
            } else {
                StepOutcome.Failed
            }
        }
    }

    /**
     * How long to wait before the next request, which depends entirely on whether anyone is looking.
     *
     * **On screen**, wait proportionally to how long the last request took, so the prefetcher's share
     * of the server shrinks as the server slows down. Clamped at both ends: a fast local server
     * should not be hammered, and one request that burns the full 30s timeout should not stall the
     * pass for two and a half minutes.
     *
     * **Off screen**, hold a flat minimum instead. The proportional gap exists to keep the
     * prefetcher out of the user's way, and with no foreground work to yield to it only makes a pass
     * long enough to outlive the window it was given. What bounds an off-screen pass instead is that
     * it stays strictly one request at a time, plus [OFF_SCREEN_BUDGET].
     *
     * Read live rather than fixed per pass, so returning to the app restores the polite gap
     * immediately instead of at the next pass.
     */
    private fun paceAfter(elapsed: Duration): Duration =
        if (foregroundSignal.isForeground.value) {
            (elapsed * PACE_FACTOR).coerceIn(MIN_PACE, MAX_PACE)
        } else {
            MIN_PACE
        }

    private sealed interface StepOutcome {
        data object Ok : StepOutcome
        data class RateLimited(val backoff: Duration) : StepOutcome
        data object Failed : StepOutcome
    }

    companion object {
        const val PACE_FACTOR = 5
        const val MAX_CONSECUTIVE_FAILURES = 3

        /** Also the whole gap when the app is off screen — see [paceAfter]. */
        const val MIN_PACE_MILLIS = 250L

        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val PRUNE_CHUNK = 400

        /** Ceiling per conversation. A single thread of screenshots must not become the whole pass. */
        private const val MAX_ATTACHMENTS_PER_CONVERSATION = 20
        private const val DATA_URI_PREFIX = "data:"
        private val MIN_PACE = MIN_PACE_MILLIS.milliseconds
        private val MAX_PACE = 30.seconds
        private val DEFAULT_RATE_LIMIT_BACKOFF = 60.seconds
        private val PRUNE_AGE = 90.days
        private val LIST_REFRESH_TTL = 5.minutes

        /** How long a tripped breaker holds before the engine is willing to try again. */
        private val BREAKER_COOLDOWN = 1.hours

        /**
         * How long a pass may continue after the app leaves the screen. Generous enough to finish
         * ordinary work, short enough that an unattended pass cannot run for the life of the process.
         */
        private val OFF_SCREEN_BUDGET = 2.minutes
    }
}
