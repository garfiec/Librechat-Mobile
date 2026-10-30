package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.data.datastore.LastUsedSelection
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A spec chat starts from the spec's tools, which are locked on; the user may add tools for the
 * session, which are never stored; leaving the spec brings the user's own toggles back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpecToolsTest {

    private val lastUsed = MutableStateFlow(LastUsedSelection("openAI", "gpt-4o", null))
    private val availableModels = MutableStateFlow(mapOf("openAI" to listOf("gpt-4o", "gpt-4.1")))
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)

    private val configRepository = mockk<ConfigRepository>(relaxed = true).also {
        every { it.availableModels } returns availableModels
        every { it.endpointConfigs } returns MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
        every { it.startupConfig } returns startupConfig
    }
    private val agentRepository = mockk<AgentRepository>(relaxed = true).also {
        every { it.revision } returns MutableStateFlow(0L)
    }
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true).also {
        every { it.lastUsedSelection } returns lastUsed
    }
    private val connectivityObserver = mockk<ConnectivityObserver>(relaxed = true).also {
        every { it.isConnected } returns MutableStateFlow(true)
    }

    private val search = ModelSpec(
        name = "search",
        preset = Preset(endpoint = "openAI", model = "gpt-4.1"),
        webSearch = true,
        mcpServers = listOf("docs"),
    )
    private val plain = ModelSpec(name = "plain", preset = Preset(endpoint = "openAI", model = "gpt-4o"))

    private val grounded = ModelSpec(
        name = "grounded",
        preset = Preset(endpoint = "openAI", model = "gpt-4.1", webSearch = true, urlContext = true),
    )

    private fun config(vararg specs: ModelSpec, enforce: Boolean = false) = StartupConfig(
        modelSpecs = ModelSpecs(list = specs.toList(), enforce = enforce),
        interfaceConfig = InterfaceConfig(modelSelect = true),
    )

    /** The user's own toggles: code execution, their MCP server, and web search in the parameters. */
    private fun newHandle(scope: CoroutineScope) = ChatStateHandle(
        stateFlow = MutableStateFlow(
            ChatUiState(
                selection = ModelSelectionState(
                    choice = ModelChoice.Raw("openAI", null),
                    enabledTools = setOf(ToolConstants.CODE_INTERPRETER),
                    selectedMcpServerNames = setOf("mine"),
                    modelParameters = ModelParameters.DEFAULT.copy(webSearch = true),
                ),
            ),
        ),
        scope = scope,
    )

    private fun newDelegate(handle: ChatStateHandle) = ModelSelectionDelegate(
        handle = ModelSelectionHandle(handle),
        configRepository = configRepository,
        agentRepository = agentRepository,
        mcpRepository = mockk<McpRepository>(relaxed = true),
        settingsDataStore = settingsDataStore,
        permissionGate = mockk<PermissionGate>(relaxed = true),
        connectivityObserver = connectivityObserver,
        preferLastUsedModel = MutableStateFlow(false),
        initialAgentId = null,
        initialEndpoint = null,
        initialModel = null,
        initialSpec = null,
    )

    private suspend fun TestScope.onSpec(spec: ModelSpec): Pair<ChatStateHandle, ModelSelectionDelegate> {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)))
        startupConfig.value = config(search, plain, grounded)
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = true)
        advanceUntilIdle()
        delegate.onSpecSelected(spec)
        return handle to delegate
    }

    @Test
    fun aSpecChatStartsFromTheSpecsToolsAndLeavesTheUsersAlone() = runTest {
        val (handle, _) = onSpec(search)

        assertThat(handle.state.effectiveEnabledTools).containsExactly(ToolConstants.WEB_SEARCH)
        assertThat(handle.state.effectiveMcpServerNames).containsExactly("docs")
        assertThat(handle.state.lockedTools).containsExactly(ToolConstants.WEB_SEARCH)
        assertThat(handle.state.enabledTools).containsExactly(ToolConstants.CODE_INTERPRETER)
        assertThat(handle.state.selectedMcpServerNames).containsExactly("mine")
    }

    @Test
    fun aToolAddedOnASpecIsNeverStored() = runTest {
        val (handle, delegate) = onSpec(search)

        delegate.toggleTool(ToolConstants.FILE_SEARCH)
        delegate.toggleMcpServer("jira")
        advanceUntilIdle()

        assertThat(handle.state.effectiveEnabledTools).containsExactly(ToolConstants.WEB_SEARCH, ToolConstants.FILE_SEARCH)
        assertThat(handle.state.effectiveMcpServerNames).containsExactly("docs", "jira")
        assertThat(handle.state.enabledTools).containsExactly(ToolConstants.CODE_INTERPRETER)
        coVerify(exactly = 0) { settingsDataStore.setEnabledTools(any()) }
        coVerify(exactly = 0) { settingsDataStore.setSelectedMcpServers(any()) }
    }

    @Test
    fun theSpecsOwnToolsCantBeTurnedOff() = runTest {
        val (handle, delegate) = onSpec(search)

        delegate.toggleTool(ToolConstants.WEB_SEARCH)
        delegate.toggleMcpServer("docs")
        delegate.updateModelParameters(handle.state.selection.sendParameters.copy(webSearch = false))

        assertThat(handle.state.effectiveEnabledTools).contains(ToolConstants.WEB_SEARCH)
        assertThat(handle.state.effectiveMcpServerNames).contains("docs")
    }

    @Test
    fun theSheetsUrlContextUnderASpecIsTheChatsAddition() = runTest {
        val (handle, delegate) = onSpec(plain)
        assertThat(handle.state.effectiveEnabledTools).doesNotContain(ToolConstants.URL_CONTEXT)

        delegate.updateModelParameters(handle.state.selection.sendParameters.copy(urlContext = true, temperature = 0.4f))

        assertThat(handle.state.effectiveEnabledTools).contains(ToolConstants.URL_CONTEXT)
        assertThat(handle.state.modelParameters.temperature).isEqualTo(0.4f)
        assertThat(handle.state.modelParameters.urlContext).isFalse()

        delegate.updateModelParameters(handle.state.selection.sendParameters.copy(urlContext = false))
        assertThat(handle.state.effectiveEnabledTools).doesNotContain(ToolConstants.URL_CONTEXT)
    }

    /** The sheet has no web-search control: the tools' one control drives both searches. */
    @Test
    fun theSheetsWebSearchUnderASpecChangesNothing() = runTest {
        val (handle, delegate) = onSpec(plain)

        delegate.updateModelParameters(handle.state.selection.sendParameters.copy(webSearch = true))

        assertThat(handle.state.effectiveEnabledTools).doesNotContain(ToolConstants.WEB_SEARCH)
        // The user's own value underneath is untouched.
        assertThat(handle.state.modelParameters.webSearch).isTrue()
    }

    @Test
    fun additionsSurviveAConfigRefreshThatKeepsTheSpec() = runTest {
        val (handle, delegate) = onSpec(search)
        delegate.toggleTool(ToolConstants.FILE_SEARCH)

        startupConfig.value = config(search, plain, grounded, search.copy(name = "other"))
        advanceUntilIdle()

        assertThat(handle.state.selectedSpec).isEqualTo("search")
        assertThat(handle.state.effectiveEnabledTools).contains(ToolConstants.FILE_SEARCH)
    }

    @Test
    fun anotherSpecStartsFromItsOwnTools() = runTest {
        val (handle, delegate) = onSpec(search)
        delegate.toggleTool(ToolConstants.FILE_SEARCH)

        delegate.onSpecSelected(plain)

        assertThat(handle.state.effectiveEnabledTools).isEmpty()
        assertThat(handle.state.effectiveMcpServerNames).isEmpty()

        // Coming back doesn't revive what was added the first time.
        delegate.onSpecSelected(search)
        assertThat(handle.state.effectiveEnabledTools).containsExactly(ToolConstants.WEB_SEARCH)
    }

    @Test
    fun leavingTheSpecBringsBackTheUsersToggles() = runTest {
        val (handle, delegate) = onSpec(plain)
        delegate.toggleTool(ToolConstants.FILE_SEARCH)

        delegate.onChoiceSelected(ModelChoice.Raw("openAI", "gpt-4o"))

        assertThat(handle.state.effectiveEnabledTools)
            .containsExactly(ToolConstants.CODE_INTERPRETER, ToolConstants.WEB_SEARCH)
        assertThat(handle.state.effectiveMcpServerNames).containsExactly("mine")
        assertThat(handle.state.selection.specTools).isNull()
    }

    @Test
    fun aConfigThatDropsTheSpecBringsBackTheUsersToggles() = runTest {
        val (handle, delegate) = onSpec(search)
        delegate.toggleTool(ToolConstants.FILE_SEARCH)

        startupConfig.value = config(plain)
        advanceUntilIdle()

        assertThat(handle.state.selectedSpec).isNull()
        assertThat(handle.state.effectiveEnabledTools).contains(ToolConstants.CODE_INTERPRETER)
        assertThat(handle.state.effectiveEnabledTools).doesNotContain(ToolConstants.FILE_SEARCH)
    }

    @Test
    fun aPresetsOwnWebSearchStartsOnAndCanBeTurnedOff() = runTest {
        val (handle, delegate) = onSpec(grounded)

        assertThat(handle.state.effectiveEnabledTools).containsExactly(ToolConstants.WEB_SEARCH, ToolConstants.URL_CONTEXT)
        assertThat(handle.state.selection.sendParameters.urlContext).isTrue()
        assertThat(handle.state.lockedTools).isEmpty()

        delegate.toggleTool(ToolConstants.URL_CONTEXT)

        assertThat(handle.state.effectiveEnabledTools).containsExactly(ToolConstants.WEB_SEARCH)
        assertThat(handle.state.selection.sendParameters.urlContext).isFalse()
    }

    @Test
    fun underEnforceAPresetsOwnWebSearchIsLocked() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)))
        startupConfig.value = config(grounded, enforce = true)
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = true)
        advanceUntilIdle()
        delegate.onSpecSelected(grounded)

        delegate.toggleTool(ToolConstants.WEB_SEARCH)

        assertThat(handle.state.selectedSpec).isEqualTo("grounded")
        assertThat(handle.state.lockedTools).containsExactly(ToolConstants.WEB_SEARCH, ToolConstants.URL_CONTEXT)
        assertThat(handle.state.effectiveEnabledTools).contains(ToolConstants.WEB_SEARCH)
    }

    @Test
    fun underHideBadgeRowAdditionsDontCount() = runTest {
        val hidden = search.copy(name = "hidden", hideBadgeRow = true)
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)))
        startupConfig.value = config(hidden)
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = true)
        advanceUntilIdle()
        delegate.onSpecSelected(hidden)

        delegate.toggleTool(ToolConstants.FILE_SEARCH)

        assertThat(handle.state.toolbarShown).isFalse()
        assertThat(handle.state.effectiveEnabledTools).containsExactly(ToolConstants.WEB_SEARCH)
    }
}
