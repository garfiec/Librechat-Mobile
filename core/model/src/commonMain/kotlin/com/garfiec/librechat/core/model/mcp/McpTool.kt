package com.garfiec.librechat.core.model.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    @SerialName("input_schema") val inputSchema: JsonObject? = null,
    @SerialName("server_name") val serverName: String? = null,
    /**
     * The RAW upstream tool name, when the model-facing key dropped a redundant
     * `<serverName>_` prefix (v0.8.8-rc2, upstream #14732). Present only where stripping applied.
     *
     * Decode surface: tool CALLS are unaffected, because the server always sends the raw name
     * upstream and heals a pre-strip persisted key by trying the stripped spelling. What this
     * identifies is the *catalog* entry a legacy agent's stored key now corresponds to — see
     * `feature/agents` for the editor-side gap that is not ported.
     */
    val serverToolName: String? = null,
)
