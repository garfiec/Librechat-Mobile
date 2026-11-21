package com.garfiec.librechat.feature.agents.viewmodel.delegate

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
