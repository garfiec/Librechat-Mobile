package com.garfiec.librechat.web.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.search.FtsQuery
import com.garfiec.librechat.core.model.search.FtsQueryPlan
import com.garfiec.librechat.core.model.search.HighlightedSnippet
import com.garfiec.librechat.core.model.search.HitSource
import com.garfiec.librechat.core.model.search.MessageSearchHit
import com.garfiec.librechat.core.model.search.RegexCheck
import com.garfiec.librechat.core.model.search.RegexGuard
import com.garfiec.librechat.core.model.search.RegexProblem
import com.garfiec.librechat.core.model.search.RemoteSearchStatus
import com.garfiec.librechat.core.model.search.RoleFilter
import com.garfiec.librechat.core.model.search.SearchFilterValues
import com.garfiec.librechat.core.model.search.SearchOptions
import com.garfiec.librechat.core.model.search.SearchSort
import com.garfiec.librechat.core.model.search.TitleMatch
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.feature.conversations.drawer.DrawerMessageSearch
import com.garfiec.librechat.feature.conversations.search.AdvancedActions
import com.garfiec.librechat.feature.conversations.search.DateRange
import com.garfiec.librechat.feature.conversations.search.PreviewTurn
import com.garfiec.librechat.feature.conversations.search.SearchChatPreview
import com.garfiec.librechat.feature.conversations.search.SearchEntry
import com.garfiec.librechat.feature.conversations.search.SearchGrouping
import com.garfiec.librechat.feature.conversations.search.SearchPreview
import com.garfiec.librechat.feature.conversations.search.SearchResultGroup
import com.garfiec.librechat.feature.conversations.search.SearchScope
import com.garfiec.librechat.feature.conversations.search.SearchScreenContent
import com.garfiec.librechat.feature.conversations.search.SearchUiState
import com.garfiec.librechat.feature.conversations.search.ThreadLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/**
 * Where a search hit opens: its chat, scrolled to the message, with find-in-page on [query]. [visit]
 * tells two opens of the same hit apart, so the second scrolls too.
 */
data class ChatJump(val messageId: String, val query: String, val inReasoning: Boolean, val visit: Int = 0)

/**
 * The app's Search screen, `SearchScreenContent`, over the active account's sample chats, with the
 * advanced options on. [session] stands in for the ViewModel.
 */
@Composable
internal fun SearchDemo(
    session: DemoSearchSession,
    onBack: (() -> Unit)?,
    onOpenMessage: (conversationId: String, jump: ChatJump) -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by session.state.collectAsState()
    val actions = remember(session) {
        AdvancedActions(
            onOptionsChange = session::onOptionsChange,
            onDateRangeChange = session::onDateRangeChange,
            onGroupingChange = session::onGroupingChange,
            onOpenFilters = session::openFilters,
            onResetFilters = session::resetFilters,
        )
    }
    ProvideDateTimeFormat(DateTimeFormatPrefs()) {
        SearchScreenContent(
            uiState = state,
            showBackButton = onBack != null,
            actions = actions,
            onNavigateBack = { onBack?.invoke() },
            onQueryChange = session::onQueryChange,
            onScopeChange = session::onScopeChange,
            onOpenHit = { hit -> onOpenMessage(hit.conversationId, hit.jump(state.query)) },
            onOpenConversation = onOpenConversation,
            onSelectHit = session::select,
            onSelectConversation = session::selectConversation,
            onMoveSelection = session::moveSelection,
            onToggleGroup = session::toggleCollapsed,
            onScanMore = {},
            onEnsureSelection = session::ensureSelection,
            onStepInConversation = session::stepInConversation,
            onCloseFilters = session::closeFilters,
            modifier = modifier,
        )
    }
}

/** A hit's jump: a pattern's hit is found in the chat by the text it matched, quoted as one phrase. */
internal fun MessageSearchHit.jump(query: String) =
    ChatJump(messageId, findText?.let { "\"$it\"" } ?: query.trim(), inReasoning)

