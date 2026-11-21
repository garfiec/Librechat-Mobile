package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Message

/**
 * A run this screen attached to rather than started, with the ids the server's status gave for
 * the turn it answers. Both are null on a server whose status carries no snapshot.
 */
@Immutable
data class AttachedRun(
    val requestId: String? = null,
    val responseId: String? = null,
) {
    /**
     * A saved copy of the reply this run is streaming: the partial the server wrote when the last
     * subscriber left, under the reply's id (or, before the run named one, the request's id plus `_`).
     */
    fun isLiveReply(message: Message): Boolean =
        !message.isCreatedByUser &&
            (message.messageId == responseId || (requestId != null && message.messageId == "${requestId}_"))
}

/**
 * The active path while the attached run streams: without any saved copy of its reply, and cut at
 * the message it answers when that is in the tree. The copy has to go by id as well as by the cut:
 * the request it hangs from can be missing from the tree (a first turn the server saves under an
 * id of its own), and the copy then reads as a root of its own and would take the path over.
 */
fun AttachedRun.displayPath(messages: List<Message>, activeBranches: Map<String, Int>): List<MessageNode> {
    val shown = if (messages.any(::isLiveReply)) messages.filterNot(::isLiveReply) else messages
    return buildActiveMessagePath(shown, activeBranches, anchorIn(shown, activeBranches))
}

/** Whether [displayPath] would show [messages] differently from the plain path. */
fun AttachedRun.changesPath(messages: List<Message>, activeBranches: Map<String, Int>): Boolean =
    messages.any(::isLiveReply) || anchorIn(messages, activeBranches) != null

/**
 * Where the attached run's reply hangs in [messages], or null while that is not known. With
 * neither id, the parent of an `unfinished` reply at the tail of the path: the partial the server
 * saved when the last subscriber left.
 */
private fun AttachedRun.anchorIn(messages: List<Message>, activeBranches: Map<String, Int>): String? {
    if (requestId != null || responseId != null) return requestId?.takeIf { id -> messages.any { it.messageId == id } }
    val tail = buildActiveMessagePath(messages, activeBranches).lastOrNull()?.message ?: return null
    return tail.parentMessageId.takeIf { !tail.isCreatedByUser && tail.unfinished }
}
