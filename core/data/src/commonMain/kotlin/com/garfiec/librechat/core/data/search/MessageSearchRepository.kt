package com.garfiec.librechat.core.data.search

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ConversationTitle
import com.garfiec.librechat.core.data.db.dao.MessageSearchDao
import com.garfiec.librechat.core.data.db.dao.MessageSearchRow
import com.garfiec.librechat.core.data.mapper.indexedTextOf
import com.garfiec.librechat.core.data.mapper.visibleTextOf
import com.garfiec.librechat.core.data.repository.SearchCapabilityRepository
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.logging.LogOrigin
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.network.api.MessagesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Message search over the offline index, merged with the server's when it has search enabled. */
interface MessageSearchRepository {
    /**
     * Searches [query] for the active account: emits local hits as soon as they're read, then, if
     * the server can search, once more with its hits merged in. Re-runs for a new account on a
     * switch and emits nothing while no account is active.
     *
     * Callers debounce typing and collect with `flatMapLatest`, so a newer query cancels this one.
     * [scanFrom] resumes a scan-engine search where the last result's [ScanProgress] stopped.
     * [remotePageSize] is how many hits the server is asked for; a caller that shows a few asks
     * for few.
     */
    fun search(
        query: String,
        limit: Int = DEFAULT_LIMIT,
        scanFrom: ScanCursor? = null,
        options: SearchOptions = SearchOptions.Default,
        remotePageSize: Int = MessagesApi.SEARCH_PAGE_SIZE,
    ): Flow<MessageSearchResult>

    /** The models and endpoints the active account's indexed messages came from, for the filters. */
    suspend fun filterValues(): SearchFilterValues = SearchFilterValues()

    companion object {
        const val DEFAULT_LIMIT: Int = 200
    }
}

data class SearchFilterValues(
    val models: List<String> = emptyList(),
    val endpoints: List<String> = emptyList(),
)

