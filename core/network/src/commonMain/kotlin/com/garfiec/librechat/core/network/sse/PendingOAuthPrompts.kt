package com.garfiec.librechat.core.network.sse

import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.mcp.mcpOAuthServerName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * `resumeState.pendingOAuthPrompts` (upstream `projectPendingMCPOAuthPrompts`, v0.8.8-rc2+): the MCP
 * sign-ins the run is still waiting on, already filtered server-side to unexpired prompts on
 * unfinished steps. Each becomes the [StreamEvent.ToolCallAuthRequired] the live `on_run_step_delta`
 * would have produced; the caller emits them after the snapshot that rebuilds the tool calls.
 *
 * As upstream's `applyPendingOAuthPrompt` does, a prompt whose call is not in [snapshot] is
 * announced with a [StreamEvent.ToolCallStart] first; one that is gets none, or its card would be
 * drawn twice. A prompt without a `toolCallId` (the type allows it) has nothing to attach to and is
 * skipped, like an id-less live delta. [onStep] records each prompt's step, as a live run step would.
 */
internal fun mapPendingOAuthPrompts(
    element: JsonElement?,
    snapshot: StreamEvent.Sync?,
    onStep: (stepId: String, toolCallId: String) -> Unit,
): List<StreamEvent> {
    val prompts = element as? JsonArray ?: return emptyList()
    val snapshotToolCallIds = snapshot?.aggregatedContent
        ?.mapNotNullTo(HashSet()) { it.toolCall?.id?.takeIf(String::isNotBlank) }
        .orEmpty()
    val events = mutableListOf<StreamEvent>()
    for (item in prompts) {
        val prompt = item as? JsonObject ?: continue
        val toolCallId = prompt.nonBlankString("toolCallId") ?: continue
        val authUrl = prompt.nonBlankString("authURL") ?: continue
        val toolName = (prompt["toolName"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        (prompt["stepId"] as? JsonPrimitive)?.contentOrNull?.let { onStep(it, toolCallId) }
        if (toolCallId !in snapshotToolCallIds) {
            events += StreamEvent.ToolCallStart(toolCallId = toolCallId, toolName = toolName, input = "")
        }
        events += StreamEvent.ToolCallAuthRequired(
            toolCallId = toolCallId,
            authUrl = authUrl,
            serverName = mcpOAuthServerName(toolName.ifEmpty { null }, authUrl),
            expiresAt = (prompt["expiresAt"] as? JsonPrimitive)?.longOrNull,
        )
    }
    return events
}

private fun JsonObject.nonBlankString(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
