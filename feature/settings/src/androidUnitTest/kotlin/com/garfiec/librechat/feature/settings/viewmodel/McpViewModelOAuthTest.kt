package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.McpOAuthLauncher
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome
import com.garfiec.librechat.core.model.mcp.McpReinitializeResponse
import com.garfiec.librechat.core.model.mcp.McpServer
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpViewModelOAuthTest {

    private val mcpRepository = mockk<McpRepository>(relaxed = true) {
        coEvery { listServers() } returns Result.Success(listOf(McpServer(name = "github")))
        coEvery { getConnectionStatus() } returns Result.Success(emptyMap())
        coEvery { reinitialize("github") } returns Result.Success(
            McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize"),
        )
    }
    private val launcher = mockk<McpOAuthLauncher>()
    private val inApp = McpOAuthLaunch.InApp("github", "https://idp/authorize", "https://chat", listOf("oauth_csrf=a"))

    private fun vm() = McpViewModel(
        mcpRepository,
        mockk<RoleRepository> { every { userPermissions } returns MutableStateFlow(null) },
        mockk<PermissionsRepository> { coEvery { getAllEffectivePermissions(any()) } returns Result.Success(emptyMap()) },
        mockk<ServerDataStore> { every { currentUrlFlow } returns flowOf("https://chat") },
        launcher,
        noLocationAnswers(),
    ).also { it.reinitializeServer("github") }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing is bound or opened before the user agrees`() = runTest {
        val vm = vm()

        assertThat(vm.uiState.value.pendingOAuth).isEqualTo(McpOAuthPrompt("github", "https://idp/authorize"))
        assertThat(vm.uiState.value.oauthLaunch).isNull()
        coVerify(exactly = 0) { launcher.prepare(any(), any()) }
    }

    @Test
    fun `continue binds then hands the screen the in-app sign-in`() = runTest {
        coEvery { launcher.prepare("github", "https://idp/authorize") } returns Result.Success(inApp)
        val vm = vm()

        vm.continueOAuth()

        assertThat(vm.uiState.value.pendingOAuth).isNull()
        assertThat(vm.uiState.value.oauthLaunch).isEqualTo(inApp)
        assertThat(vm.uiState.value.isPreparingOAuth).isFalse()
    }

    @Test
    fun `dismissing while the bind is in flight opens nothing`() = runTest {
        val bound = kotlinx.coroutines.CompletableDeferred<Result<McpOAuthLaunch>>()
        coEvery { launcher.prepare(any(), any()) } coAnswers { bound.await() }
        val vm = vm()

        vm.continueOAuth()
        vm.dismissOAuthPrompt()
        bound.complete(Result.Success(inApp))

        assertThat(vm.uiState.value.oauthLaunch).isNull()
        assertThat(vm.uiState.value.isPreparingOAuth).isFalse()
    }

    @Test
    fun `a failed bind opens nothing and offers a reconnect`() = runTest {
        coEvery { launcher.prepare(any(), any()) } returns Result.Error(message = null)
        val vm = vm()

        vm.continueOAuth()

        assertThat(vm.uiState.value.oauthLaunch).isNull()
        assertThat(vm.uiState.value.error).isEqualTo(McpViewModel.OAUTH_FAILED_MARKER)
        assertThat(vm.uiState.value.errorRetry).isEqualTo(McpRetry.Reconnect("github"))
    }

    @Test
    fun `success closes the sign-in and reloads the status`() = runTest {
        coEvery { launcher.prepare(any(), any()) } returns Result.Success(inApp)
        val vm = vm()
        vm.continueOAuth()

        vm.onOAuthOutcome(McpOAuthOutcome.Success)

        assertThat(vm.uiState.value.oauthLaunch).isNull()
        assertThat(vm.uiState.value.successMessage).isEqualTo(McpViewModel.OAUTH_SIGNED_IN_MARKER)
        // init, the reinitialize, and the outcome.
        coVerify(exactly = 3) { mcpRepository.getConnectionStatus() }
    }

    @Test
    fun `a refused callback reports a failure, closing the page does not`() = runTest {
        coEvery { launcher.prepare(any(), any()) } returns Result.Success(inApp)
        val vm = vm()
        vm.continueOAuth()
        vm.onOAuthOutcome(McpOAuthOutcome.Error("csrf_validation_failed"))
        assertThat(vm.uiState.value.error).isEqualTo(McpViewModel.OAUTH_FAILED_MARKER)

        vm.dismissError()
        vm.reinitializeServer("github")
        vm.continueOAuth()
        vm.onOAuthOutcome(McpOAuthOutcome.Abandoned)
        assertThat(vm.uiState.value.error).isNull()
        assertThat(vm.uiState.value.oauthLaunch).isNull()
    }

    @Test
    fun `an older server's sign-in is opened externally and then cleared`() = runTest {
        coEvery { launcher.prepare(any(), any()) } returns Result.Success(McpOAuthLaunch.External("github", "https://idp/authorize"))
        val vm = vm()
        vm.continueOAuth()
        assertThat(vm.uiState.value.oauthLaunch).isInstanceOf(McpOAuthLaunch.External::class.java)

        vm.onOAuthLaunched()

        assertThat(vm.uiState.value.oauthLaunch).isNull()
    }
}
