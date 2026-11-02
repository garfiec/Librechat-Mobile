package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationListFilters
import com.garfiec.librechat.core.model.ConversationPage
import com.garfiec.librechat.core.model.response.ForkConversationResponse
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Suppress("TooManyFunctions") // debt: 23 functions
interface ConversationRepository {
    fun observeConversations(isArchived: Boolean = false): Flow<Result<List<Conversation>>>
    suspend fun loadNextPage(
        cursor: String?,
        tags: List<String>? = null,
        search: String? = null,
        sortBy: String? = null,
        sortDirection: String? = null,
        isArchived: Boolean = false,
    ): Result<String?>

    /**
     * Cache-first read of [id]. [originAccount] (origin-capture provenance) scopes both the cache
     * read and the fallback [refreshConversation] write to the account that started the operation, so
     * a read-through landing after a switch doesn't miss-then-refetch under the new account. Null
     * (foreground callers) uses the live active account.
     *
     * Deliberately no default on [originAccount] (here and on the other provenance-carrying methods):
     * every caller must decide between a send-time capture (any write that can land after a switch —
     * streamed finalize, debounced or queued work) and an explicit null (foreground, where entry *is*
     * land time). A silent default would let new deferred-write paths compile with land-time
     * attribution — the mis-attribution bug this parameter exists to prevent.
     */
    suspend fun getConversation(id: String, originAccount: AccountId?): Result<Conversation>

    /**
     * Fetches a cursor page of conversations filtered by [projectId] (a project id or
     * [com.garfiec.librechat.core.model.ChatProject.UNASSIGNED]). Returned directly to the
     * caller: the project-filtered list view is network-direct by design (not yet a Room-filtered
     * query). The page is still upserted into the cache for warmth, which also keeps each row's
     * chatProjectId current. Used by the project folder/browse views.
     */
    suspend fun getConversationsForProject(
        projectId: String,
        cursor: String? = null,
        limit: Int = 25,
        sortBy: String? = null,
        sortDirection: String? = null,
    ): Result<ConversationPage>

    /**
     * Fetches a cursor page of the chats carrying the bookmark [tag] (exact name), newest first.
     * Returned directly, like [getConversationsForProject]: the list endpoint omits each chat's tags,
     * so Room can't filter by them. The page warms the cache with the tag-preserving upsert.
     */
    suspend fun getConversationsForTag(
        tag: String,
        cursor: String? = null,
        limit: Int = 25,
    ): Result<ConversationPage>

    /**
     * Fetches [id] from the server and upserts it into the cache, bypassing
     * [getConversation]'s cache-first read. Use when the local row is known to be
     * stale (e.g. picking up a server-generated title). This is the full document, so its
     * tags replace the cached ones (the list endpoints omit tags and keep them).
     */
    suspend fun refreshConversation(id: String, originAccount: AccountId?): Result<Conversation>
    suspend fun updateTitle(id: String, title: String): Result<Conversation>
    suspend fun generateTitle(conversationId: String, originAccount: AccountId?): Result<String>
    suspend fun archive(id: String, isArchived: Boolean): Result<Conversation>
    suspend fun pin(id: String, pinned: Boolean): Result<Conversation>
    suspend fun delete(id: String): Result<Unit>
    suspend fun forkConversation(
        conversationId: String,
        messageId: String,
        option: String? = null,
        splitAtTarget: Boolean? = null,
        latestMessageId: String? = null,
        excludeFiles: Boolean? = null,
    ): Result<ForkConversationResponse>
    suspend fun duplicateConversation(conversationId: String, title: String?): Result<Conversation>

    /**
     * Imports an export file. A file whose tool calls carry preview markers is refused with
     * [ToolCallPreviewImportException] before anything is sent: the route stores a file as is.
     */
    suspend fun importConversation(jsonContent: String): Result<Conversation>

    suspend fun deleteAll(): Result<Unit>

    /**
     * Archives every conversation the server can see for this user (v0.8.8-rc2), returning the
     * count it archived. Fails with a 404 [com.garfiec.librechat.core.common.result.ApiException]
     * on a server without the route — see VERSION_GATES.md for why the caller gates rather than
     * this method.
     */
    suspend fun archiveAll(): Result<Int>
    suspend fun saveConversation(conversation: Conversation, originAccount: AccountId?)
    suspend fun syncFavoritesFromServer(): Result<Unit>

    /**
     * A cursor page of the chats matching [filters] (and the server's [search], when given), newest
     * first (v0.8.9-rc1). Network-direct and never cached: Room can't answer "has files" or
     * "shared", and a filtered page written into the cache would read as the whole list.
     */
    suspend fun getConversationsFiltered(
        filters: ConversationListFilters,
        cursor: String? = null,
        search: String? = null,
        limit: Int = 25,
    ): Result<ConversationPage>

    /** The cached row for [id] under the active account, or null while none is cached. */
    fun observeConversation(id: String): Flow<Conversation?>

    /** One conversation off the server, not written to the cache (the drawer's unlisted running rows). */
    suspend fun fetchConversationUncached(id: String): Result<Conversation>

    /**
     * `POST /api/convos/seen` (v0.8.9-rc1) for the reply stamped [lastResponseAt]. True when the
     * server took it, and the cached row is then caught up. False when a newer reply landed: the row
     * stays unread and is refreshed, so the newer stamp reaches the cache.
     */
    suspend fun markSeen(id: String, lastResponseAt: Instant): Result<Boolean>

    /** `POST /api/convos/unread` (v0.8.9-rc1); the cached row takes the read state it answered with. */
    suspend fun markUnread(id: String): Result<Unit>
}

/**
 * An import file holding tool-call previews (shortened output with `outputTruncated` and its
 * siblings). Importing it would store the shortened output permanently, so it is refused.
 */
class ToolCallPreviewImportException : IllegalArgumentException("The file holds shortened tool output")
