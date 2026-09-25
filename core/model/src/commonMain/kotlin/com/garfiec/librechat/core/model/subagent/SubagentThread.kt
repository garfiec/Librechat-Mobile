package com.garfiec.librechat.core.model.subagent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Read-only views of the child threads a conversation spawned (v0.8.8-rc2).
 *
 * This is **not** the live trace mobile already renders. `on_subagent_update` and the persisted
 * `AgentToolCall.subagentContent` both hang off the parent's `subagent` tool call, so they cover
 * exactly the children a tool spawned, for the run that is in front of you. These routes add the
 * three things that cannot reach the client that way: children with **no parent tool call**
 * (`origin = "event"`), a child's history **across turns** (`turns`), and the child's own
 * messages.
 *
 * `threadId` appears nowhere else — not on the SSE envelope, not on the persisted trace — so the
 * index is the only way to learn one, and every other call here depends on having read it.
 */

object SubagentThreadStatus {
    const val DISPATCHED = "dispatched"
    const val RUNNING = "running"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val INTERRUPTED = "interrupted"
    const val CANCELLED = "cancelled"

    /**
     * Note the server maps a persisted `running` whose lease has expired to [INTERRUPTED] before
     * it reaches the wire. A thread reading as running here really is running; do not second-guess
     * it client-side.
     */
    fun isTerminal(status: String?): Boolean =
        status == COMPLETED || status == FAILED || status == INTERRUPTED || status == CANCELLED
}

object SubagentOrigin {
    /** Spawned by a `subagent` tool call, so it joins to a card the parent already renders. */
    const val TOOL = "tool"

    /** Spawned by an event binding. **No parent tool call**, so nothing renders it today. */
    const val EVENT = "event"
}

object SubagentActivityType {
    const val WRITING = "writing"
    const val REASONING = "reasoning"
    const val ACTIVITY_LABEL = "activity_label"
    const val TOOL = "tool"
}

object SubagentToolStatus {
    const val RUNNING = "running"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}

@Serializable
data class SubagentTaskSummary(
    val taskId: String,
    val status: String? = null,
    val createdAt: String? = null,
)

/** One child in the parent's index. A bounded projection — event-delivery identities stay private. */
@Serializable
data class SubagentSummary(
    val threadId: String,
    val parentMessageId: String = "",
    /** Present only for a tool-spawned child; this is the join key to the parent's own card. */
    val parentToolCallId: String? = null,
    val subagentType: String = "",
    /** `agent` or `graph`. */
    val subagentKind: String? = null,
    val agentId: String? = null,
    val title: String = "",
    val origin: String = SubagentOrigin.TOOL,
    val actorId: String? = null,
    val status: String? = null,
    val updatedAt: String? = null,
    val latestTaskId: String? = null,
    val tasks: List<SubagentTaskSummary> = emptyList(),
    val tasksTruncated: Boolean = false,
)

/** `GET /api/convos/:parentConversationId/subagents`. */
@Serializable
data class SubagentIndex(
    val parentConversationId: String = "",
    val children: List<SubagentSummary> = emptyList(),
    val childrenTruncated: Boolean = false,
)

/**
 * One item of a child's user-visible activity.
 *
 * Flattened from upstream's four-armed discriminated union for the same reason as
 * `ScheduleCadence`: a sealed hierarchy needs a discriminator serializer that throws on a value a
 * newer server added, which would fail the whole list for one row. [type] is REQUIRED and has no
 * default — the arms share nothing else, so a row without it is unreadable rather than
 * mis-readable.
 */