/**
 * **Every local query here must be bounded.** SQLite runs a statement to completion whatever
 * happens to the coroutine that issued it: cancelling a search only stops the result being
 * delivered. So local searches run one at a time behind [localSearch], and each is bounded by
 * construction (a LIMIT on the pinned index plan, or the scan budget). An uncapped query added
 * "because the flow is cancellable" would stall every search queued behind it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageSearchRepositoryImpl(
    private val searchDao: MessageSearchDao,
    private val conversationDao: ConversationDao,
    private val scanEngine: MessageScanEngine,
    private val messagesApi: MessagesApi,
    private val capability: SearchCapabilityRepository,
    private val connectivity: ConnectivityObserver,
    private val activeAccountProvider: ActiveAccountProvider,
    private val activity: LocalSearchActivity,
    private val indexState: SearchIndexState,
    private val snippetDispatcher: CoroutineDispatcher,
    /** The server's detected version, or null before it's known. */
    private val backendVersion: () -> String? = { null },
    private val clock: Clock = Clock.System,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : MessageSearchRepository {

    private val localSearch = Mutex()
    private val remoteCache = RemoteSearchCache(clock)

    override fun search(
        query: String,
        limit: Int,
        scanFrom: ScanCursor?,
        options: SearchOptions,
        remotePageSize: Int,
    ): Flow<MessageSearchResult> =
        activeAccountProvider.state
            .map { (it as? AccountState.Resolved)?.id?.value }
            .distinctUntilChanged()
            .flatMapLatest { accountId ->
                if (accountId == null) emptyFlow() else searchFor(SearchRequest(accountId, query, limit, scanFrom, options, remotePageSize))
            }

    override suspend fun filterValues(): SearchFilterValues {
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return SearchFilterValues()
        return SearchFilterValues(
            models = searchDao.modelsFor(accountId, FILTER_VALUE_LIMIT),
            endpoints = searchDao.endpointsFor(accountId, FILTER_VALUE_LIMIT),
        )
    }

    private class SearchRequest(
        val accountId: String,
        val query: String,
        val limit: Int,
        val scanFrom: ScanCursor?,
        val options: SearchOptions,
        val remotePageSize: Int,
    )

    private fun searchFor(request: SearchRequest) = flow {
        val accountId = request.accountId
        val plan = LocalPlan.of(request.query, request.options)
        if (plan == LocalPlan.Empty) return@flow
        if (plan is LocalPlan.Rejected) {
            emit(MessageSearchResult(accountId, request.query, emptyList(), RemoteSearchStatus.Unavailable, regexProblem = plan.problem))
            return@flow
        }
        val started = timeSource.markNow()
        // "Server only" skips the offline index entirely, not just its hits.
        val local = if (request.options.source == SearchSource.Server) {
            LocalRead(emptyList(), scan = null)
        } else {
            localSearch.withLock { activity.track { readLocal(request, plan) } }
        }
        val titles = titlesFor(accountId, local.rows.map { it.row.conversationId })
        val localHits = withContext(snippetDispatcher) {
            local.rows.map {
                it.row.toHit(
                    accountId,
                    titles[it.row.conversationId],
                    it.terms,
                    wordStarts = plan is LocalPlan.Fts,
                    lastIsPrefix = (plan as? LocalPlan.Fts)?.match?.lastIsPrefix ?: false,
                    inReasoning = it.inReasoning,
                    options = request.options,
                )
            }
        }
        val chosen = when {
            request.options.source == SearchSource.Cached -> RemoteSkipReason.CachedOnly
            plan is LocalPlan.Regex -> RemoteSkipReason.Regex
            request.options.since != null -> RemoteSkipReason.DateFilter
            else -> null
        }
        // A short query stays local without a note: the server is asked from 3 characters.
        val longEnough = request.query.trim().length >= MIN_REMOTE_CHARS
        val skipped = chosen ?: if (longEnough) remoteBlocker(unknownBlocks = false) else null
        // Before the first capability probe answers (a cold start with nothing cached) the server
        // isn't known to be off: the local hits go out as "searching", and the probe gets a moment.
        val probePending = skipped == null && longEnough && capability.serverSearchEnabled.first() == null
        val remoteEligible = skipped == null && longEnough
        val base = MessageSearchResult(
            accountId = accountId,
            query = request.query,
            hits = localHits,
            remoteStatus = if (remoteEligible) RemoteSearchStatus.Searching else RemoteSearchStatus.Unavailable,
            scan = local.scan,
            indexIncomplete = !indexState.isBackfillDone(),
            localCapped = local.capped,
            remoteSkipped = skipped,
        )
        emit(base)
        logSearch(started, localHits.size, "local", plan)
        if (skipped == RemoteSkipReason.Offline) {
            // Back online, the server half runs after all; a newer query cancels the wait.
            connectivity.isConnected.first { it }
            // A probe made while offline left the answer unknown; ask again now there's a connection.
            if (capability.serverSearchEnabled.first() == null) capability.refresh()
            remoteBlocker()?.let { blocker ->
                if (blocker != skipped) emit(base.copy(remoteSkipped = blocker))
                return@flow
            }
            val online = base.copy(remoteStatus = RemoteSearchStatus.Searching, remoteSkipped = null)
            emit(online)
            emit(withRemote(request, online, plan.highlightTerms()))
            return@flow
        }
        if (!remoteEligible) return@flow
        if (probePending) {
            withTimeoutOrNull(PROBE_WAIT) { capability.serverSearchEnabled.first { it != null } }
            remoteBlocker()?.let { blocker ->
                emit(base.copy(remoteStatus = RemoteSearchStatus.Unavailable, remoteSkipped = blocker))
                return@flow
            }
        }
        val merged = withRemote(request, base, plan.highlightTerms())
        emit(merged)
        logSearch(started, merged.hits.size, "remote", plan)
    }

    /** [base] with the server's hits merged in, or marked failed; [base] itself after a switch. */
    private suspend fun withRemote(request: SearchRequest, base: MessageSearchResult, terms: List<String>): MessageSearchResult {
        val accountId = request.accountId
        val pageSize = request.remotePageSize
        val remote = remoteCache.get(accountId, request.query, pageSize)
            ?: when (val fetched = safeApiCall { messagesApi.searchMessages(request.query, pageSize) }) {
                is Result.Success -> fetched.data.messages.also { remoteCache.put(accountId, request.query, pageSize, it) }
                else -> null
            }
        // The request ran on the account that was live when it was sent; a reply landing after a
        // switch belongs to that account and must not reach the new one's results.
        if (activeAccountProvider.currentAccountId()?.value != accountId) return base
        if (remote == null) return base.copy(remoteStatus = RemoteSearchStatus.Failed)
        val localIds = base.hits.mapTo(HashSet()) { it.messageId }
        val options = request.options
        // The server doesn't say which conversations are archived; the cache does, for the ones it holds.
        val archived = if (options.includeArchived) emptySet() else conversationDao.archivedIds(accountId).toHashSet()
        val remoteHits = withContext(snippetDispatcher) {
            remote.asSequence()
                .filter { it.messageId !in localIds && it.conversationId !in archived }
                .filter { options.admits(it.isCreatedByUser, it.model, it.endpoint, createdAt = null) }
                .map { it to indexedTextOf(it.text, it.content) }
                // The server's engine matches any of the words, and typos; a hit must hold every
                // word the way a local one does, so the two halves of the list agree.
                .filter { (_, indexed) ->
                    TermMatch.matches(checkedText(indexed.text.orEmpty(), indexed.visibleLength, options), request.query, options)
                }
                .map { (message, indexed) ->
                    val text = indexed.text.orEmpty()
                    message.toHit(accountId, text, terms, inReasoning = onlyInReasoning(text, indexed.visibleLength, terms), options)
                }
                .toList()
        }
        return base.copy(
            hits = base.hits + remoteHits,
            remoteStatus = RemoteSearchStatus.Done,
            remoteCapped = remote.size >= pageSize || (remote.size == MessagesApi.SERVER_DEFAULT_PAGE_SIZE && mayIgnorePageSize()),
        )
    }

    /** A server older than v0.8.8-rc2 answers 25 hits whatever `pageSize` asked for. */
    private fun mayIgnorePageSize(): Boolean {
        val version = backendVersion() ?: return true
        return !BackendVersion.isCompatibleOrNewer(version, PAGE_SIZE_SINCE)
    }

    /** A local row and the terms to mark in it (a regex hit marks what it actually matched). */
    private class LocalRow(val row: MessageSearchRow, val terms: List<String>, val inReasoning: Boolean)

    private class LocalRead(val rows: List<LocalRow>, val scan: ScanProgress?, val capped: Boolean = false)

    private suspend fun readLocal(request: SearchRequest, plan: LocalPlan): LocalRead {
        val options = request.options
        return when (plan) {
            is LocalPlan.Fts -> {
                val rows = ftsRows(request, plan.match.expression)
                val terms = plan.match.terms
                val verify = TextVerifier.of(terms, options) ?: visibleOnlyCheck(terms, options)
                val kept = if (verify == null) {
                    rows
                } else {
                    rows.filter { verify(checkedText(it.searchText.orEmpty(), it.visibleLength, options)) }
                }
                val local = kept.map { row ->
                    LocalRow(row, terms, onlyInReasoning(row.searchText.orEmpty(), row.visibleLength, terms))
                }
                // The text checks run on the newest `limit` candidates only: when they dropped some
                // of a full page, older messages that pass were never read.
                LocalRead(local, scan = null, capped = rows.size >= request.limit && kept.size < rows.size)
            }
            is LocalPlan.Literal -> scanLocal(request, TextMatcher.literal(plan.text, ignoreCase = !options.matchCase))
            // The first non-empty match: a pattern that can match nothing (`\d*`, `foo|`) would
            // otherwise report every message, as titles and preview marks already skip it.
            is LocalPlan.Regex -> scanLocal(
                request,
                TextMatcher { text -> plan.regex.findAll(text).firstOrNull { !it.range.isEmpty() }?.range },
            )
            LocalPlan.Empty, is LocalPlan.Rejected -> LocalRead(emptyList(), scan = null)
        }
    }

    private suspend fun scanLocal(request: SearchRequest, matcher: TextMatcher): LocalRead {
        val options = request.options
        val archived = if (options.includeArchived) emptySet() else conversationDao.archivedIds(request.accountId).toHashSet()
        val result = scanEngine.scan(
            accountId = request.accountId,
            matcher = matcher,
            from = request.scanFrom ?: if (options.sort == SearchSort.Oldest) ScanCursor.START_OLDEST else ScanCursor.START,
            budget = ScanBudget(maxMatches = request.limit),
            notBefore = options.since,
            admit = { row ->
                row.conversationId !in archived &&
                    options.admits(row.isCreatedByUser, row.model, row.endpoint, row.createdAt)
            },
            textOf = { row -> row.searchText?.let { checkedText(it, row.visibleLength, options) } },
        )
        val rows = result.hits.map { hit ->
            val text = hit.row.searchText.orEmpty()
            val matched = if (hit.firstMatch.last < text.length) text.substring(hit.firstMatch) else ""
            val inReasoning = hit.row.visibleLength?.let { hit.firstMatch.first >= it } ?: false
            LocalRow(hit.row, listOf(matched).filter { it.isNotEmpty() }, inReasoning)
        }
        return LocalRead(rows, ScanProgress(result.scanned, searchDao.countForAccount(request.accountId), result.next))
    }

    private suspend fun ftsRows(request: SearchRequest, match: String): List<MessageSearchRow> {
        val options = request.options
        val oldest = options.sort == SearchSort.Oldest
        if (!options.filtersRows) {
            return if (oldest) {
                searchDao.searchOldest(request.accountId, match, request.limit)
            } else {
                searchDao.search(request.accountId, match, request.limit)
            }
        }
        val isUser = when (options.role) {
            RoleFilter.Any -> null
            RoleFilter.User -> true
            RoleFilter.Assistant -> false
        }
        val since = options.since ?: 0L
        val excludeArchived = !options.includeArchived
        return if (oldest) {
            searchDao.searchFilteredOldest(
                request.accountId, match, since, isUser, options.model, options.endpoint, excludeArchived, request.limit,
            )
        } else {
            searchDao.searchFiltered(
                request.accountId, match, since, isUser, options.model, options.endpoint, excludeArchived, request.limit,
            )
        }
    }

    private suspend fun titlesFor(accountId: String, conversationIds: List<String>): Map<String, ConversationTitle> =
        conversationIds.distinct().chunked(TITLE_CHUNK)
            .flatMap { conversationDao.titlesFor(accountId, it) }
            .associateBy { it.conversationId }

    /**
     * Why the server can't be asked, or null when it can. Connectivity comes first: offline, the
     * capability may be unknown or stale, and only "offline" leads to a retry once it's back.
     * [unknownBlocks] false lets a not-yet-probed server through, for the caller to wait on.
     */
    private suspend fun remoteBlocker(unknownBlocks: Boolean = true): RemoteSkipReason? {
        // An unanswered connectivity flow must not hold the local results back.
        val online = withTimeoutOrNull(CONNECTIVITY_WAIT) { connectivity.isConnected.first() } == true
        if (!online) return RemoteSkipReason.Offline
        return when (capability.serverSearchEnabled.first()) {
            true -> null
            null -> if (unknownBlocks) RemoteSkipReason.ServerOff else null
            false -> RemoteSkipReason.ServerOff
        }
    }

    private fun MessageSearchRow.toHit(
        accountId: String,
        title: ConversationTitle?,
        terms: List<String>,
        wordStarts: Boolean,
        lastIsPrefix: Boolean,
        inReasoning: Boolean,
        options: SearchOptions,
    ) = MessageSearchHit(
        accountId = accountId,
        messageId = messageId,
        conversationId = conversationId,
        conversationTitle = title?.title,
        createdAt = createdAt,
        isCreatedByUser = isCreatedByUser,
        model = model,
        endpoint = endpoint,
        snippet = HighlightedSnippet.build(
            searchText.orEmpty(),
            terms,
            wordStartsOnly = wordStarts,
            matchCase = options.matchCase && !options.regex,
            wholeWord = options.wholeWord && !options.regex,
            lastIsPrefix = lastIsPrefix,
        ),
        source = HitSource.Local,
        inReasoning = inReasoning,
        findText = terms.firstOrNull().takeIf { options.regex },
    )

    private fun Message.toHit(
        accountId: String,
        text: String,
        terms: List<String>,
        inReasoning: Boolean,
        options: SearchOptions,
    ) = MessageSearchHit(
        accountId = accountId,
        messageId = messageId,
        conversationId = conversationId,
        conversationTitle = title,
        createdAt = createdAt?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() },
        isCreatedByUser = isCreatedByUser,
        model = model,
        endpoint = endpoint,
        snippet = HighlightedSnippet.build(text, terms, matchCase = options.matchCase, wholeWord = options.wholeWord),
        source = HitSource.Remote,
        inReasoning = inReasoning,
    )

    private fun checkedText(text: String, visibleLength: Int?, options: SearchOptions): String =
        if (options.visibleTextOnly) visibleTextOf(text, visibleLength) else text

    /** With no case or whole-word rule, "visible text only" still needs every term in the reply. */
    private fun visibleOnlyCheck(terms: List<String>, options: SearchOptions): ((String) -> Boolean)? =
        if (!options.visibleTextOnly) null else { text -> terms.all { text.contains(it, ignoreCase = true) } }

    private fun onlyInReasoning(text: String, visibleLength: Int?, terms: List<String>): Boolean {
        if (visibleLength == null || terms.isEmpty()) return false
        val visible = visibleTextOf(text, visibleLength)
        return terms.none { visible.contains(it, ignoreCase = true) }
    }

    private fun LocalPlan.highlightTerms(): List<String> = when (this) {
        is LocalPlan.Fts -> match.terms
        is LocalPlan.Literal -> listOf(text)
        is LocalPlan.Regex, is LocalPlan.Rejected, LocalPlan.Empty -> emptyList()
    }

    /** Counts and timings only: the diagnostic log is user-shareable, so no query or hit text. */
    private fun logSearch(started: TimeMark, hits: Int, source: String, plan: LocalPlan) {
        Diag.d(
            "Search",
            origin = LogOrigin.CLIENT,
            attrs = mapOf(
                "source" to source,
                "hits" to hits.toString(),
                "ms" to started.elapsedNow().inWholeMilliseconds.toString(),
                "plan" to if (plan is LocalPlan.Fts) "fts" else "scan",
            ),
        ) { "Message search" }
    }

    private companion object {
        /** Our own throttle: upstream has no rate limit on this route, so the client is the limit. */
        const val MIN_REMOTE_CHARS = 3
        const val TITLE_CHUNK = 400
        const val FILTER_VALUE_LIMIT = 50
        val CONNECTIVITY_WAIT = 500.milliseconds

        /** How long a search waits for the first capability probe before calling the server off. */
        val PROBE_WAIT = 2.seconds

        /** The first release whose search route honours `pageSize`. */
        const val PAGE_SIZE_SINCE = "0.8.8-rc2"
    }
}

