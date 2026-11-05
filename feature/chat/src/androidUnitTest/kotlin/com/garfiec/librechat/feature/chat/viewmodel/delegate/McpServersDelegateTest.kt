package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpReinitializeResponse
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The chat's MCP servers carry the MCP servers page's status, and a settings chip that needs
 * sign-in runs reconnect → sign-in → reload at the delegate boundary, reloading on every outcome.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class McpServersDelegateTest {

    private val mcpRepository = mockk<McpRepository>()
    private val github = McpServer(name = "github", title = "GitHub")
    private val servers = listOf(
        McpServer(name = "linear"),
        github,
        McpServer(name = "sentry"),
        McpServer(name = "jira"),
        McpServer(name = "notes", requestScoped = true),
        McpServer(name = "fresh"),
    )
    private val needsSignIn = mapOf(
        "linear" to McpServerStatus(connectionState = "connected"),
        "github" to McpServerStatus(requiresOAuth = true),
        "sentry" to McpServerStatus(connectionState = "error"),
        "jira" to McpServerStatus(configurationState = "needs_configuration"),
    )
    private val signedIn = needsSignIn + ("github" to McpServerStatus(connectionState = "connected"))

    private fun TestScope.newDelegate(): Pair<ChatStateHandle, McpServersDelegate> {
        val handle = ChatStateHandle(
            stateFlow = MutableStateFlow(ChatUiState()),
            scope = TestScope(StandardTestDispatcher(testScheduler)),
        )
        return handle to McpServersDelegate(ModelSelectionHandle(handle), mcpRepository)
    }

    private fun ChatStateHandle.statuses() = state.mcpServers.associate { it.name to it.status }

    @Test
    fun theLoadMapsEachServersStatusAsTheMcpPageDoes() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val (handle, delegate) = newDelegate()

        delegate.load()
        advanceUntilIdle()

        assertThat(handle.statuses()).containsExactly(
            "linear", McpDisplayStatus.CONNECTED,
            "github", McpDisplayStatus.NEEDS_SIGN_IN,
            "sentry", McpDisplayStatus.FAILED,
            "jira", McpDisplayStatus.NEEDS_SETUP,
            "notes", McpDisplayStatus.ON_DEMAND,
            "fresh", McpDisplayStatus.UNKNOWN,
        ).inOrder()
        assertThat(handle.state.mcpServers.first { it.name == "github" }.title).isEqualTo("GitHub")
    }

    @Test
    fun aFailedStatusReadLeavesStatusesUnknownNotFailed() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(listOf(github))
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Error(message = "offline")
        val (handle, delegate) = newDelegate()

        delegate.load()
        advanceUntilIdle()

        assertThat(handle.statuses()).containsExactly("github", McpDisplayStatus.UNKNOWN)
    }

    @Test
    fun aSignInReconnectsThenOpensTheLinkAndReloadsWhenItEnds() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val reconnect = CompletableDeferred<Result<McpReinitializeResponse>>()
        coEvery { mcpRepository.reinitialize("github") } coAnswers { reconnect.await() }
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()
        val opened = mutableListOf<Pair<String, String>>()
        var failures = 0

        delegate.signIn("github", { server, url -> opened += server to url }, { failures++ })
        advanceUntilIdle()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
        // A second tap while the reconnect runs starts nothing.
        delegate.signIn("github", { server, url -> opened += server to url }, { failures++ })

        reconnect.complete(
            Result.Success(McpReinitializeResponse(success = true, oauthRequired = true, oauthUrl = "https://idp/authorize")),
        )
        advanceUntilIdle()
        assertThat(opened).containsExactly("github" to "https://idp/authorize")
        assertThat(failures).isEqualTo(0)
        // Signing in is still the user's to finish: nothing reloads until it ends.
        coVerify(exactly = 1) { mcpRepository.listServers() }

        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(signedIn)
        delegate.onSignInEnded()
        advanceUntilIdle()

        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTED)
        coVerify(exactly = 1) { mcpRepository.reinitialize(any()) }
        coVerifyOrder {
            mcpRepository.listServers()
            mcpRepository.reinitialize("github")
            mcpRepository.listServers()
        }
    }

    @Test
    fun aCancelledOrFailedSignInReloadsBackToNeedingSignIn() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        coEvery { mcpRepository.reinitialize("github") } returns
            Result.Success(McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize"))
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        delegate.signIn("github", { _, _ -> }, {})
        advanceUntilIdle()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
        delegate.onSignInEnded()
        advanceUntilIdle()

        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
        // The chip can be tapped again.
        delegate.signIn("github", { _, _ -> }, {})
        advanceUntilIdle()
        coVerify(exactly = 2) { mcpRepository.reinitialize("github") }
    }

    @Test
    fun aReconnectThatNeedsNoSignInReloadsAtOnce() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        coEvery { mcpRepository.reinitialize("github") } coAnswers {
            coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(signedIn)
            Result.Success(McpReinitializeResponse(success = true))
        }
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()
        var opened = false
        var failures = 0

        delegate.signIn("github", { _, _ -> opened = true }, { failures++ })
        advanceUntilIdle()

        assertThat(opened).isFalse()
        assertThat(failures).isEqualTo(0)
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTED)
    }

    @Test
    fun aFailedReconnectSaysSoAndReloads() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        coEvery { mcpRepository.reinitialize("github") } returns Result.Error(message = "502")
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()
        var opened = false
        var failures = 0

        delegate.signIn("github", { _, _ -> opened = true }, { failures++ })
        advanceUntilIdle()

        assertThat(opened).isFalse()
        assertThat(failures).isEqualTo(1)
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
        coVerify(exactly = 2) { mcpRepository.listServers() }
    }

    @Test
    fun aRefusedReconnectWithoutALinkSaysSo() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        coEvery { mcpRepository.reinitialize("github") } returns
            Result.Success(McpReinitializeResponse(success = false, failureReason = "unreachable"))
        val (_, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()
        var failures = 0

        delegate.signIn("github", { _, _ -> }, { failures++ })
        advanceUntilIdle()

        assertThat(failures).isEqualTo(1)
        coVerify(exactly = 2) { mcpRepository.listServers() }
    }

    @Test
    fun aLoadStartedBeforeTheSignInDoesNotPaintOverIt() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()
        val slowStatus = CompletableDeferred<Result<Map<String, McpServerStatus>>>()
        coEvery { mcpRepository.getConnectionStatus() } coAnswers { slowStatus.await() }
        coEvery { mcpRepository.reinitialize("github") } returns
            Result.Success(McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize"))

        delegate.load()
        advanceUntilIdle()
        delegate.signIn("github", { _, _ -> }, {})
        advanceUntilIdle()
        slowStatus.complete(Result.Success(needsSignIn))
        advanceUntilIdle()

        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
    }
}
