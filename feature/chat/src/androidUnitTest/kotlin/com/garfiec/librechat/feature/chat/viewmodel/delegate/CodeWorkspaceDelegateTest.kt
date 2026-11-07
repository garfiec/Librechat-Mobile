package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.CodeWorkspacePreferenceStore
import com.garfiec.librechat.core.data.repository.CodeDecisionMove
import com.garfiec.librechat.core.data.repository.CodeEnvironmentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SavedCodeDecision
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.code.CodeEnvironmentConfigSchema
import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeEnvironmentPermissionField
import com.garfiec.librechat.core.model.code.CodeEnvironmentPermissionsSchema
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeWorkspaceDescriptor
import com.garfiec.librechat.core.model.code.CodeWorkspaceGitInfo
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.code.CodeWorkspaceSubmission
import com.garfiec.librechat.core.model.code.CodeWorkspaceUnavailableReason
import com.garfiec.librechat.core.model.code.PublicCodeEnvironment
import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.error.StreamErrorType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalOption
import com.garfiec.librechat.feature.chat.viewmodel.CodeConfirming
import com.garfiec.librechat.feature.chat.viewmodel.CodeTransitionAction
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceChipLabel
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceHandle
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CodeWorkspaceDelegateTest {
    private val mbp = PublicCodeEnvironment(id = "garfie-mbp", name = "garfie-mbp", type = "attached")
    private val dev = Agent(
        id = "agent_dev",
        name = "Switchboard Dev",
        tools = listOf("execute_code"),
        statefulCodeSessions = true,
        codeEnvironmentId = mbp.id,
        codeWorkspaceId = "switchboard",
    )
    private val switchboard = CodeWorkspaceDescriptor(
        id = "switchboard",
        environment = CodeWorkspaceGitInfo("garfiec/switchboard", "develop"),
    )

    private val agentsEndpoint = EndpointConfig(
        capabilities = listOf("execute_code", "stateful_code_sessions"),
        statefulCodeSessions = StatefulCodeSessionsConfig(environments = listOf(mbp)),
    )

    private fun state(conversationId: String? = null, endpoint: EndpointConfig = agentsEndpoint) = ChatUiState(
        selection = ModelSelectionState(
            choice = ModelChoice.Raw(EndpointConstants.AGENTS, dev.id),
            agents = listOf(dev),
            endpointConfigs = mapOf(EndpointConstants.AGENTS to endpoint),
        ),
        conversation = ConversationMetaState(conversationId = conversationId),
    )

    private class FakeEnvironments(
        var status: Result<CodeEnvironmentStatus>,
        var saved: Result<SavedCodeDecision> = Result.Success(SavedCodeDecision(null, null)),
        var move: CodeDecisionMove = CodeDecisionMove.Failed(unknownOutcome = false),
    ) : CodeEnvironmentRepository {
        var statusCalls = 0
        var savedCalls = 0
        val moves = mutableListOf<Pair<List<CodeWorkspaceSelection>, List<CodeWorkspaceSelection>>>()
        override suspend fun status(environmentId: String): Result<CodeEnvironmentStatus> {
            statusCalls++
            return status
        }
        override suspend fun savedDecision(conversationId: String): Result<SavedCodeDecision> {
            savedCalls++
            return saved
        }
        override suspend fun moveDecision(
            conversationId: String,
            from: List<CodeWorkspaceSelection>,
            to: List<CodeWorkspaceSelection>,
        ): CodeDecisionMove {
            moves += from to to
            return move
        }
    }

    private fun ready(vararg workspaces: CodeWorkspaceDescriptor) =
        Result.Success(CodeEnvironmentStatus(mbp.id, "ready", workspaces.toList()))

    private fun withDelegate(
        initial: ChatUiState,
        environments: FakeEnvironments,
        decisionVersion: Int? = 1,
        moveVersions: Boolean = false,
        lastApproval: String? = null,
        body: suspend TestScope.(MutableStateFlow<ChatUiState>, CodeWorkspaceDelegate) -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        val flow = MutableStateFlow(initial)
        // Its own scheduler: the status poll waits 10 s between reads, and on the test's scheduler
        // runTest would keep advancing through it for ever. Unconfined runs each read at once.
        val scope = TestScope(UnconfinedTestDispatcher())
        val config = mockk<ConfigRepository> {
            every { startupConfig } returns MutableStateFlow(
                StartupConfig(
                    codeEnvironmentDecisionVersion = decisionVersion,
                    codeEnvironmentMoveVersion = 1.takeIf { moveVersions },
                    codeEnvironmentTransitionVersion = 2.takeIf { moveVersions },
                    codeWorkspaceRecoveryVersion = 1.takeIf { moveVersions },
                ),
            )
        }
        val roles = mockk<RoleRepository> {
            every { userPermissions } returns MutableStateFlow<UserRolePermissions?>(null)
        }
        val prefs = mockk<CodeWorkspacePreferenceStore>(relaxed = true) {
            coEvery { snapshot() } returns emptyMap()
            coEvery { lastApprovalMode() } returns lastApproval
        }
        val delegate =
            CodeWorkspaceDelegate(CodeWorkspaceHandle(ChatStateHandle(flow, scope)), config, roles, environments, prefs)
        try {
            delegate.start()
            body(flow, delegate)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a new chat pre-selects the agent's workspace and sends it`() = withDelegate(
        state(),
        FakeEnvironments(ready(switchboard)),
    ) { flow, delegate ->
        assertThat(delegate.submissionForSend()).isEqualTo(
            CodeWorkspaceSubmission(
                mode = CodeEnvironmentMode.ATTACHED,
                workspaces = listOf(CodeWorkspaceSelection(mbp.id, "switchboard")),
            ),
        )
        val ui = flow.value.codeWorkspace
        // A ready pick with no machine to choose sits on the settings line until the send seals it.
        assertThat(ui.folded).isTrue()
        assertThat(ui.locked).isFalse()
        assertThat(ui.holdsSend).isFalse()
        assertThat(ui.workspace.label).isEqualTo(CodeWorkspaceChipLabel.NAME)
        assertThat(ui.branch).isEqualTo("develop")
    }

    @Test
    fun `an offline machine sends no workspace and says so`() {
        val offline = Result.Success(CodeEnvironmentStatus(mbp.id, "offline"))
        withDelegate(state(), FakeEnvironments(offline)) { flow, delegate ->
            assertThat(delegate.submissionForSend())
                .isEqualTo(CodeWorkspaceSubmission(mode = CodeEnvironmentMode.WITHOUT_ATTACHED))
            assertThat(delegate.sendsWithoutWorkspace()).isTrue()
            assertThat(flow.value.codeWorkspace.workspace.label).isEqualTo(CodeWorkspaceChipLabel.UNAVAILABLE)
        }
    }

    @Test
    fun `without the decision version an offline machine holds the send`() {
        val offline = Result.Success(CodeEnvironmentStatus(mbp.id, "offline"))
        withDelegate(state(), FakeEnvironments(offline), decisionVersion = null) { flow, delegate ->
            assertThat(delegate.submissionForSend()).isNull()
            assertThat(flow.value.codeWorkspace.holdsSend).isTrue()
        }
    }

    @Test
    fun `a chat holding a decision sends nothing and folds the row onto the settings line when healthy`() {
        val environments = FakeEnvironments(
            ready(switchboard),
            saved = Result.Success(
                SavedCodeDecision(CodeEnvironmentMode.ATTACHED, listOf(CodeWorkspaceSelection(mbp.id, "switchboard"))),
            ),
        )
        withDelegate(state(conversationId = "c1"), environments) { flow, delegate ->
            assertThat(delegate.submissionForSend()).isEqualTo(CodeWorkspaceSubmission.NONE)
            val ui = flow.value.codeWorkspace
            assertThat(ui.visible).isFalse()
            assertThat(ui.folded).isTrue()
            assertThat(ui.workspace.name).isEqualTo("switchboard")
            assertThat(ui.machine?.name).isEqualTo(mbp.displayName)
        }
    }

    @Test
    fun `an unreadable saved decision sends nothing`() {
        val environments = FakeEnvironments(ready(switchboard), saved = Result.Error())
        withDelegate(state(conversationId = "c1"), environments) { _, delegate ->
            assertThat(delegate.submissionForSend()).isEqualTo(CodeWorkspaceSubmission.NONE)
        }
    }

    @Test
    fun `picking another workspace replaces the pre-selection`() {
        val librechat = CodeWorkspaceDescriptor(id = "librechat")
        withDelegate(state(), FakeEnvironments(ready(switchboard, librechat))) { flow, delegate ->
            delegate.openSheet()
            assertThat(flow.value.codeWorkspace.sheetOpen).isTrue()
            delegate.selectWorkspace(mbp.id, "librechat")
            assertThat(flow.value.codeWorkspace.sheetOpen).isFalse()
            assertThat(delegate.submissionForSend()?.workspaces)
                .containsExactly(CodeWorkspaceSelection(mbp.id, "librechat"))
        }
    }

    @Test
    fun `a rejection naming the workspace refreshes and tints the chip`() {
        val environments = FakeEnvironments(ready(switchboard))
        withDelegate(state(), environments) { flow, _ ->
            val before = environments.statusCalls
            flow.update {
                it.copy(error = StreamErrorType.CODE_WORKSPACE_UNAVAILABLE.marker + StreamErrorType.ARG_SEPARATOR + "missing")
            }
            assertThat(environments.statusCalls).isGreaterThan(before)
            assertThat(flow.value.codeWorkspace.attention).isTrue()
        }
    }

    @Test
    fun `a plain agent shows nothing and sends nothing`() {
        val plain = state().let { s ->
            s.copy(selection = s.selection.copy(agents = listOf(dev.copy(statefulCodeSessions = false))))
        }
        withDelegate(plain, FakeEnvironments(ready(switchboard))) { flow, delegate ->
            assertThat(delegate.submissionForSend()).isEqualTo(CodeWorkspaceSubmission.NONE)
            assertThat(flow.value.codeWorkspace.visible).isFalse()
            // Nothing to send without a workspace for, so nothing to announce either.
            assertThat(delegate.sendsWithoutWorkspace()).isFalse()
        }
    }

    @Test
    fun `a follow-up queued during the run that records the decision sends nothing`() {
        // The landing picked a non-default workspace; the handed-off screen must not resend the
        // agent's default under it while the first run is still out.
        val environments = FakeEnvironments(ready(switchboard))
        val streaming = state(conversationId = "c1").copy(content = MessagesState(isStreaming = true))
        withDelegate(streaming, environments) { flow, delegate ->
            assertThat(delegate.submissionForSend()).isEqualTo(CodeWorkspaceSubmission.NONE)
            assertThat(flow.value.codeWorkspace.visible).isFalse()
        }
    }

    @Test
    fun `refresh re-reads a saved decision that could not be read`() {
        val environments = FakeEnvironments(ready(switchboard), saved = Result.Error())
        withDelegate(state(conversationId = "c1"), environments) { flow, delegate ->
            assertThat(delegate.submissionForSend()).isEqualTo(CodeWorkspaceSubmission.NONE)
            environments.saved = Result.Success(SavedCodeDecision(null, null))
            delegate.refresh()
            // Undecided after all: the chat chooses, and the send waits for the pick.
            assertThat(delegate.submissionForSend()).isNull()
            assertThat(flow.value.codeWorkspace.workspace.label).isEqualTo(CodeWorkspaceChipLabel.CHOOSE)
        }
    }

    private val savedAttached = Result.Success(
        SavedCodeDecision(CodeEnvironmentMode.ATTACHED, listOf(CodeWorkspaceSelection(mbp.id, "switchboard"))),
    )

    @Test
    fun `a healthy saved chat offers detach and the server's answer becomes the decision`() {
        val environments = FakeEnvironments(
            ready(switchboard),
            saved = savedAttached,
            move = CodeDecisionMove.Moved(SavedCodeDecision(CodeEnvironmentMode.WITHOUT_ATTACHED, null)),
        )
        withDelegate(state(conversationId = "c1"), environments, moveVersions = true) { flow, delegate ->
            val ui = flow.value.codeWorkspace
            // Healthy: folded onto the settings line, whose chip opens the Detach sheet.
            assertThat(ui.folded).isTrue()
            assertThat(ui.visible).isFalse()
            assertThat(ui.transition?.action).isEqualTo(CodeTransitionAction.DETACH)
            assertThat(ui.workspace.opensSheet).isTrue()
            delegate.openSheet()
            assertThat(flow.value.codeWorkspace.sheetOpen).isTrue()

            delegate.detach()
            assertThat(environments.moves.single())
                .isEqualTo(listOf(CodeWorkspaceSelection(mbp.id, "switchboard")) to emptyList<CodeWorkspaceSelection>())
            assertThat(flow.value.codeWorkspace.workspace.label).isEqualTo(CodeWorkspaceChipLabel.NONE)
            // Sealed without a workspace now: its footnote keeps it unfolded.
            assertThat(flow.value.codeWorkspace.visible).isTrue()
            assertThat(flow.value.codeWorkspace.folded).isFalse()
            // The server applies its recorded decision: the turn sends none.
            assertThat(delegate.submissionForSend()).isEqualTo(CodeWorkspaceSubmission.NONE)
        }
    }

    @Test
    fun `a chat sealed without a workspace attaches the sole workspace`() {
        val environments = FakeEnvironments(
            ready(switchboard),
            saved = Result.Success(SavedCodeDecision(CodeEnvironmentMode.WITHOUT_ATTACHED, null)),
            move = CodeDecisionMove.Moved(savedAttached.data),
        )
        withDelegate(state(conversationId = "c1"), environments, moveVersions = true) { flow, delegate ->
            val transition = flow.value.codeWorkspace.transition
            assertThat(transition?.action).isEqualTo(CodeTransitionAction.ATTACH)
            assertThat(transition?.canConfirm).isTrue()
            delegate.confirmTransition()
            assertThat(environments.moves.single())
                .isEqualTo(emptyList<CodeWorkspaceSelection>() to listOf(CodeWorkspaceSelection(mbp.id, "switchboard")))
            assertThat(flow.value.codeWorkspace.transition?.action).isEqualTo(CodeTransitionAction.DETACH)
        }
    }

    @Test
    fun `a move keeps the chat's approval mode rather than the last one picked anywhere`() {
        val open = CodeEnvironmentPermissionField(listOf("allow", "ask"))
        val machine = mbp.copy(
            configSchema = CodeEnvironmentConfigSchema(
                permissions = CodeEnvironmentPermissionsSchema(fileWrite = open, commandExecution = open),
            ),
        )
        val endpoint = agentsEndpoint.copy(
            statefulCodeSessions = StatefulCodeSessionsConfig(
                environments = listOf(machine),
                approvalsEnabled = true,
                approvalModes = listOf("ask", "acceptEdits", "fullAccess"),
            ),
        )
        val other = CodeWorkspaceDescriptor(id = "other", environment = CodeWorkspaceGitInfo("garfiec/other", "main"))
        val environments = FakeEnvironments(
            ready(switchboard, other),
            saved = Result.Success(savedAttached.data.copy(approvalMode = "acceptEdits")),
            // The move's answer carries only the workspace.
            move = CodeDecisionMove.Moved(
                SavedCodeDecision(CodeEnvironmentMode.ATTACHED, listOf(CodeWorkspaceSelection(mbp.id, "other"))),
            ),
        )
        withDelegate(
            state(conversationId = "c1", endpoint = endpoint),
            environments,
            moveVersions = true,
            lastApproval = "fullAccess",
        ) { flow, delegate ->
            assertThat(flow.value.codeWorkspace.approval?.selected).isEqualTo(CodeApprovalOption.ACCEPT_EDITS)
            delegate.detach()
            assertThat(environments.moves).hasSize(1)
            assertThat(flow.value.codeWorkspace.approval?.selected).isEqualTo(CodeApprovalOption.ACCEPT_EDITS)
        }
    }

    @Test
    fun `a busy refusal says so and changes nothing`() {
        val environments = FakeEnvironments(ready(switchboard), saved = savedAttached, move = CodeDecisionMove.Busy)
        withDelegate(state(conversationId = "c1"), environments, moveVersions = true) { flow, delegate ->
            val reads = environments.savedCalls
            delegate.detach()
            assertThat(flow.value.error).isEqualTo(ChatNotice.CODE_WORKSPACE_MOVE_BUSY.marker())
            assertThat(environments.savedCalls).isEqualTo(reads)
            assertThat(flow.value.codeWorkspace.confirming).isNull()
        }
    }

    @Test
    fun `a stale refusal re-reads the decision`() {
        val environments = FakeEnvironments(
            ready(switchboard),
            saved = savedAttached,
            move = CodeDecisionMove.Refused(CodeWorkspaceUnavailableReason.LOCKED),
        )
        withDelegate(state(conversationId = "c1"), environments, moveVersions = true) { flow, delegate ->
            val reads = environments.savedCalls
            delegate.detach()
            assertThat(flow.value.error).isEqualTo(ChatNotice.CODE_WORKSPACE_MOVE_STALE.marker())
            assertThat(environments.savedCalls).isGreaterThan(reads)
            assertThat(flow.value.codeWorkspace.confirming).isNull()
        }
    }

    @Test
    fun `a failed first turn holds the send until its decision is confirmed`() {
        val environments = FakeEnvironments(ready(switchboard), saved = Result.Error())
        withDelegate(state(), environments) { flow, delegate ->
            assertThat(delegate.submissionForSend()?.mode).isEqualTo(CodeEnvironmentMode.ATTACHED)
            flow.update {
                it.copy(
                    conversation = it.conversation.copy(conversationId = "c1"),
                    content = MessagesState(isStreaming = true),
                )
            }
            flow.update { it.copy(content = MessagesState(isStreaming = false), error = "boom") }
            assertThat(flow.value.codeWorkspace.confirming).isEqualTo(CodeConfirming.FAILED)
            assertThat(delegate.submissionForSend()).isNull()

            environments.saved = savedAttached
            delegate.refresh()
            assertThat(flow.value.codeWorkspace.confirming).isNull()
            assertThat(delegate.submissionForSend()).isEqualTo(CodeWorkspaceSubmission.NONE)
        }
    }

    @Test
    fun `a turn that succeeded is not confirmed again when an error shows later`() {
        val environments = FakeEnvironments(ready(switchboard), saved = Result.Error())
        withDelegate(state(), environments) { flow, delegate ->
            assertThat(delegate.submissionForSend()?.mode).isEqualTo(CodeEnvironmentMode.ATTACHED)
            flow.update {
                it.copy(
                    conversation = it.conversation.copy(conversationId = "c1"),
                    content = MessagesState(isStreaming = true),
                )
            }
            flow.update { it.copy(content = MessagesState(isStreaming = false)) }
            flow.update { it.copy(error = "boom") }
            assertThat(flow.value.codeWorkspace.confirming).isNull()
        }
    }

    @Test
    fun `a notice ending a turn that carried a decision is not a failed turn`() {
        val environments = FakeEnvironments(ready(switchboard), saved = Result.Error())
        withDelegate(state(), environments) { flow, delegate ->
            assertThat(delegate.submissionForSend()?.mode).isEqualTo(CodeEnvironmentMode.ATTACHED)
            flow.update {
                it.copy(
                    conversation = it.conversation.copy(conversationId = "c1"),
                    content = MessagesState(isStreaming = true),
                    error = ChatNotice.CODE_WORKSPACE_SENT_WITHOUT.marker(),
                )
            }
            flow.update { it.copy(content = MessagesState(isStreaming = false)) }
            assertThat(flow.value.codeWorkspace.confirming).isNull()
        }
    }

    @Test
    fun `a chat the server never saved counts as undecided`() {
        val environments = FakeEnvironments(ready(switchboard), saved = Result.Error(ApiException(404, "Not found")))
        withDelegate(state(conversationId = "c1"), environments) { flow, delegate ->
            // Not "send nothing", which the server would read as "No workspace" on a new chat.
            assertThat(delegate.submissionForSend()).isNull()
            assertThat(flow.value.codeWorkspace.workspace.label).isEqualTo(CodeWorkspaceChipLabel.CHOOSE)
        }
    }

    @Test
    fun `the approval mode rides every turn and a pick changes it`() {
        val open = CodeEnvironmentPermissionField(listOf("allow", "ask"))
        val machine = mbp.copy(
            configSchema = CodeEnvironmentConfigSchema(
                permissions = CodeEnvironmentPermissionsSchema(fileWrite = open, commandExecution = open),
            ),
        )
        val endpoint = agentsEndpoint.copy(
            statefulCodeSessions = StatefulCodeSessionsConfig(
                environments = listOf(machine),
                approvalsEnabled = true,
                approvalModes = listOf("ask", "acceptEdits", "fullAccess"),
            ),
        )
        withDelegate(state(endpoint = endpoint), FakeEnvironments(ready(switchboard))) { flow, delegate ->
            assertThat(delegate.submissionForSend()?.approvalMode).isEqualTo("ask")
            val chip = flow.value.codeWorkspace.approval
            assertThat(chip?.selected).isEqualTo(CodeApprovalOption.ASK)
            assertThat(chip?.options).containsExactly(
                CodeApprovalOption.ASK, CodeApprovalOption.ACCEPT_EDITS, CodeApprovalOption.FULL_ACCESS,
            ).inOrder()

            delegate.selectApprovalMode(CodeApprovalOption.FULL_ACCESS)
            assertThat(flow.value.codeWorkspace.approval?.selected).isEqualTo(CodeApprovalOption.FULL_ACCESS)
            assertThat(delegate.submissionForSend()?.approvalMode).isEqualTo("fullAccess")
            // Choosing "No workspace" leaves nothing for a stronger mode to govern.
            delegate.selectNoWorkspace()
            assertThat(delegate.submissionForSend()?.approvalMode).isEqualTo("ask")
        }
    }

    private fun approvalsMachine(vararg modes: String) = mbp.copy(
        configSchema = CodeEnvironmentConfigSchema(
            permissions = CodeEnvironmentPermissionsSchema(
                fileWrite = CodeEnvironmentPermissionField(listOf("allow", "ask")),
                commandExecution = CodeEnvironmentPermissionField(listOf("allow", "ask")),
            ),
        ),
    ).let { machine ->
        agentsEndpoint.copy(
            statefulCodeSessions = StatefulCodeSessionsConfig(
                environments = listOf(machine),
                approvalsEnabled = true,
                approvalModes = modes.toList(),
            ),
        )
    }

    private fun onSpec(endpoint: EndpointConfig, mode: String?) = state(endpoint = endpoint).let { base ->
        base.copy(
            selection = base.selection.copy(
                choice = ModelChoice.Spec("coder", EndpointConstants.AGENTS, dev.id),
                modelSpecs = ModelSpecs(
                    list = listOf(
                        ModelSpec(
                            name = "coder",
                            preset = Preset(endpoint = EndpointConstants.AGENTS, agentId = dev.id, codeApprovalMode = mode),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a new chat on a spec opens on the spec's approval mode over the last pick`() {
        val endpoint = approvalsMachine("ask", "acceptEdits", "fullAccess")
        withDelegate(onSpec(endpoint, "acceptEdits"), FakeEnvironments(ready(switchboard)), lastApproval = "fullAccess") { flow, delegate ->
            assertThat(flow.value.codeWorkspace.approval?.selected).isEqualTo(CodeApprovalOption.ACCEPT_EDITS)
            assertThat(delegate.submissionForSend()?.approvalMode).isEqualTo("acceptEdits")

            delegate.selectApprovalMode(CodeApprovalOption.ASK)
            assertThat(delegate.submissionForSend()?.approvalMode).isEqualTo("ask")
        }
    }

    @Test
    fun `a spec's approval mode policy doesn't offer falls back to ask`() {
        val endpoint = approvalsMachine("ask", "acceptEdits")
        withDelegate(onSpec(endpoint, "fullAccess"), FakeEnvironments(ready(switchboard))) { flow, delegate ->
            assertThat(flow.value.codeWorkspace.approval?.selected).isEqualTo(CodeApprovalOption.ASK)
            assertThat(delegate.submissionForSend()?.approvalMode).isEqualTo("ask")
        }
    }

    @Test
    fun `the requested approval mode is pick, then saved, then an undecided chat's spec, then the last pick`() {
        val decided = SavedCodeDecision(mode = "with_attached", workspaces = null)
        val undecided = SavedCodeDecision(mode = null, workspaces = null)

        assertThat(requestedApprovalMode("ask", decided.copy(approvalMode = "fullAccess"), "acceptEdits", "fullAccess"))
            .isEqualTo("ask")
        assertThat(requestedApprovalMode(null, decided.copy(approvalMode = "fullAccess"), "acceptEdits", "ask"))
            .isEqualTo("fullAccess")
        assertThat(requestedApprovalMode(null, null, "acceptEdits", "fullAccess")).isEqualTo("acceptEdits")
        assertThat(requestedApprovalMode(null, undecided, "acceptEdits", "fullAccess")).isEqualTo("acceptEdits")
        assertThat(requestedApprovalMode(null, decided, "acceptEdits", "fullAccess")).isEqualTo("fullAccess")
        assertThat(requestedApprovalMode(null, null, null, null)).isNull()
    }
}
