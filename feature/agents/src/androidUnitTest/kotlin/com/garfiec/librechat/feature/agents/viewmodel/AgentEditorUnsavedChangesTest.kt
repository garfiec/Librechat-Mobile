package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.AgentToolsRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.ToolFavoritesRepository
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentFile
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.feature.agents.util.ContentReader
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Back from the editor asks before throwing edits away — and only then. A form that counts as dirty
 * without the user touching it nags on every exit, so most of these pin the things that change
 * state after load without being edits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentEditorUnsavedChangesTest {

    private val dispatcher = StandardTestDispatcher()
    private val agentRepository = mockk<AgentRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val toolFavoritesRepository = mockk<ToolFavoritesRepository>(relaxed = true)

    private val servers = listOf(McpTool(name = "list_events", serverName = "Google Workspace"))

    private val agent = Agent(
        id = "agent-1",
        name = "Helper",
        instructions = "Be brief.",
        // The key stores the NORMALIZED server name; the catalog advertises "Google Workspace".
        tools = listOf("web_search", "sys__server__sys_mcp_Google_Workspace"),
        modelParameters = buildJsonObject { put("temperature", JsonPrimitive(0.4)) },
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // A relaxed mock cannot return a StateFlow, and the failed collect would cancel the scope.
        every { configRepository.detectedBackend } returns MutableStateFlow(null)
        every { configRepository.detectedBackendVersion } returns MutableStateFlow("0.8.8")
        every { configRepository.endpointConfigs } returns MutableStateFlow(emptyMap())
        every { configRepository.startupConfig } returns MutableStateFlow(null)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        every { toolFavoritesRepository.favorites } returns MutableStateFlow(emptySet())
        every { toolFavoritesRepository.isSupported } returns MutableStateFlow(false)
        coEvery { agentRepository.getAgentForEditing(agent.id) } returns Result.Success(agent)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun editor(agentId: String? = agent.id) = AgentEditorViewModel(
        agentRepository = agentRepository,
        configRepository = configRepository,
        mcpRepository = mockk<McpRepository>(relaxed = true),
        agentToolsRepository = mockk<AgentToolsRepository>(relaxed = true),
        fileRepository = mockk<FileRepository>(relaxed = true),
        skillsRepository = mockk<SkillsRepository>(relaxed = true),
        roleRepository = roleRepository,
        toolFavoritesRepository = toolFavoritesRepository,
        contentReader = mockk<ContentReader>(relaxed = true),
        ioDispatcher = dispatcher,
        initialAgentId = agentId,
    )

    private fun TestScope.eventsOf(viewModel: AgentEditorViewModel): List<AgentEditorEvent> {
        val events = mutableListOf<AgentEditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return events
    }

    private fun loaded() = AgentEditorUiState(isEditMode = true, agentId = agent.id).applyAgentData(agent)

    @Test
    fun `a freshly loaded agent leaves without asking`() = runTest(dispatcher) {
        val viewModel = editor()
        val events = eventsOf(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.hasUnsavedChanges.value).isFalse()
        viewModel.onBackRequested()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(events).containsExactly(AgentEditorEvent.Exit)
    }

    @Test
    fun `an edit asks before leaving, and discarding then leaves`() = runTest(dispatcher) {
        val viewModel = editor()
        val events = eventsOf(viewModel)
        advanceUntilIdle()

        viewModel.onNameChanged("Helper 2")
        advanceUntilIdle()
        assertThat(viewModel.hasUnsavedChanges.value).isTrue()

        viewModel.onBackRequested()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isTrue()
        assertThat(events).isEmpty()

        viewModel.discardChanges()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(events).containsExactly(AgentEditorEvent.Exit)
    }

    @Test
    fun `cancelling the prompt stays in the editor`() = runTest(dispatcher) {
        val viewModel = editor()
        val events = eventsOf(viewModel)
        advanceUntilIdle()
        viewModel.onNameChanged("Helper 2")
        viewModel.onBackRequested()

        viewModel.dismissDiscardConfirmation()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.name).isEqualTo("Helper 2")
        assertThat(events).isEmpty()
    }

    @Test
    fun `undoing an edit by hand is clean again`() = runTest(dispatcher) {
        val viewModel = editor()
        advanceUntilIdle()

        viewModel.onNameChanged("Helper 2")
        viewModel.onNameChanged("Helper")
        advanceUntilIdle()

        assertThat(viewModel.hasUnsavedChanges.value).isFalse()
    }

    @Test
    fun `an untouched create form leaves without asking, a named one asks`() = runTest(dispatcher) {
        val viewModel = editor(agentId = null)
        advanceUntilIdle()
        assertThat(viewModel.hasUnsavedChanges.value).isFalse()

        viewModel.onNameChanged("New agent")
        advanceUntilIdle()
        assertThat(viewModel.hasUnsavedChanges.value).isTrue()
    }

    @Test
    fun `the MCP catalog landing after the agent is not an edit`() {
        val beforeCatalog = loaded()
        assertThat(beforeCatalog.selectedMcpTools).containsExactly("Google_Workspace")

        val merged = beforeCatalog.copy(mcpTools = servers).remergeMcpServerNames(agent.tools)

        assertThat(merged.selectedMcpTools).containsExactly("Google Workspace")
        assertThat(merged.hasUnsavedChanges()).isFalse()
    }

    @Test
    fun `server gates arriving after load are not edits`() {
        val state = loaded().copy(
            showCollaborativeToggle = false,
            dropParamsMap = mapOf("agents" to JsonPrimitive("temperature")),
        )

        assertThat(state.hasUnsavedChanges()).isFalse()
    }

    @Test
    fun `files and the avatar are saved as they are picked, so they are not pending edits`() {
        val state = loaded().copy(
            avatarUrl = "/images/new.png",
            knowledgeFiles = listOf(AgentFile(fileId = "file-1")),
        )

        assertThat(state.hasUnsavedChanges()).isFalse()
    }

    @Test
    fun `reverting to a version makes that version the clean baseline`() {
        val reverted = agent.copy(name = "Helper v1", instructions = "Older.")
        val state = loaded().copy(name = "typed but unsaved").applyAgentData(reverted)

        assertThat(state.hasUnsavedChanges()).isFalse()
        assertThat(state.copy(instructions = "Newer.").hasUnsavedChanges()).isTrue()
    }

    @Test
    fun `toggling a capability is an edit`() {
        assertThat(loaded().copy(fileSearchEnabled = true).hasUnsavedChanges()).isTrue()
    }

    /** A save navigates when it lands; leaving first would let it pop the screen behind this one. */
    @Test
    fun `back is held while a save is in flight`() = runTest(dispatcher) {
        coEvery { agentRepository.updateAgent(agent.id, any()) } coAnswers { awaitCancellation() }
        val viewModel = editor()
        val events = eventsOf(viewModel)
        advanceUntilIdle()
        viewModel.onNameChanged("Helper 2")
        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isSaving).isTrue()

        viewModel.onBackRequested()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(events).isEmpty()
    }
}
