package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.response.SubagentAttachmentCallsEntry
import com.garfiec.librechat.core.model.response.SubagentAttachmentFile

/**
 * Subagent file delivery, slice 2 (upstream `useStoredSubagentHints`, `GET …/subagent-calls`).
 *
 * A previewed `subagent` call (`subagentContentOmitted`) carries `subagentAttachmentCalls`: the child
 * calls the message's files were saved under **when it loaded**. A file that reaches the message later
 * (a background task finishing, a reload after the run) names a child call no hint knows, so it
 * matched nothing and slice 1 (`withSubagentFilesAdopted`) guessed its card. These are the files the
 * server is asked about; the answer is added to the right call's hints, so the file goes to the exact
 * subagent that produced it. Slice 1 still places whatever the answer leaves out.
 */

/**
 * The stored tool files of [message] no call claims, as the route's query, when the message holds a
 * previewed subagent call (only then can the server know more than the message does). Empty otherwise.
 * Sorted and distinct, so an unchanged set asks once.
 */
fun subagentFilesInDoubt(message: Message): List<SubagentAttachmentFile> {
    val content = message.content.orEmpty()
    if (content.none { it.toolCall?.subagentContentOmitted == true }) return emptyList()
    val attachments = message.attachments.orEmpty()
    if (attachments.isEmpty()) return emptyList()
    val claimed = outputToolCallIds(content).toHashSet()
    return attachments.asSequence()
        .filter { it.isStoredToolFile() }
        .mapNotNull { attachment ->
            val id = attachment.toolCallId?.takeIf { it.isNotEmpty() && it !in claimed } ?: return@mapNotNull null
            SubagentAttachmentFile(
                toolCallId = id,
                agentId = attachment.agentId?.takeIf { it.isNotEmpty() },
                stepId = attachment.stepId?.takeIf { it.isNotEmpty() },
            )
        }
        .distinct()
        .sortedWith(compareBy({ it.toolCallId }, { it.agentId.orEmpty() }, { it.stepId.orEmpty() }))
        .toList()
}

/**
 * [nodes] with the server's answers ([hints], by message id) added to each previewed subagent call's
 * `subagentAttachmentCalls`, matched by the call's identity (`id + stepId + part.agentId`). A pure
 * per-part substitution over the built display path, like `withHydratedToolParts`; returns [nodes]
 * itself when nothing changed.
 */
fun List<MessageNode>.withStoredSubagentHints(hints: Map<String, List<SubagentAttachmentCallsEntry>>): List<MessageNode> {
    if (hints.isEmpty()) return this
    var changed = false
    val result = map { node ->
        val entries = hints[node.message.messageId] ?: return@map node
        val content = node.message.content ?: return@map node
        val hinted = content.withHints(entries)
        if (hinted === content) {
            node
        } else {
            changed = true
            node.copy(message = node.message.copy(content = hinted))
        }
    }
    return if (changed) result else this
}

private fun List<MessageContentPart>.withHints(entries: List<SubagentAttachmentCallsEntry>): List<MessageContentPart> {
    var changed = false
    val result = map { part ->
        val call = part.toolCall
        if (part.type != ContentType.TOOL_CALL || call?.subagentContentOmitted != true) return@map part
        val identity = ToolPartIdentity.of(call, part.agentId) ?: return@map part
        val added = entries.filter { entry ->
            entry.toolCallId == identity.toolCallId &&
                entry.stepId?.ifEmpty { null } == identity.stepId &&
                entry.agentId?.ifEmpty { null } == identity.agentId
        }.flatMap { it.calls }
        val existing = call.subagentAttachmentCalls.orEmpty()
        val merged = (existing + added).distinct()
        if (merged.size == existing.size) return@map part
        changed = true
        part.copy(toolCall = call.copy(subagentAttachmentCalls = merged))
    }
    return if (changed) result else this
}
