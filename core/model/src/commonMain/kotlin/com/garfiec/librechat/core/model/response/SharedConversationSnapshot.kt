package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Message
import kotlinx.serialization.Serializable

/**
 * `GET /api/share/:shareId`: a shared conversation as a viewer sees it.
 *
 * Every id in it is anonymized per request ([conversationId] and each message's ids), so nothing
 * here can be matched against the owner's own conversation. [messages] holds every branch, in a
 * stable server order; a fork addresses a branch by its tip's index in this list.
 *
 * Files and attachments appear only when the link includes files. Their paths point at
 * `/api/share/<shareId>/files/<fileId>`, and a file whose `source` is
 * [com.garfiec.librechat.core.model.FILE_SOURCE_TEXT] has no stored bytes: the server serves its
 * extracted text as `text/plain`.
 *
 * @param updatedAt the revision a fork must quote back as `shareRevision`.
 * @param hasConfiguredSender the link was published with a configured sender label, so the
 *   viewer withholds the model name. The server omits it when false.
 * @param langfuseSessionUrl a trace link the server adds only for viewers allowed to see it.
 */
@Serializable
data class SharedConversationSnapshot(
    val shareId: String,
    val title: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val conversationId: String? = null,
    val messages: List<Message> = emptyList(),
    val hasConfiguredSender: Boolean = false,
    val langfuseSessionUrl: String? = null,
)
