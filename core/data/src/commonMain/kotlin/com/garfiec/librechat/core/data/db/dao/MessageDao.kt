package com.garfiec.librechat.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.garfiec.librechat.core.common.identity.CrossAccount
import com.garfiec.librechat.core.data.db.entity.MessageEntity
import com.garfiec.librechat.core.data.db.entity.MessageSearchEntity
import com.garfiec.librechat.core.data.mapper.searchTextOf
import com.garfiec.librechat.core.data.mapper.toSearchEntity
import kotlinx.coroutines.flow.Flow

/** Stale-message ids per delete in [MessageDao.replaceAllForConversation], under SQLite's bind limit. */
private const val REPLACE_DELETE_CHUNK = 400

@Suppress("TooManyFunctions") // debt: 20 functions
@Dao
interface MessageDao {

    // --- Account-scoped reads (row-tenancy): filter accountId on every read incl. by-PK. ---

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND accountId = :accountId ORDER BY createdAt ASC")
    fun observeMessagesForAccount(conversationId: String, accountId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId AND accountId = :accountId")
    suspend fun getByIdForAccount(messageId: String, accountId: String): MessageEntity?

    /**
     * Size of the cached-message store, for the prefetch status readout.
     *
     * One-shot rather than observed on purpose. There is no index on `accountId`, so this is a full
     * table scan, and Room re-fires an observed query on every write to `messages` — which on the
     * activity screen means a scan after each conversation a pass warms, on the one screen designed
     * to be watched while a pass runs. Read on demand instead.
     */
    @Query("SELECT COUNT(*) FROM messages WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: String): Int

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND parentMessageId = :parentMessageId " +
            "AND accountId = :accountId ORDER BY createdAt ASC",
    )
    suspend fun getSiblingsForAccount(
        conversationId: String,
        parentMessageId: String,
        accountId: String,
    ): List<MessageEntity>

    /**
     * Raw row writes, for fixtures. Repositories write through [upsertAllIndexed] instead, which
     * keeps the search index in the same transaction (`MessageWritePairingTest` holds them to it).
     */
    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsertSearchRows(rows: List<MessageSearchEntity>)

    @Query("SELECT messageId, updatedAt FROM message_search WHERE conversationId = :conversationId AND accountId = :accountId")
    suspend fun indexedVersions(conversationId: String, accountId: String): List<IndexedVersion>

    /**
     * Writes [messages] and their search rows in one transaction, parent rows first (the side
     * table's foreign key needs them). Each side row takes the `accountId` already stamped on its
     * message, so this never re-resolves the active account.
     *
     * A side row is rewritten only when it is new or its `updatedAt` differs from the indexed one.
     * Room's FTS sync triggers fire on any update of the row, so rewriting an unchanged row would
     * re-tokenize its whole text on every fetch of the conversation. `!=` rather than `>`: the
     * mapper substitutes the current time when the server omits `updatedAt`, and `!=` errs toward
     * writing. The server bumps `updatedAt` on every message write, so an unchanged value means
     * unchanged text. Parent rows are always written.
     */
    @CrossAccount
    @Transaction
    suspend fun upsertAllIndexed(messages: List<MessageEntity>) {
        if (messages.isEmpty()) return
        upsertAll(messages)
        val indexed = HashMap<String, Long>()
        messages.mapNotNullTo(HashSet()) { message -> message.accountId?.let { message.conversationId to it } }
            .forEach { (conversationId, accountId) ->
                indexedVersions(conversationId, accountId).forEach { indexed[it.messageId] = it.updatedAt }
            }
        val changed = messages.filter { indexed[it.messageId] != it.updatedAt }
        if (changed.isNotEmpty()) upsertSearchRows(changed.mapNotNull { it.toSearchEntity() })
    }

    /**
     * A local edit of one message: the row and its search row, written together and
     * unconditionally.
     *
     * Deliberately NOT [upsertAllIndexed]. That one rewrites a side row only when `updatedAt`
     * differs from the indexed value, which is right for a server fetch — the server bumps it on
     * every write, so an unchanged value means unchanged text — and wrong for an edit applied
     * locally, which keeps `updatedAt` as it was. The dedupe would always hit and leave the index
     * matching the text the user just replaced: the new words would not be findable and the old
     * ones would still jump to this message. The flat-text edit ([updateText]) reindexes
     * explicitly for the same reason.
     */
    @CrossAccount
    @Transaction
    suspend fun upsertReindexed(message: MessageEntity) {
        upsert(message)
        message.toSearchEntity()?.let { upsertSearchRows(listOf(it)) }
    }

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND accountId = :accountId")
    suspend fun deleteAllForConversation(conversationId: String, accountId: String)

    @Query("SELECT messageId FROM messages WHERE conversationId = :conversationId AND accountId = :accountId")
    suspend fun messageIdsForConversation(conversationId: String, accountId: String): List<String>

    /** Chunk the ids — SQLite binds at most ~999 variables per statement. */
    @Query("DELETE FROM messages WHERE accountId = :accountId AND messageId IN (:messageIds)")
    suspend fun deleteMessages(accountId: String, messageIds: List<String>)

    // Full replace of a conversation's cached messages, scoped to the account: the delete leg threads
    // accountId so it can't wipe another account's rows for the same conversationId. @CrossAccount
    // suppresses the concrete-body lint; the body is account-safe.
    //
    // Only the rows the fresh list no longer holds are deleted; the rest are upserted in place.
    // Deleting them all would cascade their side rows away, so every refresh would tokenize the whole
    // conversation out of the index and back in, past upsertAllIndexed's unchanged-row skip.
    @CrossAccount
    @Transaction
    suspend fun replaceAllForConversation(conversationId: String, accountId: String, messages: List<MessageEntity>) {
        val kept = messages.mapTo(HashSet()) { it.messageId }
        messageIdsForConversation(conversationId, accountId)
            .filterNot { it in kept }
            .chunked(REPLACE_DELETE_CHUNK)
            .forEach { deleteMessages(accountId, it) }
        upsertAllIndexed(messages)
    }

    @Query("UPDATE messages SET feedback = :feedback WHERE messageId = :messageId AND accountId = :accountId")
    suspend fun updateFeedback(messageId: String, feedback: String?, accountId: String)

    @Query("UPDATE messages SET text = :text, content = NULL WHERE messageId = :messageId AND accountId = :accountId")
    suspend fun updateMessageText(messageId: String, text: String, accountId: String)

    @CrossAccount
    @Query(
        "INSERT OR IGNORE INTO message_search (messageId, accountId, conversationId, createdAt, updatedAt, " +
            "isCreatedByUser, model, endpoint, searchText, visibleLength) " +
            "SELECT messageId, accountId, conversationId, createdAt, updatedAt, isCreatedByUser, model, endpoint, " +
            ":searchText, :visibleLength FROM messages WHERE messageId = :messageId AND accountId = :accountId",
    )
    suspend fun indexFromMessage(messageId: String, accountId: String, searchText: String?, visibleLength: Int?)

    @Query(
        "UPDATE message_search SET searchText = :searchText, visibleLength = :visibleLength " +
            "WHERE messageId = :messageId AND accountId = :accountId " +
            "AND (searchText IS NOT :searchText OR visibleLength IS NOT :visibleLength)",
    )
    suspend fun updateSearchText(messageId: String, accountId: String, searchText: String?, visibleLength: Int?)

    /**
     * A local edit: the row's text replaces its content parts, and the index follows in the same
     * transaction. Indexes a row that never had a side row (so an edit heals one the backfill
     * hasn't reached), and leaves an already-matching one untouched rather than re-tokenizing it.
     * Indexed as [searchTextOf] indexes a row with no content parts, which the edit leaves it, so a
     * legacy `:::thinking` block in the text still counts as reasoning.
     */
    @CrossAccount
    @Transaction
    suspend fun updateText(messageId: String, text: String, accountId: String) {
        updateMessageText(messageId, text, accountId)
        val indexed = searchTextOf(text, contentJson = null)
        indexFromMessage(messageId, accountId, indexed.text, indexed.visibleLength)
        updateSearchText(messageId, accountId, indexed.text, indexed.visibleLength)
    }

    // Logout / account-remove scoped purge (the leak fix): delete only this account's rows.
    @Query("DELETE FROM messages WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)

    /**
     * Prune: drops cached messages for conversations the prefetcher no longer keeps warm. The
     * conversation rows themselves stay, so the list remains complete and reopening one simply
     * re-fetches. Chunk the ids — SQLite binds at most ~999 variables per statement.
     */
    @Query("DELETE FROM messages WHERE accountId = :accountId AND conversationId IN (:conversationIds)")
    suspend fun deleteForConversations(accountId: String, conversationIds: List<String>): Int
}
