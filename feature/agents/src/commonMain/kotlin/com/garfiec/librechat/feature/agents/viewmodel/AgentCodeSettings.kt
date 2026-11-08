package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentGitIdentity
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.PublicCodeEnvironment
import com.garfiec.librechat.core.model.code.StatefulCodeScope
import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

/** The agent's own machine, else the deployment's default; null for a machine this user can't see. */
internal fun effectiveMachine(settings: AgentCodeSettings, environments: List<PublicCodeEnvironment>?): PublicCodeEnvironment? {
    val id = settings.environmentId
    return if (id != null) environments?.firstOrNull { it.id == id } else environments?.firstOrNull { it.default == true }
}

/** Upstream `DEFAULT_AGENT_CODE_ENVIRONMENT_CHOICES`. */
internal const val DEFAULT_MACHINE_CHOICES = 32

private const val GIT_NAME_MAX = 128
private const val GIT_EMAIL_MAX = 254

/** Upstream `agentGitIdentitySchema`, or both blank. */
internal fun AgentCodeSettings.isGitIdentityValid(): Boolean {
    val name = gitName.trim()
    val email = gitEmail.trim()
    if (name.isEmpty() && email.isEmpty()) return true
    val clean = { value: String -> value.none { it == '\u0000' || it == '\r' || it == '\n' } }
    return name.length in 1..GIT_NAME_MAX && clean(name) &&
        email.length <= GIT_EMAIL_MAX && clean(email) && looksLikeEmail(email)
}

/** `local@domain.tld` by hand (Android's ICU regex isn't the JVM's), which also admits GitHub's bot addresses. */
private fun looksLikeEmail(email: String): Boolean {
    val at = email.indexOf('@')
    if (at <= 0 || at != email.lastIndexOf('@')) return false
    val domain = email.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    return email.none { it.isWhitespace() } && dot > 0 && dot < domain.length - 1
}

private fun AgentCodeSettings.gitIdentity(): AgentGitIdentity? {
    val name = gitName.trim()
    val email = gitEmail.trim()
    return if (name.isEmpty() && email.isEmpty()) null else AgentGitIdentity(name, email)
}

/** The Code fields an update sends: only those that differ from [loaded]. */
internal class AgentCodeWrite(
    val statefulSessions: Boolean?,
    val scope: String?,
    val environmentId: JsonElement?,
    val extraEnvironmentIds: List<String>?,
    val workspaceId: String?,
    val repositoryInstructions: String?,
    val gitIdentity: JsonElement?,
)

internal fun AgentCodeSettings.updateFields(loaded: AgentCodeSettings, codeEnabled: Boolean): AgentCodeWrite {
    // Web saves stateful sessions off whenever the code interpreter is off.
    val stateful = statefulSessions && codeEnabled
    val identity = gitIdentity()
    return AgentCodeWrite(
        statefulSessions = stateful.takeIf { it != loaded.statefulSessions },
        scope = scope.takeIf { it != loaded.scope },
        environmentId = if (environmentId != loaded.environmentId) environmentId?.let(::JsonPrimitive) ?: JsonNull else null,
        extraEnvironmentIds = extraEnvironmentIds.takeIf { it != loaded.extraEnvironmentIds },
        workspaceId = workspaceId.takeIf { it != loaded.workspaceId },
        repositoryInstructions = repositoryInstructions.takeIf { it != loaded.repositoryInstructions },
        gitIdentity = if (identity != loaded.gitIdentity()) {
            identity?.let {
                JsonObject(mapOf("name" to JsonPrimitive(it.name), "email" to JsonPrimitive(it.email)))
            } ?: JsonNull
        } else {
            null
        },
    )
}

internal fun AgentCodeSettings.createFields(codeEnabled: Boolean): AgentCodeWrite {
    val stateful = statefulSessions && codeEnabled
    return AgentCodeWrite(
        statefulSessions = stateful.takeIf { it },
        scope = scope,
        environmentId = environmentId?.let(::JsonPrimitive),
        extraEnvironmentIds = extraEnvironmentIds.ifEmpty { null },
        workspaceId = workspaceId.ifEmpty { null },
        repositoryInstructions = repositoryInstructions,
        gitIdentity = null,
    )
}

