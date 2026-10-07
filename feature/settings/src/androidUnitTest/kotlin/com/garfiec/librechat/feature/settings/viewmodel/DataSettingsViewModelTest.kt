package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.MemoryRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.UserPersonalization
import com.garfiec.librechat.core.model.mcp.McpOboConfig
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val cacheCleaner = mockk<PlatformCacheCleaner>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val conversationRepository = mockk<ConversationRepository>(relaxed = true)
    private val mcpRepository = mockk<McpRepository>(relaxed = true)
    private val memoryRepository = mockk<MemoryRepository>(relaxed = true)
    private val keyRepository = mockk<KeyRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val permissionGate = mockk<PermissionGate>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val diagnosticLogRepository = mockk<DiagnosticLogRepository>(relaxed = true)

    private val testUser = User(
        email = "test@example.com",
        name = "Test User",
        username = "testuser",
        avatar = "https://example.com/avatar.png",
        twoFactorEnabled = false,
    )

    private lateinit var viewModel: DataSettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Setup default API responses
        coEvery { userRepository.getUser() } returns Result.Success(testUser)
        coEvery { mcpRepository.listServers() } returns Result.Success(emptyList())
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(emptyMap())
        coEvery { memoryRepository.getMemories() } returns Result.Success(emptyList())

        // Permissive-null defaults so existing tests continue to exercise the
        // same load paths via the `?: != false` idiom.
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        coEvery { permissionGate.awaitRole() } returns null
        every { configRepository.startupConfig } returns MutableStateFlow(null)
        // uiState combines these, so a relaxed (never-emitting) stub would stall it.
        every { configRepository.detectedBackendVersion } returns MutableStateFlow(null)
        every { configRepository.detectedBackend } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DataSettingsViewModel(
        cacheCleaner = cacheCleaner,
        conversationRepository = conversationRepository,
        keyRepository = keyRepository,
        diagnosticLogRepository = diagnosticLogRepository,
        mcpRepository = mcpRepository,
        memoryRepository = memoryRepository,
        userRepository = userRepository,
        roleRepository = roleRepository,
        permissionGate = permissionGate,
        configRepository = configRepository,
    )

    @Test
    fun `clearAllChats calls conversationRepository deleteAll`() = runTest {
        coEvery { conversationRepository.deleteAll() } returns Result.Success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearAllChats()
        advanceUntilIdle()

        coVerify { conversationRepository.deleteAll() }
        assertThat(viewModel.uiState.value.isClearing).isFalse()
    }

    @Test
    fun `clearAllChats failure shows error`() = runTest {
        coEvery { conversationRepository.deleteAll() } returns
            Result.Error(message = "Failed to clear")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.clearAllChats()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Failed to clear")
    }

    @Test
    fun `dismissError clears error state`() = runTest {
        coEvery { conversationRepository.deleteAll() } returns Result.Error(message = "Error")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.clearAllChats()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNotNull()

        viewModel.dismissError()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `memoriesEnabled is hydrated from the profile opt-out`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(
            testUser.copy(personalization = UserPersonalization(memories = false)),
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.memoriesEnabled).isFalse()
    }

    @Test
    fun `memoriesEnabled defaults on when the profile has no personalization block`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(testUser.copy(personalization = null))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.memoriesEnabled).isTrue()
    }

    @Test
    fun `revokeAllKeys calls keyRepository`() = runTest {
        coEvery { keyRepository.deleteAllKeys() } returns Result.Success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeAllKeys()
        advanceUntilIdle()

        coVerify { keyRepository.deleteAllKeys() }
        assertThat(viewModel.uiState.value.isKeyRevoking).isFalse()
    }

    @Test
    fun `revokeAllKeys failure shows error`() = runTest {
        coEvery { keyRepository.deleteAllKeys() } returns
            Result.Error(message = "Failed to revoke")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.revokeAllKeys()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Failed to revoke")
    }

    @Test
    fun `exportAllData shows coming soon flag`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.exportAllData()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showExportComingSoon).isTrue()

        viewModel.dismissExportComingSoon()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showExportComingSoon).isFalse()
    }

    @Test
    fun `exportLogs offers the buffer under a log filename`() = runTest {
        val buffer = """{"ts":1,"msg":"first"}""" + "\n" + """{"ts":2,"msg":"second"}""" + "\n"
        coEvery { diagnosticLogRepository.exportText() } returns buffer

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.exportLogs()
        advanceUntilIdle()

        val payload = viewModel.uiState.value.logsExportReady
        assertThat(payload).isNotNull()
        assertThat(payload?.content).isEqualTo(buffer)
        assertThat(payload?.fileName).endsWith(".log")
        assertThat(viewModel.uiState.value.isLogsExporting).isFalse()
    }

    @Test
    fun `a refused MCP edit asks for the API key again`() = runTest {
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(
                    statusCode = 400,
                    message = "Re-enter apiKey.key",
                    body = """{"error":"MCP_API_KEY_REENTRY_REQUIRED","message":"Re-enter apiKey.key"}""",
                ),
            )
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showEditMcpServerDialog(
            McpServer(name = "docs_mcp", url = "https://docs.example.test/mcp", type = McpServerType.SSE),
        )

        viewModel.saveMcpServer(name = "Docs", url = "https://moved.example.test/mcp", type = McpServerType.SSE)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mcpApiKeyReentryRequired).isTrue()
        assertThat(viewModel.uiState.value.mcpOAuthSecretReentryRequired).isFalse()
        assertThat(viewModel.uiState.value.showMcpServerDialog).isTrue()
    }

    /** The Settings path's copy of the same rule: the prompt is cleared when its dialog closes or reopens. */
    @Test
    fun `MCP re-entry prompts do not survive the Settings dialog closing or reopening`() = runTest {
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(
                    statusCode = 400,
                    message = "x",
                    body = """{"error":"MCP_API_KEY_REENTRY_REQUIRED"}""",
                ),
            )
        val server = McpServer(name = "docs_mcp", url = "https://docs.example.test/mcp", type = McpServerType.SSE)
        viewModel = createViewModel()
        advanceUntilIdle()
        fun refused() {
            viewModel.showEditMcpServerDialog(server)
            viewModel.saveMcpServer(name = "Docs", url = "https://moved.example.test/mcp", type = McpServerType.SSE)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.mcpApiKeyReentryRequired).isTrue()
        }

        refused()
        viewModel.dismissMcpServerDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.mcpApiKeyReentryRequired).isFalse()

        refused()
        viewModel.showAddMcpServerDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.mcpApiKeyReentryRequired).isFalse()
        assertThat(viewModel.uiState.value.mcpOAuthSecretReentryRequired).isFalse()
    }

    /**
     * A failed save keeps the MCP dialog open, and `error` is a snackbar in the screen the dialog
     * covers: reported there, the failure times out unseen and the save looks like it did nothing.
     */
    @Test
    fun `a failed MCP save is reported inside the dialog, not behind it`() = runTest {
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(exception = IllegalStateException("boom"), message = "Something went wrong")
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showAddMcpServerDialog()

        viewModel.saveMcpServer(name = "Docs", url = "https://docs.example.test/mcp", type = McpServerType.SSE)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showMcpServerDialog).isTrue()
        assertThat(viewModel.uiState.value.mcpServerDialogError).isEqualTo("Something went wrong")
        assertThat(viewModel.uiState.value.error).isNull()

        viewModel.dismissMcpServerDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.mcpServerDialogError).isNull()
    }

    /** The Settings path's copy of the rule: a save whose dialog closed reports through the snackbar. */
    @Test
    fun `an MCP save failure after the Settings dialog closed is reported as a snackbar`() = runTest {
        val reply = CompletableDeferred<Result<McpServer>>()
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } coAnswers { reply.await() }
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showAddMcpServerDialog()
        viewModel.saveMcpServer(name = "Docs", url = "https://docs.example.test/mcp", type = McpServerType.SSE)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.mcpServerSaving).isTrue()

        viewModel.dismissMcpServerDialog()
        reply.complete(Result.Error(exception = IllegalStateException("boom"), message = "Something went wrong"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Something went wrong")
        assertThat(viewModel.uiState.value.mcpServerDialogError).isNull()
        assertThat(viewModel.uiState.value.mcpServerSaving).isFalse()
    }

    /** The Settings path reads the same `errors[]` reasons as the MCP screen. */
    @Test
    fun `a validation refusal shows the server's specific reason in the Settings dialog`() = runTest {
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(
                    statusCode = 400,
                    message = "Invalid configuration",
                    body = """{"message":"Invalid configuration","errors":[{"message":"url: Invalid url"}]}""",
                ),
                message = "Invalid configuration",
            )
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showAddMcpServerDialog()

        viewModel.saveMcpServer(name = "Docs", url = "not a url", type = McpServerType.SSE)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mcpServerDialogError).isEqualTo("url: Invalid url")
    }

    /** The Settings path shows a coded refusal's own sentence too. */
    @Suppress("MaxLineLength") // single-line wire JSON fixture
    @Test
    fun `a coded refusal shows the server's message in the Settings dialog`() = runTest {
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(statusCode = 403, message = "x", body = """{"error":"MCP_DOMAIN_NOT_ALLOWED","message":"Domain \"http://evil.example.org\" is not allowed"}"""),
                message = "Something went wrong. Please try again.",
            )
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showAddMcpServerDialog()

        viewModel.saveMcpServer(name = "Docs", url = "http://evil.example.org/mcp", type = McpServerType.SSE)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mcpServerDialogError)
            .isEqualTo("Domain \"http://evil.example.org\" is not allowed")
    }

    /** The Settings path resends the stored icon and OBO scopes on an edit too. */
    @Test
    fun `a Settings MCP edit resends the stored icon and OBO scopes`() = runTest {
        val server = McpServer(
            name = "obo_mcp",
            url = "https://obo.example.test/mcp",
            type = McpServerType.STREAMABLE_HTTP,
            title = "Obo",
            iconPath = "https://obo.example.test/icon.png",
            obo = McpOboConfig(scopes = "api://obo/Mcp.Tools"),
        )
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.showEditMcpServerDialog(server)

        viewModel.saveMcpServer(name = "Obo (renamed)", url = server.url, type = server.type)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            mcpRepository.updateServer(
                serverName = "obo_mcp",
                name = "Obo (renamed)",
                description = null,
                url = server.url,
                type = server.type,
                apiKey = null,
                oauth = null,
                iconPath = "https://obo.example.test/icon.png",
                obo = McpOboConfig(scopes = "api://obo/Mcp.Tools"),
            )
        }
    }

    /** The Settings path reports a re-entry refusal by its field alone too. */
    @Test
    fun `a Settings MCP re-entry refusal is reported by its field alone`() = runTest {
        for (code in listOf("MCP_API_KEY_REENTRY_REQUIRED", "MCP_OAUTH_SECRET_REENTRY_REQUIRED")) {
            coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
                Result.Error(exception = ApiException(statusCode = 400, message = "x", body = """{"error":"$code"}"""))
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.showEditMcpServerDialog(
                McpServer(name = "docs_mcp", url = "https://docs.example.test/mcp", type = McpServerType.SSE),
            )

            viewModel.saveMcpServer(name = "Docs", url = "https://moved.example.test/mcp", type = McpServerType.SSE)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.mcpApiKeyReentryRequired || state.mcpOAuthSecretReentryRequired).isTrue()
            assertThat(state.mcpServerDialogError).isNull()
        }
    }

    @Test
    fun `archive-all is offered on an unresolved server and withdrawn once it answers 404`() = runTest {
        coEvery { conversationRepository.archiveAll() } returns
            Result.Error(exception = ApiException(statusCode = 404, message = "Not Found"))
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.archiveAllSupported).isTrue()

        viewModel.archiveAllChats()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.archiveAllSupported).isFalse()
        assertThat(viewModel.uiState.value.error).contains("doesn't support archiving")
    }
}
