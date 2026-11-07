package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.mcp.McpReinitializeResponse
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.core.model.mcp.McpToolCatalog
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionHandle
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

@OptIn(ExperimentalCoroutinesApi::class)
abstract class McpServersDelegateTestBase {

    protected val mcpRepository = mockk<McpRepository>()
    protected val github = McpServer(name = "github", title = "GitHub")
    protected val servers = listOf(
        McpServer(name = "linear"),
        github,
        McpServer(name = "sentry"),
        McpServer(name = "jira"),
        McpServer(name = "notes", requestScoped = true),
        McpServer(name = "fresh"),
    )
    protected val needsSignIn = mapOf(
        "linear" to McpServerStatus(connectionState = "connected"),
        "github" to McpServerStatus(requiresOAuth = true),
        "sentry" to McpServerStatus(connectionState = "error"),
        "jira" to McpServerStatus(configurationState = "needs_configuration"),
    )
    protected val signedIn = needsSignIn + ("github" to McpServerStatus(connectionState = "connected"))

    /** github reads connecting with a sign-in pending: its OAuth flow is still open on the server. */
    protected val flowPending =
        needsSignIn + ("github" to McpServerStatus(connectionState = "connecting", requiresOAuth = true))

    init {
        // Tool discovery reports nothing lapsed unless a test says so.
        coEvery { mcpRepository.getTools() } returns Result.Success(McpToolCatalog())
        coEvery { mcpRepository.cancelOAuth(any()) } returns Result.Success(Unit)
    }

    /** A chat on a raw model, so the composer shows its tool controls (`toolbarShown`). */
    protected val modelChat = ChatUiState(selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", "gpt-4o")))

    protected fun TestScope.newDelegate(initial: ChatUiState = modelChat): Pair<ChatStateHandle, McpServersDelegate> {
        val handle = ChatStateHandle(
            stateFlow = MutableStateFlow(initial),
            scope = TestScope(StandardTestDispatcher(testScheduler)),
        )
        return handle to McpServersDelegate(ModelSelectionHandle(handle), mcpRepository)
    }

    protected fun ChatStateHandle.statuses() = state.mcpServers.associate { it.name to it.status }

    /**
     * github's reconnect opens an OAuth flow on the server, which then reads connecting until the
     * flow completes, times out, or the cancel route removes it.
     */
    protected fun stubPendingFlowOnReconnect() {
        coEvery { mcpRepository.reinitialize("github") } coAnswers {
            coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPending)
            Result.Success(McpReinitializeResponse(oauthRequired = true, oauthUrl = "https://idp/authorize"))
        }
        coEvery { mcpRepository.cancelOAuth("github") } coAnswers {
            coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
            Result.Success(Unit)
        }
    }

    /** A chip sign-in on github that opened its link, leaving its flow pending; after one load. */
    protected fun TestScope.openedChipSignIn(initial: ChatUiState = modelChat): Pair<ChatStateHandle, McpServersDelegate> {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        stubPendingFlowOnReconnect()
        val pair = newDelegate(initial)
        pair.second.load()
        advanceUntilIdle()
        pair.second.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        return pair
    }

    /** A chat whose github sign-in is pending on the server when it loads (a browser sign-in left open). */
    protected fun TestScope.pendingSignInChat(): Pair<ChatStateHandle, McpServersDelegate> {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(flowPending)
        stubPendingFlowOnReconnect()
        val pair = newDelegate()
        pair.second.load()
        advanceUntilIdle()
        return pair
    }

    protected fun TestScope.returnToTheChat(delegate: McpServersDelegate) {
        delegate.onBackground()
        delegate.onForeground(signInEnding = false)
        advanceUntilIdle()
    }
}
