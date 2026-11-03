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
)

/**
 * Where the attached run's reply hangs in [messages], or null while that is not known: the request,
 * else the parent of the reply's saved row. With neither id, the parent of an `unfinished` reply at
 * the tail of the path, which is the partial the server saved when the last subscriber left.
 */
fun AttachedRun.anchorIn(messages: List<Message>, activeBranches: Map<String, Int>): String? {
    val ids = messages.mapTo(HashSet()) { it.messageId }
    requestId?.let { return it.takeIf { id -> id in ids } }
    responseId?.let { id ->
        return messages.firstOrNull { it.messageId == id }?.parentMessageId?.takeIf { it in ids }
    }
    val tail = buildActiveMessagePath(messages, activeBranches).lastOrNull()?.message ?: return null
    return tail.parentMessageId.takeIf { !tail.isCreatedByUser && tail.unfinished }
}
