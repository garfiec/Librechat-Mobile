package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.RunStepStatus
import com.garfiec.librechat.core.model.ToolCallType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class AgentToolCall(
    val type: ToolCallType? = null,
    val name: String? = null,
    val args: JsonElement? = null,
    val id: String? = null,
    /**
     * The server's run-step identity for this call (v0.8.9-rc1). Unlike a provider's call [id] it is
     * unique across turns, and the full-part route matches on it exactly: a call that carries one
     * is only found when it is sent back.
     */
    val stepId: String? = null,
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
    /** First observed argument fragment, epoch ms, while the call is still being prepared (v0.8.9-rc1). */
    val toolPreparationStartedAt: Long? = null,
    /** The SDK's handoff to execution, epoch ms (v0.8.9-rc1). Not proof the tool itself started. */
    val toolDispatchedAt: Long? = null,
    /**
     * `attached_workspace` when the call resolved to the attached-workspace `bash_tool` (v0.8.9-rc1),
     * stamped by the server at run-step completion. The sandbox tool shares the name, so this is
     * the only sign the output ends in an exit-status trailer; never derive it from the text.
     */
    val executor: String? = null,
    /**
     * Stamped when a detached task's final output replaced the dispatch handle in [output]: the
     * only durable signal that the call ran in the background. When set, [runStepDurationMs] is
     * dispatch time rather than the task's runtime, so the code-execution card omits it (as
     * upstream does).
     */
    val backgrounded: Boolean? = null,
    /** The settled background task behind this call. See [BackgroundToolTask]. */
    val backgroundTask: BackgroundToolTask? = null,
    /**
     * Tool-call previews (v0.8.9-rc1, `?toolPreviews=1`): the server sent a bounded [output], and
     * [outputLength] is the full value's length in UTF-16 units. Sent as `true` or not at all. A call
     * carrying any preview marker must never be written back, exported or copied as if it were full.
     */
    val outputTruncated: Boolean? = null,
    val outputLength: Int? = null,
    /** [args] is a preview; [argsLength] is the full serialised length. */
    val argsTruncated: Boolean? = null,
    val argsLength: Int? = null,
    /** [subagentContent] was left out; [subagentContentParts] counts its top-level parts. */
    val subagentContentOmitted: Boolean? = null,
    val subagentContentParts: Int? = null,
    /**
     * The calls inside the omitted transcript that the message's attachments were saved under, so
     * those files still route to this card before the transcript loads.
     */
    val subagentAttachmentCalls: List<SubagentAttachmentCall>? = null,
    /** The message's `updatedAt` when the preview was built. Part of [previewKey], never the key alone. */
    val previewRevision: String? = null,
    /**
     * For a saved `subagent` call: who ran it (v0.8.9-rc1, host-resolved). [SubagentIdentity.subagentAgentId]
     * is what a child's file names as its `agentId`, so it is how a file is matched to the right one of
     * several subagent calls (upstream `runAgentId`).
     */
    val subagentIdentity: SubagentIdentity? = null,
) {
    /** True when any of this call's content is a preview rather than the stored value (`hasToolCallPreview`). */
    val isPreview: Boolean
        get() = outputTruncated == true || argsTruncated == true || subagentContentOmitted == true

    /**
     * This preview with the stored content in place of what the server shortened (upstream
     * `withFullToolCall`). Only the shortened fields come from [full], and the seven preview markers
     * go; everything else stays as this copy has it, so a fetched copy never rolls back a newer field.
     */
    fun withFull(full: AgentToolCall): AgentToolCall = copy(
        args = if (argsTruncated == true && full.args != null) full.args else args,
        output = if (outputTruncated == true && full.output != null) full.output else output,
        subagentContent = if (subagentContentOmitted == true && full.subagentContent != null) {
            full.subagentContent
        } else {
            subagentContent
        },
        outputTruncated = null,
        outputLength = null,
        argsTruncated = null,
        argsLength = null,
        subagentContentOmitted = null,
        subagentContentParts = null,
        previewRevision = null,
    )

    /**
     * Identifies the stored version this preview stands for (upstream `getToolCallPreviewRevision`):
     * [previewRevision] plus a fingerprint of what the preview carries. [previewRevision] alone is
     * the MESSAGE's `updatedAt`, not this call's, so it is never the key by itself.
     */
    fun previewKey(): String {
        val content = listOf(
            output.orEmpty(),
            serializedArgs(),
            outputLength?.toString().orEmpty(),
            argsLength?.toString().orEmpty(),
            subagentContentParts?.toString().orEmpty(),
        ).joinToString(PREVIEW_KEY_SEPARATOR)
        return "${previewRevision.orEmpty()}:${fingerprint(content)}"
    }

    /** [args] as the server serialises it: a string as is, anything else as compact JSON. */
    fun serializedArgs(): String {
        val value = args ?: return "null"
        return if (value is JsonPrimitive && value.isString) value.content else value.toString()
    }

    /** Characters the model sees for [args]: the full length when [args] is a preview (upstream `toolCallLengths`). */
    fun fullArgsLength(): Int = argsLength ?: if (args == null) 0 else serializedArgs().length

    /** Characters the model sees for [output]: the full length when [output] is a preview. */
    fun fullOutputLength(): Int = outputLength ?: output.orEmpty().length

    /**
     * The step's verdict as a card should show it: a background task the user stopped reads as
     * cancelled even though its run step closed cleanly (upstream `Part.tsx`).
     */
    val effectiveRunStepStatus: RunStepStatus?
        get() = if (backgroundTask?.cancelled == true) RunStepStatus.CANCELLED else runStepStatus

    companion object {
        /** [executor] of the attached-workspace `bash_tool`. */
        const val EXECUTOR_ATTACHED_WORKSPACE = "attached_workspace"

        private const val PREVIEW_KEY_SEPARATOR = "\u0000"
    }
}

/** Upstream `SubagentIdentity` (`types/runs.ts`): the kind and agent of a saved subagent run. */
@Serializable
data class SubagentIdentity(
    val subagentKind: String? = null,
    val subagentAgentId: String? = null,
)

/** A call inside an omitted subagent transcript that attachments were saved under (`SubagentAttachmentCall`). */
@Serializable
data class SubagentAttachmentCall(
    val id: String,
    val agentId: String? = null,
    val stepId: String? = null,
)

/** 32-bit FNV-1a over UTF-16 code units in base 36 (upstream `fingerprint`): a cache key, not a security hash. */
internal fun fingerprint(text: String): String {
    var hash = FNV_OFFSET
    for (ch in text) {
        hash = hash xor ch.code.toUInt()
        hash *= FNV_PRIME
    }
    return hash.toLong().toString(FINGERPRINT_RADIX)
}

private const val FNV_OFFSET: UInt = 0x811c9dc5u
private const val FNV_PRIME: UInt = 0x01000193u
private const val FINGERPRINT_RADIX = 36

/**
 * The result record of a background task, kept beside the call that dispatched it
 * (`PartMetadata.backgroundTask`). Only [cancelled] drives a decision here; it is additive and
 * absent on servers that predate it.
 */
@Serializable
data class BackgroundToolTask(
    val taskId: String? = null,
    val toolName: String? = null,
    val status: String? = null,
    val cancelled: Boolean? = null,
)
