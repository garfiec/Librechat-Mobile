package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpReinitializeResponse
import com.garfiec.librechat.feature.chat.viewmodel.McpSignInEnd
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

/**
 * Whose OAuth flow a closed chip sign-in cancels: its own, never a run's (this chat's, or one on the
 * web or another device that the server replays into the chip's dialog), and when a cancel was held
 * back or failed, at the right later moment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class McpServersDelegateLeftoverFlowTest : McpServersDelegateTestBase() {

    @Test
    fun aSignInTheScreenTurnsDownCancelsTheFlowItsReconnectLeftPending() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        stubPendingFlowOnReconnect()
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        // Another sign-in (a tool card's) is binding or on screen, so the chip's never opens.
        delegate.signIn("github", { _, _ -> false }, {})
        advanceUntilIdle()

        // Cancelled before the reload, so the chip offers sign-in again instead of reading
        // connecting until the server's flow times out.
        coVerifyOrder {
            mcpRepository.reinitialize("github")
            mcpRepository.cancelOAuth("github")
            mcpRepository.listServers()
        }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
        // A later sign-in for github is a tool call's own: its end cancels nothing more.
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()
        coVerify(exactly = 1) { mcpRepository.cancelOAuth(any()) }
    }

    @Test
    fun aSignInTurnedDownWhileARunStreamsKeepsTheFlowAndReadsConnecting() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        stubPendingFlowOnReconnect()
        val (handle, delegate) = newDelegate(modelChat.copy(content = MessagesState(isStreaming = true)))
        delegate.load()
        advanceUntilIdle()

        // The sign-in holding the screen may be the run's own, on this very flow.
        delegate.signIn("github", { _, _ -> false }, {})
        advanceUntilIdle()

        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        val chip = handle.state.mcpServers.first { it.name == "github" }
        assertThat(chip.status).isEqualTo(McpDisplayStatus.CONNECTING)
        // Still reads "· sign in": a tap replays the flow.
        assertThat(chip.requiresOAuth).isTrue()
    }

    @Test
    fun aClosedChipSignInNeverCancelsTheFlowARunsToolCallWaitsOn() = runTest {
        val (handle, delegate) = openedChipSignIn()
        // A tool call on github paused for sign-in, with nothing streaming (the stream dropped or
        // ended around it): the server replayed that flow into the chip's dialog.
        handle.update { copy(content = content.copy(isStreaming = false, activeToolCalls = listOf(waitingOn("github")))) }

        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        // Cancelling would fail the run's tool call and refuse the card's sign-in at its callback.
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        coVerify(exactly = 2) { mcpRepository.listServers() }
    }

    @Test
    fun aClosedChipSignInStillCancelsWhenTheWaitingCallIsAnotherServersOrDone() = runTest {
        for (call in listOf(waitingOn("linear"), waitingOn("github", isComplete = true))) {
            val (handle, delegate) = openedChipSignIn()
            handle.update { copy(content = content.copy(isStreaming = false, activeToolCalls = listOf(call))) }

            delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
            advanceUntilIdle()
        }

        // Neither call waits on github's flow, so the chip's own pending flow goes both times.
        coVerify(exactly = 2) { mcpRepository.cancelOAuth("github") }
    }

    @Test
    fun aClosedChipSignInNeverCancelsTheFlowACompareLaneWaitsOn() = runTest {
        val (handle, delegate) = openedChipSignIn()
        // Only lane B still runs, and nothing on the primary list waits.
        handle.update { copy(comparisonState = comparisonState.copy(secondaryIsStreaming = true)) }

        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }

        // Lane B stopped streaming, but its call still waits on github's sign-in.
        val (laneHandle, laneDelegate) = openedChipSignIn()
        laneHandle.update {
            copy(comparisonState = comparisonState.copy(secondaryActiveToolCalls = listOf(waitingOn("github"))))
        }
        laneDelegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
    }

    @Test
    fun aClosedChipSignInNeverCancelsAFlowThatWasPendingBeforeTheTap() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        // A run in another chat (or web, or another device) waits on github's sign-in: the server
        // keeps one flow per user and server, and this chat sees only that github reads connecting.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPendingElsewhere)
        coEvery { mcpRepository.reinitialize("github") } returns
            Result.Success(McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize"))
        val (handle, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()
        val chip = handle.state.mcpServers.first { it.name == "github" }
        assertThat(chip.status).isEqualTo(McpDisplayStatus.CONNECTING)
        assertThat(chip.requiresOAuth).isTrue()

        // The reconnect replays that flow into the chip's dialog, which the user closes.
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        // Not this chat's flow: cancelling it would fail the other run's tool call.
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        coVerify(exactly = 2) { mcpRepository.listServers() }

        // Nor when the replayed sign-in never opens.
        delegate.signIn("github", { _, _ -> false }, {})
        advanceUntilIdle()
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        coVerify(exactly = 3) { mcpRepository.listServers() }
    }

    @Test
    fun aChipsOwnAbandonedBrowserFlowIsStillCancelledWhenItsNextSignInCloses() = runTest {
        val (handle, delegate) = openedChipSignIn()
        // The browser sign-in was left unfinished, so github reads connecting on return.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPendingElsewhere)
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = null))
        advanceUntilIdle()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)

        // The tap replays the chip's own flow; closing that one cancels it, as any chip sign-in.
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        coVerify(exactly = 1) { mcpRepository.cancelOAuth("github") }
    }

    @Test
    fun aChipTapRightAfterAClosedSignInWaitsForItsCancel() = runTest {
        val (handle, delegate) = openedChipSignIn()
        val cancel = CompletableDeferred<Result<Unit>>()
        coEvery { mcpRepository.cancelOAuth("github") } coAnswers { cancel.await() }
        val opened = mutableListOf<String>()

        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()
        // Tapped again while the cancel is still on its way.
        delegate.signIn("github", { server, _ -> opened += server; true }, {})
        advanceUntilIdle()

        // The server would replay the flow being cancelled, and the cancel would then kill it.
        coVerify(exactly = 1) { mcpRepository.reinitialize("github") }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)

        cancel.complete(Result.Success(Unit))
        advanceUntilIdle()

        coVerifyOrder {
            mcpRepository.cancelOAuth("github")
            mcpRepository.reinitialize("github")
        }
        assertThat(opened).containsExactly("github")
        // The cancel's own reload gave way to the new sign-in, which the chip still reads.
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
        coVerify(exactly = 1) { mcpRepository.listServers() }

        // The tap's optimistic connecting was the chip's own, not another client's flow: closing
        // this sign-in cancels its flow too.
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()
        coVerify(exactly = 2) { mcpRepository.cancelOAuth("github") }
    }

    @Test
    fun aFlowTheChipOnlyReplayedIntoTheBrowserIsNeverCancelledByItsNextSignIn() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        // A run elsewhere waits on github's sign-in before the chip is tapped.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPendingElsewhere)
        coEvery { mcpRepository.reinitialize("github") } returns
            Result.Success(McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize"))
        val (_, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        // The replayed flow goes to the browser and the user comes back unfinished.
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = null))
        advanceUntilIdle()
        // Tapped again, and this time the dialog is closed.
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        // Still the other run's flow: cancelling it would fail that run's tool call.
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
    }

    @Test
    fun aFlowLeftForAReplyIsCancelledAsTheReplyEndsNotAtTheNextTap() = runTest {
        val (handle, delegate) = chipFlowLeftForAReply()

        // The reply ends. That connecting is still the chip's own flow, so it goes now, before a run
        // on the web or another device can pick the same flow up.
        handle.update { copy(content = content.copy(isStreaming = false)) }
        advanceUntilIdle()
        coVerify(exactly = 1) { mcpRepository.cancelOAuth("github") }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)

        // Later a run elsewhere waits on github's sign-in. The chip's tap replays that flow, and
        // closing it leaves it to that run.
        coEvery { mcpRepository.reinitialize("github") } returns
            Result.Success(McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize"))
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPendingElsewhere)
        returnToTheChat(delegate)
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()
        coVerify(exactly = 1) { mcpRepository.cancelOAuth("github") }
    }

    @Test
    fun aFlowLeftForAReplyThatEndedElsewhereIsNeverCancelledLater() = runTest {
        val (handle, delegate) = chipFlowLeftForAReply()

        // Signed in on the MCP servers page while the reply streams: that flow is over.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(signedIn)
        returnToTheChat(delegate)
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTED)
        handle.update { copy(content = content.copy(isStreaming = false)) }
        advanceUntilIdle()
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }

        // Later a run in another chat waits on github's sign-in again. Not the chip's flow.
        coEvery { mcpRepository.reinitialize("github") } returns
            Result.Success(McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize"))
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPendingElsewhere)
        returnToTheChat(delegate)
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
    }

    @Test
    fun aFlowLeftForAReplyIsCancelledByAChipSignInClosedJustAsTheReplyEnds() = runTest {
        val (handle, delegate) = chipFlowLeftForAReply()

        // The reply ends and the chip is tapped before anything else runs: that sign-in decides.
        handle.update { copy(content = content.copy(isStreaming = false)) }
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        coVerify(exactly = 1) { mcpRepository.cancelOAuth("github") }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
    }

    @Test
    fun aFlowWhoseCancelFailedIsCancelledAgainWhenTheNextChipSignInCloses() = runTest {
        val (handle, delegate) = openedChipSignIn()
        // The reconnect left a pending flow, and the first cancel doesn't reach the server.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPendingElsewhere)
        var cancels = 0
        coEvery { mcpRepository.cancelOAuth("github") } coAnswers {
            if (cancels++ == 0) {
                Result.Error(message = "offline")
            } else {
                coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
                Result.Success(Unit)
            }
        }

        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)

        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        coVerify(exactly = 2) { mcpRepository.cancelOAuth("github") }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
    }

    @Test
    fun aFailedStatusReadKeepsAFlowWhoseCancelFailedForTheNextClose() = runTest {
        val (handle, delegate) = openedChipSignIn()
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPendingElsewhere)
        stubFirstCancelFails()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        // One return's status read fails: it says nothing about the flow.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Error(message = "timeout")
        returnToTheChat(delegate)
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.UNKNOWN)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPendingElsewhere)
        returnToTheChat(delegate)

        // Still the chip's own flow: closing the next sign-in cancels it.
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()
        coVerify(exactly = 2) { mcpRepository.cancelOAuth("github") }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
    }

    @Test
    fun aFlowWhoseCancelFailedIsLetGoOnceItReadsConnected() = runTest {
        val (handle, delegate) = openedChipSignIn()
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPendingElsewhere)
        stubFirstCancelFails()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        // Signed in on the MCP servers page: the flow the cancel missed is over.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(signedIn)
        returnToTheChat(delegate)
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTED)

        // A run in another chat now waits on github's sign-in. The chip only replays its flow.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPendingElsewhere)
        returnToTheChat(delegate)
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        // Only the first, failed cancel: the other run's flow is left alone.
        coVerify(exactly = 1) { mcpRepository.cancelOAuth("github") }
    }
}