@Serializable
data class SubagentActivityItem(
    val type: String,
    // writing / reasoning
    val text: String? = null,
    val textTruncated: Boolean? = null,
    // activity_label
    val label: String? = null,
    val labelType: String? = null,
    val toolCallIds: List<String>? = null,
    val activityStartIndex: Int? = null,
    val activityEndIndex: Int? = null,
    val activityCount: Int? = null,
    val agentIds: List<String>? = null,
    val labelTruncated: Boolean? = null,
    // tool
    val toolCallId: String? = null,
    val name: String? = null,
    val input: String? = null,
    val output: String? = null,
    val inputValidationError: Boolean? = null,
    val inputTruncated: Boolean? = null,
    val outputTruncated: Boolean? = null,
    /** `activity_label` uses `ok`/`partial`/`failed`; `tool` uses [SubagentToolStatus]. */
    val status: String? = null,
    val pending: Boolean? = null,
)

@Serializable
data class SubagentThreadMessage(
    val messageId: String = "",
    /** Explicitly nullable on the wire — the branch root carries `null`. */
    val parentMessageId: String? = null,
    val role: String = "",
    val text: String = "",
    val createdAt: String? = null,
    val error: Boolean? = null,
    val textTruncated: Boolean? = null,
)

object SubagentTriggerKind {
    const val PARENT_DISPATCH = "parent_dispatch"
    const val PARENT_CONTINUATION = "parent_continuation"
    const val EXTERNAL_EVENT = "external_event"
}

@Serializable
data class SubagentExternalEvent(
    val eventType: String? = null,
    val sourceType: String? = null,
    val occurredAt: String? = null,
    val expectedActionToolName: String? = null,
)

@Serializable
data class SubagentTrigger(
    val kind: String? = null,
    val summary: String = "",
    val createdAt: String? = null,
    val summaryTruncated: Boolean? = null,
    val externalEvent: SubagentExternalEvent? = null,
)

/** One chronological child execution boundary — the unit the tool-call trace has no concept of. */
@Serializable
data class SubagentThreadTurn(
    val taskId: String = "",
    val trigger: SubagentTrigger? = null,
    val status: String? = null,
    val activity: List<SubagentActivityItem> = emptyList(),
    val activityTruncated: Boolean = false,
    val messages: List<SubagentThreadMessage> = emptyList(),
    /**
     * Parent-to-child command receipts. Kept RAW: mobile is read-only here, and modelling the
     * receipt type invites wiring the control route that produces it.
     */
    val controlReceipts: JsonElement? = null,
    val controlReceiptsTruncated: Boolean? = null,
)

/** `GET /api/convos/:parentConversationId/subagents/:threadId`. */
@Serializable
data class SubagentThreadView(
    val threadId: String,
    val parentConversationId: String = "",
    val parentMessageId: String = "",
    val parentToolCallId: String = "",
    val subagentType: String = "",
    val subagentKind: String? = null,
    /** Bounded to one by the host runtime today — a child never spawns its own child. */
    val depth: Int? = null,
    val agentId: String? = null,
    val title: String = "",
    val status: String? = null,
    val activity: List<SubagentActivityItem> = emptyList(),
    val activityTruncated: Boolean = false,
    val turns: List<SubagentThreadTurn>? = null,
    val messages: List<SubagentThreadMessage> = emptyList(),
    val historyTruncated: Boolean = false,
    /**
     * Some branch rows were omitted and **cannot be recovered with [nextCursor]** — the retained
     * chain vanished between requests. A "load older" that keeps returning nothing is the wrong
     * rendering; say the history is unavailable.
     */
    val historyUnavailable: Boolean? = null,
    /** Opaque cursor for the next older page. Absent means there is nothing older to ask for. */
    val nextCursor: String? = null,
    val updatedAt: String? = null,
    /** See [SubagentThreadTurn.controlReceipts]. */
    val controlReceipts: JsonElement? = null,
    val controlReceiptsTruncated: Boolean? = null,
) {
    /**
     * The activity to draw, in reading order. Upstream fills top-level [activity] only for a
     * `?taskId=` read. The plain read and every cursor page answer it EMPTY and carry each
     * execution in [turns] instead, so reading [activity] alone drew a blank thread on every
     * server — including after "load older".
     */
    val renderedActivity: List<SubagentActivityItem>
        get() = turns?.takeIf { it.isNotEmpty() }?.flatMap { it.activity } ?: activity
}
