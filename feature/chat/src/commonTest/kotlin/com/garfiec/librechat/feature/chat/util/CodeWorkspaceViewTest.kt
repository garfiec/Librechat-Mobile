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
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalOption
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutMode
import com.garfiec.librechat.feature.chat.viewmodel.CodeConfirming
import com.garfiec.librechat.feature.chat.viewmodel.CodeFootnote
import com.garfiec.librechat.feature.chat.viewmodel.CodeRequirementState
import com.garfiec.librechat.feature.chat.viewmodel.CodeTransitionAction
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceChipLabel
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceUiState
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
        assertNeedsUser(ui)
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
        // A ready pick with no machine to choose folds; the next send seals it, as its sheet says.
        assertFolded(ui)
        assertFalse(ui.locked)
        assertTrue(ui.workspace.opensSheet)
        assertEquals("switchboard", ui.workspace.name)
        // Named as the sealed chat's chip will be, so the first send doesn't rename it.
        assertEquals("garfie-mbp", ui.lineMachineName())
        assertEquals("develop", ui.branch)
        assertEquals(CodeCheckoutMode.WORKTREE, ui.checkout?.mode)
        assertTrue(ui.checkout?.pickable == true)
        val row = ui.sheet.sections.single().rows.first()
        assertTrue(row.selected)
        assertNotNull(row.checkout)
        assertTrue(row.checkout!!.offersWorktree)
    }

    @Test
    fun aFoldedChipDoesNotNameAMachineCalledAsItsWorkspace() {
        val server = PublicCodeEnvironment(id = mbp.id, type = "attached", name = "Server workspace")
        val serverWorkspace = switchboard.copy(name = "Server workspace")
        val ui = codeWorkspaceUiState(
            CodeWorkspaceResolver.resolve(
                capabilities.copy(statefulCodeSessions = StatefulCodeSessionsConfig(environments = listOf(server))),
                chat(CodeEnvironmentMode.ATTACHED, listOf(CodeWorkspaceSelection(mbp.id, "switchboard"))),
                mapOf(dev.id to dev),
                mapOf(mbp.id to StatusResult.Loaded(CodeEnvironmentStatus(mbp.id, "ready", listOf(serverWorkspace)))),
            ),
            CodeEnvironmentMode.ATTACHED, null, null, sheetOpen = false, attention = false,
        )
        assertFolded(ui)
        assertEquals("Server workspace", ui.workspace.name)
        assertNull(ui.lineMachineName())
    }

    @Test
    fun anUndecidedPickWithAMachineToChooseNeedsTheUser() {
        // Not folded while a machine is left to choose: the pick isn't made until the send seals it.
        val vm = PublicCodeEnvironment(id = "build-vm", type = "attached")
        val chooser = dev.copy(codeEnvironmentIds = listOf(vm.id))
        val ui = codeWorkspaceUiState(
            CodeWorkspaceResolver.resolve(
                capabilities.copy(
                    statefulCodeSessions = StatefulCodeSessionsConfig(environments = listOf(mbp, vm), allowEnvironmentSelection = true),
                ),
                chat(CodeEnvironmentMode.ATTACHED, listOf(CodeWorkspaceSelection(mbp.id, "switchboard", "isolated"))),
                mapOf(dev.id to chooser),
                mapOf(mbp.id to StatusResult.Loaded(CodeEnvironmentStatus(mbp.id, "ready", listOf(switchboard, librechat)))),
            ),
            CodeEnvironmentMode.ATTACHED, null, null, sheetOpen = false, attention = false,
        )
        assertFalse(ui.holdsSend)
        assertEquals(2, ui.machine?.choices?.size)
        assertNeedsUser(ui)
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
        assertNeedsUser(ui)
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
        assertNeedsUser(ui)
        assertEquals(CodeWorkspaceChipLabel.UNAVAILABLE, ui.workspace.label)
        assertTrue(ui.workspace.refreshes)
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

    private fun savedChat(
        mode: String? = CodeEnvironmentMode.ATTACHED,
        selections: List<CodeWorkspaceSelection>? = listOf(CodeWorkspaceSelection(mbp.id, "switchboard", "source")),
    ) = CodeWorkspaceResolver.Chat(
        isAgentsConversation = true,
        isNewChat = false,
        agentId = dev.id,
        mode = mode,
        selections = selections,
        holdsSavedDecision = true,
    )

    private val transitions = capabilities.copy(moveVersion = 1, transitionVersion = 2)

    private fun savedUi(
        chat: CodeWorkspaceResolver.Chat = savedChat(),
        caps: CodeWorkspaceResolver.Capabilities = capabilities,
        status: CodeEnvironmentStatus = CodeEnvironmentStatus(mbp.id, "ready", listOf(switchboard, librechat)),
        attention: Boolean = false,
        moving: Boolean = false,
    ) = codeWorkspaceUiState(
        CodeWorkspaceResolver.resolve(caps, chat, mapOf(dev.id to dev), mapOf(mbp.id to StatusResult.Loaded(status))),
        null, null, null, sheetOpen = false, attention = attention, moving = moving,
    )

    /** Needs the user: the chip is still on the line, with its notice or tint. */
    private fun assertNeedsUser(ui: CodeWorkspaceUiState) {
        assertTrue(ui.visible)
        assertFalse(ui.folded)
        assertTrue(ui.shown)
    }

    private fun assertFolded(ui: CodeWorkspaceUiState) {
        assertFalse(ui.visible)
        assertTrue(ui.folded)
        assertTrue(ui.onSettingsLine)
        assertFalse(ui.holdsSend)
    }

    @Test
    fun aHealthySavedDecisionFoldsOntoTheSettingsLine() {
        // Web hides this row without the transition version; the line still names the workspace.
        val ui = savedUi()
        assertFolded(ui)
        assertEquals("garfie-mbp", ui.machine?.name)
        assertEquals(CodeWorkspaceChipLabel.NAME, ui.workspace.label)
        assertEquals("switchboard", ui.workspace.name)
        // Nothing to change here: a tap re-reads the machine's status.
        assertFalse(ui.workspace.opensSheet)
        assertTrue(ui.workspace.refreshes)
        // Branch and checkout don't unfold it.
        assertEquals("develop", ui.branch)
        assertEquals(CodeCheckoutMode.REGISTERED, ui.checkout?.mode)
    }

    @Test
    fun aHealthySavedDecisionWithDetachOnOfferFoldsAndOpensTheTransitionSheet() {
        val ui = savedUi(caps = transitions)
        assertFolded(ui)
        assertEquals(CodeTransitionAction.DETACH, ui.transition?.action)
        assertTrue(ui.workspace.opensSheet)
        assertFalse(ui.workspace.refreshes)
    }

    @Test
    fun aSavedDecisionThatNeedsTheUserSaysSo() {
        // A rejection named the workspace, with and without the transition version.
        assertNeedsUser(savedUi(attention = true))
        assertNeedsUser(savedUi(caps = transitions, attention = true))
        // The decision is being changed.
        assertNeedsUser(savedUi(caps = transitions, moving = true))
        // The machine is offline: the send waits.
        val offline = savedUi(status = CodeEnvironmentStatus(mbp.id, "offline"))
        assertNeedsUser(offline)
        assertTrue(offline.holdsSend)
        // The saved workspace is gone.
        val gone = savedUi(caps = transitions, status = CodeEnvironmentStatus(mbp.id, "ready", listOf(librechat)))
        assertNeedsUser(gone)
        assertTrue(gone.transition?.action != CodeTransitionAction.DETACH)
    }

    @Test
    fun aSealedNoWorkspaceKeepsItsFootnoteAndIsALabel() {
        val ui = savedUi(savedChat(mode = CodeEnvironmentMode.WITHOUT_ATTACHED, selections = null))
        assertNeedsUser(ui)
        assertEquals(CodeFootnote.WITHOUT_WORKSPACE, ui.footnote)
        assertEquals(CodeWorkspaceChipLabel.NONE, ui.workspace.label)
        // No machine status to read again and no transition on offer: neither a sheet nor Refresh.
        assertFalse(ui.workspace.opensSheet)
        assertFalse(ui.workspace.refreshes)
    }

    @Test
    fun aSealedNoWorkspaceWithAttachOnOfferOpensTheTransitionSheet() {
        val ui = savedUi(savedChat(mode = CodeEnvironmentMode.WITHOUT_ATTACHED, selections = null), caps = transitions)
        assertNeedsUser(ui)
        assertEquals(CodeTransitionAction.ATTACH, ui.transition?.action)
        assertTrue(ui.workspace.opensSheet)
        assertFalse(ui.workspace.refreshes)
    }

    @Test
    fun aConfirmingChatNeedsTheUser() {
        val ui = codeWorkspaceUiState(
            CodeWorkspaceResolver.resolve(
                capabilities,
                savedChat(),
                mapOf(dev.id to dev),
                mapOf(mbp.id to StatusResult.Loaded(CodeEnvironmentStatus(mbp.id, "ready", listOf(switchboard)))),
            ),
            null, null, null, sheetOpen = false, attention = false, confirming = CodeConfirming.PENDING,
        )
        assertNeedsUser(ui)
        assertTrue(ui.holdsSend)
    }

    @Test
    fun aHealthySavedDecisionOnTwoMachinesFoldsAsACount() {
        val vm = PublicCodeEnvironment(id = "build-vm", type = "attached")
        val ops = dev.copy(id = "agent_ops", name = "Ops", codeEnvironmentId = vm.id)
        val lead = dev.copy(agentIds = listOf(ops.id))
        val selections = listOf(CodeWorkspaceSelection(mbp.id, "switchboard"), CodeWorkspaceSelection(vm.id, "scratch"))
        val ui = codeWorkspaceUiState(
            CodeWorkspaceResolver.resolve(
                capabilities.copy(statefulCodeSessions = StatefulCodeSessionsConfig(environments = listOf(mbp, vm))),
                savedChat(selections = selections),
                mapOf(lead.id to lead, ops.id to ops),
                mapOf(
                    mbp.id to StatusResult.Loaded(CodeEnvironmentStatus(mbp.id, "ready", listOf(switchboard))),
                    vm.id to StatusResult.Loaded(CodeEnvironmentStatus(vm.id, "ready", listOf(CodeWorkspaceDescriptor(id = "scratch")))),
                ),
            ),
            null, null, null, sheetOpen = false, attention = false,
        )
        assertFolded(ui)
        assertNull(ui.machine)
        assertEquals(CodeWorkspaceChipLabel.COUNT, ui.workspace.label)
        assertEquals(2, ui.workspace.count)
    }

    @Test
    fun theWorkspaceAndApprovalChipsAreAlwaysOnTheLine() {
        val approval = CodeApprovalChip(CodeApprovalOption.ASK, emptyList())
        assertTrue(CodeWorkspaceUiState(approval = approval).onSettingsLine)
        assertFalse(CodeWorkspaceUiState().onSettingsLine)
        // A workspace that needs the user stays in the same place, the line.
        assertTrue(CodeWorkspaceUiState(visible = true, approval = approval).onSettingsLine)
        assertTrue(CodeWorkspaceUiState(visible = true).shown)
    }
}
