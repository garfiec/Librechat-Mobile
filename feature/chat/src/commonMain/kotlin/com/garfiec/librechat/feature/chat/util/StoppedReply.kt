package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message

/** `Constants.TOOL_CALL_LIMIT_FINISH_REASON`: the tool-call-limit notice offers its own ways on. */
private const val TOOL_CALL_LIMIT_FINISH_REASON = "tool_call_limit"

/**
 * The reply a Continue can extend: the last one on the path, when the server saved it cut short.
 * Null when there is none.
 *
 * Web's rule (`useGenerationsByLatest.continueSupported`) needs a `finish_reason` other than `stop`,
 * which a v0.8.8 agents reply never carries except for the tool-call limit, which it excludes. A
 * stopped or dropped reply is saved `unfinished` instead, so that counts too. A reply waiting on a
 * tool approval or a question is also `unfinished`; [hasPendingAction] keeps the chip off it, since
 * the run resumes from the docked panel.
 *
 * Continue replays the user turn above the reply, so a reply with none (a chained or imported one)
 * and a compaction summary get nothing. So does a comparison's reply unless [comparing]: Continue
 * on it would save the primary's answer alone and drop the second model's. While comparing, the
 * panes offer "Keep and continue" instead, since a Continue that carries both lanes makes the
 * server rewrite both from scratch.
 */
internal fun stoppedReplyId(
    displayMessages: List<MessageNode>,
    isStreaming: Boolean,
    hasPendingAction: Boolean,
    comparing: Boolean,
): String? {
    if (isStreaming || hasPendingAction) return null
    val reply = displayMessages.lastOrNull()?.message ?: return null
    val turn = displayMessages.getOrNull(displayMessages.lastIndex - 1)?.message ?: return null
    val replaysTurn = turn.isCreatedByUser && reply.parentMessageId == turn.messageId
    val eligible = !reply.isCreatedByUser && !reply.error && replaysTurn &&
        !reply.isSummary() && hasParallelParts(reply) == comparing
    return reply.messageId.takeIf { eligible && reply.wasCutShort() }
}

private fun Message.wasCutShort(): Boolean =
    unfinished || (finishReason != null && finishReason != "stop" && finishReason != TOOL_CALL_LIMIT_FINISH_REASON)

private fun Message.isSummary(): Boolean =
    content?.let { parts -> parts.isNotEmpty() && parts.all { it.type == ContentType.SUMMARY } } == true
