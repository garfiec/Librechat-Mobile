package com.garfiec.librechat.core.data.search

/** Where a hit came from. A message found in both is reported once, as [Local]. */
enum class HitSource { Local, Remote }

/**
 * One message that matched. [accountId] is the account the search ran for: a hit whose account is
 * no longer active must not be opened, which is how a list drawn just before a switch is kept from
 * opening another account's chat.
 */
data class MessageSearchHit(
    val accountId: String,
    val messageId: String,
    val conversationId: String,
    val conversationTitle: String?,
    /** Null for a server hit: Meilisearch returns no timestamps. */
    val createdAt: Long?,
    val isCreatedByUser: Boolean,
    val model: String?,
    val endpoint: String?,
    val snippet: HighlightedSnippet,
    val source: HitSource,
    /** Every match is in the message's reasoning, not the visible reply. */
    val inReasoning: Boolean = false,
    /**
     * For a pattern search, the text the pattern matched, which is what a jump finds in the chat (the
     * chat's find can't run a pattern). Null for a word search, whose query is found as typed.
     */
    val findText: String? = null,
)

enum class RemoteSearchStatus {
    /** Server search is off, unknown, offline, or the query is too short to send. */
    Unavailable,

    /** The request is in flight; local hits are already shown. */
    Searching,
    Done,

    /** The request failed; local hits stand alone. */
    Failed,
}

/** Why the server wasn't asked even though it can search. */
enum class RemoteSkipReason {
    /** The server can't run a pattern. */
    Regex,

    /** Server hits carry no timestamps, so a date filter can't be applied to them. */
    DateFilter,

    /** The user chose cached results only. */
    CachedOnly,

    /** The server has message search turned off (or hasn't said yet). */
    ServerOff,

    /** The device is offline. */
    Offline,
}

/** How a scan-engine search (unsegmented scripts, regex) stopped. */
data class ScanProgress(
    val scanned: Int,
    val total: Int,
    /** Non-null when the budget ran out first: pass it back to scan further. */
    val resumeFrom: ScanCursor?,
)

/**
 * The answer to one query for one account. Local hits come first, newest first; server-only hits
 * follow in the server's order, since Meilisearch returns no timestamps to sort them by.
 */
data class MessageSearchResult(
    val accountId: String,
    val query: String,
    val hits: List<MessageSearchHit>,
    val remoteStatus: RemoteSearchStatus,
    /** Set when the local half ran on the scan engine rather than the index. */
    val scan: ScanProgress? = null,
    /** True while the background backfill hasn't finished, so local results may be incomplete. */
    val indexIncomplete: Boolean = false,
    /**
     * Match case, whole word or answers-only dropped candidates from a full page of index hits, so
     * older messages that would pass weren't checked: the local hits are only the first ones.
     */
    val localCapped: Boolean = false,
    /**
     * The server answered with a full page, so it likely holds more matches than it returned
     * (see `MessagesApi.searchMessages`).
     */
    val remoteCapped: Boolean = false,
    /** Set when a regex search couldn't run. */
    val regexProblem: RegexProblem? = null,
    val remoteSkipped: RemoteSkipReason? = null,
)
