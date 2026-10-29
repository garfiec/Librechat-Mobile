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

    /**
     * The cached tags of each of [conversationIds] that has a cached row, as they change. Rows paged
     * from the server carry no tags (the list endpoint omits them); this is what a screen shows for
     * them, e.g. the favorite star, which the session's favorite sync and every write keep current.
     */
    fun observeCachedTags(conversationIds: List<String>): Flow<Map<String, List<String>>>

    /** The tags [conversationId] carries on the server, written to the cache. Waits for writes still out. */
    suspend fun getConversationTags(conversationId: String): Result<List<String>>

    /**
     * Attaches or detaches [tag] on [conversationId]: reads the chat's tags from the server, sends
     * that set with the one change, and caches the set the server returns. Tags set elsewhere (the
     * web) survive. An attach posts the name first, so one the account doesn't have (or no longer
     * has) is created at the end of the order, never by the chat PUT.
     * Returns the chat's tags after the write.
     *
     * The favorite star is `SAVED_TAG` through here too. [attached] is what the user asked for (the
     * opposite of what the row showed), never a flip: a flip against the server's set would undo a
     * favorite made on the web that the row didn't show yet, and a second tap would undo the first.
     */
    suspend fun setConversationTag(conversationId: String, tag: String, attached: Boolean): Result<List<String>>

    /**
     * Creates the bookmark [name] at the end of the order, attached to [addToConversationId] when
     * given. A blank [description] is not sent.
     *
     * An error means the bookmark wasn't created. Once it was, the answer is a success even when
     * adding it to the chat failed: [CreatedTag.attachFailed] says so.
     */
    suspend fun createTag(
        name: String,
        description: String? = null,
        addToConversationId: String? = null,
    ): Result<CreatedTag>

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

    /**
     * Moves [name] just below [after] (to the top when null) in the server's current order: re-reads
     * the list first, since a stored position sent against a stale one lands a slot off, then
     * [reorderTag]s. Nothing is sent when either is gone by then, or [name] already sits there; a
     * failed re-read is the answer.
     *
     * Moves run one at a time, in the order asked, each against the order the one before it left.
     * The whole move outlives the caller: a screen that goes away mid-move stops only its wait.
     */
    suspend fun reorderAfter(name: String, after: String?): Result<Unit>

    suspend fun clearCache()
}

/** A bookmark [TagRepository.createTag] made. [attachFailed]: it exists, but isn't on the chat it was asked for. */
data class CreatedTag(val tag: ConversationTag, val attachFailed: Boolean = false)
