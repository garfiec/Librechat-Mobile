package com.garfiec.librechat.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("parentMessageId"),
        Index(value = ["conversationId", "createdAt"]),
    ],
)
data class MessageEntity(
    @PrimaryKey
    val messageId: String,
    val conversationId: String,
    val parentMessageId: String?,
    val sender: String?,
    val text: String?,
    val content: String?,
    val isCreatedByUser: Boolean,
    val model: String?,
    val endpoint: String?,
    val iconURL: String?,
    val unfinished: Boolean = false,
    val error: Boolean = false,
    val finishReason: String?,
    val tokenCount: Int?,
    val feedback: String?,
    val files: String?,
    val attachments: String?,
    val metadata: String?,
    /** JSON-encoded `List<String>` of verbatim quote excerpts (v0.8.7). Null when absent. */
    val quotes: String? = null,
    /** JSON-encoded `List<String>`: skill names the user picked for this turn. Null when absent. */
    val manualSkills: String? = null,
    /** JSON-encoded `List<String>`: skills the server primed on its own (`always-apply`). Null when absent. */
    val alwaysAppliedSkills: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Row-tenancy owner (self-owning; stamped from the carried command, not only transitively via conversation). See ConversationEntity.accountId. */
    val accountId: String? = null,
)
