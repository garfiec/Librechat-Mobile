package com.garfiec.librechat.feature.conversations.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.accountTransitions
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.mapper.LegacyThinking
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.data.repository.SearchRepository
import com.garfiec.librechat.core.data.search.FtsQuery
import com.garfiec.librechat.core.data.search.FtsQueryPlan
import com.garfiec.librechat.core.data.search.HighlightedSnippet
import com.garfiec.librechat.core.data.search.HitSource
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.core.data.search.MessageSearchResult
import com.garfiec.librechat.core.data.search.RegexCheck
import com.garfiec.librechat.core.data.search.RegexGuard
import com.garfiec.librechat.core.data.search.RemoteSearchStatus
import com.garfiec.librechat.core.data.search.RoleFilter
import com.garfiec.librechat.core.data.search.ScanCursor
import com.garfiec.librechat.core.data.search.SearchOptions
import com.garfiec.librechat.core.data.search.SearchSort
import com.garfiec.librechat.core.data.search.SearchSource
import com.garfiec.librechat.core.data.search.TitleMatch
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.flattenSearchableText
import com.garfiec.librechat.feature.conversations.drawer.DrawerSearchSources
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * The full Search screen: one list of conversations, each with its title marked when it matched
 * and its message hits nested under it, plus a preview of the selected row on wide layouts.
 *
 * Results are kept only while they belong to the active account. A result tagged with another
 * account is dropped, and an account change clears the query, results and selection, so nothing
 * computed for one account is shown, or opened, under another.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    initialQuery: String,
    private val sources: DrawerSearchSources,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val agentRepository: AgentRepository,
    private val activeAccountProvider: ActiveAccountProvider,
    private val defaultDispatcher: CoroutineDispatcher,
    preferenceStore: PreferenceStore,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState(query = initialQuery))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val query = MutableStateFlow(initialQuery)
    private val options = MutableStateFlow(SearchOptions.Default)

    /** Advanced options apply only while the setting is on; turning it off restores a plain search. */
    private val advancedEnabled = preferenceStore.observe(AppPreferences.AdvancedSearch)

    /** Where "Scan more" resumes; set by the latest scan-engine result. */
    private val scanFrom = MutableStateFlow<ScanCursor?>(null)
    private var previewJob: Job? = null

    /** The query and options the shown preview marks its matches for; a change rebuilds it. */
    private var previewMarkedFor: Pair<String, SearchOptions>? = null

    init {
        viewModelScope.launch {
            advancedEnabled.collect { enabled ->
                _state.update { it.copy(advancedEnabled = enabled) }
                // The wide layout's model chip reads these without ever opening the filters sheet.
                if (enabled) loadFilterValues()
            }
        }
        viewModelScope.launch {
            val effectiveOptions = combine(advancedEnabled, options) { enabled, chosen ->
                if (enabled) chosen else SearchOptions.Default
            }
            query
                .map { it.trim() }
                .debounce(DEBOUNCE_MS)
                .combine(effectiveOptions) { text, chosen -> text to chosen }
                .distinctUntilChanged()
                .flatMapLatest { (text, chosen) ->
                    scanFrom.value = null
                    if (isEmptyQuery(text, chosen)) {
                        flowOf(null to TitleMatches.None)
                    } else {
                        // Separate inputs, so the server's title search never holds back message hits.
                        combine(resultsFor(text, chosen), titleMatchesFor(text, chosen)) { result, chats -> result to chats }
                    }
                }
                .collect { (result, chats) -> apply(result, chats) }
        }
        viewModelScope.launch {
            activeAccountProvider.accountTransitions().collect { clear() }
        }
    }

    fun onQueryChange(text: String) {
        query.value = text
        _state.update { it.copy(query = text) }
    }

    fun onScopeChange(scope: SearchScope) {
        _state.update { it.copy(scope = scope) }
    }

    fun onOptionsChange(transform: (SearchOptions) -> SearchOptions) {
        val next = transform(options.value)
        options.value = next
        _state.update { it.copy(options = next) }
    }

    fun onDateRangeChange(range: DateRange) {
        onOptionsChange { it.copy(since = range.days?.let { days -> clock.now().toEpochMilliseconds() - days * DAY_MS }) }
        _state.update { it.copy(dateRange = range) }
    }

    fun onGroupingChange(grouping: SearchGrouping) {
        _state.update { it.copy(grouping = grouping) }
    }

    fun openFilters() {
        _state.update { it.copy(filtersOpen = true) }
        loadFilterValues()
    }

    private fun loadFilterValues() {
        viewModelScope.launch {
            val values = sources.messages.filterValues()
            _state.update { it.copy(filterValues = values) }
            // Agent chats list the agent's id as their model; the filter shows its name.
            values.models.forEach(::resolveAgentName)
        }
    }

    /** Back to the default filters and layout; the text toggles stay as they are. */
    fun resetFilters() {
        onOptionsChange { SearchOptions.Default.copy(matchCase = it.matchCase, wholeWord = it.wholeWord, regex = it.regex) }
        _state.update { it.copy(dateRange = DateRange.AnyTime, grouping = SearchGrouping.ByConversation) }
    }

    fun closeFilters() {
        _state.update { it.copy(filtersOpen = false) }
    }

    fun scanMore() {
        _state.value.scan?.resumeFrom?.let { scanFrom.value = it }
    }

    fun select(messageId: String) {
        val hit = _state.value.orderedHits.firstOrNull { it.messageId == messageId } ?: return
        _state.update { it.copy(selectedMessageId = messageId, selectedConversationId = null, chatPreview = null) }
        loadPreview(hit)
    }

    /** Selects a conversation header for the preview pane; a phone selects it too as it opens the chat. */
    fun selectConversation(conversationId: String) {
        val group = _state.value.groups.firstOrNull { it.conversationId == conversationId } ?: return
        _state.update {
            it.copy(
                selectedConversationId = conversationId,
                selectedMessageId = null,
                preview = null,
                chatPreview = SearchChatPreview(group),
            )
        }
        loadChatPreview(group)
    }

    /** A selected hit inside a folded group moves to its header. */
    fun toggleCollapsed(conversationId: String) {
        _state.update { current ->
            val folding = conversationId !in current.collapsed
            val next = if (folding) current.collapsed + conversationId else current.collapsed - conversationId
            current.copy(collapsed = next)
        }
        val state = _state.value
        val selectedHit = state.preview?.hit
        if (conversationId in state.collapsed && selectedHit?.conversationId == conversationId) selectConversation(conversationId)
    }

    /** Selects the first row when nothing is selected, so a two-pane preview is never blank. */
    fun ensureSelection() {
        val state = _state.value
        if (state.selectedMessageId != null || state.selectedConversationId != null) return
        when (val first = state.entries.firstOrNull { it is SearchEntry.Hit } ?: state.entries.firstOrNull()) {
            is SearchEntry.Conversation -> selectConversation(first.conversationId)
            is SearchEntry.Hit -> select(first.hit.messageId)
            null -> Unit
        }
    }

    /** Moves the selection [delta] rows through the whole list (↑/↓), headers included. */
    fun moveSelection(delta: Int) {
        val entries = _state.value.entries
        if (entries.isEmpty()) return
        val current = entries.indexOfFirst { it.isSelectedIn(_state.value) }
        val next = if (current < 0) 0 else (current + delta).coerceIn(0, entries.lastIndex)
        when (val entry = entries[next]) {
            is SearchEntry.Conversation -> selectConversation(entry.conversationId)
            is SearchEntry.Hit -> select(entry.hit.messageId)
        }
    }

    private fun SearchEntry.isSelectedIn(state: SearchUiState): Boolean = when (this) {
        is SearchEntry.Conversation -> conversationId == state.selectedConversationId
        is SearchEntry.Hit -> hit.messageId == state.selectedMessageId
    }

    /** Steps within the selected hit's conversation (`‹ ›` in the preview). */
    fun stepInConversation(delta: Int) {
        val preview = _state.value.preview ?: return
        val group = _state.value.groups.firstOrNull { it.conversationId == preview.hit.conversationId } ?: return
        val next = (preview.index + delta).coerceIn(0, group.hits.lastIndex)
        select(group.hits[next].messageId)
    }

    /** The hit to open, or null when it was found for an account that is no longer active. */
    fun openable(hit: MessageSearchHit): MessageSearchHit? =
        hit.takeIf { it.accountId == activeAccountProvider.currentAccountId()?.value }

    /** The conversation to open, or null when its group was computed for another account. */
    fun openableConversation(conversationId: String): String? =
        _state.value.groups.firstOrNull { it.conversationId == conversationId }
            ?.takeIf { it.accountId == activeAccountProvider.currentAccountId()?.value }
            ?.conversationId

    private fun apply(result: MessageSearchResult?, titles: TitleMatches) {
        if (result == null) {
            // An emptied query clears what the last search reported too, or its notes outlive it.
            _state.update {
                it.copy(
                    groups = emptyList(),
                    messageCount = 0,
                    remoteCapped = false,
                    cappedAt = null,
                    shortPrefix = null,
                    scan = null,
                    indexIncomplete = false,
                    regexProblem = null,
                    remoteSkipped = null,
                    remoteStatus = RemoteSearchStatus.Unavailable,
                    searched = false,
                    preview = null,
                    selectedMessageId = null,
                    selectedConversationId = null,
                    chatPreview = null,
                )
            }
            return
        }
        if (result.accountId != activeAccountProvider.currentAccountId()?.value) return
        val chosen = if (_state.value.advancedEnabled) options.value else SearchOptions.Default
        val mostHits = chosen.sort == SearchSort.MostHits
        val groups = merge(result.accountId, group(result.hits), titles, chosen).let { merged ->
            if (mostHits) byMostHits(merged) else merged
        }
        _state.update { current ->
            current.copy(regexProblem = result.regexProblem, remoteSkipped = result.remoteSkipped)
        }
        _state.update { current ->
            // A server hit is missing from the local emission that comes first, so a selection waits
            // for the server's answer before it is dropped: otherwise a toggled option (Match case)
            // falls back to the first row before the selected hit lands again.
            val selected = current.selectedMessageId?.takeIf { id ->
                result.hits.any { it.messageId == id } || result.remoteStatus == RemoteSearchStatus.Searching
            }
            current.copy(
                groups = groups,
                flatHits = if (_state.value.advancedEnabled && options.value.sort == SearchSort.MostHits) {
                    groups.flatMap { it.hits }
                } else {
                    result.hits
                },
                messageCount = result.hits.size,
                remoteCapped = result.remoteCapped,
                shortPrefix = shortPrefixOf(result.query, chosen),
                searched = true,
                remoteStatus = result.remoteStatus,
                scan = result.scan,
                indexIncomplete = result.indexIncomplete,
                cappedAt = result.hits.count { it.source == HitSource.Local }.takeIf { result.localCapped },
                selectedMessageId = selected,
                // Server hits land after the first emission, so the hit's place in its chat moves.
                preview = current.preview?.takeIf { selected != null }?.let { preview ->
                    val group = groups.firstOrNull { it.conversationId == preview.hit.conversationId }
                    val hits = group?.hits.orEmpty()
                    val index = hits.indexOfFirst { it.messageId == preview.hit.messageId }
                    val placed = if (index < 0) preview else preview.copy(index = index, count = hits.size)
                    placed.copy(titleHighlights = group?.titleHighlights)
                },
                selectedConversationId = current.selectedConversationId?.takeIf { id -> groups.any { it.conversationId == id } },
                collapsed = if (current.searchedQuery == result.query) current.collapsed else emptySet(),
                searchedQuery = result.query,
                chatPreview = current.chatPreview?.let { preview ->
                    groups.firstOrNull { it.conversationId == preview.group.conversationId }?.let { preview.copy(group = it) }
                },
            )
        }
        if (previewMarkedFor != null && previewMarkedFor != (result.query to chosen)) {
            val state = _state.value
            val hit = state.selectedMessageId?.let { id -> result.hits.firstOrNull { it.messageId == id } }
            val group = state.selectedConversationId?.let { id -> groups.firstOrNull { it.conversationId == id } }
            when {
                hit != null -> loadPreview(hit)
                group != null -> loadChatPreview(group)
            }
        }
    }

    /**
     * The message search for [text]. A "Scan more" continuation returns only what it found past
     * the cursor, so it is added to what the scan had already found rather than replacing it.
     */
    private fun resultsFor(text: String, chosen: SearchOptions): Flow<MessageSearchResult> {
        var latest: MessageSearchResult? = null
        return scanFrom.flatMapLatest { cursor ->
            val earlier = latest.takeIf { cursor != null }
            sources.messages.search(text, LIMIT, cursor, chosen).map { page ->
                (earlier?.extendedBy(page) ?: page).also { latest = it }
            }
        }
    }

    /**
     * Cached conversations whose title matches, then again with the server's conversation search
     * once it answers. The server returns every conversation whose title **or any message**
     * matched, with nothing to say which, so [merge] decides by matching the title itself.
     */
    private fun titleMatchesFor(text: String, chosen: SearchOptions): Flow<TitleMatches> =
        // A title has no author and no reasoning, so a role or answers-only filter can't admit one.
        if (chosen.role != RoleFilter.Any || chosen.visibleTextOnly) {
            flowOf(TitleMatches(query = text, cached = emptyList(), server = emptyList(), options = chosen))
        } else {
            flow {
                val cached = if (chosen.source == SearchSource.Server) {
                    emptyList()
                } else {
                    val archived = if (chosen.includeArchived) cachedConversations(isArchived = true) else emptyList()
                    (cachedConversations(isArchived = false) + archived)
                        .filter { chosen.admitsConversation(it) && TitleMatch.matches(it.title, text, chosen) }
                }
                emit(TitleMatches(query = text, cached = cached, server = emptyList(), options = chosen))
                // The server can't run a pattern.
                val askServer = text.length >= MIN_REMOTE_CHARS && chosen.source != SearchSource.Cached && !chosen.regex
                // Every page, as the web sidebar lists them: the first holds only the newest few.
                val remote = if (askServer) {
                    sources.remoteTitles(text, SearchRepository.ALL_MATCHES).orEmpty().filter { chosen.admitsConversation(it) }
                } else {
                    emptyList()
                }
                if (remote.isNotEmpty()) emit(TitleMatches(query = text, cached = cached, server = remote, options = chosen))
            }
        }

    private suspend fun cachedConversations(isArchived: Boolean): List<Conversation> =
        (conversationRepository.observeConversations(isArchived).first() as? Result.Success)?.data.orEmpty()

    /**
     * Loads the selected conversation's thread: through its first hit, else its newest one. A
     * conversation only the server matched is fetched first.
     */
    private fun loadChatPreview(group: SearchResultGroup) {
        previewJob?.cancel()
        previewMarkedFor = markedQuery to effectiveOptions()
        val marks = previewMarksFor(markedQuery, effectiveOptions())
        val chosen = effectiveOptions()
        val firstHit = group.hits.firstOrNull()?.messageId
        if (group.fromServer) {
            _state.update { current -> current.copy(chatPreview = current.chatPreview?.copy(load = ThreadLoad.Loading)) }
        }
        previewJob = viewModelScope.launch {
            val conversation = (conversationRepository.observeConversations().first() as? Result.Success)
                ?.data?.firstOrNull { it.conversationId == group.conversationId }
            resolveAgentName(conversation?.model)
            val messages = messageRepository.conversationMessages(group.conversationId, firstHit, fetch = group.fromServer)
            if (openableConversation(group.conversationId) == null) return@launch
            val turns = withContext(defaultDispatcher) {
                messages?.let { all ->
                    val thread = firstHit?.let { PreviewThread.of(all, it) }?.ifEmpty { null } ?: PreviewThread.latest(all)
                    turnsOf(thread, hit = null, marks, chosen)
                }
            }
            val focus = turns.orEmpty().indexOfFirst { it.messageId == firstHit }.takeIf { it >= 0 }
                ?: turns.orEmpty().indexOfFirst { it.snippet.highlights.isNotEmpty() }.coerceAtLeast(0)
            _state.update { current ->
                val preview = current.chatPreview
                if (current.selectedConversationId != group.conversationId || preview == null) {
                    current
                } else {
                    current.copy(
                        chatPreview = preview.copy(
                            model = conversation?.model,
                            updatedAt = conversation?.updatedAt?.toEpochMilliseconds(),
                            thread = turns.orEmpty(),
                            focusIndex = focus,
                            // Nothing readable either way: the notice says to open the chat, not a blank pane.
                            load = if (turns.isNullOrEmpty()) ThreadLoad.Failed else ThreadLoad.Ready,
                        ),
                    )
                }
            }
        }
    }

    /**
     * An agent chat's model is the agent's id (`agent_…`); its name is looked up once, from the
     * agent list the repository caches, for the preview header and the Model filter to show instead.
     */
    private fun resolveAgentName(model: String?) {
        if (model == null || !model.startsWith(AGENT_ID_PREFIX) || model in _state.value.agentNames) return
        viewModelScope.launch {
            val name = (agentRepository.getAgent(model) as? Result.Success)?.data?.name?.takeIf { it.isNotBlank() } ?: return@launch
            _state.update { it.copy(agentNames = it.agentNames + (model to name)) }
        }
    }

    /** The query the list's results are for, which a preview marks; the typed text until one lands. */
    private val markedQuery: String get() = _state.value.searchedQuery ?: _state.value.query

    private fun effectiveOptions(): SearchOptions = if (_state.value.advancedEnabled) options.value else SearchOptions.Default

    private fun loadPreview(hit: MessageSearchHit) {
        previewJob?.cancel()
        resolveAgentName(hit.model)
        previewMarkedFor = markedQuery to effectiveOptions()
        val group = _state.value.groups.firstOrNull { it.conversationId == hit.conversationId }
        val index = group?.hits?.indexOfFirst { it.messageId == hit.messageId } ?: 0
        val count = group?.hits?.size ?: 1
        val remote = hit.source == HitSource.Remote
        _state.update { current ->
            // Stepping within one conversation keeps its thread on screen, so the pane scrolls to
            // the next hit instead of blanking while the thread is rebuilt.
            val kept = current.preview?.takeIf { it.hit.conversationId == hit.conversationId }?.thread.orEmpty()
            val keptIndex = kept.indexOfFirst { it.messageId == hit.messageId }
            current.copy(
                preview = SearchPreview(
                    hit = hit,
                    index = index,
                    count = count,
                    thread = if (keptIndex >= 0) kept else emptyList(),
                    hitIndex = keptIndex.coerceAtLeast(0),
                    titleHighlights = group?.titleHighlights,
                    load = if (remote && keptIndex < 0) ThreadLoad.Loading else ThreadLoad.Ready,
                ),
            )
        }
        val marks = previewMarksFor(markedQuery, effectiveOptions())
        val chosen = effectiveOptions()
        previewJob = viewModelScope.launch {
            val messages = messageRepository.conversationMessages(hit.conversationId, hit.messageId, fetch = remote)
            // The account may have switched while the conversation was fetched.
            if (openable(hit) == null) return@launch
            val turns = withContext(defaultDispatcher) {
                messages?.let { turnsOf(PreviewThread.of(it, hit.messageId), hit, marks, chosen) }
            }
            _state.update { current ->
                // The position may have moved while this loaded (server hits merged in): keep the current one.
                val shown = current.preview
                if (current.selectedMessageId != hit.messageId || shown == null) {
                    current
                } else {
                    current.copy(
                        preview = shown.copy(
                            thread = turns.orEmpty(),
                            hitIndex = turns.orEmpty().indexOfFirst { it.messageId == hit.messageId }.coerceAtLeast(0),
                            load = if (turns == null) ThreadLoad.Failed else ThreadLoad.Ready,
                        ),
                    )
                }
            }
        }
    }

    private fun clear() {
        previewJob?.cancel()
        previewMarkedFor = null
        query.value = ""
        scanFrom.value = null
        options.value = SearchOptions.Default
        _state.value = SearchUiState(advancedEnabled = _state.value.advancedEnabled)
        if (_state.value.advancedEnabled) loadFilterValues()
    }

    /** A regex is searched as typed; anything else needs enough text for the index (or a scan). */
    private fun isEmptyQuery(text: String, chosen: SearchOptions): Boolean =
        if (chosen.regex) text.isEmpty() else FtsQuery.plan(text) == FtsQueryPlan.Empty

    internal companion object {
        const val DEBOUNCE_MS = 300L
        const val LIMIT = 200
        const val MIN_REMOTE_CHARS = 3
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** Enough of a message to read it in the preview, still bounded for pasted logs. */
        const val PREVIEW_WINDOW = 2_000

        const val AGENT_ID_PREFIX = "agent_"

        /**
         * A thread's turns with matches marked; [hit]'s reasoning is shown when the match is in it.
         * A turn with nothing to read (a message that only called tools) is left out, unless it is
         * the hit, rather than shown as an empty bubble.
         */
        fun turnsOf(
            thread: List<Message>,
            hit: MessageSearchHit?,
            marks: PreviewMarks,
            chosen: SearchOptions,
        ): List<PreviewTurn> = thread.mapNotNull { message ->
            val isHit = message.messageId == hit?.messageId
            PreviewTurn(
                messageId = message.messageId,
                snippet = snippetOf(message.answerText(), marks, PREVIEW_WINDOW, chosen),
                isUser = message.isCreatedByUser,
                reasoning = message.takeIf { isHit && hit?.inReasoning == true }?.reasoningText()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { snippetOf(it, marks, PREVIEW_WINDOW, chosen) },
            ).takeIf { isHit || it.snippet.text.isNotBlank() || it.reasoning != null }
        }

        fun previewMarksFor(query: String, chosen: SearchOptions): PreviewMarks {
            if (chosen.regex) {
                val pattern = (RegexGuard.compile(query.trim(), chosen.matchCase) as? RegexCheck.Ok)?.regex
                return PreviewMarks(terms = emptyList(), lastIsPrefix = false, pattern = pattern)
            }
            val plan = FtsQuery.plan(query, prefixLastToken = !chosen.wholeWord) as? FtsQueryPlan.Match
            return PreviewMarks(terms = plan?.terms ?: listOf(query.trim()), lastIsPrefix = plan?.lastIsPrefix ?: false)
        }

        /** A preview snippet, marked under the same case and whole-word rules as the list. */
        fun snippetOf(text: String, marks: PreviewMarks, window: Int, options: SearchOptions): HighlightedSnippet =
            marks.pattern?.let { HighlightedSnippet.buildPattern(text, it, window) }
                ?: HighlightedSnippet.build(
                    text,
                    marks.terms,
                    window,
                    matchCase = options.matchCase,
                    wholeWord = options.wholeWord,
                    lastIsPrefix = marks.lastIsPrefix,
                )

        /** What the reader sees in the reply: the text without the reasoning. */
        fun Message.answerText(): String =
            content?.flattenSearchableText(includeSteer = true, skipReasoning = true)?.ifBlank { null }
                ?: LegacyThinking.split(text)?.answer
                ?: text

        fun Message.reasoningText(): String =
            content?.filter { it.type == ContentType.THINK }?.flattenSearchableText()?.ifBlank { null }
                ?: LegacyThinking.split(text)?.reasoning.orEmpty()

        /** The text the index matched against, so the preview marks the same words. */
        fun Message.searchableText(): String =
            content?.flattenSearchableText(includeSteer = true)?.ifBlank { text } ?: text

        /**
         * Conversations with the most matching messages first, local and server groups each ranked
         * on their own so the "From server" divider still separates them. Ties keep their order.
         */
        fun byMostHits(groups: List<SearchResultGroup>): List<SearchResultGroup> {
            val (server, local) = groups.partition { it.fromServer }
            return local.sortedByDescending { it.hits.size } + server.sortedByDescending { it.hits.size }
        }

        /**
         * Folds title matches into the message groups. A group whose title matches is marked and
         * moves ahead of the groups that only matched in messages (local and server each keep
         * their own block, so the "From server" divider still separates them). A title match with
         * no message hits becomes a group of its own. A server conversation whose title doesn't
         * match was found by a message: dropped when its hits are already listed, otherwise kept
         * as a [SearchResultGroup.serverBodyMatch].
         */
        fun merge(
            accountId: String,
            groups: List<SearchResultGroup>,
            titles: TitleMatches,
            options: SearchOptions = titles.options,
        ): List<SearchResultGroup> {
            val byId = groups.associateBy { it.conversationId }
            val cachedIds = titles.cached.mapNotNullTo(HashSet()) { it.conversationId }
            val bodyMatches = admitsBodyMatches(titles.query, options)
            val marked = groups.map { group ->
                val title = group.title ?: titles.titleOf(group.conversationId)
                group.copy(
                    accountId = accountId,
                    title = title,
                    titleHighlights = TitleMatch.of(title, titles.query, options),
                    // Server hits carry no date; the server's conversation does, which tells two
                    // chats with one title apart.
                    newestAt = group.newestAt ?: titles.updatedAtOf(group.conversationId),
                )
            }
            val titleOnly = (titles.cached.map { it to false } + titles.server.map { it to true })
                .filter { (conversation, _) -> conversation.conversationId != null && conversation.conversationId !in byId }
                .distinctBy { (conversation, _) -> conversation.conversationId }
                .mapNotNull { (conversation, fromServer) ->
                    val id = conversation.conversationId ?: return@mapNotNull null
                    val highlights = TitleMatch.of(conversation.title, titles.query, options)
                    when {
                        highlights != null -> conversation.toGroup(accountId, fromServer = fromServer && id !in cachedIds, highlights)
                        fromServer && bodyMatches -> conversation.toGroup(accountId, fromServer = true, highlights = null)
                            .copy(serverBodyMatch = true)
                        else -> null
                    }
                }
            val all = marked + titleOnly
            val (server, local) = all.partition { it.fromServer }
            return local.titleMatchesFirst() + server.titleMatchesFirst()
        }

        /**
         * Whether a server conversation found only by a message (no text to check) may be listed.
         * Not when the server's message hits were skipped (a date filter, a pattern, cached only),
         * nor under a filter or rule that the missing text can't be checked against, nor for more
         * than one word, since the server's engine matches any one of them.
         */
        fun admitsBodyMatches(query: String, options: SearchOptions): Boolean {
            val words = (FtsQuery.plan(query) as? FtsQueryPlan.Match)?.terms?.size ?: return false
            return words == 1 && options.since == null && !options.regex && options.source != SearchSource.Cached &&
                options.role == RoleFilter.Any && options.model == null && options.endpoint == null &&
                !options.matchCase && !options.wholeWord && !options.visibleTextOnly
        }

        /**
         * A two-letter word, while typed, matches only whole words in messages (a two-letter prefix
         * would expand to most of the index), while titles already take it as a prefix: the word, to
         * say one more letter widens the search, or null.
         */
        fun shortPrefixOf(query: String, options: SearchOptions): String? {
            if (options.regex || options.wholeWord) return null
            val plan = FtsQuery.plan(query) as? FtsQueryPlan.Match ?: return null
            val last = plan.terms.last()
            return last.takeIf { plan.typingLast && it.length < FtsQuery.MIN_PREFIX_CHARS }
        }

        private fun List<SearchResultGroup>.titleMatchesFirst(): List<SearchResultGroup> {
            val (titled, rest) = partition { it.titleMatched }
            return titled + rest
        }

        private fun Conversation.toGroup(accountId: String, fromServer: Boolean, highlights: List<IntRange>?) =
            SearchResultGroup(
                conversationId = conversationId.orEmpty(),
                title = title,
                hits = emptyList(),
                fromServer = fromServer,
                accountId = accountId,
                titleHighlights = highlights,
                newestAt = updatedAt?.toEpochMilliseconds(),
            )

        /**
         * This scan result with [page], its "Scan more" continuation, added: local hits in scan
         * order (the scan walks in the sort's direction, so a later page follows), then the
         * server's, each listed once, and the scanned counts summed.
         */
        fun MessageSearchResult.extendedBy(page: MessageSearchResult): MessageSearchResult {
            if (page.accountId != accountId || page.query != query) return page
            val (earlierLocal, earlierRemote) = hits.partition { it.source == HitSource.Local }
            val (pageLocal, pageRemote) = page.hits.partition { it.source == HitSource.Local }
            val local = (earlierLocal + pageLocal).distinctBy { it.messageId }
            val localIds = local.mapTo(HashSet()) { it.messageId }
            val remote = (earlierRemote + pageRemote).distinctBy { it.messageId }.filter { it.messageId !in localIds }
            val earlierScanned = scan?.scanned ?: 0
            return page.copy(
                hits = local + remote,
                scan = page.scan?.let { progress -> progress.copy(scanned = earlierScanned + progress.scanned) },
            )
        }

        /** Groups hits by conversation, keeping the order hits arrived in (local first). */
        fun group(hits: List<MessageSearchHit>): List<SearchResultGroup> =
            hits.groupBy { it.conversationId }.map { (conversationId, inConversation) ->
                SearchResultGroup(
                    conversationId = conversationId,
                    title = inConversation.firstNotNullOfOrNull { it.conversationTitle },
                    hits = inConversation,
                    fromServer = inConversation.all { it.source == HitSource.Remote },
                    newestAt = inConversation.mapNotNull { it.createdAt }.maxOrNull(),
                )
            }
    }
}

