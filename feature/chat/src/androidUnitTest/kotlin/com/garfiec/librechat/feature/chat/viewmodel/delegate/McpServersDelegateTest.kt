package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpReinitializeResponse
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerDiscovery
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.core.model.mcp.McpToolCatalog
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpServersDelegateTest : McpServersDelegateTestBase() {

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

        delegate.signIn("github", { server, url -> opened += server to url; true }, { failures++ })
        advanceUntilIdle()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
        // A second tap while the reconnect runs starts nothing.
        delegate.signIn("github", { server, url -> opened += server to url; true }, { failures++ })

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
        // A finished sign-in leaves nothing pending to cancel.
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        coVerifyOrder {
            mcpRepository.listServers()
            mcpRepository.reinitialize("github")
            mcpRepository.listServers()
        }
    }

    @Test
    fun aSignInThatEndsUnfinishedReloadsAndCanBeTappedAgain() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        coEvery { mcpRepository.reinitialize("github") } returns
            Result.Success(McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize"))
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
        delegate.onSignInEnded()
        advanceUntilIdle()

        // The server reads it as needing sign-in again, so the chip says that.
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        // The chip can be tapped again.
        delegate.signIn("github", { _, _ -> true }, {})
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

        delegate.signIn("github", { _, _ -> opened = true; true }, { failures++ })
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

        delegate.signIn("github", { _, _ -> opened = true; true }, { failures++ })
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

        delegate.signIn("github", { _, _ -> true }, { failures++ })
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
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        slowStatus.complete(Result.Success(needsSignIn))
        advanceUntilIdle()

        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
    }

    @Test
    fun aFirstResumeWithNoPauseBeforeItReReadsNothing() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val (_, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        delegate.onForeground(signInEnding = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { mcpRepository.listServers() }
    }

    @Test
    fun aReturnWhileTheFirstDiscoveryReadIsOutStillFoldsItsVerdictIn() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(listOf(github))
        coEvery { mcpRepository.getConnectionStatus() } returns
            Result.Success(mapOf("github" to McpServerStatus(connectionState = "error")))
        val catalog = CompletableDeferred<Result<McpToolCatalog>>()
        coEvery { mcpRepository.getTools() } coAnswers { catalog.await() }
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        // The file picker opens from "+" and closes while the catalog load is still running.
        returnToTheChat(delegate)
        coVerify(exactly = 2) { mcpRepository.listServers() }
        catalog.complete(
            Result.Success(McpToolCatalog(servers = mapOf("github" to McpServerDiscovery(authorizationState = "reauth_required")))),
        )
        advanceUntilIdle()

        // The lapsed authorization still reads as sign in, from the one discovery read.
        assertThat(handle.statuses()).containsExactly("github", McpDisplayStatus.NEEDS_SIGN_IN)
        coVerify(exactly = 1) { mcpRepository.getTools() }
    }

    @Test
    fun aReturnToTheChatPicksUpAStatusChangedElsewhere() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)

        // Signed in on the MCP servers page meanwhile, with no chip sign-in pending here.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(signedIn)
        delegate.onBackground()
        delegate.onForeground(signInEnding = false)
        advanceUntilIdle()

        // A tap now turns github off rather than reconnecting a live connection.
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTED)
        // The list didn't change, so discovery (a full catalog load) isn't asked again.
        coVerify(exactly = 1) { mcpRepository.getTools() }

        // Signed out there again: the next return reads that too.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        delegate.onBackground()
        delegate.onForeground(signInEnding = false)
        advanceUntilIdle()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)

        // A server added elsewhere is new to discovery, so it is asked then.
        coEvery { mcpRepository.listServers() } returns Result.Success(servers + McpServer(name = "added"))
        delegate.onBackground()
        delegate.onForeground(signInEnding = false)
        advanceUntilIdle()
        coVerify(exactly = 2) { mcpRepository.getTools() }
    }

    @Test
    fun aReturnLeavesTheReloadToAChipReconnectStillOut() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val reconnect = CompletableDeferred<Result<McpReinitializeResponse>>()
        coEvery { mcpRepository.reinitialize("github") } coAnswers { reconnect.await() }
        val (_, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()

        returnToTheChat(delegate)

        // The reconnect's end reloads; a read now would paint over the chip's connecting.
        coVerify(exactly = 1) { mcpRepository.listServers() }
    }

    @Test
    fun aReturnLeavesTheReloadToABrowserSignInEndingOnIt() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val (_, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        delegate.onBackground()
        delegate.onForeground(signInEnding = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { mcpRepository.listServers() }

        // The sign-in's end is the one reload of that return.
        delegate.onSignInEnded()
        advanceUntilIdle()
        coVerify(exactly = 2) { mcpRepository.listServers() }
    }

    @Test
    fun aNewerLoadCancelsTheOlderOnesReads() = runTest {
        var firstReadCancelled = false
        coEvery { mcpRepository.listServers() } coAnswers {
            try {
                awaitCancellation()
            } finally {
                firstReadCancelled = true
            }
        }
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        delegate.load()
        advanceUntilIdle()

        assertThat(firstReadCancelled).isTrue()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
    }

    @Test
    fun aReturnBeforeTheFirstLoadReadsNothing() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val (_, delegate) = newDelegate()

        // MCP_SERVERS.USE not answered yet (or denied): the chat never loaded its servers.
        delegate.onBackground()
        delegate.onForeground(signInEnding = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { mcpRepository.listServers() }
    }

    @Test
    fun noServersMeansNoToolDiscoveryRead() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(emptyList())
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(emptyMap())
        val (_, delegate) = newDelegate()

        delegate.load()
        advanceUntilIdle()

        coVerify(exactly = 0) { mcpRepository.getTools() }
    }

    @Test
    fun anAgentChatAsksForDiscoveryOnlyOnceTheToolControlsShow() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(listOf(github))
        coEvery { mcpRepository.getConnectionStatus() } returns
            Result.Success(mapOf("github" to McpServerStatus(connectionState = "error")))
        coEvery { mcpRepository.getTools() } returns Result.Success(
            McpToolCatalog(servers = mapOf("github" to McpServerDiscovery(authorizationState = "reauth_required"))),
        )
        val agent = ModelChoice.Raw(EndpointConstants.AGENTS, "agent_1")
        val (handle, delegate) = newDelegate(ChatUiState(selection = ModelSelectionState(choice = agent)))

        delegate.load()
        advanceUntilIdle()
        // Nothing in an agent chat reads the verdicts, and the route runs a full catalog load.
        coVerify(exactly = 0) { mcpRepository.getTools() }
        assertThat(handle.statuses()).containsExactly("github", McpDisplayStatus.FAILED)

        handle.update { copy(selection = selection.copy(choice = ModelChoice.Raw("openAI", "gpt-4o"))) }
        advanceUntilIdle()
        coVerify(exactly = 1) { mcpRepository.getTools() }
        assertThat(handle.statuses()).containsExactly("github", McpDisplayStatus.NEEDS_SIGN_IN)

        // Once per load, not on every change of model.
        handle.update { copy(selection = selection.copy(choice = agent)) }
        advanceUntilIdle()
        handle.update { copy(selection = selection.copy(choice = ModelChoice.Raw("openAI", "gpt-4.1"))) }
        advanceUntilIdle()
        coVerify(exactly = 1) { mcpRepository.getTools() }
    }

    @Test
    fun theServersShowBeforeToolDiscoveryAnswersAndItFoldsInWhenItLands() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(listOf(github))
        coEvery { mcpRepository.getConnectionStatus() } returns
            Result.Success(mapOf("github" to McpServerStatus(connectionState = "error")))
        val catalog = CompletableDeferred<Result<McpToolCatalog>>()
        coEvery { mcpRepository.getTools() } coAnswers { catalog.await() }
        val (handle, delegate) = newDelegate()

        delegate.load()
        advanceUntilIdle()
        // A cold server's tools/list can hold that route for 30s; the chips don't wait on it.
        assertThat(handle.statuses()).containsExactly("github", McpDisplayStatus.FAILED)

        catalog.complete(
            Result.Success(McpToolCatalog(servers = mapOf("github" to McpServerDiscovery(authorizationState = "reauth_required")))),
        )
        advanceUntilIdle()
        assertThat(handle.statuses()).containsExactly("github", McpDisplayStatus.NEEDS_SIGN_IN)
    }

    @Test
    fun aLaterLoadPaintsWithTheLastDiscoveryWhileItsOwnIsStillOut() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(listOf(github))
        coEvery { mcpRepository.getConnectionStatus() } returns
            Result.Success(mapOf("github" to McpServerStatus(connectionState = "error")))
        coEvery { mcpRepository.getTools() } returns Result.Success(
            McpToolCatalog(servers = mapOf("github" to McpServerDiscovery(authorizationState = "reauth_required"))),
        )
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        val slowCatalog = CompletableDeferred<Result<McpToolCatalog>>()
        coEvery { mcpRepository.getTools() } coAnswers { slowCatalog.await() }
        delegate.load()
        advanceUntilIdle()

        // As the MCP servers page does: the verdict stays until a newer discovery replaces it.
        assertThat(handle.statuses()).containsExactly("github", McpDisplayStatus.NEEDS_SIGN_IN)
        slowCatalog.complete(Result.Error(message = "timeout"))
        advanceUntilIdle()
        assertThat(handle.statuses()).containsExactly("github", McpDisplayStatus.NEEDS_SIGN_IN)
    }

    @Test
    fun aSignInAToolCallOpenedIsNeverCancelled() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val (_, delegate) = newDelegate()

        // The run's own flow: no chip opened it.
        delegate.onSignInEnded()
        advanceUntilIdle()

        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        coVerify(exactly = 1) { mcpRepository.listServers() }
    }

    @Test
    fun aLapsedAuthorizationDiscoveryFindsReadsAsSignInOverAnErrorStatus() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(listOf(github))
        coEvery { mcpRepository.getConnectionStatus() } returns
            Result.Success(mapOf("github" to McpServerStatus(connectionState = "error")))
        coEvery { mcpRepository.getTools() } returns Result.Success(
            McpToolCatalog(servers = mapOf("github" to McpServerDiscovery(authorizationState = "reauth_required"))),
        )
        val (handle, delegate) = newDelegate()

        delegate.load()
        advanceUntilIdle()

        // As the MCP servers page reads it (applyDiscoveryAuthorizationState), not FAILED.
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
        assertThat(handle.state.mcpServers.single().requiresOAuth).isTrue()
    }
}
