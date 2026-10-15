package com.garfiec.librechat.feature.conversations.search

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.data.search.HighlightedSnippet
import com.garfiec.librechat.core.data.search.HitSource
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.core.data.search.RegexProblem
import com.garfiec.librechat.core.data.search.RemoteSearchStatus
import com.garfiec.librechat.core.data.search.RemoteSkipReason
import com.garfiec.librechat.core.data.search.RoleFilter
import com.garfiec.librechat.core.data.search.ScanProgress
import com.garfiec.librechat.core.data.search.SearchFilterValues
import com.garfiec.librechat.core.data.search.SearchOptions
import com.garfiec.librechat.core.data.search.SearchSort
import com.garfiec.librechat.core.data.search.SearchSource

enum class SearchScope { All, Messages, Chats }

enum class SearchGrouping { ByConversation, None }

/** The advanced date filter's choices, as a span ending now. */
enum class DateRange(val days: Long?) { AnyTime(null), PastWeek(7), PastMonth(30), PastYear(365) }

/**
 * A conversation and what matched in it: its title, its messages, or both. One hierarchy, so a
 * chat whose title and messages both match is listed once, with its message hits under it.
 */
@Immutable
data class SearchResultGroup(
    val conversationId: String,
    val title: String?,
    val hits: List<MessageSearchHit>,
    /** Every hit came from the server, so none of this conversation's messages are cached. */
    val fromServer: Boolean,
    /** The account the result was computed for; a group from another account isn't opened. */
    val accountId: String = "",
    /** Ranges of [title] to mark when the title matched, or null when only messages did. */
    val titleHighlights: List<IntRange>? = null,
    /**
     * The server's conversation search matched this chat in a message it didn't return as a hit
     * (its message search and conversation search cap separately), so there's no snippet to show.
     */
    val serverBodyMatch: Boolean = false,
    /** Newest hit, or the conversation's last update for a title-only match, as epoch millis. */
    val newestAt: Long? = null,
) {
    val titleMatched: Boolean get() = titleHighlights != null
}

/** A row the keyboard can select: a conversation header, or one message hit. */
@Immutable
sealed interface SearchEntry {
    data class Conversation(val conversationId: String) : SearchEntry
    data class Hit(val hit: MessageSearchHit) : SearchEntry
}

@Immutable
data class PreviewTurn(
    val messageId: String,
    val snippet: HighlightedSnippet,
    val isUser: Boolean,
    /** The reasoning, shown apart from the answer; only on the hit, when the match is in it. */
    val reasoning: HighlightedSnippet? = null,
)

enum class ThreadLoad {
    /** Read from the cache, or fetched. */
    Ready,

    /** Not cached here: the conversation is being fetched from the server. */
    Loading,

    /** Not cached here, and the fetch failed. */
    Failed,
}

/** The selected conversation in the tablet preview pane. */
@Immutable
data class SearchChatPreview(
    val group: SearchResultGroup,
    val model: String? = null,
    val updatedAt: Long? = null,
    /**
     * The conversation's thread: through its first hit, else its newest thread. Empty while
     * loading, or when nothing is cached and the fetch failed.
     */
    val thread: List<PreviewTurn> = emptyList(),
    /** The turn to show first: the first that matches, else the top. */
    val focusIndex: Int = 0,
    val load: ThreadLoad = ThreadLoad.Ready,
)

/** The selected hit in the tablet preview pane, in the thread that holds it. */
@Immutable
data class SearchPreview(
    val hit: MessageSearchHit,
    /** The hit's position among its conversation's hits, for `‹ n/m ›`. */
    val index: Int,
    val count: Int,
    /**
     * The whole thread through the hit, with matches marked. Empty while it loads, or when the
     * hit isn't cached and the conversation couldn't be fetched: the pane shows the hit's snippet.
     */
    val thread: List<PreviewTurn> = emptyList(),
    val hitIndex: Int = 0,
    val load: ThreadLoad = ThreadLoad.Ready,
    /** Ranges of the chat's title to mark when the title matched too, as in the list. */
    val titleHighlights: List<IntRange>? = null,
)

