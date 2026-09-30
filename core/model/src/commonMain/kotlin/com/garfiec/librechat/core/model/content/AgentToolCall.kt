package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.RunStepStatus
import com.garfiec.librechat.core.model.ToolCallType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AgentToolCall(
    val type: ToolCallType? = null,
    val name: String? = null,
    val args: JsonElement? = null,
    val id: String? = null,
    val output: String? = null,
    val auth: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val function: FunctionCall? = null,
    /**
     * For a `subagent` tool_call: the child agent's full run trace (reasoning /
     * tool calls / final text) harvested onto the parent at message-save time
     * (server `finalizeSubagentContent`, v0.8.6). Present only on reload — live
     * progress arrives via `on_subagent_update` SSE events. Lets the trace
     * survive a refresh, mirroring the web client's persisted `subagent_content`.
     */
    @SerialName("subagent_content") val subagentContent: List<MessageContentPart>? = null,
    /**
     * True when the server rejected this call's arguments against the tool's schema, so the tool
     * never ran. Stamped onto the persisted part at run-step completion, and sent as `true` or
     * not at all — never `false`.
     *
     * Only `ask_user_question` reads it today: a question whose args failed validation was never
     * put to the user, and rendering it as one they declined to answer would be a lie.
     */
    val inputValidationError: Boolean? = null,
    /**
     * Terminal lifecycle status of the run step that produced this call (v0.8.8-rc2), stamped by
     * the server from `on_run_step_closed`. Absent on parts saved before the event existed and on
     * endpoints that never emit it, where the card falls back to inferring completion from the
     * presence of output.
     */
    val runStepStatus: RunStepStatus? = null,
    /**
     * Wall-clock milliseconds the run step lived, from the same event: its TOTAL lifetime, which
     * includes argument generation, not the tool's own execution time (upstream labels it "Total
     * elapsed" and shows it only when neither split below was measured). Absent means "not
     * derivable" — the server only writes it when both timestamps were present and ordered —
     * never "instant".
     */
    val runStepDurationMs: Long? = null,
    /** Host-reported close time of the run step, epoch milliseconds (v0.8.8). Absent on older content. */
    val runStepClosedAt: Long? = null,
    /**
     * From the first observed argument fragment to the SDK's handoff to execution (v0.8.8,
     * `on_tool_preparation` → `on_tool_calls_dispatched`). Omitted by the server rather than
     * reported when either stamp is missing or the two disagree in order.
     */
    val toolPreparationDurationMs: Long? = null,
    /**
     * From the SDK's handoff to this call's result (v0.8.8) — the tool's own time, not the MCP
     * round trip or database time. Same omission rule as [toolPreparationDurationMs].
     */
    val toolExecutionDurationMs: Long? = null,
)
