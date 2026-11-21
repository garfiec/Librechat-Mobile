package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

/**
 * In-conversation find-in-page state. Owned by
 * [com.garfiec.librechat.feature.chat.viewmodel.delegate.InConversationSearchDelegate].
 */
@Immutable
data class ChatSearchState(
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchMatchIndices: List<SearchMatch> = emptyList(),
    val currentSearchMatchIndex: Int = 0,
    /** The search occurrence to scroll to when the focused match changes. Consumed by MessageList. */
    val searchFocusRequest: SearchFocusRequest? = null,
    /**
     * A jump's target: outlined while find-in-page is open on it (closing it clears this), or, when
     * [JumpFlash.fade] (its query had no literal match to highlight), flashed once instead.
     */
    val jumpFlash: JumpFlash? = null,
    /**
     * A jump to a message is under way: the list holds its first scroll to the bottom until the
     * jump focuses the message or gives up, so the chat doesn't show its end first.
     */
    val jumpPending: Boolean = false,
)

/** Flash [messageIndex] once; [requestId] makes a repeat jump replay it. */
@Immutable
data class JumpFlash(
    val messageIndex: Int,
    val requestId: Long,
    val fade: Boolean = true,
)

/**
 * Represents a single occurrence of a search query match.
 * @param messageIndex Index into [ChatUiState.displayMessages] containing this occurrence.
 * @param occurrenceInMessage 0-based index of this occurrence within the message text.
 */
@Immutable
data class SearchMatch(
    val messageIndex: Int,
    val occurrenceInMessage: Int,
)

/**
 * A request to scroll the message list to one specific search occurrence. [messageIndex] is the
 * scroll target; [requestId] is a monotonic id that both makes repeat jumps to the same occurrence
 * distinct (so the list's LaunchedEffect re-fires) and identifies the position report to act on.
 */
@Immutable
data class SearchFocusRequest(
    val messageIndex: Int,
    val requestId: Long,
)
