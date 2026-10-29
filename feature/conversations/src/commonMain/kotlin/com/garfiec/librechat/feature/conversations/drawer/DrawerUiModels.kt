package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.feature.conversations.search.SearchNote
import kotlin.time.Instant

/**
 * Lightweight snapshot of fields DrawerConversationItem actually renders.
 * Avoids passing the full 28-field Conversation through composition.
 */
@Immutable
data class DrawerConversationDisplayData(
    val conversationId: String,
    val title: String,
    val model: String?,
    val endpoint: String?,
    // Parsed timestamp, formatted at render time by the row (see DrawerConversationItem).
    // Deliberately NOT a pre-formatted "5m ago": that depends on the clock, so baking it into this
    // immutable snapshot leaves it stale until an unrelated Room emission re-runs the mapping.
    // Parsing, by contrast, is clock-independent and so stays upstream in the mapping.
    val updatedAt: Instant?,
    val isActive: Boolean,
    val isFavorite: Boolean,
    val isPinned: Boolean,
    val chatProjectId: String? = null,
    val tags: List<String>,
    val endpointIconUrl: String? = null,
    val modelLabel: String? = model,
    val iconEndpoint: String? = endpoint,
)

/**
 * Inline project-chat accordion state for the drawer's Projects tab: which project is expanded
 * (null = none), its network-loaded chats mapped to the drawer row type, and a loading flag.
 * Single-expand, so only the open project's chats are held.
 */
@Immutable
data class InlineProjectChatsState(
    val expandedProjectId: String? = null,
    val conversations: List<DrawerConversationDisplayData> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * One signed-in account as the drawer header chip + switcher sheet render it (multi-account,
 * issue #179). Mapped from the persisted roster; ordered active-first, then by recency.
 */
@Immutable
data class AccountUiModel(
    val accountId: String,
    val displayLabel: String,
    val serverHost: String,
    val avatarUrl: String?,
    val isActive: Boolean,
    /** The session expired: the account stays listed, but needs signing in to before it's usable. */
    val needsSignIn: Boolean = false,
)

/**
 * Combined UI state for the drawer sidebar.
 */
@Immutable
data class DrawerUiState(
    val groupedConversations: List<Pair<DrawerGroupKey, List<DrawerConversationDisplayData>>> = emptyList(),
    val favoriteConversations: List<DrawerConversationDisplayData> = emptyList(),
    val pinnedConversations: List<DrawerConversationDisplayData> = emptyList(),
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    // Role-permission gates — default permissive.
    val agentsEnabled: Boolean = true,
    /** MARKETPLACE.USE: browsing agents. Upstream's USER role is denied it by default. */
    val marketplaceEnabled: Boolean = true,
    /**
     * Bookmarks and the favorite star (BOOKMARKS USE and `interface.bookmarks`): the stars,
     * Favorites, the menu's Favorite and "Bookmarks…".
     */
    val bookmarksEnabled: Boolean = true,
    /** Role SKILLS.USE (permissive) AND the agents `skills` capability (fail-closed). */
    val skillsEnabled: Boolean = false,
    /** Scheduled chats: the SCHEDULES permission AND the server's `interface.schedules` flag,
     *  which is absent-means-OFF. Default false — the feature is opt-in. */
    val schedulesEnabled: Boolean = false,
    // Config-driven: whether server-side shared links are enabled (gates the menu's Share action).
    val sharedLinksEnabled: Boolean = false,
    // Version-gated (v0.8.7+): whether the pin/unpin action is offered. Older servers lack
    // POST /api/convos/pin, so the action is hidden there.
    val pinEnabled: Boolean = false,
    // Version-gated (v0.8.7+): whether the move-to-project action is offered.
    val projectsEnabled: Boolean = false,
    /** Message hits for the current query; [DrawerMessageSearch.Idle] while not searching. */
    val messageSearch: DrawerMessageSearch = DrawerMessageSearch.Idle,
) {
    /**
     * The Agents entry (phone drawer and tablet sidebar): AGENTS.USE and MARKETPLACE.USE, as the web
     * app hides its marketplace. It is also the app's only way to the agent editor, so a role without
     * MARKETPLACE.USE can't create or edit agents here; that was decided, not overlooked.
     */
    val agentsEntryVisible: Boolean get() = agentsEnabled && marketplaceEnabled
}

/**
 * The drawer's Messages section: a short preview of the query's message hits, with the full list a
 * tap away on the Search screen.
 */
@Immutable
data class DrawerMessageSearch(
    /** The first [PREVIEW_COUNT] hits. */
    val preview: List<MessageSearchHit> = emptyList(),
    val totalCount: Int = 0,
    /** [totalCount] hit the drawer's search limit, so there may be more. */
    val countCapped: Boolean = false,
    /** A result has landed for the current query (before it, the section shows nothing). */
    val searched: Boolean = false,
    val searchingServer: Boolean = false,
    /** Why only cached messages were searched, or null when the server answered too. */
    val note: SearchNote? = null,
) {
    companion object {
        const val PREVIEW_COUNT: Int = 5
        val Idle = DrawerMessageSearch()
    }
}
