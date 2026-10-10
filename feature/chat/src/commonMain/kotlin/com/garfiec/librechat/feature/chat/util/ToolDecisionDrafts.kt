package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.PendingActionPayload
import com.garfiec.librechat.core.model.ToolApprovalDecisions
import com.garfiec.librechat.core.model.request.ToolApprovalResolution
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * One paused tool call's in-progress decision inside a `tool_approval` pause, keyed by its
 * `tool_call_id`.
 *
 * Held in `MessagesState.toolDecisionDrafts`, not in the panel's own state: the panel switches
 * between its compact and wide layouts on a fold or rotation, and an eight-call batch is eight
 * decisions to redo if that drops them.
 */
@Immutable
data class ToolDecisionDraft(
    val decision: String? = null,
    val editedArguments: String = "",
    val responseText: String = "",
)

/**
 * Converts a draft to the wire resolution, or null while it is still incomplete — the two
 * payload-bearing decisions carry their payload or nothing (`edit` needs valid JSON arguments,
 * `respond` needs text). Returning null is what keeps the submit disabled rather than letting the
 * server 400 the batch.
 */
internal fun ToolDecisionDraft.toResolution(toolCallId: String): ToolApprovalResolution? {
    val decision = this.decision ?: return null
    return when (decision) {
        ToolApprovalDecisions.EDIT -> {
            val parsed = editedArguments.parseToolArgumentsOrNull() ?: return null
            ToolApprovalResolution(toolCallId = toolCallId, decision = decision, editedArguments = parsed)
        }
        ToolApprovalDecisions.RESPOND -> {
            val text = responseText.trim().ifEmpty { return null }
            ToolApprovalResolution(toolCallId = toolCallId, decision = decision, responseText = text)
        }
        else -> ToolApprovalResolution(toolCallId = toolCallId, decision = decision)
    }
}

/** Parses edited arguments, requiring a JSON OBJECT — the SDK's `updatedInput` is a map. */
internal fun String.parseToolArgumentsOrNull(): JsonObject? = try {
    Json.parseToJsonElement(this) as? JsonObject
} catch (_: Exception) {
    null
}

/**
 * The whole batch's resolutions, in payload order, or null until every call is decided AND
 * complete: the server 400s a partial batch, so nothing goes up before then.
 */
internal fun toolBatchResolutions(
    payload: PendingActionPayload,
    drafts: Map<String, ToolDecisionDraft>,
): List<ToolApprovalResolution>? {
    if (payload.actionRequests.isEmpty()) return null
    val resolutions = payload.actionRequests.mapNotNull { request ->
        drafts[request.toolCallId]?.toResolution(request.toolCallId)
    }
    return resolutions.takeIf { it.size == payload.actionRequests.size }
}

/**
 * Which decisions the policy permits for each call, joined by `tool_call_id` and never by
 * position: one batch can hold the same tool twice (a model fanning out parallel calls), and
 * by-position would then apply the wrong policy.
 */
internal fun PendingActionPayload.allowedDecisionsByCallId(): Map<String, List<String>> =
    reviewConfigs.associate { it.toolCallId to it.allowedDecisions }

/**
 * Fallback policy for a call the batch's `review_configs` doesn't mention. Approve/reject only:
 * those are the two decisions every policy permits, so a missing config can't produce a control
 * the server would 403.
 */
internal val DEFAULT_ALLOWED_TOOL_DECISIONS =
    listOf(ToolApprovalDecisions.APPROVE, ToolApprovalDecisions.REJECT)

/**
 * The index of the call after [fromIndex] whose decision is still missing or incomplete,
 * searching forward and wrapping back round to [fromIndex] itself, or null when every call is
 * resolvable. The tool-approval twin of [nextBlankQuestionIndex].
 */
internal fun nextUndecidedCallIndex(
    toolCallIds: List<String>,
    drafts: Map<String, ToolDecisionDraft>,
    fromIndex: Int,
): Int? = (1..toolCallIds.size)
    .map { (fromIndex + it) % toolCallIds.size }
    .firstOrNull { index ->
        val id = toolCallIds[index]
        drafts[id]?.toResolution(id) == null
    }
