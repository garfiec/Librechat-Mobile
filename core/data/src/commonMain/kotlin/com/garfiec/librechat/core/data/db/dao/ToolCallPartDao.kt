package com.garfiec.librechat.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.garfiec.librechat.core.common.identity.CrossAccount
import com.garfiec.librechat.core.data.db.entity.ToolCallPartEntity
import com.garfiec.librechat.core.data.db.entity.ToolCallPartSize

/**
 * Stored full tool calls ([ToolCallPartEntity]). Every query is account-scoped by hand:
 * `tool_call_parts` is not one of the tables the tenancy detekt rule infers.
 */
@Dao
interface ToolCallPartDao {

    @Query(
        "SELECT * FROM tool_call_parts WHERE accountId = :accountId AND conversationId = :conversationId " +
            "AND messageId IN (:messageIds)",
    )
    suspend fun forMessages(accountId: String, conversationId: String, messageIds: List<String>): List<ToolCallPartEntity>

    /** Identities stored for [conversationId], for the prefetcher to skip what it already holds. */
    @Query(
        "SELECT messageId, toolCallId, stepId, agentId, chars FROM tool_call_parts " +
            "WHERE accountId = :accountId AND conversationId = :conversationId",
    )
    suspend fun sizesForConversation(accountId: String, conversationId: String): List<ToolCallPartSize>

    @Query("SELECT COUNT(*) FROM messages WHERE messageId = :messageId AND accountId = :accountId")
    suspend fun countCachedMessage(messageId: String, accountId: String): Int

    @Upsert
    suspend fun upsert(row: ToolCallPartEntity)

    /** Newest first, so the budget prune keeps a prefix. */
    @Query(
        "SELECT messageId, toolCallId, stepId, agentId, chars FROM tool_call_parts " +
            "WHERE accountId = :accountId ORDER BY storedAt DESC",
    )
    suspend fun sizesNewestFirst(accountId: String): List<ToolCallPartSize>

    @Query(
        "DELETE FROM tool_call_parts WHERE accountId = :accountId AND messageId = :messageId " +
            "AND toolCallId = :toolCallId AND stepId = :stepId AND agentId = :agentId",
    )
    suspend fun delete(accountId: String, messageId: String, toolCallId: String, stepId: String, agentId: String)

    /**
     * Writes [rows] whose message is cached for its account (the rest would fail the foreign key
     * or attach to another account's row), then drops the oldest rows past [budgetChars]. Returns
     * how many were written. [CrossAccount]: every statement is scoped by the row's own accountId.
     */
    @CrossAccount
    @Transaction
    suspend fun storeWithinBudget(rows: List<ToolCallPartEntity>, budgetChars: Long): Int {
        var written = 0
        rows.forEach { row ->
            if (countCachedMessage(row.messageId, row.accountId) > 0) {
                upsert(row)
                written++
            }
        }
        rows.mapTo(HashSet()) { it.accountId }.forEach { accountId ->
            var total = 0L
            sizesNewestFirst(accountId).forEach { size ->
                total += size.chars
                if (total > budgetChars) delete(accountId, size.messageId, size.toolCallId, size.stepId, size.agentId)
            }
        }
        return written
    }
}
