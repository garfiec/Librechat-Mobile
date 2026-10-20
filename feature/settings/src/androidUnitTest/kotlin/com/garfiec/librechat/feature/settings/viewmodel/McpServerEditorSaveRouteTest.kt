package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.mcp.McpApiKeyConfig
import com.garfiec.librechat.core.model.mcp.McpAuthMode
import com.garfiec.librechat.core.model.mcp.McpOboConfig
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Which route a save takes, and what a refusal from it does to the form.
 *
 * The two are the same subject: `MCP_OAUTH_SECRET_REENTRY_REQUIRED` is raised by the UPDATE route
 * alone (`ServerConfigsDB.update`, reachable only through `PATCH /api/mcp/servers/:serverName`), so
 * an edit sent as a create can never produce it. A test that stubs the error and asserts the
 * prompt passes against a build that posts a create; asserting the route is what makes it real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class McpServerEditorSaveRouteTest {

    private val mcpRepository = mockk<McpRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository> {
        every { startupConfig } returns MutableStateFlow<StartupConfig?>(null)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { mcpRepository.listServers() } returns Result.Success(listOf(SERVER, OBO_SERVER))
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } returns Result.Success(SERVER)
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Success(SERVER)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun editor(serverName: String? = SERVER.name) = McpServerEditorViewModel(mcpRepository, configRepository, serverName)

    private fun refuseUpdate(code: String) {
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Error(exception = ApiException(statusCode = 400, message = "x", body = """{"error":"$code"}"""))
    }

    /** The form's name field is the title; the record is addressed by its stored name. */
    @Test
    fun `saving an edited server patches the stored server`() = runTest {
        val vm = editor()
        vm.update { it.copy(name = "Docs (renamed)") }

        vm.save()

        coVerify(exactly = 1) {
            mcpRepository.updateServer(
                serverName = "docs_mcp",
                name = "Docs (renamed)",
                description = null,
                url = URL,
                type = McpServerType.SSE,
                apiKey = null,
                oauth = null,
                iconPath = null,
                obo = null,
            )
        }
        coVerify(exactly = 0) { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) }
        assertThat(vm.uiState.value.savedServerName).isEqualTo("docs_mcp")

        // The save's own pop is under way; back must not queue a second one or a discard prompt.
        vm.onBackRequested()
        assertThat(vm.uiState.value.exitRequested).isFalse()
        assertThat(vm.uiState.value.showDiscardConfirm).isFalse()
    }

    @Test
    fun `saving a new server posts a create`() = runTest {
        val vm = editor(serverName = null)
        vm.update { it.copy(name = "Docs", url = URL, trusted = true) }

        vm.save()

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
        coVerify(exactly = 0) { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    /**
     * The stored client secret is bound to the OAuth endpoints it was issued for, so editing either
     * invalidates it and the same body can only be refused again. The form has to ask for the secret
     * rather than report a failure the user would retry.
     */
    @Test
    fun `a refused edit asks for the client secret again`() = runTest {
        refuseUpdate("MCP_OAUTH_SECRET_REENTRY_REQUIRED")
        val vm = editor()
        vm.update { it.copy(authMode = McpAuthMode.OAUTH, oauthAuthUrl = "https://auth.example.test/authorize") }

        vm.save()

        assertThat(vm.uiState.value.oauthSecretReentryRequired).isTrue()
        assertThat(vm.uiState.value.savedServerName).isNull()
    }

    /**
     * v0.8.8-rc4: a retained admin API key is bound to the connection it was entered for, so an
     * edit that moves the URL without re-sending the key is refused the same way, every time.
     */
    @Test
    fun `a refused edit asks for the API key again`() = runTest {
        refuseUpdate("MCP_API_KEY_REENTRY_REQUIRED")
        val vm = editor()
        vm.update { it.copy(url = "https://moved.example.test/mcp") }

        vm.save()

        assertThat(vm.uiState.value.apiKeyReentryRequired).isTrue()
        assertThat(vm.uiState.value.oauthSecretReentryRequired).isFalse()
    }

    /**
     * A re-entry refusal is told once, by the red field that has to be filled in. A form-level
     * message on top would say the same thing twice, one of the two copies scrolled out of view.
     */
    @Test
    fun `a re-entry refusal is reported by its field alone`() = runTest {
        for (code in listOf("MCP_API_KEY_REENTRY_REQUIRED", "MCP_OAUTH_SECRET_REENTRY_REQUIRED")) {
            refuseUpdate(code)
            val vm = editor()

            vm.save()

            assertThat(vm.uiState.value.apiKeyReentryRequired || vm.uiState.value.oauthSecretReentryRequired).isTrue()
            assertThat(vm.uiState.value.saveError).isNull()
        }
    }

    @Test
    fun `an unrelated failure does not ask for the client secret`() = runTest {
        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Error(message = "Server unreachable")
        val vm = editor()

        vm.save()

        assertThat(vm.uiState.value.oauthSecretReentryRequired).isFalse()
        assertThat(vm.uiState.value.apiKeyReentryRequired).isFalse()
        assertThat(vm.uiState.value.saveError).isEqualTo("Server unreachable")
    }

    /** A prompt belongs to the save that raised it; the next attempt starts clean. */
    @Test
    fun `a new save clears the last refusal`() = runTest {
        refuseUpdate("MCP_API_KEY_REENTRY_REQUIRED")
        val vm = editor()
        vm.save()
        assertThat(vm.uiState.value.apiKeyReentryRequired).isTrue()

        coEvery { mcpRepository.updateServer(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.Error(message = "Server unreachable")
        vm.save()

        assertThat(vm.uiState.value.apiKeyReentryRequired).isFalse()
        assertThat(vm.uiState.value.saveError).isEqualTo("Server unreachable")
    }

    /** A schema refusal says which field is wrong under `errors[]`; the headline alone does not. */
    @Test
    fun `a validation refusal shows the server's specific reason`() = runTest {
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
        val vm = editor(serverName = null)
        vm.update { it.copy(name = "Docs", url = URL, trusted = true) }

        vm.save()

        assertThat(vm.uiState.value.saveError)
            .isEqualTo("OAuth client_secret with client_id requires both authorization_url and token_url")
    }

    /**
     * A coded refusal this client has no copy of still has the server's own sentence, and that is
     * the specific part: the generic screen drops it because it contains a URL.
     */
    @Suppress("MaxLineLength") // single-line wire JSON fixture
    @Test
    fun `a coded refusal shows the server's message`() = runTest {
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(
                exception = ApiException(statusCode = 403, message = "x", body = """{"error":"MCP_DOMAIN_NOT_ALLOWED","message":"Domain \"http://evil.example.org\" is not allowed"}"""),
                message = "Something went wrong. Please try again.",
            )
        val vm = editor(serverName = null)
        vm.update { it.copy(name = "Docs", url = "http://evil.example.org/mcp", trusted = true) }

        vm.save()

        assertThat(vm.uiState.value.saveError).isEqualTo("Domain \"http://evil.example.org\" is not allowed")
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
        val vm = editor(OBO_SERVER.name)
        vm.update { it.copy(name = "Obo (renamed)") }
        vm.save()

        coVerify(exactly = 1) {
            mcpRepository.updateServer(
                serverName = "obo_mcp",
                name = "Obo (renamed)",
                description = null,
                url = OBO_SERVER.url,
                type = OBO_SERVER.type,
                apiKey = null,
                oauth = null,
                iconPath = "https://obo.example.test/icon.png",
                obo = McpOboConfig(scopes = "api://obo/Mcp.Tools"),
            )
        }

        val second = editor(OBO_SERVER.name)
        second.update { it.copy(name = "Obo", authMode = McpAuthMode.API_KEY, apiKey = "k") }
        second.save()

        coVerify(exactly = 1) {
            mcpRepository.updateServer(
                serverName = "obo_mcp",
                name = "Obo",
                description = null,
                url = OBO_SERVER.url,
                type = OBO_SERVER.type,
                apiKey = McpApiKeyConfig(key = "k"),
                oauth = null,
                iconPath = "https://obo.example.test/icon.png",
                obo = null,
            )
        }
    }

    /** The create route names every POST afresh, so a second tap on a slow save would add a second server. */
    @Test
    fun `a second tap while a save is in flight sends nothing`() = runTest {
        val reply = CompletableDeferred<Result<McpServer>>()
        coEvery { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) } coAnswers { reply.await() }
        val vm = editor(serverName = null)
        vm.update { it.copy(name = "Docs", url = URL, trusted = true) }

        vm.save()
        assertThat(vm.uiState.value.isSaving).isTrue()
        vm.save()
        reply.complete(Result.Success(SERVER))

        coVerify(exactly = 1) { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) }
        assertThat(vm.uiState.value.isSaving).isFalse()
    }

    private companion object {
        const val URL = "https://docs.example.test/mcp"
        val SERVER = McpServer(name = "docs_mcp", url = URL, type = McpServerType.SSE, title = "Docs")
        val OBO_SERVER = McpServer(
            name = "obo_mcp",
            url = "https://obo.example.test/mcp",
            type = McpServerType.STREAMABLE_HTTP,
            title = "Obo",
            iconPath = "https://obo.example.test/icon.png",
            obo = McpOboConfig(scopes = "api://obo/Mcp.Tools"),
        )
    }
}
