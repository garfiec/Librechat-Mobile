package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus

/**
 * The agent editor's Code section, web's `SidePanel/Agents/Code/Settings.tsx`: stateful sessions,
 * the machine, the machines new chats may pick, the workspace scope, the default workspace,
 * repository instructions and the Git identity. Loaded from the `/expanded` record (the only
 * single-agent read that carries these fields).
 */
@Immutable
data class AgentCodeSettings(
    val statefulSessions: Boolean = false,
    /** `stateful_code_environment`; null when the agent has none. */
    val scope: String? = null,
    /** `code_environment_id`; null = the deployment's default machine. */
    val environmentId: String? = null,
    /** `code_environment_ids`: extra machines new chats may pick. */
    val extraEnvironmentIds: List<String> = emptyList(),
    /** `code_workspace_id`; `""` = the last workspace used for this agent and machine. */
    val workspaceId: String = "",
    val repositoryInstructions: String? = null,
    val gitName: String = "",
    val gitEmail: String = "",
) {
    companion object {
        fun of(agent: Agent) = AgentCodeSettings(
            statefulSessions = agent.statefulCodeSessions == true,
            scope = agent.statefulCodeEnvironment,
            environmentId = agent.codeEnvironmentId?.takeIf { it.isNotEmpty() },
            extraEnvironmentIds = agent.codeEnvironmentIds.orEmpty(),
            workspaceId = agent.codeWorkspaceId.orEmpty(),
            repositoryInstructions = agent.repositoryInstructions,
            gitName = agent.gitIdentity?.name.orEmpty(),
            gitEmail = agent.gitIdentity?.email.orEmpty(),
        )
    }
}

/** `prefer` / `defer` / `off`; upstream's editor defaults it to `prefer`. */
object RepositoryInstructions {
    const val PREFER = "prefer"
    const val DEFER = "defer"
    const val OFF = "off"
    val ALL = listOf(PREFER, DEFER, OFF)
}

@Immutable
data class AgentCodeOption(val id: String, val name: String, val attached: Boolean)

/** A saved default workspace the machine doesn't list: shown disabled, with why. */
enum class AgentCodeWorkspaceState { LOADING, MISSING, UNAVAILABLE, UNSUPPORTED }

@Immutable
data class AgentCodeWorkspaceOption(val id: String, val name: String)

@Immutable
data class AgentCodeMachineChoice(val id: String, val name: String, val checked: Boolean, val enabled: Boolean, val isDefault: Boolean)

/** The Code section, mapped from the form, `/api/endpoints` and the machine's status. */
@Immutable
data class AgentCodeSectionView(
    val statefulSessions: Boolean,
    /** Saved machine ids this user can no longer see; unchecking removes them. */
    val unavailableMachines: List<String>,
    /** Null hides the machine picker (no machines configured). */
    val machines: List<AgentCodeOption>?,
    /** The selected machine id; null = deployment default. */
    val machineId: String?,
    /** Null hides "Machines available in new chats". */
    val machineChoices: List<AgentCodeMachineChoice>?,
    val machineChoiceLimit: Int,
    val scopes: List<String>,
    val scope: String?,
    /** The scope the agent holds that this deployment no longer allows. */
    val scopeDisallowed: Boolean,
    /** Null hides the default workspace, repository instructions and Git identity (not an attached machine). */
    val attached: AgentCodeAttachedView?,
)

@Immutable
data class AgentCodeAttachedView(
    val workspaces: List<AgentCodeWorkspaceOption>,
    /** `""` = last used. */
    val workspaceId: String,
    /** Why the saved default isn't among [workspaces]; null when it is, or none is saved. */
    val workspaceState: AgentCodeWorkspaceState?,
    /** Why the machine lists no workspaces, saved default or not (web's `workspaceDiscoveryState`); null when ready. */
    val discovery: AgentCodeWorkspaceState?,
    val repositoryInstructions: String,
    val gitName: String,
    val gitEmail: String,
    val gitIdentityError: Boolean,
)

/** The machine status the default-workspace picker reads; null while loading, or [failed]. */
data class AgentCodeMachineStatus(val environmentId: String, val status: CodeEnvironmentStatus?, val failed: Boolean)
