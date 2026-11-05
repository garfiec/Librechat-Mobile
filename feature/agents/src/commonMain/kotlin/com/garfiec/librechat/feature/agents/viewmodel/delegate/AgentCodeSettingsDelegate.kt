package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.CodeEnvironmentRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.code.StatefulCodeScope
import com.garfiec.librechat.feature.agents.viewmodel.AgentCodeMachineStatus
import com.garfiec.librechat.feature.agents.viewmodel.AgentCodeSettings
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.effectiveMachine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the Code section asks of the editor. */
sealed interface AgentCodeEvent {
    data class StatefulSessions(val enabled: Boolean) : AgentCodeEvent

    /** Null = the deployment's default machine. */
    data class Machine(val id: String?) : AgentCodeEvent
    data class MachineChoice(val id: String, val checked: Boolean) : AgentCodeEvent

    /** An unavailable saved machine unchecked: removed from the allowlist. */
    data class RemoveUnavailableMachine(val id: String) : AgentCodeEvent
    data class Scope(val scope: String) : AgentCodeEvent

    /** `""` = the last workspace used. */
    data class Workspace(val id: String) : AgentCodeEvent
    data class RepositoryInstructions(val value: String) : AgentCodeEvent
    data class GitName(val value: String) : AgentCodeEvent
    data class GitEmail(val value: String) : AgentCodeEvent
}

/** What the Code section reads beyond the form: machine status, and the user's default scope. */
class AgentCodeSources(
    val environments: CodeEnvironmentRepository,
    val users: UserRepository,
)

/**
 * Edits the Code section's draft, and reads the machine's status for its workspace list while
 * the section shows one (an attached machine, stateful sessions on). Turning stateful sessions on
 * applies the user's default workspace scope (Settings › Code execution), as web's toggle does.
 */
internal class AgentCodeSettingsDelegate(
    private val stateHandle: AgentEditorStateHandle,
    sources: AgentCodeSources,
) {
    private val environments = sources.environments
    private val users = sources.users
    private var statusJob: Job? = null
    private var defaultScope: String? = null
    private var defaultScopeLoaded = false

    fun start() {
        // Read the status of the machine the default-workspace picker lists, once per machine.
        stateHandle.scope.launch {
            stateHandle.stateFlow
                .map { state ->
                    val code = state.code
                    val machine = effectiveMachine(code, state.statefulCodeConfig?.environments)
                    machine?.id?.takeIf {
                        state.isStatefulCodeAvailable && code.statefulSessions && machine.isAttached
                    }
                }
                .distinctUntilChanged()
                .collect(::loadStatus)
        }
    }

    private fun loadStatus(environmentId: String?) {
        statusJob?.cancel()
        if (environmentId == null) return
        stateHandle.update { copy(codeMachineStatus = AgentCodeMachineStatus(environmentId, null, failed = false)) }
        statusJob = stateHandle.scope.launch {
            val result = environments.status(environmentId)
            stateHandle.update {
                copy(
                    codeMachineStatus = AgentCodeMachineStatus(
                        environmentId = environmentId,
                        status = (result as? Result.Success)?.data,
                        failed = result !is Result.Success,
                    ),
                )
            }
        }
    }

    fun onEvent(event: AgentCodeEvent) {
        when (event) {
            is AgentCodeEvent.StatefulSessions -> toggleStateful(event.enabled)
            is AgentCodeEvent.Machine -> update { copy(environmentId = event.id, workspaceId = "") }
            is AgentCodeEvent.MachineChoice -> update {
                copy(
                    extraEnvironmentIds = if (event.checked) {
                        (extraEnvironmentIds + event.id).distinct()
                    } else {
                        extraEnvironmentIds - event.id
                    },
                )
            }
            is AgentCodeEvent.RemoveUnavailableMachine -> update { copy(extraEnvironmentIds = extraEnvironmentIds - event.id) }
            is AgentCodeEvent.Scope -> {
                val allowed = StatefulCodeScope.allowed(stateHandle.state.statefulCodeConfig?.allowedEnvironments)
                if (event.scope in allowed) update { copy(scope = event.scope) }
            }
            is AgentCodeEvent.Workspace -> {
                val state = stateHandle.state
                val machine = effectiveMachine(state.code, state.statefulCodeConfig?.environments)
                // Web writes the machine with a picked workspace, so a default machine becomes explicit.
                update {
                    copy(
                        workspaceId = event.id,
                        environmentId = if (event.id.isNotEmpty()) machine?.id ?: environmentId else environmentId,
                    )
                }
            }
            is AgentCodeEvent.RepositoryInstructions -> update { copy(repositoryInstructions = event.value) }
            is AgentCodeEvent.GitName -> {
                update { copy(gitName = event.value) }
                stateHandle.update { copy(codeGitIdentityError = false) }
            }
            is AgentCodeEvent.GitEmail -> {
                update { copy(gitEmail = event.value) }
                stateHandle.update { copy(codeGitIdentityError = false) }
            }
        }
    }

    private fun update(block: AgentCodeSettings.() -> AgentCodeSettings) {
        stateHandle.update { copy(code = code.block()) }
    }

    private fun toggleStateful(enabled: Boolean) {
        update { copy(statefulSessions = enabled) }
        if (!enabled) return
        stateHandle.scope.launch {
            if (!defaultScopeLoaded) {
                defaultScope = (users.getUser() as? Result.Success)?.data?.personalization?.statefulCodeEnvironment
                defaultScopeLoaded = true
            }
            val allowed = stateHandle.state.statefulCodeConfig?.allowedEnvironments
            update {
                if (scope != null && scope in StatefulCodeScope.allowed(allowed)) {
                    this
                } else {
                    copy(scope = StatefulCodeScope.resolve(defaultScope ?: StatefulCodeScope.USER, allowed) ?: StatefulCodeScope.USER)
                }
            }
        }
    }
}