/** The drawer's Messages section for [query]: the first few hits and how many there are. */
internal fun drawerMessageSearch(accountId: String?, query: String): DrawerMessageSearch {
    if (accountId == null || isEmptyQuery(query.trim(), SearchOptions.Default)) return DrawerMessageSearch.Idle
    val hits = searchMessages(accountId, query.trim(), SearchOptions.Default).hits
    return DrawerMessageSearch(preview = hits.take(DrawerMessageSearch.PREVIEW_COUNT), totalCount = hits.size, searched = true)
}

/**
 * What `SearchViewModel` holds, worked out from the sample chats at once: there is no index to
 * wait on and no server to ask, so every search is complete and the server counts as answered.
 */
internal class DemoSearchSession(private val accountId: String, initialQuery: String) {
    private val _state = MutableStateFlow(SearchUiState(query = initialQuery, advancedEnabled = true))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(filterValues = filterValues(accountId)) }
        search()
    }

    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text) }
        search()
    }

    fun onScopeChange(scope: SearchScope) = _state.update { it.copy(scope = scope) }

    fun onOptionsChange(transform: (SearchOptions) -> SearchOptions) {
        _state.update { it.copy(options = transform(it.options)) }
        search()
    }

    fun onDateRangeChange(range: DateRange) {
        val since = range.days?.let { PageLoadedAt.toEpochMilliseconds() - it * DAY_MS }
        _state.update { it.copy(dateRange = range, options = it.options.copy(since = since)) }
        search()
    }

    fun onGroupingChange(grouping: SearchGrouping) = _state.update { it.copy(grouping = grouping) }

    fun openFilters() = _state.update { it.copy(filtersOpen = true) }

    fun closeFilters() = _state.update { it.copy(filtersOpen = false) }

    /** Back to the default filters and layout; the text toggles stay as they are. */
    fun resetFilters() {
        _state.update {
            it.copy(
                options = SearchOptions.Default.copy(matchCase = it.options.matchCase, wholeWord = it.options.wholeWord, regex = it.options.regex),
                dateRange = DateRange.AnyTime,
                grouping = SearchGrouping.ByConversation,
            )
        }
        search()
    }

    fun select(messageId: String) {
        val current = _state.value
        val hit = current.orderedHits.firstOrNull { it.messageId == messageId } ?: return
        val group = current.groups.firstOrNull { it.conversationId == hit.conversationId }
        val hits = group?.hits.orEmpty()
        val thread = turnsOf(hit.conversationId, hit, current.query, current.options)
        _state.update {
            it.copy(
                selectedMessageId = messageId,
                selectedConversationId = null,
                chatPreview = null,
                preview = SearchPreview(
                    hit = hit,
                    index = hits.indexOfFirst { h -> h.messageId == messageId }.coerceAtLeast(0),
                    count = hits.size.coerceAtLeast(1),
                    thread = thread,
                    hitIndex = thread.indexOfFirst { turn -> turn.messageId == messageId }.coerceAtLeast(0),
                    titleHighlights = group?.titleHighlights,
                ),
            )
        }
    }

    fun selectConversation(conversationId: String) {
        val current = _state.value
        val group = current.groups.firstOrNull { it.conversationId == conversationId } ?: return
        val chat = sampleChat(conversationId)
        val thread = turnsOf(conversationId, hit = null, current.query, current.options)
        val firstHit = group.hits.firstOrNull()?.messageId
        val focus = thread.indexOfFirst { it.messageId == firstHit }.takeIf { it >= 0 }
            ?: thread.indexOfFirst { it.snippet.highlights.isNotEmpty() }.coerceAtLeast(0)
        _state.update {
            it.copy(
                selectedConversationId = conversationId,
                selectedMessageId = null,
                preview = null,
                chatPreview = SearchChatPreview(
                    group = group,
                    model = chat?.model?.name,
                    updatedAt = chat?.let { c -> (PageLoadedAt - c.ago).toEpochMilliseconds() },
                    thread = thread,
                    focusIndex = focus,
                    // Nothing to read (a chat with no transcript): the pane says to open the chat.
                    load = if (thread.isEmpty()) ThreadLoad.Failed else ThreadLoad.Ready,
                ),
            )
        }
    }

    /** A selected hit inside a folded group moves to its header. */
    fun toggleCollapsed(conversationId: String) {
        _state.update {
            it.copy(collapsed = if (conversationId in it.collapsed) it.collapsed - conversationId else it.collapsed + conversationId)
        }
        val state = _state.value
        if (conversationId in state.collapsed && state.preview?.hit?.conversationId == conversationId) selectConversation(conversationId)
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
        val state = _state.value
        val entries = state.entries
        if (entries.isEmpty()) return
        val current = entries.indexOfFirst {
            when (it) {
                is SearchEntry.Conversation -> it.conversationId == state.selectedConversationId
                is SearchEntry.Hit -> it.hit.messageId == state.selectedMessageId
            }
        }
        when (val entry = entries[if (current < 0) 0 else (current + delta).coerceIn(0, entries.lastIndex)]) {
            is SearchEntry.Conversation -> selectConversation(entry.conversationId)
            is SearchEntry.Hit -> select(entry.hit.messageId)
        }
    }

    /** Steps within the selected hit's conversation (`‹ ›` in the preview). */
    fun stepInConversation(delta: Int) {
        val preview = _state.value.preview ?: return
        val group = _state.value.groups.firstOrNull { it.conversationId == preview.hit.conversationId } ?: return
        select(group.hits[(preview.index + delta).coerceIn(0, group.hits.lastIndex)].messageId)
    }

    private fun search() {
        val current = _state.value
        val text = current.query.trim()
        val options = current.options
        if (isEmptyQuery(text, options)) {
            _state.update {
                SearchUiState(
                    query = it.query,
                    scope = it.scope,
                    grouping = it.grouping,
                    advancedEnabled = true,
                    options = it.options,
                    dateRange = it.dateRange,
                    filtersOpen = it.filtersOpen,
                    filterValues = it.filterValues,
                )
            }
            return
        }
        val found = searchMessages(accountId, text, options)
        val groups = groupsOf(found.hits, text, options)
        _state.update {
            it.copy(
                groups = groups,
                flatHits = if (options.sort == SearchSort.MostHits) groups.flatMap { g -> g.hits } else found.hits,
                messageCount = found.hits.size,
                shortPrefix = shortPrefixOf(text, options),
                searched = true,
                remoteStatus = RemoteSearchStatus.Done,
                regexProblem = found.regexProblem,
                collapsed = if (it.searchedQuery == text) it.collapsed else emptySet(),
                searchedQuery = text,
            )
        }
        // The selection stays where it still is, its preview marked for the new query.
        val state = _state.value
        val hitId = state.selectedMessageId?.takeIf { id -> state.orderedHits.any { it.messageId == id } }
        val chatId = state.selectedConversationId?.takeIf { id -> state.groups.any { it.conversationId == id } }
        when {
            hitId != null -> select(hitId)
            chatId != null -> selectConversation(chatId)
            else -> _state.update { it.copy(selectedMessageId = null, selectedConversationId = null, preview = null, chatPreview = null) }
        }
    }

    private fun groupsOf(hits: List<MessageSearchHit>, query: String, options: SearchOptions): List<SearchResultGroup> {
        val withHits = hits.groupBy { it.conversationId }.map { (id, inChat) ->
            val title = sampleChat(id)?.title
            SearchResultGroup(
                conversationId = id,
                title = title,
                hits = inChat,
                fromServer = false,
                accountId = accountId,
                titleHighlights = TitleMatch.of(title, query, options),
                newestAt = inChat.mapNotNull { it.createdAt }.maxOrNull(),
            )
        }
        // A title has no author and no reasoning, so a role or answers-only filter can't admit one.
        val titleOnly = if (options.role != RoleFilter.Any || options.visibleTextOnly) {
            emptyList()
        } else {
            accountChats(accountId)
                .filter { chat -> withHits.none { it.conversationId == chat.id } && chat.admittedBy(options) }
                .mapNotNull { chat ->
                    val highlights = TitleMatch.of(chat.title, query, options) ?: return@mapNotNull null
                    SearchResultGroup(
                        conversationId = chat.id,
                        title = chat.title,
                        hits = emptyList(),
                        fromServer = false,
                        accountId = accountId,
                        titleHighlights = highlights,
                        newestAt = (PageLoadedAt - chat.ago).toEpochMilliseconds(),
                    )
                }
        }
        val (titled, rest) = (withHits + titleOnly).partition { it.titleMatched }
        val ordered = titled + rest
        return if (options.sort == SearchSort.MostHits) ordered.sortedByDescending { it.hits.size } else ordered
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

// ─── Searching the samples ───────────────────────────────────────────

private class SampleSearchResult(val hits: List<MessageSearchHit>, val regexProblem: RegexProblem? = null)

/**
 * One sample message as the index holds it: the reply's words apart from its reasoning, and the
 * chat's model for a message the user wrote.
 */
private class IndexedMessage(val chat: SampleChat, val message: Message, val createdAt: Long) {
    val model: String = message.model ?: chat.model.name
    val endpoint: String = message.endpoint ?: chat.model.endpoint
    val answer: String = message.content.orEmpty().filter { it.type == ContentType.TEXT }.joinToString("\n") { it.text.orEmpty() }
    val reasoning: String = message.content.orEmpty().filter { it.type == ContentType.THINK }.joinToString("\n") { it.think.orEmpty() }
}

private val indexes = HashMap<String, List<IndexedMessage>>()

private fun accountChats(accountId: String): List<SampleChat> = AccountSamples[accountId]?.chats.orEmpty()

private fun indexFor(accountId: String): List<IndexedMessage> = indexes.getOrPut(accountId) {
    accountChats(accountId).flatMap { chat ->
        chat.toMessages().map { IndexedMessage(chat, it, Instant.parse(it.createdAt.orEmpty()).toEpochMilliseconds()) }
    }
}

private fun filterValues(accountId: String): SearchFilterValues {
    val models = indexFor(accountId).map { it.chat.model }.distinct()
    return SearchFilterValues(models = models.map { it.name }.distinct().sorted(), endpoints = models.map { it.endpoint }.distinct().sorted())
}

private fun SampleChat.admittedBy(options: SearchOptions): Boolean =
    (options.since == null || (PageLoadedAt - ago).toEpochMilliseconds() >= options.since!!) &&
        (options.model == null || options.model == model.name) &&
        (options.endpoint == null || options.endpoint == model.endpoint)

/** A pattern is searched as typed; anything else needs enough text to be a word. */
private fun isEmptyQuery(text: String, options: SearchOptions): Boolean =
    if (options.regex) text.isEmpty() else FtsQuery.plan(text) == FtsQueryPlan.Empty

/**
 * The messages that match, by the index's rules: every word whole, the last one a prefix while it is
 * typed (from three letters), case and whole word as the options say, or the pattern for a regex.
 */
private fun searchMessages(accountId: String, query: String, options: SearchOptions): SampleSearchResult {
    val pattern = if (options.regex) {
        when (val check = RegexGuard.compile(query, options.matchCase)) {
            is RegexCheck.Ok -> check.regex
            is RegexCheck.Rejected -> return SampleSearchResult(emptyList(), check.problem)
        }
    } else {
        null
    }
    val plan = if (pattern == null) FtsQuery.plan(query, prefixLastToken = !options.wholeWord) else null
    val hits = indexFor(accountId)
        .filter { options.admits(it.message.isCreatedByUser, it.model, it.endpoint, it.createdAt) }
        .mapNotNull { indexed ->
            val texts = listOf(indexed.answer to false) + if (options.visibleTextOnly) emptyList() else listOf(indexed.reasoning to true)
            if (pattern != null) {
                val (text, inReasoning, match) = texts.firstNotNullOfOrNull { (text, inReasoning) ->
                    pattern.find(text)?.takeIf { !it.range.isEmpty() }?.let { Triple(text, inReasoning, it) }
                } ?: return@mapNotNull null
                indexed.hit(accountId, HighlightedSnippet.buildPattern(text, pattern), inReasoning, findText = match.value)
            } else {
                val (text, inReasoning) = texts.firstOrNull { (text, _) -> matchesAllTerms(text, plan, options) } ?: return@mapNotNull null
                indexed.hit(accountId, snippetOf(text, plan, options), inReasoning)
            }
        }
    val sorted = if (options.sort == SearchSort.Oldest) hits.sortedBy { it.createdAt } else hits.sortedByDescending { it.createdAt }
    return SampleSearchResult(sorted)
}

private fun IndexedMessage.hit(accountId: String, snippet: HighlightedSnippet, inReasoning: Boolean, findText: String? = null) =
    MessageSearchHit(
        accountId = accountId,
        messageId = message.messageId,
        conversationId = chat.id,
        conversationTitle = chat.title,
        createdAt = createdAt,
        isCreatedByUser = message.isCreatedByUser,
        model = model,
        endpoint = endpoint,
        snippet = snippet,
        source = HitSource.Local,
        inReasoning = inReasoning,
        findText = findText,
    )

private fun matchesAllTerms(text: String, plan: FtsQueryPlan?, options: SearchOptions): Boolean = when (plan) {
    is FtsQueryPlan.Match -> plan.terms.withIndex().all { (index, term) ->
        val prefix = plan.lastIsPrefix && index == plan.terms.lastIndex
        HighlightedSnippet.build(text, listOf(term), text.length + 1, matchCase = options.matchCase, wholeWord = options.wholeWord, lastIsPrefix = prefix)
            .highlights.isNotEmpty()
    }
    is FtsQueryPlan.Scan ->
        HighlightedSnippet.build(text, listOf(plan.literal), text.length + 1, wordStartsOnly = false, matchCase = options.matchCase)
            .highlights.isNotEmpty()
    else -> false
}

private fun snippetOf(text: String, plan: FtsQueryPlan?, options: SearchOptions, window: Int = HighlightedSnippet.DEFAULT_WINDOW) =
    when (plan) {
        is FtsQueryPlan.Scan -> HighlightedSnippet.build(text, listOf(plan.literal), window, wordStartsOnly = false, matchCase = options.matchCase)
        is FtsQueryPlan.Match -> HighlightedSnippet.build(
            text, plan.terms, window, matchCase = options.matchCase, wholeWord = options.wholeWord, lastIsPrefix = plan.lastIsPrefix,
        )
        else -> HighlightedSnippet.build(text, emptyList(), window)
    }

/** A two-letter last word matches only whole words in messages: the word, to say one more letter widens it. */
private fun shortPrefixOf(query: String, options: SearchOptions): String? {
    if (options.regex || options.wholeWord) return null
    val plan = FtsQuery.plan(query) as? FtsQueryPlan.Match ?: return null
    return plan.terms.last().takeIf { plan.typingLast && it.length < FtsQuery.MIN_PREFIX_CHARS }
}

/** The chat's thread with matches marked; [hit]'s reasoning is shown when the match is in it. */
private fun turnsOf(conversationId: String, hit: MessageSearchHit?, query: String, options: SearchOptions): List<PreviewTurn> {
    val chat = sampleChat(conversationId) ?: return emptyList()
    val pattern = if (options.regex) (RegexGuard.compile(query.trim(), options.matchCase) as? RegexCheck.Ok)?.regex else null
    val plan = if (options.regex) null else FtsQuery.plan(query, prefixLastToken = !options.wholeWord)
    val mark: (String) -> HighlightedSnippet = { text ->
        pattern?.let { HighlightedSnippet.buildPattern(text, it, PREVIEW_WINDOW) } ?: snippetOf(text, plan, options, PREVIEW_WINDOW)
    }
    return chat.toMessages().map { message ->
        val indexed = IndexedMessage(chat, message, 0)
        PreviewTurn(
            messageId = message.messageId,
            snippet = mark(indexed.answer),
            isUser = message.isCreatedByUser,
            reasoning = indexed.reasoning.takeIf { hit?.messageId == message.messageId && hit.inReasoning && it.isNotBlank() }?.let(mark),
        )
    }
}

// Enough of a message to read it in the preview.
private const val PREVIEW_WINDOW = 2_000
