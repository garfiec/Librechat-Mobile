package com.garfiec.librechat.feature.agents.util

import com.garfiec.librechat.core.common.ToolConstants
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

private const val RUN_IN_BACKGROUND = "run_in_background"

/**
 * The two tools sharing the code sandbox. The builder's one switch writes both (web
 * `SidePanel/Agents/Code/Background.tsx`), and `bash_tool` is never an `agent.tools` entry, so the
 * save keeps its options while `execute_code` is saved (`pruneToolOptions`).
 */
internal val CODE_SANDBOX_TOOL_IDS: List<String> = listOf(ToolConstants.EXECUTE_CODE, ToolConstants.BASH_TOOL)

private fun JsonObject?.runInBackgroundOf(toolId: String): Boolean? =
    ((this?.get(toolId) as? JsonObject)?.get(RUN_IN_BACKGROUND) as? JsonPrimitive)?.booleanOrNull

/**
 * Whether the code tools run commands in the background. Background-native: no entry means on.
 * `bash_tool`'s own key first, then `execute_code`'s, as the server resolves the runtime tool.
 */
fun JsonObject?.codeBackgroundEnabled(): Boolean =
    (runInBackgroundOf(ToolConstants.BASH_TOOL) ?: runInBackgroundOf(ToolConstants.EXECUTE_CODE)) != false

/**
 * Port of upstream `withNativeBooleanOptOut` (`client/src/hooks/Agents/useMCPToolOptions.ts`) over
 * both sandbox tools. Off writes `run_in_background: false`; on removes the key (on is the native
 * default), dropping an entry left empty. Every other key, on these tools or any other, is kept.
 * Returns null for an empty map, as the editor holds no-options.
 */
fun JsonObject?.withCodeBackground(enabled: Boolean): JsonObject? {
    val updated = this.orEmpty().toMutableMap()
    for (toolId in CODE_SANDBOX_TOOL_IDS) {
        val current = updated[toolId] as? JsonObject
        if (!enabled) {
            updated[toolId] = JsonObject(current.orEmpty() + (RUN_IN_BACKGROUND to JsonPrimitive(false)))
            continue
        }
        if (current == null || RUN_IN_BACKGROUND !in current) continue
        val rest = current - RUN_IN_BACKGROUND
        if (rest.isEmpty()) updated.remove(toolId) else updated[toolId] = JsonObject(rest)
    }
    return if (updated.isEmpty()) null else JsonObject(updated)
}
