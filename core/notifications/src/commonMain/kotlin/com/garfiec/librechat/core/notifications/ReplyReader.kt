package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.result.getOrNull
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.MessageRepository

/** A finished reply as a notification shows it. */
data class FinishedReply(
    val title: String?,
    val preview: String,
    val failed: Boolean,
    val isTemporary: Boolean,
)

/** Reads a chat's latest reply from the server, for a notification about it. */
class ReplyReader(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) {
    suspend fun read(conversationId: String, accountId: AccountId): FinishedReply {
        // From the server: the cached row predates the run, so its title and last-reply id are stale.
        val conversation = conversationRepository.refreshConversation(conversationId, accountId).getOrNull()
        val messages = messageRepository.getMessages(conversationId, toolPreviews = false).getOrNull().orEmpty()
        val reply = conversation?.lastResponseMessageId
            ?.let { id -> messages.firstOrNull { it.messageId == id } }
            ?: messages.lastOrNull { !it.isCreatedByUser }
        return FinishedReply(
            title = conversation?.title,
            preview = reply?.let(::notificationPreviewOf).orEmpty(),
            failed = reply?.error == true,
            isTemporary = conversation?.isTemporary == true,
        )
    }
}
