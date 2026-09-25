package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.RunStepStatus

/**
 * How a tool call ended, as far as the user is concerned.
 *
 * Two cards answer this question — the live [StreamingToolCallCard] from `ActiveToolCall`, and
 * [GenericToolCallCard] from the persisted `AgentToolCall.runStepStatus` — so the rule is single
 * sourced rather than written out twice. They render the answer differently (a reloaded part has
 * no "running" state, and gets no success badge it never had), but they must not disagree about
 * what a closed step *was*.
 */
internal enum class ToolCallVerdict {
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

/**
 * [closedStatus] is the run's own terminal verdict, from `on_run_step_closed` (v0.8.8-rc2) live or
 * from the server-stamped field on a reloaded part. It wins outright: a closed step is never
 * running, whatever else the stream did or did not send, and a green check on a step the user
 * stopped — or one that failed — reads as a result they never got.
 *
 * [isComplete] is the pre-rc2 heuristic and the fallback for every server and endpoint that emits
 * no close event, where it decides alone exactly as it always did.
 */
internal fun toolCallVerdict(closedStatus: RunStepStatus?, isComplete: Boolean): ToolCallVerdict =
    when (closedStatus) {
        RunStepStatus.CANCELLED -> ToolCallVerdict.CANCELLED
        RunStepStatus.FAILED -> ToolCallVerdict.FAILED
        RunStepStatus.COMPLETED -> ToolCallVerdict.COMPLETED
        null -> if (isComplete) ToolCallVerdict.COMPLETED else ToolCallVerdict.RUNNING
    }
