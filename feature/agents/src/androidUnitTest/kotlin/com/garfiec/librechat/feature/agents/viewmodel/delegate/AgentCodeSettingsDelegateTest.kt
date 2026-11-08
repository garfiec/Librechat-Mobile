package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.CodeEnvironmentRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.UserPersonalization
import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentCodeSettingsDelegateTest {
    private val users = mockk<UserRepository>()

    @Test
    fun `a failed read where the deployment disallows user applies an allowed scope`() = runTest(UnconfinedTestDispatcher()) {
        val flow = MutableStateFlow(
            AgentEditorUiState(
                isStatefulCodeAvailable = true,
                statefulCodeConfig = StatefulCodeSessionsConfig(allowedEnvironments = listOf("conversation")),
            ),
        )
        val delegate = AgentCodeSettingsDelegate(
            AgentEditorStateHandle(flow, this),
            AgentCodeSources(mockk<CodeEnvironmentRepository>(), users),
        )
        coEvery { users.getUser() } returns Result.Error(message = "offline")
        delegate.onEvent(AgentCodeEvent.StatefulSessions(true))
        assertThat(flow.value.code.scope).isEqualTo("conversation")
    }

    @Test
    fun `a failed read of the user's default scope is read again on the next toggle`() = runTest(UnconfinedTestDispatcher()) {
        val flow = MutableStateFlow(
            AgentEditorUiState(
                isStatefulCodeAvailable = true,
                statefulCodeConfig = StatefulCodeSessionsConfig(allowedEnvironments = listOf("user", "conversation")),
            ),
        )
        val delegate = AgentCodeSettingsDelegate(
            AgentEditorStateHandle(flow, this),
            AgentCodeSources(mockk<CodeEnvironmentRepository>(), users),
        )
        coEvery { users.getUser() } returns Result.Error(message = "offline")
        delegate.onEvent(AgentCodeEvent.StatefulSessions(true))
        assertThat(flow.value.code.scope).isNull()

        coEvery { users.getUser() } returns Result.Success(
            User(email = "m@x.dev", personalization = UserPersonalization(statefulCodeEnvironment = "conversation")),
        )
        delegate.onEvent(AgentCodeEvent.StatefulSessions(false))
        delegate.onEvent(AgentCodeEvent.StatefulSessions(true))
        assertThat(flow.value.code.scope).isEqualTo("conversation")
    }
}
