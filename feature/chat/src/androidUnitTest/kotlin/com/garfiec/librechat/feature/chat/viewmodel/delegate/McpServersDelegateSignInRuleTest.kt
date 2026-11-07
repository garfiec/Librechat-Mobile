package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpReinitializeResponse
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Web's `useMCPServerManager` rule: the app never cancels a pending OAuth flow by itself. */
@OptIn(ExperimentalCoroutinesApi::class)
class McpServersDelegateSignInRuleTest : McpServersDelegateTestBase() {

    @Test
    fun aTapThatNeedsSignInReconnectsOpensTheSignInAndReloadsWhenItEnds() = runTest {
        val opened = mutableListOf<Pair<String, String>>()
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        stubPendingFlowOnReconnect()
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        delegate.signIn("github", { server, url -> opened += server to url; true }, {})
        advanceUntilIdle()
        assertThat(opened).containsExactly("github" to "https://idp/authorize")

        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(signedIn)
        delegate.onSignInEnded()
        advanceUntilIdle()

        coVerifyOrder {
            mcpRepository.listServers()
            mcpRepository.reinitialize("github")
            mcpRepository.listServers()
        }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTED)
    }

    @Test
    fun closingTheSignInCancelsNothingAndTheChipReadsConnecting() = runTest {
        val (handle, delegate) = openedChipSignIn()

        // Closed, refused or abandoned: the sign-in ends, and only the statuses reload.
        delegate.onSignInEnded()
        advanceUntilIdle()

        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        coVerify(exactly = 2) { mcpRepository.listServers() }
        // The flow is still open on the server, so the chip shows it (and offers its menu).
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
        assertThat(handle.state.mcpServers.first { it.name == "github" }.requiresOAuth).isTrue()
    }

    @Test
    fun aSignInTheScreenTurnsDownCancelsNothingAndReloads() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        stubPendingFlowOnReconnect()
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        // Another sign-in (a tool card's) is binding or on screen, so the chip's never opens.
        delegate.signIn("github", { _, _ -> false }, {})
        advanceUntilIdle()

        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        coVerify(exactly = 2) { mcpRepository.listServers() }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
    }

    @Test
    fun aRunEndingCancelsNothing() = runTest {
        val streaming = modelChat.copy(content = MessagesState(isStreaming = true))
        val (handle, delegate) = openedChipSignIn(streaming)
        delegate.onSignInEnded()
        advanceUntilIdle()

        handle.update { copy(content = content.copy(isStreaming = false)) }
        advanceUntilIdle()

        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
    }

    @Test
    fun aReturnFromTheBrowserCancelsNothingAndEveryReturnReReads() = runTest {
        val (handle, delegate) = openedChipSignIn()
        // The browser sign-in ends as the user comes back, mid-way.
        delegate.onSignInEnded()
        advanceUntilIdle()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)

        // Finished in the browser later: the next return picks it up.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(signedIn)
        returnToTheChat(delegate)
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTED)
        coVerify(exactly = 3) { mcpRepository.listServers() }

        // And every return after that re-reads too.
        returnToTheChat(delegate)
        coVerify(exactly = 4) { mcpRepository.listServers() }
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
    }

    @Test
    fun cancelSignInRunsTheCancelRouteThenReloads() = runTest {
        val (handle, delegate) = pendingSignInChat()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)

        var failures = 0
        delegate.cancelSignIn("github") { failures++ }
        advanceUntilIdle()

        coVerifyOrder {
            mcpRepository.listServers()
            mcpRepository.cancelOAuth("github")
            mcpRepository.listServers()
        }
        assertThat(failures).isEqualTo(0)
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
        coVerify(exactly = 0) { mcpRepository.reinitialize(any()) }
    }

    @Test
    fun aCancelThatFailsSaysSoAndStillReloads() = runTest {
        val (handle, delegate) = pendingSignInChat()
        coEvery { mcpRepository.cancelOAuth("github") } returns Result.Error(message = "offline")
        var failures = 0

        delegate.cancelSignIn("github") { failures++ }
        advanceUntilIdle()

        // The chip reads exactly as before the tap, so only this tells the user it didn't land.
        assertThat(failures).isEqualTo(1)
        coVerify(exactly = 2) { mcpRepository.listServers() }
        // Still pending on the server, so the chip still offers its menu.
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
    }

    @Test
    fun startOverReconnectsAgainAndOpensTheSignIn() = runTest {
        val (handle, delegate) = pendingSignInChat()
        val opened = mutableListOf<String>()

        delegate.signIn("github", { server, _ -> opened += server; true }, {})
        advanceUntilIdle()

        coVerify(exactly = 1) { mcpRepository.reinitialize("github") }
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        assertThat(opened).containsExactly("github")
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
    }

    @Test
    fun startOverRightAfterCancelWaitsForTheCancel() = runTest {
        val (handle, delegate) = pendingSignInChat()
        val cancel = CompletableDeferred<Result<Unit>>()
        coEvery { mcpRepository.cancelOAuth("github") } coAnswers { cancel.await() }
        val opened = mutableListOf<String>()

        delegate.cancelSignIn("github", {})
        advanceUntilIdle()
        delegate.signIn("github", { server, _ -> opened += server; true }, {})
        advanceUntilIdle()
        // The server would replay the flow being cancelled, and the cancel would then refuse it.
        coVerify(exactly = 0) { mcpRepository.reinitialize(any()) }

        cancel.complete(Result.Success(Unit))
        advanceUntilIdle()

        coVerifyOrder {
            mcpRepository.cancelOAuth("github")
            mcpRepository.reinitialize("github")
        }
        assertThat(opened).containsExactly("github")
        // The cancel's own reload gave way to the new sign-in, which the chip reads.
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
        coVerify(exactly = 1) { mcpRepository.listServers() }
    }

    @Test
    fun cancelWhileAReconnectIsOutIsIgnored() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val reconnect = CompletableDeferred<Result<McpReinitializeResponse>>()
        coEvery { mcpRepository.reinitialize("github") } coAnswers { reconnect.await() }
        val (_, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()

        delegate.cancelSignIn("github", {})
        advanceUntilIdle()

        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
    }

    @Test
    fun theChipsReadBusyWhileTheirOwnReconnectIsOut() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val reconnect = CompletableDeferred<Result<McpReinitializeResponse>>()
        coEvery { mcpRepository.reinitialize("github") } coAnswers { reconnect.await() }
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()
        assertThat(handle.state.mcpServers.none { it.signInBusy }).isTrue()

        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        // Connecting, but its Cancel sign-in and Start over, and every other chip's sign-in, would be
        // ignored until the reconnect answers, so none is offered.
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
        assertThat(handle.state.mcpServers.all { it.signInBusy }).isTrue()

        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPending)
        reconnect.complete(Result.Success(McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize")))
        advanceUntilIdle()
        assertThat(handle.state.mcpServers.none { it.signInBusy }).isTrue()
        // Still pending on the server, so its menu is offered now.
        delegate.onSignInEnded()
        advanceUntilIdle()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
        assertThat(handle.state.mcpServers.none { it.signInBusy }).isTrue()
    }

    @Test
    fun theChipsReadBusyWhileACancelIsOut() = runTest {
        val (handle, delegate) = pendingSignInChat()
        val cancel = CompletableDeferred<Result<Unit>>()
        coEvery { mcpRepository.cancelOAuth("github") } coAnswers { cancel.await() }

        delegate.cancelSignIn("github", {})
        advanceUntilIdle()
        assertThat(handle.state.mcpServers.all { it.signInBusy }).isTrue()

        cancel.complete(Result.Success(Unit))
        advanceUntilIdle()
        assertThat(handle.state.mcpServers.none { it.signInBusy }).isTrue()
    }

    @Test
    fun aReturnWhileACancelIsOutLeavesTheReloadToIt() = runTest {
        val (_, delegate) = pendingSignInChat()
        val cancel = CompletableDeferred<Result<Unit>>()
        coEvery { mcpRepository.cancelOAuth("github") } coAnswers { cancel.await() }
        delegate.cancelSignIn("github", {})
        advanceUntilIdle()

        returnToTheChat(delegate)
        coVerify(exactly = 1) { mcpRepository.listServers() }

        cancel.complete(Result.Success(Unit))
        advanceUntilIdle()
        coVerify(exactly = 2) { mcpRepository.listServers() }
    }
}
