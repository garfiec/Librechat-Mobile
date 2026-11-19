package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.code.PublicCodeEnvironment
import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import com.garfiec.librechat.feature.agents.viewmodel.AgentCodeSettings
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorEvent
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentSaveDelegateTest {
    private val repository = mockk<AgentRepository>()
    private val machine = PublicCodeEnvironment(id = "mbp", name = "MacBook", type = "attached")
    private val saved = Agent(id = "a", name = "Coder", statefulCodeSessions = true, codeEnvironmentId = machine.id)
    private val editing = AgentEditorUiState(
        isEditMode = true,
        agentId = saved.id,
        name = saved.name.orEmpty(),
        loadedAgent = saved,
        codeInterpreterEnabled = true,
        isCodeInterpreterAvailable = true,
        isStatefulCodeAvailable = true,
        statefulCodeConfig = StatefulCodeSessionsConfig(environments = listOf(machine)),
        code = AgentCodeSettings.of(saved),
    )

    private fun TestScope.save(state: AgentEditorUiState): MutableStateFlow<AgentEditorUiState> {
        coEvery { repository.updateAgent(any(), any()) } returns Result.Success(saved)
        val flow = MutableStateFlow(state)
        AgentSaveDelegate(AgentEditorStateHandle(flow, this), repository, mockk(relaxed = true), MutableSharedFlow<AgentEditorEvent>())
            .save()
        return flow
    }

    @Test
    fun `a half-filled identity the editor hides does not block Save`() = runTest(UnconfinedTestDispatcher()) {
        val flow = save(editing.copy(codeInterpreterEnabled = false, code = editing.code.copy(gitName = "Lia")))
        coVerify { repository.updateAgent(saved.id, match { it.gitIdentity == null }) }
        assertThat(flow.value.codeGitIdentityError).isFalse()
    }

    @Test
    fun `a shown identity the server would reject blocks Save on the field`() = runTest(UnconfinedTestDispatcher()) {
        val flow = save(editing.copy(code = editing.code.copy(gitName = "Lia", gitEmail = "dev.@example.com")))
        coVerify(exactly = 0) { repository.updateAgent(any(), any()) }
        assertThat(flow.value.codeGitIdentityError).isTrue()
    }
}
