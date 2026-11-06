package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpReinitializeResponse
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerDiscovery
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.core.model.mcp.McpToolCatalog
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.McpSignInEnd
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
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

    init {
        // Tool discovery reports nothing lapsed unless a test says so.
        coEvery { mcpRepository.getTools() } returns Result.Success(McpToolCatalog())
        coEvery { mcpRepository.cancelOAuth(any()) } returns Result.Success(Unit)
    }

    /** A chat on a raw model, so the composer shows its tool controls (`toolbarShown`). */
    private val modelChat = ChatUiState(selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", "gpt-4o")))

    private fun TestScope.newDelegate(initial: ChatUiState = modelChat): Pair<ChatStateHandle, McpServersDelegate> {
        val handle = ChatStateHandle(
            stateFlow = MutableStateFlow(initial),
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
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = true))
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
    fun aCancelledOrFailedSignInReloadsBackToNeedingSignIn() = runTest {
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
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
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

    /** A chip sign-in on github that opened its link; returns the delegate after one load. */
    private fun TestScope.openedChipSignIn(): Pair<ChatStateHandle, McpServersDelegate> {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        coEvery { mcpRepository.reinitialize("github") } returns
            Result.Success(McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize"))
        val pair = newDelegate()
        pair.second.load()
        advanceUntilIdle()
        pair.second.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        return pair
    }

    @Test
    fun aChipSignInClosedUnfinishedCancelsItsPendingFlowBeforeReloading() = runTest {
        val (handle, delegate) = openedChipSignIn()

        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()

        // Cancelled first: otherwise the status route reads the pending flow as connecting.
        coVerifyOrder {
            mcpRepository.reinitialize("github")
            mcpRepository.cancelOAuth("github")
            mcpRepository.listServers()
        }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
    }

    @Test
    fun aReturnFromABrowserSignInNeverCancelsItAndReReadsUntilItSettles() = runTest {
        val (handle, delegate) = openedChipSignIn()
        // Back in the app mid-way: the flow is still pending, so the server reads connecting.
        val pending = needsSignIn + ("github" to McpServerStatus(connectionState = "connecting", requiresOAuth = true))
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(pending)

        delegate.onSignInEnded(McpSignInEnd("github", signedIn = null))
        advanceUntilIdle()

        // Cancelling would make the browser's callback fail once the user finishes there.
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
        coVerify(exactly = 2) { mcpRepository.listServers() }

        // Finished in the browser; the next return re-reads.
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(signedIn)
        delegate.onForeground()
        advanceUntilIdle()
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTED)
        coVerify(exactly = 3) { mcpRepository.listServers() }

        // Settled: later returns don't re-read.
        delegate.onForeground()
        advanceUntilIdle()
        coVerify(exactly = 3) { mcpRepository.listServers() }
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
    }

    @Test
    fun aReturnWithNothingPendingReReadsNothing() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        val (_, delegate) = newDelegate()
        delegate.load()
        advanceUntilIdle()

        delegate.onForeground()
        advanceUntilIdle()

        coVerify(exactly = 1) { mcpRepository.listServers() }
    }

    /** github's reconnect leaves a pending flow, read as connecting until a cancel removes it. */
    private fun stubPendingFlowOnReconnect() {
        val pending = needsSignIn + ("github" to McpServerStatus(connectionState = "connecting", requiresOAuth = true))
        coEvery { mcpRepository.reinitialize("github") } coAnswers {
            coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(pending)
            Result.Success(McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize"))
        }
        coEvery { mcpRepository.cancelOAuth("github") } coAnswers {
            coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
            Result.Success(Unit)
        }
    }

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

    /** A live tool call waiting on [server]'s sign-in. */
    private fun waitingOn(server: String, isComplete: Boolean = false) = ActiveToolCall(
        id = "call_1",
        name = "list_issues_mcp_$server",
        isComplete = isComplete,
        authUrl = "https://idp/authorize",
        authServerName = server,
    )

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

    /** github already reads connecting with a sign-in pending before the chip is tapped. */
    private val flowPendingElsewhere =
        needsSignIn + ("github" to McpServerStatus(connectionState = "connecting", requiresOAuth = true))

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
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
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
