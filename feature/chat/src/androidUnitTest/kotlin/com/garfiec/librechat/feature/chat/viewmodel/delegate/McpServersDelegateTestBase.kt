package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpReinitializeResponse
import com.garfiec.librechat.core.model.mcp.McpServer
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
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * Shared fixture for the MCP servers delegate's tests: a fake server list and statuses, a delegate
 * over a real state handle, and the chip sign-in set-ups the cancel rules start from.
 */
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

    /** A chip sign-in on github that opened its link; returns the delegate after one load. */
    protected fun TestScope.openedChipSignIn(): Pair<ChatStateHandle, McpServersDelegate> {
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

    /** github's reconnect leaves a pending flow, read as connecting until a cancel removes it. */
    protected fun stubPendingFlowOnReconnect() {
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

    /** A live tool call waiting on [server]'s sign-in. */
    protected fun waitingOn(server: String, isComplete: Boolean = false) = ActiveToolCall(
        id = "call_1",
        name = "list_issues_mcp_$server",
        isComplete = isComplete,
        authUrl = "https://idp/authorize",
        authServerName = server,
    )

    /** github already reads connecting with a sign-in pending before the chip is tapped. */
    protected val flowPendingElsewhere =
        needsSignIn + ("github" to McpServerStatus(connectionState = "connecting", requiresOAuth = true))

    /** A chip sign-in on github closed while a reply streams, so its flow is left pending. */
    protected fun TestScope.chipFlowLeftForAReply(): Pair<ChatStateHandle, McpServersDelegate> {
        coEvery { mcpRepository.listServers() } returns Result.Success(servers)
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
        stubPendingFlowOnReconnect()
        val pair = newDelegate(modelChat.copy(content = MessagesState(isStreaming = true)))
        val (handle, delegate) = pair
        delegate.load()
        advanceUntilIdle()
        delegate.signIn("github", { _, _ -> true }, {})
        advanceUntilIdle()
        delegate.onSignInEnded(McpSignInEnd("github", signedIn = false))
        advanceUntilIdle()
        coVerify(exactly = 0) { mcpRepository.cancelOAuth(any()) }
        assertThat(handle.statuses()["github"]).isEqualTo(McpDisplayStatus.CONNECTING)
        return pair
    }

    protected fun TestScope.returnToTheChat(delegate: McpServersDelegate) {
        delegate.onBackground()
        delegate.onForeground()
        advanceUntilIdle()
    }

    /** github's first cancel doesn't reach the server, so its flow stays pending; later ones do. */
    protected fun stubFirstCancelFails() {
        var cancels = 0
        coEvery { mcpRepository.cancelOAuth("github") } coAnswers {
            if (cancels++ == 0) {
                Result.Error(message = "offline")
            } else {
                coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(needsSignIn)
                Result.Success(Unit)
            }
        }
    }
}
