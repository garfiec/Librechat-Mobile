package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.SubagentAttachmentCall

/**
 * The child calls a previewed `subagent` call's files were saved under, while its transcript is
 * left out (`subagentContentOmitted`). The transcript walk ([outputToolCallIds]) finds nothing
 * then, so these are what route the files to the card before it is opened. Once the transcript is
 * hydrated the walk yields the same ids.
 */
fun omittedTranscriptCalls(call: AgentToolCall?): List<SubagentAttachmentCall> =
    if (call?.subagentContentOmitted == true) call.subagentAttachmentCalls.orEmpty() else emptyList()

/**
 * [attachments] less the ones a hint claims by id but whose scope disagrees (upstream
 * `ownsAttachment`, non-exact): a scope both the hint and the file carry must match. A file some
 * other id in [otherIds] claims is never dropped here.
 */
fun attachmentsOwnedByHints(
    attachments: List<Attachment>,
    hints: List<SubagentAttachmentCall>,
    otherIds: List<String>,
): List<Attachment> {
    if (hints.isEmpty()) return attachments
    val hintIds = hints.mapTo(HashSet()) { it.id }
    return attachments.filter { attachment ->
        val id = attachment.toolCallId
        id == null || id !in hintIds || id in otherIds || hints.any { it.owns(attachment) }
    }
}

private fun SubagentAttachmentCall.owns(attachment: Attachment): Boolean =
    id == attachment.toolCallId && agrees(agentId, attachment.agentId) && agrees(stepId, attachment.stepId)

private fun agrees(own: String?, file: String?): Boolean = file == null || own == null || own == file
