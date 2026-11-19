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

/**
 * Upstream's GitHub App bot address, else zod 4's `email()`, ported by hand: Android's ICU regex
 * isn't the JVM's. Keep in step with `agentGitIdentitySchema` and zod's email pattern.
 */
private fun looksLikeEmail(email: String): Boolean = isGitHubBotEmail(email) || isZodEmail(email)

private const val GITHUB_BOT_SUFFIX = "[bot]@users.noreply.github.com"

/** `^\d+\+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\[bot\]@users\.noreply\.github\.com$`, case-insensitive. */
private fun isGitHubBotEmail(email: String): Boolean {
    if (!email.endsWith(GITHUB_BOT_SUFFIX, ignoreCase = true)) return false
    val plus = email.indexOf('+')
    if (plus <= 0 || email.substring(0, plus).any { it !in '0'..'9' }) return false
    val slug = email.substring(plus + 1, email.length - GITHUB_BOT_SUFFIX.length)
    return slug.isNotEmpty() && slug.first() != '-' && slug.last() != '-' && slug.all { it.isAsciiLetterOrDigit() || it == '-' }
}

/** zod 4.6.7: `^(?:[A-Za-z0-9_'+\-]+\.)*[A-Za-z0-9_'+\-]*[A-Za-z0-9_+-]@(?:[A-Za-z0-9][A-Za-z0-9\-]*\.)+[A-Za-z]{2,}$` */
private fun isZodEmail(email: String): Boolean {
    val at = email.indexOf('@')
    if (at <= 0 || at != email.lastIndexOf('@') || ".." in email) return false
    val local = email.substring(0, at)
    if (local.first() == '.' || local.last() == '\'' || local.last() == '.') return false
    if (!local.all { it.isAsciiLetterOrDigit() || it in "_'+.-" }) return false
    val labels = email.substring(at + 1).split('.')
    val tld = labels.last()
    return labels.size >= 2 && tld.length >= 2 && tld.all { it in 'a'..'z' || it in 'A'..'Z' } &&
        labels.dropLast(1).all { label ->
            label.isNotEmpty() && label.first() != '-' && label.all { it.isAsciiLetterOrDigit() || it == '-' }
        }
}

private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

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

/** Web's `showGitIdentity`: the Git fields are drawn only for an attached machine. */
internal fun AgentCodeSettings.gitIdentityShown(config: StatefulCodeSessionsConfig?, available: Boolean, codeEnabled: Boolean): Boolean =
    available && codeEnabled && statefulSessions && effectiveMachine(this, config?.environments)?.isAttached == true

/** Web's `releaseIdentity`: a hidden identity is written only when it's blank or valid; else [loaded]'s is kept. */
internal fun AgentCodeSettings.releaseHiddenIdentity(loaded: AgentCodeSettings, shown: Boolean): AgentCodeSettings =
    if (shown || isGitIdentityValid()) this else copy(gitName = loaded.gitName, gitEmail = loaded.gitEmail)

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
        environments.filter { it.isAttached }.map { env ->
            val isDefault = env.id == machine.id
            val checked = isDefault || env.id in settings.extraEnvironmentIds
            AgentCodeMachineChoice(
                id = env.id,
                name = env.displayName,
                checked = checked,
                // The server caps the list that is sent, which may still hold the default.
                enabled = !isDefault && (checked || settings.extraEnvironmentIds.size < choiceLimit),
                isDefault = isDefault,
            )
        }
    } else {
        null
    }
    val allowedScopes = StatefulCodeScope.allowed(config?.allowedEnvironments)
    // The server runs a missing scope as `user`.
    val shownScope = settings.scope ?: StatefulCodeScope.USER
    val attached = if (machine != null && settings.gitIdentityShown(config, available, codeEnabled)) {
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
        scope = shownScope,
        scopeDisallowed = shownScope !in allowedScopes,
        attached = attached,
    )
}
