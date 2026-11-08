package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.CodeEnvironmentRepository
import com.garfiec.librechat.core.data.repository.CodeMachinesRepository
import com.garfiec.librechat.core.data.repository.CodePairingOutcome
import com.garfiec.librechat.core.data.repository.CodeSetupOutcome
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.UserPersonalization
import com.garfiec.librechat.core.model.code.CodeControlPlane
import com.garfiec.librechat.core.model.code.CodeEnvironmentConfigSchema
import com.garfiec.librechat.core.model.code.CodeEnvironmentPairingResponse
import com.garfiec.librechat.core.model.code.CodeEnvironmentPermissionField
import com.garfiec.librechat.core.model.code.CodeEnvironmentPermissionsSchema
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeEnvironmentSummary
import com.garfiec.librechat.core.model.code.CodeEnvironmentsResponse
import com.garfiec.librechat.core.model.code.CodeSetupAttempt
import com.garfiec.librechat.core.model.code.CodeWorkerPairing
import com.garfiec.librechat.core.model.code.CodeWorkerShell
import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class CodeExecutionViewModelTest {
    private val machines = mockk<CodeMachinesRepository>()
    private val environments = mockk<CodeEnvironmentRepository>()
    private val users = mockk<UserRepository>()
    private val config = mockk<ConfigRepository> {
        every { endpointConfigs } returns MutableStateFlow(
            mapOf(
                "agents" to EndpointConfig(
                    statefulCodeSessions = StatefulCodeSessionsConfig(allowedEnvironments = listOf("user", "conversation")),
                ),
            ),
        )
    }
    private val vm = CodeEnvironmentSummary(
        id = "code-1",
        name = "Dev VM",
        type = "attached",
        canEdit = true,
        canDelete = true,
        configSchema = CodeEnvironmentConfigSchema(
            permissions = CodeEnvironmentPermissionsSchema(
                fileWrite = CodeEnvironmentPermissionField(listOf("allow", "ask"), default = "ask"),
            ),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { machines.list() } returns Result.Success(
            CodeEnvironmentsResponse(listOf(vm), listOf(CodeControlPlane("plane", "Main"))),
        )
        coEvery { environments.status(any()) } returns Result.Success(
            CodeEnvironmentStatus("code-1", "ready", sandboxProfile = "native", runtimes = listOf("python")),
        )
        coEvery { users.getUser() } returns Result.Success(
            User(email = "m@x.dev", personalization = UserPersonalization(statefulCodeEnvironment = "agent-user")),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = CodeExecutionViewModel(machines, environments, users, config)

    @Test
    fun `machines show their status and the permissions they offer`() = runTest {
        val state = viewModel().uiState.value
        val row = state.machines.single()
        assertThat(row.status).isEqualTo(CodeMachineStatusLabel.READY)
        assertThat(row.detail).isEqualTo("native · python")
        assertThat(row.permissions.single().options).containsExactly("allow", "ask").inOrder()
        assertThat(state.pairPlaneId).isEqualTo("plane")
    }

    @Test
    fun `a saved scope the deployment no longer allows is listed but disabled`() = runTest {
        val scope = viewModel().uiState.value.scope!!
        assertThat(scope.selected).isEqualTo("agent-user")
        assertThat(scope.disallowed).isEqualTo("agent-user")
        assertThat(scope.options).containsExactly("user", "conversation", "agent-user").inOrder()
    }

    @Test
    fun `a refused scope goes back to the saved one`() = runTest {
        coEvery { users.setStatefulCodeEnvironment("conversation") } returns Result.Error()
        val model = viewModel()
        model.selectScope("conversation")
        assertThat(model.uiState.value.scope!!.selected).isEqualTo("agent-user")
    }

    @Test
    fun `an unread saved scope is not shown as the default`() = runTest {
        coEvery { users.getUser() } returns Result.Error(message = "offline")
        val model = viewModel()
        assertThat(model.uiState.value.scope).isNull()
        assertThat(model.uiState.value.scopeLoadFailed).isTrue()

        coEvery { users.getUser() } returns Result.Success(
            User(email = "m@x.dev", personalization = UserPersonalization(statefulCodeEnvironment = "conversation")),
        )
        model.loadScope()
        assertThat(model.uiState.value.scopeLoadFailed).isFalse()
        assertThat(model.uiState.value.scope!!.selected).isEqualTo("conversation")
    }

    @Test
    fun `a failed reload after a removal says so and keeps the rows`() = runTest {
        coEvery { machines.remove("code-1") } returns null
        val model = viewModel()
        coEvery { machines.list() } returns Result.Error(message = "offline")
        model.requestRemoval("code-1")
        model.confirmRemoval()
        assertThat(model.uiState.value.loadFailed).isTrue()
        assertThat(model.uiState.value.machines).hasSize(1)
    }

    @Test
    fun `pairing shows the one-time command for the chosen shell`() = runTest {
        coEvery { machines.pair("Dev VM", "plane") } returns CodePairingOutcome.Paired(
            CodeEnvironmentPairingResponse(
                pairing = CodeWorkerPairing("code-2", "c0de", "2999-01-01T00:00:00.000Z", "https://code.example.com"),
            ),
        )
        val model = viewModel()
        model.setPairName("Dev VM")
        model.pair()
        assertThat(model.uiState.value.pairingCommand!!.command).startsWith("librechat-code pair 'https://code.example.com'")
        model.selectShell(CodeWorkerShell.POWERSHELL)
        assertThat(model.uiState.value.pairingCommand!!.command).contains("\$env:LIBRECHAT_CODE_WORKER_ID")
    }

    @Test
    fun `a setup link opens its attempt and anything else is flagged`() = runTest {
        val model = viewModel()
        model.setSetupLink("https://chat.example.com/nope")
        model.openSetupLink()
        assertThat(model.uiState.value.setupLinkInvalid).isTrue()
    }

    @Test
    fun `the setup page offers approve only for a pending attempt with a plane picked`() = runTest {
        val id = "D".repeat(43)
        coEvery { machines.setupAttempt(id) } returns CodeSetupOutcome.Attempt(
            CodeSetupAttempt(
                attemptId = id,
                status = "pending",
                expiresAt = "2999-01-01T00:00:00.000Z",
                machineName = "laptop",
                controlPlanes = listOf(CodeControlPlane("plane", "Main")),
            ),
        )
        coEvery { machines.approveSetup(id, "plane") } returns CodeSetupOutcome.Attempt(
            CodeSetupAttempt(attemptId = id, status = "approved", controlPlaneId = "plane"),
        )
        val setup = CodeSetupViewModel(id, machines, users)
        assertThat(setup.uiState.value.status).isEqualTo(CodeSetupStatusText.PENDING)
        assertThat(setup.uiState.value.canApprove).isFalse()
        setup.selectPlane("plane")
        assertThat(setup.uiState.value.canApprove).isTrue()
        setup.approve()
        assertThat(setup.uiState.value.status).isEqualTo(CodeSetupStatusText.APPROVED)
        assertThat(setup.uiState.value.decisionOpen).isFalse()
        // The page polls while approved; end it so the poll doesn't outlive the test.
        setup.viewModelScope.cancel()
    }
}
