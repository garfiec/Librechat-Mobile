package com.garfiec.librechat.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * The full content of one previewed tool call (v0.8.9-rc1 `?toolPreviews=1`), fetched from
 * `GET …/parts/:partIndex` for a finished message, so a later session (or an offline one) opens the
 * card without fetching it again.
 *
 * **Never merged into `messages`.** The message row stays exactly what the server last sent, preview
 * markers included, so a refresh can replace it freely and nothing ever reads a shortened call as
 * whole. A stored row applies to a preview only while it is still a full copy of it (same identity,
 * same full lengths); a call that changed on the server is fetched again and overwrites the row.
 *
 * Identity columns use `""` for an absent [stepId] / [agentId] (a primary key column cannot be null),
 * matching the part route, which treats an empty value as absent.
 *
 * The cascade drops the row wherever its message is deleted (a conversation delete, a prune, an
 * account purge, a refresh that no longer holds the message), so no delete path needs to know this
 * table exists. A row cannot be written for a message that is not cached: the key fails, and the
 * write is skipped. `messages` is never written with REPLACE (see `MessageSearchEntity`), so an
 * upsert of the parent never cascades this away.
 */
@Entity(
    tableName = "tool_call_parts",
    primaryKeys = ["messageId", "toolCallId", "stepId", "agentId"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["messageId"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["accountId", "storedAt"])],
)
data class ToolCallPartEntity(
    val messageId: String,
    val toolCallId: String,
    val stepId: String,
    val agentId: String,
    val accountId: String,
    val conversationId: String,
    /** The `AgentToolCall` as JSON. */
    val toolCall: String,
    /** What the row costs: output, args and serialised transcript, in characters. */
    val chars: Long,
    /** Wall clock of the write; the oldest go first past the account's budget. */
    val storedAt: Long,
)

/** One row's size, for the budget prune. */
data class ToolCallPartSize(
    val messageId: String,
    val toolCallId: String,
    val stepId: String,
    val agentId: String,
    val chars: Long,
)
