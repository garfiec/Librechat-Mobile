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
import com.garfiec.librechat.core.model.request.UpdateAgentRequest
import com.garfiec.librechat.feature.agents.util.ContentReader
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * An agent loaded with a value its model no longer offers loses it on ANY save — including one that
 * never opened the Advanced panel. Driven through the real save path: the panel's parameter bridge
 * only runs when a control changes, so testing it alone passes against a build whose save writes
 * the loaded `model_parameters` back verbatim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentEditorStaleParameterSaveTest {

    private val dispatcher = StandardTestDispatcher()
    private val agentRepository = mockk<AgentRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val toolFavoritesRepository = mockk<ToolFavoritesRepository>(relaxed = true)

    /** What version detection has found; null until it runs, and forever on a server it can't read. */
    private val detectedVersion = MutableStateFlow<String?>("0.8.8-rc4")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Every StateFlow the ViewModel collects is stubbed: a relaxed mock cannot return one, and
        // the failed collect would cancel the delegate scope the load runs in.
        every { configRepository.detectedBackend } returns MutableStateFlow(null)
        every { configRepository.detectedBackendVersion } returns detectedVersion
        every { configRepository.endpointConfigs } returns MutableStateFlow(emptyMap())
        every { configRepository.startupConfig } returns MutableStateFlow(null)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        every { toolFavoritesRepository.favorites } returns MutableStateFlow(emptySet())
        every { toolFavoritesRepository.isSupported } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun editorFor(agent: Agent) = AgentEditorViewModel(
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
        initialAgentId = agent.id,
    ).also {
        coEvery { agentRepository.getAgentForEditing(agent.id) } returns Result.Success(agent)
    }

    private fun agent(
        effort: String,
        provider: String = "openAI",
        model: String = "gpt-6-sol",
        effortKey: String = "reasoning_effort",
    ) = Agent(
        id = "agent_stale",
        name = "Stale Effort",
        description = "before",
        provider = provider,
        model = model,
        modelParameters = buildJsonObject {
            put(effortKey, effort)
            put("temperature", 0.7)
        },
    )

    /** Loads [agent], edits only its description, saves, and returns what the save sent. */
    private fun saveAfterDescriptionEdit(agent: Agent): UpdateAgentRequest {
        lateinit var request: UpdateAgentRequest
        runTest(dispatcher) {
            val sent = slot<UpdateAgentRequest>()
            coEvery { agentRepository.updateAgent(agent.id, capture(sent)) } returns Result.Success(agent)
            val vm = editorFor(agent)
            advanceUntilIdle()

            vm.onDescriptionChanged("after")
            vm.save()
            advanceUntilIdle()

            request = sent.captured
        }
        return request
    }

    @Test
    fun `a stale effort is dropped by a save that only edited the description`() {
        val request = saveAfterDescriptionEdit(agent("minimal"))

        assertThat(request.description).isEqualTo("after")
        assertThat(request.modelParameters?.containsKey("reasoning_effort")).isFalse()
        // Everything else the agent carried goes back untouched.
        assertThat(request.modelParameters?.get("temperature")).isEqualTo(JsonPrimitive(0.7))
    }

    @Test
    fun `an effort the model offers survives the same save`() {
        val request = saveAfterDescriptionEdit(agent("low"))

        assertThat(request.modelParameters?.get("reasoning_effort")).isEqualTo(JsonPrimitive("low"))
    }

    /**
     * `max` is filtered from the options until the server version is detected — and forever on a
     * server whose version never is. That is a version gate, not the model: the value is valid, and
     * dropping it on "not in the options" deleted it from the server for every client.
     */
    @Test
    fun `a version-gated effort survives a save made before the version is detected`() {
        detectedVersion.value = null
        val request = saveAfterDescriptionEdit(
            agent("max", provider = "anthropic", model = "claude-opus-4-5", effortKey = "effort"),
        )

        assertThat(request.modelParameters?.get("effort")).isEqualTo(JsonPrimitive("max"))
    }

    /** A value a newer backend added is in no list this app knows; it is the user's, and it stays. */
    @Test
    fun `an effort this app does not know survives the save`() {
        val request = saveAfterDescriptionEdit(agent("ultra"))

        assertThat(request.modelParameters?.get("reasoning_effort")).isEqualTo(JsonPrimitive("ultra"))
    }
}
