package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The app's JSON export. The top-level [conversationId], [endpoint], [title], [branches],
 * [recursive], [options] and [messages] are web's export shape (`useExportConversation`), which is
 * what the server's import route recognises (`getImporter` needs a top-level `conversationId` and
 * `messages`). [conversation], [exportedAt] and [version] are the app's own; the server ignores them.
 */
@Serializable
data class ConversationExport(
    val conversationId: String? = null,
    val endpoint: String? = null,
    val title: String? = null,
    /** Every branch is exported, as a flat list linked by `parentMessageId`. */
    val branches: Boolean? = null,
    val recursive: Boolean? = null,
    /** The conversation's endpoint and model settings, which the import applies to the new chat. */
    val options: JsonObject? = null,
    val conversation: Conversation,
    val messages: List<Message>,
    val exportedAt: Long,
    val version: Int = 1,
)
