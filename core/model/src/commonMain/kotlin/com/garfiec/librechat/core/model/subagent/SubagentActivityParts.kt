package com.garfiec.librechat.core.model.subagent

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.RunStepStatus
import com.garfiec.librechat.core.model.ToolCallType
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.FunctionCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns a child's activity into the content parts the app already renders.
 *
 * The thread view models user-visible activity in its own four-armed shape rather than as the
 * LangChain messages that produced it, so it does NOT arrive as `MessageContentPart` the way
 * `AgentToolCall.subagentContent` does. Every arm has an exact counterpart in the part model
 * though, and the renderers for all four already exist — including the activity-label grouping —
 * so this converts rather than duplicating them.
 *
 * An arm this client does not recognize is DROPPED, not rendered as an empty row: a future
 * `type` carries fields nothing here can lay out, and a blank line in the middle of a trace reads
 * as a bug. The row still decoded, so the rest of the thread is unaffected.
 */
fun List<SubagentActivityItem>.toContentParts(): List<MessageContentPart> = mapNotNull { it.toPart() }

private fun SubagentActivityItem.toPart(): MessageContentPart? = when (type) {
    SubagentActivityType.WRITING -> MessageContentPart(
        type = ContentType.TEXT,
        text = text.orEmpty(),
    )

    SubagentActivityType.REASONING -> MessageContentPart(
        type = ContentType.THINK,
        // Absent on projections persisted before reasoning was retained. Upstream renders those
        // as a bare marker, and an empty think block is exactly that.
        think = text.orEmpty(),
    )

    SubagentActivityType.ACTIVITY_LABEL -> MessageContentPart(
        type = ContentType.ACTIVITY_LABEL,
        activityLabel = label,
        activityLabelType = labelType,
        activityStartIndex = activityStartIndex,
        activityEndIndex = activityEndIndex,
        activityCount = activityCount,
        agentIds = agentIds,
        status = status,
        pending = pending,
        toolCallIds = toolCallIds,
    )

    SubagentActivityType.TOOL -> MessageContentPart(
        type = ContentType.TOOL_CALL,
        toolCall = AgentToolCall(
            type = ToolCallType.FUNCTION,
            id = toolCallId,
            name = name,
            // The view projects arguments as a bounded STRING, while a live tool call carries
            // structured JSON. Wrapped as a primitive so the card shows what the server sent
            // instead of failing to decode something that was never an object.
            args = input?.let(::JsonPrimitive),
            // …and carried on `function` as well, because that is the field the card actually
            // draws its Input block from (`toolCall.function.arguments`). With only `args` set,
            // every tool row in the thread viewer renders its output and silently omits what the
            // tool was called with.
            function = FunctionCall(name = name, arguments = input, output = output),
            output = output,
            inputValidationError = inputValidationError.takeIf { it == true },
            // The projection's own verdict, in the field every card reads it from. Without it a
            // FAILED or CANCELLED child tool call renders here exactly like one that succeeded.
            // The live path stamps `runStepStatus` for the same reason; this REST path rebuilds
            // the identical card, so it has to stamp it too. [SubagentToolStatus] spells its
            // terminal values the way `RunStepStatus` does; `running` maps to null and degrades
            // to the heuristic, which is what it should do.
            runStepStatus = RunStepStatus.fromWire(status),
        ),
    )

    else -> null
}

/** Whether anything in this activity was cut short, so the view can say so once rather than per row. */
val List<SubagentActivityItem>.hasTruncatedContent: Boolean
    get() = any {
        it.textTruncated == true || it.labelTruncated == true ||
            it.inputTruncated == true || it.outputTruncated == true
    }
