package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.code.CodeEnvironmentConfigSchema
import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeEnvironmentWorkspacesSchema
import com.garfiec.librechat.core.model.code.CodeWorkspaceDescriptor
import com.garfiec.librechat.core.model.code.CodeWorkspaceGitInfo
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.StatusResult
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.code.PublicCodeEnvironment
import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutMode
import com.garfiec.librechat.feature.chat.viewmodel.CodeFootnote
import com.garfiec.librechat.feature.chat.viewmodel.CodeRequirementState
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceChipLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeWorkspaceViewTest {
    private val mbp = PublicCodeEnvironment(
        id = "garfie-mbp",
        type = "attached",
        configSchema = CodeEnvironmentConfigSchema(CodeEnvironmentWorkspacesSchema(allowCheckoutSelection = true)),
    )
    private val dev = Agent(
        id = "agent_dev",
        name = "Switchboard Dev",
        tools = listOf("execute_code"),
        statefulCodeSessions = true,
        codeEnvironmentId = mbp.id,
    )
    private val capabilities = CodeWorkspaceResolver.Capabilities(
        canRunCode = true,
        agentsCapabilities = listOf("execute_code", "stateful_code_sessions"),
        statefulCodeSessions = StatefulCodeSessionsConfig(environments = listOf(mbp)),
        decisionVersion = 1,
        inheritanceVersion = 1,
    )
    private val switchboard = CodeWorkspaceDescriptor(
        id = "switchboard",
        workspaceInstances = listOf("git_worktree"),
        environment = CodeWorkspaceGitInfo("garfiec/switchboard", "develop"),
    )
    private val librechat =
        CodeWorkspaceDescriptor(id = "librechat", environment = CodeWorkspaceGitInfo("danny-avila/LibreChat", "main"))

    private fun chat(mode: String? = null, selections: List<CodeWorkspaceSelection>? = null) = CodeWorkspaceResolver.Chat(
        isAgentsConversation = true,
        isNewChat = true,
        agentId = dev.id,
        mode = mode,
        selections = selections,
        holdsSavedDecision = false,
    )

    private fun resolve(chat: CodeWorkspaceResolver.Chat) = CodeWorkspaceResolver.resolve(
        capabilities,
        chat,
        mapOf(dev.id to dev),
        mapOf(mbp.id to StatusResult.Loaded(CodeEnvironmentStatus(mbp.id, "ready", listOf(switchboard, librechat)))),
    )

    @Test
    fun choosingSaysWhoNeedsAWorkspaceAndHoldsTheSend() {
        val ui = codeWorkspaceUiState(resolve(chat()), null, null, null, sheetOpen = false, attention = false)
        assertTrue(ui.holdsSend)
        assertEquals(CodeWorkspaceChipLabel.CHOOSE, ui.workspace.label)
        assertEquals(CodeRequirementState.CHOOSE, ui.requirements.single().state)
        assertEquals("Switchboard Dev", ui.requirements.single().agents)
        assertFalse(ui.graphRequirement)
        assertNull(ui.checkout)
        assertTrue(ui.sheet.offersNoWorkspace)
        assertEquals(
            listOf("garfiec/switchboard · develop", "danny-avila/LibreChat · main"),
            ui.sheet.sections.single().rows.map { it.subtitle },
        )
    }

    @Test
    fun aPickShowsTheBranchAndAPickableCheckout() {
        val selected =
            chat(CodeEnvironmentMode.ATTACHED, listOf(CodeWorkspaceSelection(mbp.id, "switchboard", "isolated")))
        val ui =
            codeWorkspaceUiState(resolve(selected), CodeEnvironmentMode.ATTACHED, null, null, sheetOpen = true, attention = false)
        assertFalse(ui.holdsSend)
        assertEquals("switchboard", ui.workspace.name)
        assertEquals("develop", ui.branch)
        assertEquals(CodeCheckoutMode.WORKTREE, ui.checkout?.mode)
        assertTrue(ui.checkout?.pickable == true)
        val row = ui.sheet.sections.single().rows.first()
        assertTrue(row.selected)
        assertNotNull(row.checkout)
        assertTrue(row.checkout!!.offersWorktree)
    }

    @Test
    fun noWorkspaceShowsItsLine() {
        val ui = codeWorkspaceUiState(
            resolve(chat(CodeEnvironmentMode.WITHOUT_ATTACHED)),
            CodeEnvironmentMode.WITHOUT_ATTACHED,
            null,
            null,
            sheetOpen = false,
            attention = false,
        )
        assertEquals(CodeWorkspaceChipLabel.NONE, ui.workspace.label)
        assertEquals(CodeFootnote.WITHOUT_WORKSPACE, ui.footnote)
        assertTrue(ui.sheet.noWorkspaceSelected)
        assertNull(ui.branch)
    }

    @Test
    fun aLockedChatOfflineKeepsItsRecordedCheckout() {
        val saved = CodeWorkspaceResolver.Chat(
            isAgentsConversation = true,
            isNewChat = false,
            agentId = dev.id,
            mode = CodeEnvironmentMode.ATTACHED,
            selections = listOf(CodeWorkspaceSelection(mbp.id, "switchboard", "source")),
            holdsSavedDecision = true,
        )
        val resolution = CodeWorkspaceResolver.resolve(
            capabilities,
            saved,
            mapOf(dev.id to dev),
            mapOf(mbp.id to StatusResult.Loaded(CodeEnvironmentStatus(mbp.id, "offline"))),
        )
        val ui = codeWorkspaceUiState(resolution, null, null, null, sheetOpen = false, attention = false)
        assertTrue(ui.locked)
        assertTrue(ui.holdsSend)
        assertEquals(CodeWorkspaceChipLabel.UNAVAILABLE, ui.workspace.label)
        assertEquals(CodeCheckoutMode.REGISTERED, ui.checkout?.mode)
        assertFalse(ui.checkout?.pickable == true)
    }

    @Test
    fun linkedWorktreesShowOnlyWhileTheChatSharesTheRegisteredCheckout() {
        val linked = switchboard.copy(workspaceScopes = listOf("git_linked_worktree"))
        fun ui(checkout: String?, schema: PublicCodeEnvironment = mbp) = codeWorkspaceUiState(
            CodeWorkspaceResolver.resolve(
                capabilities.copy(statefulCodeSessions = StatefulCodeSessionsConfig(environments = listOf(schema))),
                chat(CodeEnvironmentMode.ATTACHED, listOf(CodeWorkspaceSelection(mbp.id, "switchboard", checkout))),
                mapOf(dev.id to dev),
                mapOf(mbp.id to StatusResult.Loaded(CodeEnvironmentStatus(mbp.id, "ready", listOf(linked, librechat)))),
            ),
            CodeEnvironmentMode.ATTACHED, null, null, sheetOpen = false, attention = false,
        )
        // Auto on a workspace that offers worktrees isolates the chat in its own.
        assertFalse(ui(null).linkedWorktrees)
        assertFalse(ui("isolated").linkedWorktrees)
        assertTrue(ui("source").linkedWorktrees)
        val off = mbp.copy(
            configSchema = CodeEnvironmentConfigSchema(
                CodeEnvironmentWorkspacesSchema(allowCheckoutSelection = true, linkedWorktrees = false),
            ),
        )
        assertFalse(ui("source", off).linkedWorktrees)
    }

    @Test
    fun twoMachinesCountTheirPicksAndSummariseExplicitCheckouts() {
        val vm = PublicCodeEnvironment(
            id = "build-vm",
            type = "attached",
            configSchema = CodeEnvironmentConfigSchema(CodeEnvironmentWorkspacesSchema(allowCheckoutSelection = true)),
        )
        val ops = dev.copy(id = "agent_ops", name = "Ops", codeEnvironmentId = vm.id)
        val lead = dev.copy(agentIds = listOf(ops.id))
        val selections = listOf(
            CodeWorkspaceSelection(vm.id, "scratch", "source"),
            CodeWorkspaceSelection(mbp.id, "switchboard"),
        )
        val resolution = CodeWorkspaceResolver.resolve(
            capabilities.copy(statefulCodeSessions = StatefulCodeSessionsConfig(environments = listOf(mbp, vm))),
            chat(CodeEnvironmentMode.ATTACHED, selections),
            mapOf(lead.id to lead, ops.id to ops),
            mapOf(
                mbp.id to StatusResult.Loaded(CodeEnvironmentStatus(mbp.id, "ready", listOf(switchboard))),
                vm.id to StatusResult.Loaded(CodeEnvironmentStatus(vm.id, "ready", listOf(CodeWorkspaceDescriptor(id = "scratch")))),
            ),
        )
        val ui = codeWorkspaceUiState(
            resolution, CodeEnvironmentMode.ATTACHED, null, null, sheetOpen = false, attention = false,
        )
        assertEquals(CodeWorkspaceChipLabel.COUNT, ui.workspace.label)
        assertEquals(2, ui.workspace.count)
        assertEquals(listOf("build-vm"), ui.checkoutSummaries.map { it.machine })
        assertEquals(CodeCheckoutMode.REGISTERED, ui.checkoutSummaries.single().mode)
        assertFalse(ui.selectionConflict)
    }
}
