package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.CodeWorkspacePreferenceStore
import com.garfiec.librechat.core.data.repository.CodeEnvironmentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SavedCodeDecision
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeWorkspaceDescriptor
import com.garfiec.librechat.core.model.code.CodeWorkspaceGitInfo
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.code.CodeWorkspaceSubmission
import com.garfiec.librechat.core.model.code.PublicCodeEnvironment
import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.error.StreamErrorType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
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

    private fun state(conversationId: String? = null) = ChatUiState(
        selection = ModelSelectionState(
            choice = ModelChoice.Raw(EndpointConstants.AGENTS, dev.id),
            agents = listOf(dev),
            endpointConfigs = mapOf(EndpointConstants.AGENTS to agentsEndpoint),
        ),
        conversation = ConversationMetaState(conversationId = conversationId),
    )

    private class FakeEnvironments(
        var status: Result<CodeEnvironmentStatus>,
        var saved: Result<SavedCodeDecision> = Result.Success(SavedCodeDecision(null, null)),
    ) : CodeEnvironmentRepository {
        var statusCalls = 0
        override suspend fun status(environmentId: String): Result<CodeEnvironmentStatus> {
            statusCalls++
            return status
        }
        override suspend fun savedDecision(conversationId: String) = saved
    }

    private fun ready(vararg workspaces: CodeWorkspaceDescriptor) =
        Result.Success(CodeEnvironmentStatus(mbp.id, "ready", workspaces.toList()))

    private fun withDelegate(
        initial: ChatUiState,
        environments: FakeEnvironments,
        decisionVersion: Int? = 1,
        body: suspend TestScope.(MutableStateFlow<ChatUiState>, CodeWorkspaceDelegate) -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        val flow = MutableStateFlow(initial)
        // Its own scheduler: the status poll waits 10 s between reads, and on the test's scheduler
        // runTest would keep advancing through it for ever. Unconfined runs each read at once.
        val scope = TestScope(UnconfinedTestDispatcher())
        val config = mockk<ConfigRepository> {
            every { startupConfig } returns MutableStateFlow(StartupConfig(codeEnvironmentDecisionVersion = decisionVersion))
        }
        val roles = mockk<RoleRepository> {
            every { userPermissions } returns MutableStateFlow<UserRolePermissions?>(null)
        }
        val prefs = mockk<CodeWorkspacePreferenceStore>(relaxed = true) {
            coEvery { snapshot() } returns emptyMap()
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
        assertThat(ui.visible).isTrue()
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
    fun `a chat holding a decision sends nothing and hides the row when healthy`() {
        val environments = FakeEnvironments(
            ready(switchboard),
            saved = Result.Success(
                SavedCodeDecision(CodeEnvironmentMode.ATTACHED, listOf(CodeWorkspaceSelection(mbp.id, "switchboard"))),
            ),
        )
        withDelegate(state(conversationId = "c1"), environments) { flow, delegate ->
            assertThat(delegate.submissionForSend()).isEqualTo(CodeWorkspaceSubmission.NONE)
            assertThat(flow.value.codeWorkspace.visible).isFalse()
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
            assertThat(ChatNotice.CODE_WORKSPACE_SENT_WITHOUT.marker()).isNotEmpty()
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
}
