package com.garfiec.librechat.core.model.code

import com.garfiec.librechat.core.model.Agent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Web's `useCodeApprovalMode`, `getAllowedCodeApprovalModes` and `resolveSubmittedCodeApprovalMode`. */
class CodeApprovalTest {
    private fun permissions(allowed: List<String>, default: String? = null) = CodeEnvironmentPermissionField(allowed, default)

    private val open = PublicCodeEnvironment(
        id = "mbp",
        type = "attached",
        configSchema = CodeEnvironmentConfigSchema(
            permissions = CodeEnvironmentPermissionsSchema(
                fileWrite = permissions(listOf("allow", "ask")),
                commandExecution = permissions(listOf("allow", "ask")),
            ),
        ),
    )
    private val editsOnly = open.copy(
        id = "vm",
        configSchema = CodeEnvironmentConfigSchema(
            permissions = CodeEnvironmentPermissionsSchema(
                fileWrite = permissions(listOf("allow", "ask")),
                commandExecution = permissions(listOf("ask", "deny")),
            ),
        ),
    )
    private val dev = Agent(
        id = "agent_dev",
        tools = listOf("execute_code"),
        statefulCodeSessions = true,
        codeEnvironmentId = open.id,
    )

    private fun config(vararg environments: PublicCodeEnvironment, modes: List<String> = CodeApprovalMode.ALL) =
        StatefulCodeSessionsConfig(environments = environments.toList(), approvalsEnabled = true, approvalModes = modes)

    private fun picker(
        config: StatefulCodeSessionsConfig?,
        agent: Agent = dev,
        environmentMode: String? = null,
        requested: String? = null,
    ) = CodeApproval.resolve(config, true, listOf(agent), listOf(agent.id), mapOf(agent.id to agent), null, environmentMode, requested)

    @Test
    fun aMachineThatDeniesCommandsOffersAcceptEditsButNotFullAccess() {
        assertEquals(CodeApprovalMode.ALL, CodeApproval.allowedModes(open))
        assertEquals(listOf(CodeApprovalMode.ASK, CodeApprovalMode.ACCEPT_EDITS), CodeApproval.allowedModes(editsOnly))
        assertEquals(listOf(CodeApprovalMode.ASK), CodeApproval.allowedModes(open.copy(configSchema = null)))
    }

    @Test
    fun aUserDenyOverridesWhatTheMachineOffers() {
        val choosable = open.copy(
            configSchema = CodeEnvironmentConfigSchema(
                permissions = CodeEnvironmentPermissionsSchema(
                    fileWrite = permissions(listOf("allow", "ask", "deny")),
                    commandExecution = permissions(listOf("allow", "ask")),
                ),
            ),
        )
        val denied = choosable.copy(
            settings = CodeEnvironmentUserSettings(CodeEnvironmentPermissionSettings(fileWrite = "deny")),
        )
        assertEquals(listOf(CodeApprovalMode.ASK), CodeApproval.allowedModes(denied))
        // A choice the machine doesn't offer is ignored: its default applies.
        val unoffered = open.copy(
            settings = CodeEnvironmentUserSettings(CodeEnvironmentPermissionSettings(fileWrite = "deny")),
        )
        assertEquals(CodeApprovalMode.ALL, CodeApproval.allowedModes(unoffered))
    }

    @Test
    fun thePickerNeedsApprovalsAndAnAttachedMachine() {
        assertFalse(picker(config(open).copy(approvalsEnabled = null)).supported)
        val plain = dev.copy(statefulCodeSessions = false)
        val noMachine = picker(config(open), plain)
        assertTrue(noMachine.supported)
        assertFalse(noMachine.available)
        assertEquals(CodeApprovalMode.ASK, noMachine.selected)
    }

    @Test
    fun aRememberedPickPolicyNoLongerAllowsFallsBackToAsk() {
        val p = picker(config(open, modes = listOf("ask", "acceptEdits")), requested = CodeApprovalMode.FULL_ACCESS)
        assertEquals(listOf(CodeApprovalMode.ASK, CodeApprovalMode.ACCEPT_EDITS), p.modes)
        assertEquals(CodeApprovalMode.ASK, p.selected)
        assertEquals(CodeApprovalMode.ACCEPT_EDITS, picker(config(open), requested = CodeApprovalMode.ACCEPT_EDITS).selected)
    }

    @Test
    fun aTurnWithoutAWorkspaceCarriesAsk() {
        val p = picker(config(open), requested = CodeApprovalMode.FULL_ACCESS)
        assertEquals(CodeApprovalMode.FULL_ACCESS, CodeApproval.submitted(p, CodeApprovalMode.FULL_ACCESS, CodeEnvironmentMode.ATTACHED))
        assertEquals(CodeApprovalMode.ASK, CodeApproval.submitted(p, CodeApprovalMode.FULL_ACCESS, CodeEnvironmentMode.WITHOUT_ATTACHED))
        assertNull(CodeApproval.submitted(CodeApproval.Picker.NONE, CodeApprovalMode.FULL_ACCESS, null))
    }
}
