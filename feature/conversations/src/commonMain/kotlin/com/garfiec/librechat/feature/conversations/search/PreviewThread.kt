package com.garfiec.librechat.feature.conversations.search

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** How long a server hit stays selected before its conversation is fetched. */
internal const val FETCH_DELAY_MS = 250L

/**
 * The branch of a conversation a preview shows: from its first message down to a target, then on
 * through the newest reply at each step, the one the chat shows by default.
 *
 * Built from the cached (or just fetched) messages alone; a parent missing from them ends the walk
 * up, so an orphaned message starts its own thread.
 */
internal object PreviewThread {

    /** The thread through [targetId], or empty when it isn't among [messages]. */
    fun of(messages: List<Message>, targetId: String): List<Message> {
        val byId = messages.associateBy { it.messageId }
        val target = byId[targetId] ?: return emptyList()
        val seen = HashSet<String>()
        val thread = ArrayList<Message>()
        var node: Message? = target
        while (node != null && seen.add(node.messageId)) {
            thread += node
            node = node.parentMessageId?.let(byId::get)
        }
        thread.reverse()
        // Sorted by creation, so the last child is the newest; a stable sort keeps list order on ties.
        val children = messages.sortedBy { it.createdAt }.groupBy { it.parentMessageId }
        var tip = target
        while (true) {
            val next = children[tip.messageId]?.lastOrNull { it.messageId !in seen } ?: break
            seen += next.messageId
            thread += next
            tip = next
        }
        return thread
    }

    /** The conversation's newest thread: the one through its newest message. */
    fun latest(messages: List<Message>): List<Message> =
        messages.maxWithOrNull(compareBy { it.createdAt })?.let { of(messages, it.messageId) }.orEmpty()
}

/**
 * A conversation's messages from the cache, or from the server when [fetch] is set and the cache
 * doesn't hold [messageId] (or anything, without one). Null when that fetch fails.
 */
internal suspend fun MessageRepository.conversationMessages(conversationId: String, messageId: String?, fetch: Boolean): List<Message>? {
    val cached = observeMessages(conversationId).first()
    val complete = if (messageId != null) cached.any { it.messageId == messageId } else cached.isNotEmpty()
    if (complete || !fetch) return cached
    // Arrowing through server hits passes over most of them: fetch only where the user stops.
    delay(FETCH_DELAY_MS)
    return (getMessages(conversationId) as? Result.Success)?.data
}