internal fun AgentCodeSettings.createGitIdentity(): AgentGitIdentity? = gitIdentity()

// ── View data ───────────────────────────────────────────────────────────

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

/** Web's visibility rules, one mapping. Null hides the section: no `stateful_code_sessions`, or code off. */
@Suppress("CyclomaticComplexMethod") // web's section layout in one mapping
internal fun agentCodeSection(
    settings: AgentCodeSettings,
    config: StatefulCodeSessionsConfig?,
    available: Boolean,
    codeEnabled: Boolean,
    status: AgentCodeMachineStatus?,
    gitIdentityError: Boolean,
): AgentCodeSectionView? {
    if (!available || !codeEnabled) return null
    val environments = config?.environments.orEmpty()
    val knownIds = environments.map { it.id }.toSet()
    val unavailable = settings.extraEnvironmentIds.filter { it !in knownIds }
    val stateful = settings.statefulSessions
    val machine = effectiveMachine(settings, environments)
    val choiceLimit = config?.maxEnvironmentChoices ?: DEFAULT_MACHINE_CHOICES
    val machineChoices = if (stateful && config?.allowEnvironmentSelection == true && machine?.isAttached == true) {
        val extras = settings.extraEnvironmentIds.filter { it != machine.id }
        environments.filter { it.isAttached }.map { env ->
            val isDefault = env.id == machine.id
            val checked = isDefault || env.id in extras
            AgentCodeMachineChoice(
                id = env.id,
                name = env.displayName,
                checked = checked,
                enabled = !isDefault && (checked || extras.size < choiceLimit),
                isDefault = isDefault,
            )
        }
    } else {
        null
    }
    val allowedScopes = StatefulCodeScope.allowed(config?.allowedEnvironments)
    val attached = if (stateful && machine?.isAttached == true) {
        val loaded = status?.status?.takeIf { it.environmentId == machine.id && it.isReady }
        val workspaces = loaded?.workspaces.orEmpty().map { AgentCodeWorkspaceOption(it.id, it.displayName) }
        val saved = settings.workspaceId
        val discovery = when {
            status == null || (status.status == null && !status.failed) -> AgentCodeWorkspaceState.LOADING
            loaded == null -> AgentCodeWorkspaceState.UNAVAILABLE
            loaded.workspaces == null -> AgentCodeWorkspaceState.UNSUPPORTED
            else -> null
        }
        val workspaceState = when {
            saved.isEmpty() || workspaces.any { it.id == saved } -> null
            else -> discovery ?: AgentCodeWorkspaceState.MISSING
        }
        AgentCodeAttachedView(
            workspaces = workspaces,
            workspaceId = saved,
            workspaceState = workspaceState,
            discovery = discovery,
            repositoryInstructions = settings.repositoryInstructions ?: RepositoryInstructions.PREFER,
            gitName = settings.gitName,
            gitEmail = settings.gitEmail,
            gitIdentityError = gitIdentityError,
        )
    } else {
        null
    }
    return AgentCodeSectionView(
        statefulSessions = stateful,
        unavailableMachines = unavailable,
        machines = if (stateful && environments.isNotEmpty()) {
            environments.map { AgentCodeOption(it.id, it.displayName, it.isAttached) }
        } else {
            null
        },
        machineId = settings.environmentId,
        machineChoices = machineChoices,
        machineChoiceLimit = choiceLimit,
        scopes = if (stateful) allowedScopes else emptyList(),
        scope = settings.scope ?: StatefulCodeScope.resolve(StatefulCodeScope.USER, config?.allowedEnvironments),
        scopeDisallowed = settings.scope != null && settings.scope !in allowedScopes,
        attached = attached,
    )
}
