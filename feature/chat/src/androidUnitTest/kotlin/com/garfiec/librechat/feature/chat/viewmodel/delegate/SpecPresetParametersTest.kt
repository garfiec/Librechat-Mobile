package com.garfiec.librechat.feature.chat.viewmodel.delegate

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
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.google.common.truth.Truth.assertThat
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
 * A spec's served preset becomes the chat's parameters when the spec is picked or seeded (web copies
 * `{...spec.preset, spec}` into the conversation); leaving a spec resets them; only a change of spec
 * touches them, so a tweak survives every config refresh; under `enforce` the sheet can't edit them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpecPresetParametersTest {

    private val lastUsed = MutableStateFlow(LastUsedSelection("openAI", "gpt-4o", null))
    private val availableModels = MutableStateFlow(mapOf("openAI" to listOf("gpt-4o", "gpt-4.1", "gpt-5")))
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

    private val deep = ModelSpec(
        name = "deep",
        preset = Preset(endpoint = "openAI", model = "gpt-4.1", temperature = 0.9, reasoningEffort = "high", maxTokens = 32000),
    )
    private val quick = ModelSpec(
        name = "quick",
        preset = Preset(endpoint = "openAI", model = "gpt-4o", temperature = 0.2),
    )

    private fun config(vararg specs: ModelSpec, enforce: Boolean = false, modelSelect: Boolean = false) = StartupConfig(
        modelSpecs = ModelSpecs(list = specs.toList(), enforce = enforce),
        interfaceConfig = InterfaceConfig(modelSelect = modelSelect),
    )

    private fun newHandle(scope: CoroutineScope, conversationId: String? = null) = ChatStateHandle(
        stateFlow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = conversationId),
                selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", null)),
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

    private val deepParameters = ModelParameters.DEFAULT.copy(
        temperature = 0.9f,
        maxOutputTokens = 32000,
        dynamicValues = mapOf("reasoning_effort" to "high"),
    )

    @Test
    fun aSeededSpecOpensOnItsPreset() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)))
        startupConfig.value = config(deep)

        newDelegate(handle).seedInitialSelection(isNewConversation = true)
        advanceUntilIdle()

        assertThat(handle.state.selectedSpec).isEqualTo("deep")
        assertThat(handle.state.modelParameters).isEqualTo(deepParameters)
    }

    @Test
    fun aTweakSurvivesAConfigRefreshThatKeepsTheSpec() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)))
        startupConfig.value = config(deep)
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = true)
        advanceUntilIdle()

        delegate.updateModelParameters(handle.state.modelParameters.copy(temperature = 0.3f))
        startupConfig.value = config(deep, quick)
        advanceUntilIdle()

        assertThat(handle.state.selectedSpec).isEqualTo("deep")
        assertThat(handle.state.modelParameters.temperature).isEqualTo(0.3f)
    }

    @Test
    fun pickingAnotherSpecOpensOnItsPresetAlone() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)))
        startupConfig.value = config(deep, quick, modelSelect = true)
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = true)
        advanceUntilIdle()

        delegate.onSpecSelected(deep)
        assertThat(handle.state.modelParameters).isEqualTo(deepParameters)
        delegate.onSpecSelected(quick)

        assertThat(handle.state.modelParameters).isEqualTo(ModelParameters.DEFAULT.copy(temperature = 0.2f))
    }

    @Test
    fun leavingASpecForARawModelResetsAllButTheToolToggles() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)))
        startupConfig.value = config(deep, modelSelect = true)
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = true)
        advanceUntilIdle()
        // The user's own toggle, set before the spec: the spec's tools stand in for it meanwhile.
        delegate.updateModelParameters(handle.state.modelParameters.copy(webSearch = true))
        delegate.onSpecSelected(deep)

        delegate.onChoiceSelected(ModelChoice.Raw("openAI", "gpt-4o"))

        assertThat(handle.state.modelParameters).isEqualTo(ModelParameters.DEFAULT.copy(webSearch = true))
    }

    @Test
    fun aRawPickAfterARawPickKeepsTheTweaks() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)))
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = true)
        advanceUntilIdle()
        delegate.updateModelParameters(ModelParameters.DEFAULT.copy(temperature = 0.3f))

        delegate.onChoiceSelected(ModelChoice.Raw("openAI", "gpt-4.1"))

        assertThat(handle.state.modelParameters.temperature).isEqualTo(0.3f)
    }

    @Test
    fun aRetargetedSpecIsDroppedThenReappliedWithItsNewPreset() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)))
        startupConfig.value = config(deep)
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = true)
        advanceUntilIdle()

        startupConfig.value = config(deep.copy(preset = Preset(endpoint = "openAI", model = "gpt-5", temperature = 0.5)))
        advanceUntilIdle()

        assertThat(handle.state.selectedModel).isEqualTo("gpt-5")
        assertThat(handle.state.modelParameters).isEqualTo(ModelParameters.DEFAULT.copy(temperature = 0.5f))
    }

    @Test
    fun aConfigThatDropsTheSpecOfAnOpenConversationResetsItsParameters() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)), conversationId = "c1")
        startupConfig.value = config(deep, modelSelect = true)
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = false)
        advanceUntilIdle()
        delegate.applyResolvedConversationModel("openAI", "gpt-4.1", "deep")
        delegate.updateModelParameters(ModelParameters.DEFAULT.copy(temperature = 0.4f), restoring = true)

        startupConfig.value = config(quick, modelSelect = true)
        advanceUntilIdle()

        assertThat(handle.state.selectedSpec).isNull()
        assertThat(handle.state.modelParameters).isEqualTo(ModelParameters.DEFAULT)
    }

    @Test
    fun aConversationsOwnSpecDoesNotApplyThePreset() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)), conversationId = "c1")
        startupConfig.value = config(deep, modelSelect = true)
        val delegate = newDelegate(handle)

        delegate.applyResolvedConversationModel("openAI", "gpt-4.1", "deep")

        assertThat(handle.state.selectedSpec).isEqualTo("deep")
        assertThat(handle.state.modelParameters).isEqualTo(ModelParameters.DEFAULT)
    }

    @Test
    fun underEnforceTheSheetCannotEditASpecsParameters() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)))
        startupConfig.value = config(deep, enforce = true)
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = true)
        advanceUntilIdle()

        assertThat(handle.state.parametersSetBySpec).isTrue()
        delegate.updateModelParameters(ModelParameters.DEFAULT.copy(temperature = 0.1f))

        assertThat(handle.state.modelParameters).isEqualTo(deepParameters)
    }

    @Test
    fun anEnforcedConfigArrivingOverAnOpenSpecChatAppliesThePreset() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)), conversationId = "c1")
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = false)
        advanceUntilIdle()
        // Stored before enforce was on: the server now sends the preset in their place.
        delegate.applyResolvedConversationModel("openAI", "gpt-4.1", "deep")
        delegate.updateModelParameters(ModelParameters.DEFAULT.copy(temperature = 0.4f), restoring = true)

        startupConfig.value = config(deep, enforce = true)
        advanceUntilIdle()

        assertThat(handle.state.selectedSpec).isEqualTo("deep")
        assertThat(handle.state.modelParameters).isEqualTo(deepParameters)
    }

    @Test
    fun withoutEnforceTheSheetEditsASpecsParameters() = runTest {
        val handle = newHandle(TestScope(StandardTestDispatcher(testScheduler)))
        startupConfig.value = config(deep)
        val delegate = newDelegate(handle)
        delegate.seedInitialSelection(isNewConversation = true)
        advanceUntilIdle()

        assertThat(handle.state.parametersSetBySpec).isFalse()
        delegate.updateModelParameters(ModelParameters.DEFAULT.copy(temperature = 0.1f))

        assertThat(handle.state.modelParameters.temperature).isEqualTo(0.1f)
    }
}