/**
 * The last few server answers, so backspacing over a query doesn't fetch it again. Keyed by
 * account and query, and an entry for another account is dropped on the next lookup, so a switch
 * clears it. Entries expire after a minute; local hits cover anything newer.
 *
 * An entry remembers the page size it was fetched with and serves a request for that many or
 * fewer, so the Search screen's answer serves the drawer but the drawer's never serves the screen.
 */
internal class RemoteSearchCache(private val clock: Clock) {
    private class Entry(val at: Instant, val pageSize: Int, val messages: List<Message>)

    private val lock = Mutex()
    private val entries = LinkedHashMap<Pair<String, String>, Entry>()

    suspend fun get(accountId: String, query: String, pageSize: Int): List<Message>? = lock.withLock {
        entries.keys.removeAll { it.first != accountId }
        val key = accountId to query.trim()
        val entry = entries.remove(key) ?: return@withLock null
        if (clock.now() - entry.at > TTL) return@withLock null
        entries[key] = entry
        entry.messages.takeIf { entry.pageSize >= pageSize }?.take(pageSize)
    }

    suspend fun put(accountId: String, query: String, pageSize: Int, messages: List<Message>) = lock.withLock {
        val key = accountId to query.trim()
        // A smaller answer never replaces a larger one that is still fresh.
        val kept = entries[key]?.takeIf { it.pageSize > pageSize && clock.now() - it.at <= TTL }
        entries.remove(key)
        entries[key] = kept ?: Entry(clock.now(), pageSize, messages)
        while (entries.size > CAPACITY) entries.remove(entries.keys.first())
    }

    private companion object {
        const val CAPACITY = 16
        val TTL = 60.seconds
    }
}
