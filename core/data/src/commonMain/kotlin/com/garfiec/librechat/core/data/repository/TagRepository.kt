package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.ConversationTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's conversation tags (bookmarks, plus the reserved `SAVED_TAG` behind the favorite star).
 *
 * The server is the source of truth for which tags a chat carries: the conversation list omits
 * them, so a cached row's tags are only a hint. Every per-chat write goes through
 * [setConversationTag], which reads the chat's tags from the server first.
 */
interface TagRepository {
    /** Bumps after every tag change the server accepted; see [TagChangeSignal]. */
    val revision: StateFlow<Long>

    /** Bookmarks renamed or deleted here, each ahead of its [revision] bump; see [TagEvent]. */
    val events: SharedFlow<TagEvent>

    /** The account's tags in `position` order, blank names dropped. Saved included: filter with `bookmarks()`. */
    fun observeTags(): Flow<List<ConversationTag>>

    /** Re-reads the tag list (names, counts, order) into the cache. Does not bump [revision]. */
    suspend fun refreshTags(): Result<Unit>

    /**
     * The tags the cached row of [conversationId] carries, as they change; empty while it has no row.
     * A hint only (see the class doc), but every write here and every full read of the chat lands in it.
     */
    fun observeConversationTags(conversationId: String): Flow<List<String>>

    /** The tags [conversationId] carries on the server, written to the cache. */
    suspend fun getConversationTags(conversationId: String): Result<List<String>>

    /**
     * Attaches or detaches [tag] on [conversationId]: reads the chat's tags from the server, sends
     * that set with the one change, and caches the set the server returns. Tags set elsewhere (the
     * web) survive. A name the account doesn't have yet is created first, at the end of the order.
     * Returns the chat's tags after the write.
     */
    suspend fun setConversationTag(conversationId: String, tag: String, attached: Boolean): Result<List<String>>

    /** The favorite star: [setConversationTag] for `SAVED_TAG`, flipping what [currentTags] showed. */
    suspend fun toggleFavorite(conversationId: String, currentTags: List<String>): Result<List<String>>

    /**
     * Creates the bookmark [name] at the end of the order, attached to [addToConversationId] when
     * given. A blank [description] is not sent.
     */
    suspend fun createTag(
        name: String,
        description: String? = null,
        addToConversationId: String? = null,
    ): Result<ConversationTag>

    /**
     * Renames, re-describes or moves [name]. Null leaves a field alone; `description = ""` clears it.
     * A rename reaches every chat server-side, and the cached chats follow.
     */
    suspend fun updateTag(
        name: String,
        newName: String? = null,
        description: String? = null,
        position: Int? = null,
    ): Result<ConversationTag>

    /** Deletes [name] and takes it off every chat. */
    suspend fun deleteTag(name: String): Result<Unit>

    /**
     * Moves [name] to [toIndex] of the `bookmarks()` list (Saved excluded). Reorders the cache at
     * once and puts it back if the server refuses.
     */
    suspend fun reorderTag(name: String, toIndex: Int): Result<Unit>

    suspend fun clearCache()
}
