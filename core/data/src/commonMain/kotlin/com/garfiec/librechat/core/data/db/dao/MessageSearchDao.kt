package com.garfiec.librechat.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.garfiec.librechat.core.common.identity.CrossAccount
import com.garfiec.librechat.core.data.db.entity.MessageSearchEntity
import com.garfiec.librechat.core.data.mapper.searchTextOf

/**
 * Reads of the message search index, plus the background work that keeps it complete.
 *
 * Every search read takes an explicit `accountId` and never reads `messages`: the hit list comes
 * from the narrow `message_search` rows. Queries that touch both `message_search` and
 * `messages_fts` are `@CrossAccount` only because the tenancy rule can't tell which table a
 * predicate scopes; each one filters `message_search.accountId` at the top level and has an
 * `AccountReadIsolationTest` case.
 *
 * **Every query here is bounded by construction** (a LIMIT, a pinned index plan). SQLite runs a
 * statement to completion whatever happens to the coroutine that asked for it, so an unbounded
 * search is not made safe by being cancellable.
 */
@Dao
interface MessageSearchDao {

    /**
     * Full-text hits for [match] (an `FtsQuery` expression, never raw user input), newest first.
     *
     * The plan this is written for, pinned by `MessageSearchIndexTest`: the FTS subquery is
     * materialized once into a rowid list, then the `(accountId, createdAt)` index is walked
     * backwards and each entry's rowid is checked against it, stopping at [limit]. No temp
     * b-tree and no table seek before a hit. The `+` on `rowid` stops SQLite from choosing the
     * rowid-IN lookup instead, which would have to sort every hit to apply the ORDER BY.
     */
    @CrossAccount
    @Query(
        "SELECT rowid AS rowId, messageId, conversationId, createdAt, isCreatedByUser, model, endpoint, searchText, " +
            "visibleLength " +
            "FROM message_search WHERE accountId = :accountId " +
            "AND +rowid IN (SELECT rowid FROM messages_fts WHERE messages_fts MATCH :match) " +
            "ORDER BY createdAt DESC LIMIT :limit",
    )
    suspend fun search(accountId: String, match: String, limit: Int): List<MessageSearchRow>

    /** [search], oldest first: the same index walked forwards. */
    @CrossAccount
    @Query(
        "SELECT rowid AS rowId, messageId, conversationId, createdAt, isCreatedByUser, model, endpoint, searchText, " +
            "visibleLength " +
            "FROM message_search WHERE accountId = :accountId " +
            "AND +rowid IN (SELECT rowid FROM messages_fts WHERE messages_fts MATCH :match) " +
            "ORDER BY createdAt ASC LIMIT :limit",
    )
    suspend fun searchOldest(accountId: String, match: String, limit: Int): List<MessageSearchRow>

    /**
     * [search] narrowed by advanced-search filters. A null [isUser]/[model]/[endpoint] means no
     * filter on that column. [since] bounds the `(accountId, createdAt)` index walk from below, so
     * a date filter shortens the walk; the other filters are checked per row on the FTS survivors,
     * which needs no further index. Archived conversations are excluded through a subquery on the
     * account's own conversations.
     */
    @CrossAccount
    @Query(
        "SELECT rowid AS rowId, messageId, conversationId, createdAt, isCreatedByUser, model, endpoint, searchText, " +
            "visibleLength " +
            "FROM message_search WHERE accountId = :accountId " +
            "AND +rowid IN (SELECT rowid FROM messages_fts WHERE messages_fts MATCH :match) " +
            "AND createdAt >= :since " +
            "AND (:isUser IS NULL OR isCreatedByUser = :isUser) " +
            "AND (:model IS NULL OR model = :model) " +
            "AND (:endpoint IS NULL OR endpoint = :endpoint) " +
            "AND (:excludeArchived = 0 OR conversationId NOT IN " +
            "(SELECT conversationId FROM conversations WHERE accountId = :accountId AND isArchived = 1)) " +
            "ORDER BY createdAt DESC LIMIT :limit",
    )
    @Suppress("LongParameterList") // one parameter per filter, all bound into a single statement
    suspend fun searchFiltered(
        accountId: String,
        match: String,
        since: Long,
        isUser: Boolean?,
        model: String?,
        endpoint: String?,
        excludeArchived: Boolean,
        limit: Int,
    ): List<MessageSearchRow>

