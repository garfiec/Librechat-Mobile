package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.garfiec.librechat.feature.chat.viewmodel.SubagentTrace

/** A `subagent` call a message renders, with the agent running it when known. */
data class SubagentOwner(val toolCallId: String, val agentId: String?)

/**
 * Hands each tool file no rendered call claims to the message's `subagent` call, by rewriting its
 * `toolCallId` to that call's (upstream web's subagent attachment routing). The fallback: for a
 * previewed saved message the server's `/subagent-calls` answer places files first
 * ([withStoredSubagentHints]), and this guesses only what that leaves.
 *
 * A tool inside a subagent run saves its files under the CHILD call's id, which appears only in
 * the subagent's transcript. After a mid-run resume there is no transcript yet (run steps are not
 * replayed), so the file matches no call and would render nowhere until the reply is saved. With
 * one subagent call it goes there; with several, to the one whose agent stamped the file, else
 * the last. Once the transcript names the child call, [claimedIds] covers it and the file stays
 * with its real owner, so it never renders twice.
 *
 * Only stored files move: metadata attachments (memory writes, search sources) keep their own
 * orphan handling, and a message with no `subagent` call is returned unchanged.
 */
fun adoptUnclaimedSubagentFiles(
    attachments: List<Attachment>,
    claimedIds: Collection<String>,
    subagents: List<SubagentOwner>,
): List<Attachment> {
    if (subagents.isEmpty() || attachments.isEmpty()) return attachments
    val claimed = claimedIds as? Set<String> ?: claimedIds.toSet()
    var changed = false
    val adopted = attachments.map { attachment ->
        val callId = attachment.toolCallId
        if (callId.isNullOrEmpty() || callId in claimed || !attachment.isStoredToolFile()) return@map attachment
        val owner = subagents.singleOrNull()
            ?: subagents.firstOrNull { it.agentId != null && it.agentId == attachment.agentId }
            ?: subagents.last()
        changed = true
        attachment.copy(toolCallId = owner.toolCallId)
    }
    return if (changed) adopted else attachments
}

/** A file the tool produced: a stored one, or a code output's download fallback (a `filepath`, no id). */
internal fun Attachment.isStoredToolFile(): Boolean =
    (!fileId.isNullOrBlank() || !filepath.isNullOrBlank()) &&
        memory == null && webSearch == null && fileSearch == null && uiResources == null

/**
 * [adoptUnclaimedSubagentFiles] for a saved message: its calls claim their own files and, through
 * `subagent_content`, their children's ([outputToolCallIds]).
 */
fun List<Attachment>.withSubagentFilesAdopted(parts: List<MessageContentPart>?): List<Attachment> {
    if (parts.isNullOrEmpty() || isEmpty()) return this
    val subagents = parts.mapNotNull { part ->
        val call = part.toolCall ?: return@mapNotNull null
        val name = call.name ?: call.function?.name
        call.id?.takeIf { it.isNotEmpty() && name.equals(ToolConstants.SUBAGENT, ignoreCase = true) }
            // The agent that ran it, so a file is matched to the right one of several subagents.
            ?.let { SubagentOwner(it, agentId = call.subagentIdentity?.subagentAgentId?.ifEmpty { null }) }
    }
    if (subagents.isEmpty()) return this
    return adoptUnclaimedSubagentFiles(this, outputToolCallIds(parts).toSet(), subagents)
}

/**
 * [adoptUnclaimedSubagentFiles] for the reply streaming now: the live cards claim their own ids,
 * and a live subagent's agent comes from its trace.
 */
fun List<Attachment>.withLiveSubagentFilesAdopted(
    calls: List<ActiveToolCall>,
    traces: Map<String, SubagentTrace>,
): List<Attachment> {
    if (calls.isEmpty() || isEmpty()) return this
    val subagents = calls.filter { it.name.equals(ToolConstants.SUBAGENT, ignoreCase = true) }
        .map { SubagentOwner(it.id, traces[it.id]?.subagentAgentId) }
    if (subagents.isEmpty()) return this
    return adoptUnclaimedSubagentFiles(this, calls.mapTo(HashSet()) { it.id }, subagents)
}
