package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.McpOAuthLauncher
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpSignInViewModelTest {

    private val launcher = mockk<McpOAuthLauncher>()
    private val inApp = McpOAuthLaunch.InApp("github", "https://idp", "https://chat", listOf("oauth_csrf=a"))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a tap binds and hands over the sign-in, showing progress meanwhile`() = runTest {
        val bound = CompletableDeferred<Result<McpOAuthLaunch>>()
        coEvery { launcher.prepare("github", "https://idp") } coAnswers { bound.await() }
        val vm = McpSignInViewModel(launcher)

        vm.signIn("github", "https://idp")
        assertThat(vm.uiState.value.preparingServer).isEqualTo("github")
        // A second tap while binding is ignored.
        vm.signIn("github", "https://idp")

        bound.complete(Result.Success(inApp))
        assertThat(vm.uiState.value.preparingServer).isNull()
        assertThat(vm.uiState.value.launch).isEqualTo(inApp)
        coVerify(exactly = 1) { launcher.prepare(any(), any()) }
    }

    @Test
    fun `a sign-in asked for while another is binding or on screen opens nothing and says so`() = runTest {
        val bound = CompletableDeferred<Result<McpOAuthLaunch>>()
        coEvery { launcher.prepare("linear", "https://idp") } coAnswers { bound.await() }
        val vm = McpSignInViewModel(launcher)

        assertThat(vm.signIn("linear", "https://idp")).isTrue()
        // A chip's sign-in for github lands while linear's binds.
        assertThat(vm.signIn("github", "https://idp")).isFalse()
        bound.complete(Result.Success(inApp))
        // And while linear's dialog is up.
        assertThat(vm.signIn("github", "https://idp")).isFalse()

        coVerify(exactly = 0) { launcher.prepare("github", any()) }
        vm.onOutcome(McpOAuthOutcome.Success)
        assertThat(vm.uiState.value.ended).isTrue()
    }

    @Test
    fun `a failed bind opens nothing and is reported once`() = runTest {
        coEvery { launcher.prepare(any(), any()) } returns Result.Error(message = "x")
        val vm = McpSignInViewModel(launcher)

        vm.signIn("github", "https://idp")

        assertThat(vm.uiState.value.launch).isNull()
        assertThat(vm.uiState.value.failed).isTrue()
        vm.dismissFailure()
        assertThat(vm.uiState.value.failed).isFalse()
    }

    @Test
    fun `the outcome closes the sign-in, and only a refused callback is a failure`() = runTest {
        coEvery { launcher.prepare(any(), any()) } returns Result.Success(inApp)
        val vm = McpSignInViewModel(launcher)

        vm.signIn("github", "https://idp")
        vm.onOutcome(McpOAuthOutcome.Abandoned)
        assertThat(vm.uiState.value.launch).isNull()
        assertThat(vm.uiState.value.failed).isFalse()

        vm.signIn("github", "https://idp")
        vm.onOutcome(McpOAuthOutcome.Error("csrf_validation_failed"))
        assertThat(vm.uiState.value.failed).isTrue()

        vm.signIn("github", "https://idp")
        vm.onOutcome(McpOAuthOutcome.Success)
        assertThat(vm.uiState.value.launch).isNull()
        assertThat(vm.uiState.value.failed).isFalse()
    }

    @Test
    fun `every end of an in-app sign-in is reported once`() = runTest {
        coEvery { launcher.prepare(any(), any()) } returns Result.Success(inApp)
        val vm = McpSignInViewModel(launcher)

        for (outcome in listOf(McpOAuthOutcome.Success, McpOAuthOutcome.Abandoned, McpOAuthOutcome.Error("x"))) {
            vm.signIn("github", "https://idp")
            assertThat(vm.uiState.value.ended).isFalse()
            vm.onOutcome(outcome)
            assertThat(vm.uiState.value.ended).isTrue()
            vm.consumeEnded()
            assertThat(vm.uiState.value.ended).isFalse()
        }
    }

    @Test
    fun `a failed bind ends the sign-in`() = runTest {
        coEvery { launcher.prepare(any(), any()) } returns Result.Error(message = "x")
        val vm = McpSignInViewModel(launcher)

        vm.signIn("github", "https://idp")

        assertThat(vm.uiState.value.ended).isTrue()
    }

    @Test
    fun `a browser sign-in ends when the user is back, or at once if the browser never opened`() = runTest {
        val external = McpOAuthLaunch.External("github", "https://idp")
        coEvery { launcher.prepare(any(), any()) } returns Result.Success(external)
        val vm = McpSignInViewModel(launcher)

        // A resume with nothing out ends nothing.
        vm.onReturned()
        assertThat(vm.uiState.value.ended).isFalse()

        vm.signIn("github", "https://idp")
        vm.onLaunched(opened = true)
        assertThat(vm.uiState.value.ended).isFalse()
        vm.onReturned()
        assertThat(vm.uiState.value.ended).isTrue()
        vm.consumeEnded()
        vm.onReturned()
        assertThat(vm.uiState.value.ended).isFalse()

        vm.signIn("github", "https://idp")
        vm.onLaunched(opened = false)
        assertThat(vm.uiState.value.ended).isTrue()
        assertThat(vm.uiState.value.awaitingReturn).isFalse()
    }
}
