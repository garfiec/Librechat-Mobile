package com.garfiec.librechat.core.model.code

import com.garfiec.librechat.core.model.Agent

/** `codeApprovalMode` values, in the order web offers them. MIRRORED from upstream `CODE_APPROVAL_MODES`. */
object CodeApprovalMode {
    /** Asks before edits and commands. */
    const val ASK = "ask"

    /** Edits files, asks before commands. */
    const val ACCEPT_EDITS = "acceptEdits"

    /** Edits and runs commands; sandbox limits still apply. */
    const val FULL_ACCESS = "fullAccess"

    val ALL: List<String> = listOf(ASK, ACCEPT_EDITS, FULL_ACCESS)
}

/**
 * The code approval mode picker's rules: web's `useCodeApprovalMode` (`client/src/hooks/Agents/`),
 * `getAllowedCodeApprovalModes` (`packages/data-provider/src/code/approval.ts`) and
 * `resolveSubmittedCodeApprovalMode` (`client/src/hooks/Agents/codeDecision.ts`). Pure; registered
 * in `scripts/mirrors.json`. The server re-checks every mode at admission, so a client that offered
 * too much would only see the turn refused; one that offers too little is merely conservative.
 */
object CodeApproval {
    /** What each mode lets through without asking: (file changes, commands). */
    private val MODE_ALLOWS = mapOf(
        CodeApprovalMode.ASK to (false to false),
        CodeApprovalMode.ACCEPT_EDITS to (true to false),
        CodeApprovalMode.FULL_ACCESS to (true to true),
    )

    /** The modes one attached machine's permissions allow: a mode needing `allow` needs the machine to offer and not deny it. */
    fun allowedModes(environment: PublicCodeEnvironment): List<String> {
        val schema = environment.configSchema?.permissions
        val settings = environment.settings?.permissions
        return CodeApprovalMode.ALL.filter { mode ->
            val (writes, commands) = MODE_ALLOWS.getValue(mode)
            (!writes || permits(schema?.fileWrite, settings?.fileWrite)) &&
                (!commands || permits(schema?.commandExecution, settings?.commandExecution))
        }
    }

    private fun permits(field: CodeEnvironmentPermissionField?, configured: String?): Boolean {
        val effective = field?.effective(configured) ?: CodePermissionDecision.ASK
        return effective != CodePermissionDecision.DENY && field?.allowed?.contains(CodePermissionDecision.ALLOW) == true
    }

    data class Picker(
        /** The agents endpoint with approvals on: a mode is sent with every turn. */
        val supported: Boolean,
        /** The picker shows: [supported], `ask` offered, and an attached machine in reach. */
        val available: Boolean,
        val modes: List<String>,
        /** The mode the next turn uses; null when not [supported]. */
        val selected: String?,
    ) {
        companion object {
            val NONE = Picker(supported = false, available = false, modes = emptyList(), selected = null)
        }
    }

    /**
     * [environmentMode] is the mode the composer resolved for the next turn (a chat running
     * without a workspace has no attached target, so no stronger mode applies). [requested] is the
     * chat's own mode, else the reader's last pick, else null (`ask`).
     */
    @Suppress("LongParameterList") // web's hook inputs, kept one-to-one
    fun resolve(
        config: StatefulCodeSessionsConfig?,
        isAgentsConversation: Boolean,
        roots: List<Agent?>,
        rootIds: List<String?>,
        agentsById: Map<String, Agent>,
        selections: List<CodeWorkspaceSelection>?,
        environmentMode: String?,
        requested: String?,
    ): Picker {
        if (!isAgentsConversation || config == null || config.approvalsEnabled != true) return Picker.NONE
        val environments = config.environments
        val reachable = CodeWorkspaceAgents.collectReachableAgents(roots, agentsById, rootIds)
        val codeEnvironments = reachable.agents.filter(CodeWorkspaceAgents::routesCode).map { agent ->
            CodeWorkspaceAgents.findExecutionEnvironment(agent, environments, config.allowEnvironmentSelection, selections)
        }
        val attached = if (environmentMode == CodeEnvironmentMode.WITHOUT_ATTACHED) {
            emptyList()
        } else {
            codeEnvironments.filterNotNull().filter { it.isAttached }
        }
        val endpointModes = config.approvalModes.orEmpty()
        val available = CodeApprovalMode.ASK in endpointModes && attached.isNotEmpty()
        val modes = if (!available) {
            emptyList()
        } else {
            val allowed = mutableSetOf(CodeApprovalMode.ASK)
            var fullAccess = CodeApprovalMode.FULL_ACCESS in endpointModes && reachable.complete &&
                codeEnvironments.all { it != null }
            for (environment in attached) {
                val machineModes = allowedModes(environment)
                if (CodeApprovalMode.ACCEPT_EDITS in endpointModes && CodeApprovalMode.ACCEPT_EDITS in machineModes) {
                    allowed += CodeApprovalMode.ACCEPT_EDITS
                }
                fullAccess = fullAccess && CodeApprovalMode.FULL_ACCESS in machineModes
            }
            if (fullAccess) allowed += CodeApprovalMode.FULL_ACCESS
            CodeApprovalMode.ALL.filter { it in allowed }
        }
        val wanted = requested ?: CodeApprovalMode.ASK
        val selected = if (available && wanted in modes) wanted else CodeApprovalMode.ASK
        return Picker(supported = true, available = available, modes = modes, selected = selected)
    }

    /**
     * The mode a turn carries: the chat's pick while policy offers it, else the picker's
     * fallback; `ask` for a turn sent without a workspace; null when the picker isn't [Picker.supported].
     */
    fun submitted(picker: Picker, requested: String?, environmentMode: String?): String? {
        val fallback = picker.selected ?: return null
        if (environmentMode == CodeEnvironmentMode.WITHOUT_ATTACHED) return CodeApprovalMode.ASK
        return requested?.takeIf { it in picker.modes } ?: fallback
    }
}