    /** [searchFiltered], oldest first. */
    @CrossAccount
    @Query(
        "SELECT rowid AS rowId, messageId, conversationId, createdAt, isCreatedByUser, model, endpoint, searchText, " +
            "visibleLength " +
            "FROM message_search WHERE accountId = :accountId " +
            "AND +rowid IN (SELECT rowid FROM messages_fts WHERE messages_fts MATCH :match) " +
            "AND createdAt >= :since " +
            "AND (:isUser IS NULL OR isCreatedByUser = :isUser) " +
            "AND (:model IS NULL OR model = :model) " +
            "AND (:endpoint IS NULL OR endpoint = :endpoint) " +
            "AND (:excludeArchived = 0 OR conversationId NOT IN " +
            "(SELECT conversationId FROM conversations WHERE accountId = :accountId AND isArchived = 1)) " +
            "ORDER BY createdAt ASC LIMIT :limit",
    )
    @Suppress("LongParameterList") // one parameter per filter, all bound into a single statement
    suspend fun searchFilteredOldest(
        accountId: String,
        match: String,
        since: Long,
        isUser: Boolean?,
        model: String?,
        endpoint: String?,
        excludeArchived: Boolean,
        limit: Int,
    ): List<MessageSearchRow>

    /** The models an account's indexed messages came from, for the model filter. */
    @Query(
        "SELECT DISTINCT model FROM message_search WHERE accountId = :accountId AND model IS NOT NULL " +
            "ORDER BY model LIMIT :limit",
    )
    suspend fun modelsFor(accountId: String, limit: Int): List<String>

    /** The endpoints an account's indexed messages came from, for the endpoint filter. */
    @Query(
        "SELECT DISTINCT endpoint FROM message_search WHERE accountId = :accountId AND endpoint IS NOT NULL " +
            "ORDER BY endpoint LIMIT :limit",
    )
    suspend fun endpointsFor(accountId: String, limit: Int): List<String>

    /**
     * One keyset page of an account's indexed messages, newest first, for the scan engine (regex,
     * and scripts the tokenizer can't split into words). The `(accountId, createdAt)` index entry
     * carries the rowid, so the rowid tiebreak keeps the walk index-ordered with no sort.
     */
    @Query(
        "SELECT rowid AS rowId, messageId, conversationId, createdAt, isCreatedByUser, model, endpoint, searchText, " +
            "visibleLength " +
            "FROM message_search WHERE accountId = :accountId " +
            "AND (createdAt < :beforeCreatedAt OR (createdAt = :beforeCreatedAt AND rowid < :beforeRowId)) " +
            "ORDER BY createdAt DESC, rowid DESC LIMIT :limit",
    )
    suspend fun scanPage(
        accountId: String,
        beforeCreatedAt: Long,
        beforeRowId: Long,
        limit: Int,
    ): List<MessageSearchRow>

    /** [scanPage] oldest first: the page starts strictly above (createdAt, rowid). */
    @Query(
        "SELECT rowid AS rowId, messageId, conversationId, createdAt, isCreatedByUser, model, endpoint, searchText, " +
            "visibleLength " +
            "FROM message_search WHERE accountId = :accountId " +
            "AND (createdAt > :afterCreatedAt OR (createdAt = :afterCreatedAt AND rowid > :afterRowId)) " +
            "ORDER BY createdAt ASC, rowid ASC LIMIT :limit",
    )
    suspend fun scanPageOldest(
        accountId: String,
        afterCreatedAt: Long,
        afterRowId: Long,
        limit: Int,
    ): List<MessageSearchRow>

