package com.garfiec.librechat.feature.agents.components.model

import kotlinx.serialization.json.JsonElement

/**
 * Agent advanced settings. The three typed fields back the visible controls
 * in [AgentAdvancedPanel]; [extras] preserves every other key from the
 * server's `model_parameters` object (e.g. `frequency_penalty`,
 * `reasoning_effort`, `verbosity`) so a load → save round-trip never drops
 * server-set values that mobile doesn't yet surface in the UI.
 */
data class AgentAdvancedSettings(
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    /** The exact wire key the server used for [topP] when loaded (`top_p` or
     *  `topP`). Re-emitted on save so a Bedrock-Anthropic / Google agent
     *  doesn't silently flip from `topP` → `top_p`. Null when the value was
     *  set by the editor (default to `top_p`). */
    val topPKey: String? = null,
    /** The exact wire key the server used for [maxTokens] when loaded
     *  (`max_tokens`, `maxTokens`, or `maxOutputTokens`). Re-emitted on save.
     *  Null when the value was set by the editor (default to `max_tokens`). */
    val maxTokensKey: String? = null,
    val extras: Map<String, JsonElement> = emptyMap(),
)