data class TitleMatches(
    val query: String,
    val cached: List<Conversation>,
    /** Conversations whose title **or a message** matched on the server. */
    val server: List<Conversation>,
    val options: SearchOptions = SearchOptions.Default,
) {
    fun titleOf(conversationId: String): String? = find(conversationId)?.title

    fun updatedAtOf(conversationId: String): Long? = find(conversationId)?.updatedAt?.toEpochMilliseconds()

    private fun find(conversationId: String): Conversation? =
        cached.firstOrNull { it.conversationId == conversationId } ?: server.firstOrNull { it.conversationId == conversationId }

    companion object {
        val None = TitleMatches("", emptyList(), emptyList())
    }
}

/**
 * Whether a conversation passes the filters that describe a conversation: its last update for a
 * date filter, its current model and endpoint. Applied to title matches, which have no message.
 */
internal fun SearchOptions.admitsConversation(conversation: Conversation): Boolean =
    since.let { from -> from == null || (conversation.updatedAt?.toEpochMilliseconds() ?: 0L) >= from } &&
        (model == null || conversation.model == model) &&
        (endpoint == null || conversation.endpoint == endpoint)

/**
 * What a preview marks: the query's words under the index's rule ([lastIsPrefix] for the one still
 * being typed), or every match of [pattern] for a pattern search.
 */
internal class PreviewMarks(val terms: List<String>, val lastIsPrefix: Boolean, val pattern: Regex? = null)
