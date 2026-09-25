package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentSubagentsConfig
import com.garfiec.librechat.core.model.request.UpdateAgentRequest
import com.garfiec.librechat.feature.agents.util.ContentReader
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.garfiec.librechat.feature.agents.viewmodel.applyAgentData
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * The server `$set`s `subagents` whole, so every field the editor has no control for must still be
 * sent back on save. Driven from a loaded agent through the real mapper, because the loss happens
 * in the gap between what load reads and what save writes — a test that builds editor state by
 * hand cannot see it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentSubagentsRoundTripTest {

    private val agentRepository = mockk<AgentRepository>()

    private val graphs: JsonElement = buildJsonArray {
        add(buildJsonObject { put("id", "team-1") })
    }

    private fun saveAfterLoading(scope: TestScope, subagents: AgentSubagentsConfig): UpdateAgentRequest {
        val agent = Agent(id = AGENT_ID, name = "Researcher", subagents = subagents)
        val flow = MutableStateFlow(
            AgentEditorUiState(isEditMode = true, agentId = AGENT_ID).applyAgentData(agent),
        )
        val handle = AgentEditorStateHandle(flow, scope)
        val sent = slot<UpdateAgentRequest>()
        coEvery { agentRepository.updateAgent(AGENT_ID, capture(sent)) } returns Result.Success(agent)
        val files = AgentFilesDelegate(
            stateHandle = handle,
            agentRepository = agentRepository,
            fileRepository = mockk<FileRepository>(relaxed = true),
            contentReader = mockk<ContentReader>(relaxed = true),
            ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        )

        AgentSaveDelegate(handle, agentRepository, files, MutableSharedFlow(extraBufferCapacity = 8)).save()

        return sent.captured
    }

    @Test
    fun `an enabled agent keeps the team and file sharing configured on the web`() =
        runTest(UnconfinedTestDispatcher()) {
            val request = saveAfterLoading(
                this,
                AgentSubagentsConfig(
                    enabled = true,
                    allowSelf = true,
                    agentIds = listOf("other"),
                    shareFiles = true,
                    graphs = graphs,
                ),
            )

            assertThat(request.subagents?.graphs).isEqualTo(graphs)
            assertThat(request.subagents?.shareFiles).isTrue()
        }

    /** Disabling subagents still sends the object on update, so the same fields ride on it. */
    @Test
    fun `a disabled agent does not lose them either`() = runTest(UnconfinedTestDispatcher()) {
        val request = saveAfterLoading(
            this,
            AgentSubagentsConfig(enabled = false, shareFiles = true, graphs = graphs),
        )

        assertThat(request.subagents?.enabled).isFalse()
        assertThat(request.subagents?.graphs).isEqualTo(graphs)
        assertThat(request.subagents?.shareFiles).isTrue()
    }

    private companion object {
        const val AGENT_ID = "agent_abc"
    }
}
