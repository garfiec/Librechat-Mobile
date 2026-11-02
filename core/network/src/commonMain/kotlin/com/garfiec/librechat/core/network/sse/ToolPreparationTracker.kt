package com.garfiec.librechat.core.network.sse

import com.garfiec.librechat.core.model.StreamEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * `on_tool_preparation` / `on_tool_calls_dispatched` for [SseEventMapper] (v0.8.9-rc1, upstream
 * #16619), resolved like upstream `packages/chat/src/core/stream/timing.ts`. Per-connection state
 * like the mapper's own; reset with it.
 */
internal class ToolPreparationTracker {

    /**
     * Steps and calls whose marker arrived before the run step announced them. The marker carries
     * no call id for a call the SDK has not named yet, so it can only name the step; the announcing
     * [StreamEvent.ToolCallStart] then starts out preparing.
     */
    private val preparedBeforeStart = mutableSetOf<String>()

    fun clear() = preparedBeforeStart.clear()

    /** Whether the call [toolCallId] (announced by [stepId]) was marked before its start; consumes the mark. */
    fun consumeForStart(toolCallId: String, stepId: String?): Boolean =
        preparedBeforeStart.remove(toolCallId) or (stepId != null && preparedBeforeStart.remove(stepId))

    /**
     * Maps `{id: stepId, index, toolCallId?, observed_at}`, the first argument fragment of a call:
     * the marker's own call id, else the step's call when it is the step's first (`index` 0); the
     * mapper only tracks a step's first call ([stepToolCallIds]). A marker for a step not announced
     * yet is held for its start.
     */
    fun onPreparation(data: JsonObject, stepToolCallIds: Map<String, String>): StreamEvent? {
        val stepId = (data["id"] as? JsonPrimitive)?.contentOrNull ?: return null
        val index = (data["index"] as? JsonPrimitive)?.intOrNull ?: 0
        val markerCallId = (data["toolCallId"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val toolCallId = markerCallId ?: stepToolCallIds[stepId]?.takeIf { index == 0 }
        if (toolCallId == null || stepId !in stepToolCallIds) {
            val held = markerCallId ?: stepId.takeIf { index == 0 } ?: return null
            if (preparedBeforeStart.size < MAX_PREPARED_BEFORE_START) preparedBeforeStart += held
            return null
        }
        return StreamEvent.ToolCallPreparation(toolCallIds = listOf(toolCallId), dispatched = false)
    }

    /** Maps `{dispatched_at, toolCalls: [{id, name, stepId?}]}`. */
    fun onDispatched(data: JsonObject): StreamEvent? {
        val calls = data["toolCalls"] as? JsonArray ?: return null
        val ids = calls.mapNotNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull }
        if (ids.isEmpty()) return null
        ids.forEach { preparedBeforeStart.remove(it) }
        return StreamEvent.ToolCallPreparation(toolCallIds = ids, dispatched = true)
    }

    private companion object {
        /** Markers for steps that are never announced must not pile up. */
        const val MAX_PREPARED_BEFORE_START = 64
    }
}

/**
 * A steer applied inside a child run (`on_subagent_update` phase `steer_applied`, data
 * `{index, part}`). Host-authored guidance need not carry a `steerId`, which the parent's own
 * `on_steer_applied` mapping requires to retire a chip; here nothing is retired, so only the text
 * is needed.
 */
internal fun mapSubagentSteer(data: JsonObject): StreamEvent? {
    val part = data["part"] as? JsonObject ?: return null
    val text = (part["steer"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        ?: return null
    return StreamEvent.SteerApplied(
        steerId = (part["steerId"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
        index = (data["index"] as? JsonPrimitive)?.intOrNull,
        text = text,
        clientSteerId = (part["clientSteerId"] as? JsonPrimitive)?.contentOrNull,
    )
}
