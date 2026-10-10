package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Configuration for spawning subagents (isolated-context child agents) from an agent.
 * When [enabled] is true, the agent gets a subagent-spawn tool that can delegate work
 * to itself (when [allowSelf] is true) and/or the agents listed in [agentIds].
 *
 * Mirrors upstream `AgentSubagentsConfig`
 * (`packages/data-provider/src/types/assistants.ts`). The agent editor edits [enabled],
 * [allowSelf] and [agentIds]; [shareFiles] and [graphs] have no control and are carried through
 * a save unchanged.
 */
@Serializable
data class AgentSubagentsConfig(
    val enabled: Boolean? = null,
    /** When true (default server-side), the agent may spawn itself in an isolated context. */
    val allowSelf: Boolean? = null,
    /** Share the current turn's files with authorized descendants. Off unless enabled. */
    val shareFiles: Boolean? = null,
    /** Specific agents that may be spawned as subagents. */
    @SerialName("agent_ids") val agentIds: List<String>? = null,
    /**
     * Saved agent teams that may be spawned as bounded child graphs (v0.8.8-rc2).
     *
     * Raw JSON on purpose. The shape is a whole graph — members, edges, entry and result nodes —
     * and modelling it would imply an editor this client does not have and is not building. It
     * round-trips so a save from mobile cannot silently delete a team configured on the web.
     */
    val graphs: JsonElement? = null,
)
