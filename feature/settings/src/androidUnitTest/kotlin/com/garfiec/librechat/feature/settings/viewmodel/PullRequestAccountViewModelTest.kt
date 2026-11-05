package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PullRequestConnectLaunch
import com.garfiec.librechat.core.data.repository.PullRequestConnectionRepository
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome
import com.garfiec.librechat.core.model.pullrequest.PullRequestConnection
import com.garfiec.librechat.core.model.pullrequest.PullRequestProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class PullRequestAccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<PullRequestConnectionRepository>()
    private val config =
        MutableStateFlow<StartupConfig?>(StartupConfig(pullRequestsEnabled = true, pullRequestsConnect = "github"))
    private val version = MutableStateFlow<String?>("0.8.9-rc1")
    private val configRepository = mockk<ConfigRepository> {
        every { startupConfig } returns config
        every { detectedBackendVersion } returns version
    }
    private val accounts = mockk<ActiveAccountProvider> {
        every { state } returns MutableStateFlow<AccountState>(AccountState.Resolved(AccountId("a")))
    }
    private val notConnected = PullRequestConnection(configured = true, provider = "github", connected = false)
    private val connected = notConnected.copy(connected = true, flowId = "f1")
    private val launch = PullRequestConnectLaunch(
        McpOAuthLaunch.InApp("GitHub", "https://github.com/login/oauth/authorize", "https://chat.example.com", listOf("oauth_csrf=a")),
        flowId = "f1",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PullRequestAccountViewModel(repository, configRepository, accounts)

    @Test
    fun `connect opens the in-app sign-in, and its success re-reads the status`() = runTest {
        coEvery { repository.status() } returns Result.Success(notConnected)
        coEvery { repository.connect("GitHub") } returns Result.Success(launch)
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.visible).isTrue()
        assertThat(vm.uiState.value.status).isEqualTo(PullRequestAccountStatus.NOT_CONNECTED)
        assertThat(vm.uiState.value.provider).isEqualTo(PullRequestProvider.GITHUB)

        vm.onRowClick()
        advanceUntilIdle()
        assertThat(vm.uiState.value.signIn).isEqualTo(launch.launch)

        coEvery { repository.status() } returns Result.Success(connected)
        vm.onSignInOutcome(McpOAuthOutcome.Success)
        advanceUntilIdle()
        assertThat(vm.uiState.value.signIn).isNull()
        assertThat(vm.uiState.value.status).isEqualTo(PullRequestAccountStatus.CONNECTED)
    }

    @Test
    fun `a closed sign-in page still re-reads the status, silently`() = runTest {
        coEvery { repository.status() } returns Result.Success(notConnected)
        coEvery { repository.connect(any()) } returns Result.Success(launch)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onRowClick()
        advanceUntilIdle()
        vm.onSignInOutcome(McpOAuthOutcome.Abandoned)
        advanceUntilIdle()
        coVerify(exactly = 2) { repository.status() }
        assertThat(vm.uiState.value.notice).isNull()
    }

    @Test
    fun `an error page says connecting failed`() = runTest {
        coEvery { repository.status() } returns Result.Success(notConnected)
        coEvery { repository.connect(any()) } returns Result.Success(launch)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onRowClick()
        advanceUntilIdle()
        vm.onSignInOutcome(McpOAuthOutcome.Error("invalid_state"))
        advanceUntilIdle()
        assertThat(vm.uiState.value.notice).isEqualTo(PullRequestAccountNotice.CONNECT_FAILED)
    }

    @Test
    fun `a not-configured connect hides the row and says so`() = runTest {
        coEvery { repository.status() } returns Result.Success(notConnected)
        coEvery { repository.connect(any()) } returns
            Result.Error(exception = ApiException(statusCode = 404, message = "x"), message = "x")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onRowClick()
        advanceUntilIdle()
        assertThat(vm.uiState.value.visible).isFalse()
        assertThat(vm.uiState.value.notice).isEqualTo(PullRequestAccountNotice.NOT_AVAILABLE)
    }

    @Test
    fun `a grant whose app is gone shows as not in use and can still be removed`() = runTest {
        config.value = StartupConfig(pullRequestsEnabled = true)
        coEvery { repository.status() } returns Result.Success(PullRequestConnection(configured = false, connected = true))
        coEvery { repository.disconnect() } returns Result.Success(Unit)
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.visible).isTrue()
        assertThat(vm.uiState.value.status).isEqualTo(PullRequestAccountStatus.NOT_IN_USE)
        assertThat(vm.uiState.value.provider).isNull()

        vm.onRowClick()
        advanceUntilIdle()
        assertThat(vm.uiState.value.confirmingDisconnect).isTrue()
        coEvery { repository.status() } returns Result.Success(PullRequestConnection(configured = false, connected = false))
        vm.confirmDisconnect()
        advanceUntilIdle()
        assertThat(vm.uiState.value.confirmingDisconnect).isFalse()
        // Nothing left to remove and nothing to connect: the row goes.
        assertThat(vm.uiState.value.visible).isFalse()
    }

    @Test
    fun `a failed disconnect stays in the dialog unless the status says it went through`() = runTest {
        coEvery { repository.status() } returns Result.Success(connected)
        coEvery { repository.disconnect() } returns Result.Error(message = null)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onRowClick()
        advanceUntilIdle()
        vm.confirmDisconnect()
        advanceUntilIdle()
        assertThat(vm.uiState.value.confirmingDisconnect).isTrue()
        assertThat(vm.uiState.value.disconnectFailed).isTrue()

        coEvery { repository.status() } returns Result.Success(notConnected)
        vm.confirmDisconnect()
        advanceUntilIdle()
        assertThat(vm.uiState.value.confirmingDisconnect).isFalse()
    }

    @Test
    fun `a failed status read offers Retry, and an older server is never asked`() = runTest {
        coEvery { repository.status() } returns Result.Error(message = null)
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.status).isEqualTo(PullRequestAccountStatus.ERROR)
        assertThat(vm.uiState.value.visible).isTrue()

        version.value = "0.8.8"
        advanceUntilIdle()
        assertThat(vm.uiState.value.visible).isFalse()
        vm.refresh()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.status() }
    }
}