    @Query("SELECT COUNT(*) FROM message_search WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: String): Int

    // --- Index maintenance (cross-account by design: it keeps every account's rows indexed) ---

    @CrossAccount
    @Query(
        "SELECT rowid AS rowId, messageId, accountId, conversationId, createdAt, updatedAt, isCreatedByUser, " +
            "model, endpoint, text, content FROM messages " +
            "WHERE rowid < :beforeRowId AND accountId IS NOT NULL ORDER BY rowid DESC LIMIT :limit",
    )
    suspend fun messagesBefore(beforeRowId: Long, limit: Int): List<MessageIndexSource>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(rows: List<MessageSearchEntity>)

    /**
     * Indexes the next [limit] messages below [beforeRowId] that have no side row yet, walking
     * `messages` by descending rowid. Returns the lowest rowid read, or null when nothing is left.
     *
     * The read and the insert share one write transaction. Apart, a prune landing between them
     * deletes a parent the insert then references, and `INSERT OR IGNORE` does not cover foreign
     * key failures, so the batch would throw; and an edit landing between them would be
     * overwritten by the text read before it. Rows that already have a side row are skipped by
     * the IGNORE and fire no trigger. Never writes `messages`, so no chat observer re-fires.
     */
    @CrossAccount
    @Transaction
    suspend fun indexBatch(beforeRowId: Long, limit: Int): Long? {
        val sources = messagesBefore(beforeRowId, limit)
        if (sources.isEmpty()) return null
        insertMissing(sources.map { it.toSearchEntity() })
        return sources.last().rowId
    }

    /** Rows in `messages`, read from its smallest index rather than its wide rows. */
    @CrossAccount
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun countAllMessages(): Int

    @CrossAccount
    @Query("SELECT COUNT(*) FROM message_search")
    suspend fun countAllIndexed(): Int

    @CrossAccount
    @Query(
        "SELECT rowid AS rowId, messageId, accountId, conversationId, createdAt, updatedAt, isCreatedByUser, " +
            "model, endpoint, text, content FROM messages m WHERE accountId IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM message_search s WHERE s.messageId = m.messageId) LIMIT :limit",
    )
    suspend fun unindexedMessages(limit: Int): List<MessageIndexSource>

    /**
     * Indexes up to [limit] messages that have no side row, wherever they are: legacy rows claimed
     * after the backfill passed them, or anything a write path missed. Returns how many it read.
     * Same single-transaction rule as [indexBatch].
     */
    @CrossAccount
    @Transaction
    suspend fun repairBatch(limit: Int): Int {
        val sources = unindexedMessages(limit)
        if (sources.isNotEmpty()) insertMissing(sources.map { it.toSearchEntity() })
        return sources.size
    }
}

/** A search hit as read from `message_search`. */
data class MessageSearchRow(
    val rowId: Long,
    val messageId: String,
    val conversationId: String,
    val createdAt: Long,
    val isCreatedByUser: Boolean,
    val model: String?,
    val endpoint: String?,
    val searchText: String?,
    val visibleLength: Int?,
)

/** The `messages` columns a side row is built from. */
data class MessageIndexSource(
    val rowId: Long,
    val messageId: String,
    val accountId: String,
    val conversationId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isCreatedByUser: Boolean,
    val model: String?,
    val endpoint: String?,
    val text: String?,
    val content: String?,
) {
    fun toSearchEntity(): MessageSearchEntity = searchTextOf(text, content).let { indexed ->
        MessageSearchEntity(
        messageId = messageId,
        accountId = accountId,
        conversationId = conversationId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isCreatedByUser = isCreatedByUser,
        model = model,
        endpoint = endpoint,
        searchText = indexed.text,
        visibleLength = indexed.visibleLength,
        )
    }
}