@Immutable
data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.All,
    /** Local groups first (newest first), then server-only groups in the server's order. */
    val groups: List<SearchResultGroup> = emptyList(),
    /** Every hit in the repository's order (the sort), for the ungrouped list. */
    val flatHits: List<MessageSearchHit> = emptyList(),
    val grouping: SearchGrouping = SearchGrouping.ByConversation,
    val messageCount: Int = 0,
    /** The server answered with a full page, so more matches likely exist there. */
    val remoteCapped: Boolean = false,
    /** A two-letter last word that titles took as a prefix but messages only as a whole word. */
    val shortPrefix: String? = null,
    /** A result has landed for [query]. */
    val searched: Boolean = false,
    val remoteStatus: RemoteSearchStatus = RemoteSearchStatus.Unavailable,
    val scan: ScanProgress? = null,
    val indexIncomplete: Boolean = false,
    /**
     * How many cached matches are shown when only the first ones were checked (see
     * `MessageSearchResult.localCapped`), or null when the list is complete.
     */
    val cappedAt: Int? = null,
    val selectedMessageId: String? = null,
    val preview: SearchPreview? = null,
    /** A conversation header selected in the two-pane layout; exclusive with [selectedMessageId]. */
    val selectedConversationId: String? = null,
    val chatPreview: SearchChatPreview? = null,
    /** The query the current results answer, so folding survives a re-emission but not a new query. */
    val searchedQuery: String? = null,
    /** Agent names by id, for showing an agent chat's model (its agent id) by name. */
    val agentNames: Map<String, String> = emptyMap(),
    /** Groups whose hits are folded away; cleared when the query changes. */
    val collapsed: Set<String> = emptySet(),
    /** Settings → General → Advanced search. Off hides the options and searches plainly. */
    val advancedEnabled: Boolean = false,
    val options: SearchOptions = SearchOptions.Default,
    val dateRange: DateRange = DateRange.AnyTime,
    val filtersOpen: Boolean = false,
    val filterValues: SearchFilterValues = SearchFilterValues(),
    val regexProblem: RegexProblem? = null,
    val remoteSkipped: RemoteSkipReason? = null,
) {
    /** How many row filters are set, for the Filters button's badge. */
    val activeFilterCount: Int
        get() = listOf(
            options.role != RoleFilter.Any,
            options.model != null,
            options.endpoint != null,
            options.since != null,
            options.includeArchived,
            options.visibleTextOnly,
            options.source != SearchSource.All,
        ).count { it }

    /** How many of the wide layout's "More options" differ from the default. */
    val moreOptionsCount: Int
        get() = listOf(
            options.includeArchived,
            options.visibleTextOnly,
            options.source != SearchSource.All,
            options.sort != SearchSort.Newest,
            grouping != SearchGrouping.ByConversation,
        ).count { it }

    /**
     * A model filter is on and server hits are listed: the server reports each message under its
     * conversation's current model, not the model that wrote it, so those hits answer "chats now
     * on this model" rather than "messages this model wrote".
     */
    val serverModelCaveat: Boolean
        get() = options.model != null && groups.any { group -> group.hits.any { it.source == HitSource.Remote } }

    /** Why only cached messages were searched, or null when the server answered too. */
    val note: SearchNote?
        get() = if (searched) searchNote(remoteStatus, remoteSkipped) else null

    /** Every hit in display order. */
    val orderedHits: List<MessageSearchHit>
        get() = if (grouping == SearchGrouping.None) flatHits else groups.flatMap { it.hits }

    /** Conversations whose title matched, for the Chats tab and its count. */
    val chatCount: Int get() = groups.count { it.titleMatched }

    /**
     * What the Messages tab counts: every hit, plus each server chat matched in a message it didn't
     * return, which the tab lists too.
     */
    val messageTabCount: Int get() = messageCount + groups.count { it.serverBodyMatch }

    val visibleGroups: List<SearchResultGroup>
        get() = when (scope) {
            SearchScope.All -> groups
            SearchScope.Messages -> groups.filter { it.hits.isNotEmpty() || it.serverBodyMatch }
            SearchScope.Chats -> groups.filter { it.titleMatched }
        }

    val showsHits: Boolean get() = scope != SearchScope.Chats

    /**
     * Every selectable row in display order, for ↑/↓. Grouped: each header, then its hits.
     * Ungrouped: title-only conversations, then the hits in the sort's order.
     */
    val entries: List<SearchEntry>
        get() = if (grouping == SearchGrouping.None && showsHits) {
            visibleGroups.filter { it.hits.isEmpty() }.map { SearchEntry.Conversation(it.conversationId) } +
                flatHits.filter { hit -> scope == SearchScope.All || visibleGroups.any { it.conversationId == hit.conversationId } }
                    .map { SearchEntry.Hit(it) }
        } else {
            visibleGroups.flatMap { group ->
                listOf(SearchEntry.Conversation(group.conversationId)) +
                    if (showsHits && group.conversationId !in collapsed) group.hits.map { SearchEntry.Hit(it) } else emptyList()
            }
        }

    /** Server only was chosen and the server can't search right now. */
    val serverUnavailable: Boolean
        get() = searched && options.source == SearchSource.Server && remoteStatus != RemoteSearchStatus.Searching &&
            remoteStatus != RemoteSearchStatus.Done
}
