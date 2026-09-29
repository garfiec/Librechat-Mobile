package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.mcp.McpApiKeyConfig
import com.garfiec.librechat.core.model.mcp.McpOAuthConfig
import com.garfiec.librechat.core.model.mcp.McpOboConfig
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.garfiec.librechat.core.model.mcp.McpToolCatalog
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

/**
 * Which route a save takes, and what a refusal from it does to the dialog.
 *
 * The two are the same subject: `MCP_OAUTH_SECRET_REENTRY_REQUIRED` is raised by the UPDATE route
 * alone (`ServerConfigsDB.update`, reachable only through `PATCH /api/mcp/servers/:serverName`), so
 * an edit sent as a create can never produce it. A test that stubs the error and asserts the
 * prompt passes against a build that posts a create; asserting the route is what makes it real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class McpViewModelSaveRouteTest {

    private val mcpRepository = mockk<McpRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { mcpRepository.listServers() } returns Result.Success(emptyList())
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(emptyMap())
        coEvery { mcpRepository.getTools() } returns Result.Success(McpToolCatalog())
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } returns
            Result.Success(SERVER)
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Success(SERVER)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The dialog's own name field is the title; the record is addressed by its stored name. */
    @Test
    fun `saving an edited server patches the stored server`() = runTest {
        val vm = McpViewModel(mcpRepository)
        vm.showEditServerDialog(SERVER)

        vm.saveServer(name = "Docs (renamed)", url = URL, type = McpServerType.SSE)

        coVerify(exactly = 1) {
            mcpRepository.updateServer(
                serverName = "docs_mcp",
                name = "Docs (renamed)",
                description = null,
                url = URL,
                type = McpServerType.SSE,
                apiKey = null,
                oauth = null,
            )
        }
        coVerify(exactly = 0) { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `saving a new server still posts a create`() = runTest {
        val vm = McpViewModel(mcpRepository)
        vm.showAddServerDialog()

        vm.saveServer(name = "Docs", url = URL, type = McpServerType.SSE)

        coVerify(exactly = 1) {
            mcpRepository.createServer(
                name = "Docs",
                description = null,
                url = URL,
                type = McpServerType.SSE,
                apiKey = null,
                oauth = null,
            )
        }
        coVerify(exactly = 0) { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) }
    }

    /**
     * The stored client secret is bound to the OAuth endpoints it was issued for, so editing either
     * invalidates it and the same body can only be refused again. The dialog has to ask for the
     * secret rather than report a failure the user would retry.
     */
    @Test
    fun `a refused edit asks for the client secret again`() = runTest {
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(
                    statusCode = 400,
                    message = "Client secret required",
                    body = """{"error":"MCP_OAUTH_SECRET_REENTRY_REQUIRED"}""",
                ),
            )
        val vm = McpViewModel(mcpRepository)
        vm.showEditServerDialog(SERVER)

        vm.saveServer(
            name = "Docs",
            url = URL,
            type = McpServerType.SSE,
            oauth = McpOAuthConfig(authorizationUrl = "https://auth.example.test/authorize"),
        )

        assertThat(vm.uiState.value.oauthSecretReentryRequired).isTrue()
        assertThat(vm.uiState.value.showServerDialog).isTrue()
    }

    /**
     * v0.8.8-rc4: a retained admin API key is bound to the connection it was entered for, so an
     * edit that moves the URL without re-sending the key is refused the same way, every time.
     */
    @Test
    fun `a refused edit asks for the API key again`() = runTest {
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(
                    statusCode = 400,
                    message = "Re-enter apiKey.key",
                    body = """{"error":"MCP_API_KEY_REENTRY_REQUIRED","message":"Re-enter apiKey.key"}""",
                ),
            )
        val vm = McpViewModel(mcpRepository)
        vm.showEditServerDialog(SERVER)

        vm.saveServer(name = "Docs", url = "https://moved.example.test/mcp", type = McpServerType.SSE)

        assertThat(vm.uiState.value.apiKeyReentryRequired).isTrue()
        assertThat(vm.uiState.value.oauthSecretReentryRequired).isFalse()
        assertThat(vm.uiState.value.showServerDialog).isTrue()
    }

    /**
     * A re-entry prompt belongs to the save that raised it. Kept across a dismiss or a reopen, it
     * marks the key or secret field red on the next dialog — another server's, or a blank add dialog.
     */
    @Test
    fun `re-entry prompts do not survive the dialog closing or reopening`() = runTest {
        for (code in listOf("MCP_API_KEY_REENTRY_REQUIRED", "MCP_OAUTH_SECRET_REENTRY_REQUIRED")) {
            coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } returns
                Result.Error(exception = ApiException(statusCode = 400, message = "x", body = """{"error":"$code"}"""))
            val vm = McpViewModel(mcpRepository)
            fun refused() {
                vm.showEditServerDialog(SERVER)
                vm.saveServer(name = "Docs", url = URL, type = McpServerType.SSE)
                assertThat(vm.uiState.value.apiKeyReentryRequired || vm.uiState.value.oauthSecretReentryRequired)
                    .isTrue()
            }

            refused()
            vm.dismissServerDialog()
            assertThat(vm.uiState.value.apiKeyReentryRequired).isFalse()
            assertThat(vm.uiState.value.oauthSecretReentryRequired).isFalse()

            refused()
            vm.showAddServerDialog()
            assertThat(vm.uiState.value.apiKeyReentryRequired).isFalse()
            assertThat(vm.uiState.value.oauthSecretReentryRequired).isFalse()
        }
    }

    /** A schema refusal says which field is wrong under `errors[]`; the headline alone does not. */
    @Test
    fun `a validation refusal shows the server's specific reason in the dialog`() = runTest {
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(
                    statusCode = 400,
                    message = "Invalid configuration",
                    body = """{"message":"Invalid configuration","errors":[{"code":"custom","path":["oauth"],""" +
                        """"message":"OAuth client_secret with client_id requires both authorization_url and token_url"}]}""",
                ),
                message = "Invalid configuration",
            )
        val vm = McpViewModel(mcpRepository)
        vm.showAddServerDialog()

        vm.saveServer(name = "Docs", url = URL, type = McpServerType.SSE)

        assertThat(vm.uiState.value.serverDialogError)
            .isEqualTo("OAuth client_secret with client_id requires both authorization_url and token_url")
    }

    /**
     * A coded refusal this client has no copy of still has the server's own sentence, and that is
     * the specific part: the generic screen drops it because it contains a URL.
     */
    @Test
    fun `a coded refusal shows the server's message in the dialog`() = runTest {
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(statusCode = 403, message = "x", body = """{"error":"MCP_DOMAIN_NOT_ALLOWED","message":"Domain \"http://evil.example.org\" is not allowed"}"""),
                message = "Something went wrong. Please try again.",
            )
        val vm = McpViewModel(mcpRepository)
        vm.showAddServerDialog()

        vm.saveServer(name = "Docs", url = "http://evil.example.org/mcp", type = McpServerType.SSE)

        assertThat(vm.uiState.value.serverDialogError).isEqualTo("Domain \"http://evil.example.org\" is not allowed")
        assertThat(vm.uiState.value.apiKeyReentryRequired).isFalse()
        assertThat(vm.uiState.value.oauthSecretReentryRequired).isFalse()
    }

    /**
     * The update route replaces the config, so the icon and the OBO scopes — set on the web, never
     * shown here — go back as stored. OBO stays only while the save carries no other auth, as
     * upstream drops it when the user picks another auth type.
     */
    @Test
    fun `an edit resends the stored icon and OBO scopes`() = runTest {
        val server = McpServer(
            name = "obo_mcp",
            url = "https://obo.example.test/mcp",
            type = McpServerType.STREAMABLE_HTTP,
            title = "Obo",
            iconPath = "https://obo.example.test/icon.png",
            obo = McpOboConfig(scopes = "api://obo/Mcp.Tools"),
        )
        val vm = McpViewModel(mcpRepository)
        vm.showEditServerDialog(server)
        vm.saveServer(name = "Obo (renamed)", url = server.url, type = server.type)

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

        vm.showEditServerDialog(server)
        vm.saveServer(name = "Obo", url = server.url, type = server.type, apiKey = McpApiKeyConfig(key = "k"))

        coVerify(exactly = 1) {
            mcpRepository.updateServer(
                serverName = "obo_mcp",
                name = "Obo",
                description = null,
                url = server.url,
                type = server.type,
                apiKey = McpApiKeyConfig(key = "k"),
                oauth = null,
                iconPath = "https://obo.example.test/icon.png",
                obo = null,
            )
        }
    }

    /** Any other refusal keeps the generic message; the secret field must not turn red for it. */
    @Test
    fun `an unrelated failure does not ask for the client secret`() = runTest {
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Error(message = "Server unreachable")
        val vm = McpViewModel(mcpRepository)
        vm.showEditServerDialog(SERVER)

        vm.saveServer(name = "Docs", url = URL, type = McpServerType.SSE)

        assertThat(vm.uiState.value.oauthSecretReentryRequired).isFalse()
        assertThat(vm.uiState.value.apiKeyReentryRequired).isFalse()
        assertThat(vm.uiState.value.serverDialogError).isEqualTo("Server unreachable")
    }

    /**
     * A save outlives its dialog when the user cancels while the server is still inspecting the MCP
     * URL. Its failure then has no dialog to show in, so it goes to the screen's snackbar rather
     * than into state nothing renders.
     */
    @Test
    fun `a failure that lands after the dialog closed is reported as a snackbar`() = runTest {
        val reply = CompletableDeferred<Result<McpServer>>()
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } coAnswers { reply.await() }
        val vm = McpViewModel(mcpRepository)
        vm.showEditServerDialog(SERVER)
        vm.saveServer(name = "Docs", url = URL, type = McpServerType.SSE)

        vm.dismissServerDialog()
        reply.complete(Result.Error(message = "Server unreachable"))

        assertThat(vm.uiState.value.error).isEqualTo("Server unreachable")
        assertThat(vm.uiState.value.serverDialogError).isNull()
    }

    /** Nor may it mark, or close, the dialog the user opened on another server in the meantime. */
    @Test
    fun `a late outcome leaves the dialog opened since alone`() = runTest {
        val refusal = CompletableDeferred<Result<McpServer>>()
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } coAnswers { refusal.await() }
        val vm = McpViewModel(mcpRepository)
        vm.showEditServerDialog(SERVER)
        vm.saveServer(name = "Docs", url = URL, type = McpServerType.SSE)

        vm.dismissServerDialog()
        vm.showAddServerDialog()
        refusal.complete(
            Result.Error(exception = ApiException(statusCode = 400, message = "x", body = """{"error":"MCP_API_KEY_REENTRY_REQUIRED"}""")),
        )

        assertThat(vm.uiState.value.showServerDialog).isTrue()
        assertThat(vm.uiState.value.apiKeyReentryRequired).isFalse()
        assertThat(vm.uiState.value.serverDialogError).isNull()
        assertThat(vm.uiState.value.error).isNotNull()

        val success = CompletableDeferred<Result<McpServer>>()
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any()) } coAnswers { success.await() }
        vm.dismissServerDialog()
        vm.showEditServerDialog(SERVER)
        vm.saveServer(name = "Docs", url = URL, type = McpServerType.SSE)
        vm.dismissServerDialog()
        vm.showAddServerDialog()
        success.complete(Result.Success(SERVER))

        assertThat(vm.uiState.value.showServerDialog).isTrue()
    }

    /** The create route names every POST afresh, so a second tap on a slow save would add a second server. */
    @Test
    fun `a second tap while a save is in flight sends nothing`() = runTest {
        val reply = CompletableDeferred<Result<McpServer>>()
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } coAnswers { reply.await() }
        val vm = McpViewModel(mcpRepository)
        vm.showAddServerDialog()

        vm.saveServer(name = "Docs", url = URL, type = McpServerType.SSE)
        assertThat(vm.uiState.value.isSavingServer).isTrue()
        vm.saveServer(name = "Docs", url = URL, type = McpServerType.SSE)
        reply.complete(Result.Success(SERVER))

        coVerify(exactly = 1) { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) }
        assertThat(vm.uiState.value.isSavingServer).isFalse()
    }

    private companion object {
        const val URL = "https://docs.example.test/mcp"
        val SERVER = McpServer(
            name = "docs_mcp",
            url = URL,
            type = McpServerType.SSE,
            title = "Docs",
        )
    }
}
