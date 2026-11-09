package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpFailureReasons
import com.garfiec.librechat.core.model.mcp.McpReinitializeResponse
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.core.model.mcp.McpToolCatalog
import com.garfiec.librechat.core.model.mcp.McpUserVar
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpViewModelServerSheetTest {

    private val servers = MutableStateFlow(
        listOf(McpServer(name = "github", dbId = "1"), McpServer(name = "linear", dbId = "2")),
    )
    private val mcpRepository = mockk<McpRepository>(relaxed = true) {
        coEvery { listServers() } answers { Result.Success(servers.value) }
        coEvery { getConnectionStatus() } returns Result.Success(emptyMap())
        coEvery { getTools() } returns Result.Success(
            McpToolCatalog(
                tools = listOf(
                    McpTool(name = "create_issue", serverName = "github"),
                    McpTool(name = "list_issues", serverName = "linear"),
                ),
            ),
        )
        coEvery { deleteServer("github") } answers {
            servers.value = servers.value.filterNot { it.name == "github" }
            Result.Success(Unit)
        }
    }
    private val roleRepository = mockk<RoleRepository> { every { userPermissions } returns MutableStateFlow(null) }
    private val permissionsRepository = mockk<PermissionsRepository> {
        coEvery { getAllEffectivePermissions(any()) } returns Result.Success(emptyMap())
    }
    private val serverDataStore = mockk<ServerDataStore> {
        every { currentUrlFlow } returns flowOf("https://chat.example.test")
    }

    private fun vm() = McpViewModel(
        mcpRepository,
        roleRepository,
        permissionsRepository,
        serverDataStore,
        mockk(relaxed = true),
        noLocationAnswers(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an open server shows its own tools only`() = runTest {
        val vm = vm()

        vm.openServer("github")

        assertThat(vm.uiState.value.selectedRow?.name).isEqualTo("github")
        assertThat(vm.uiState.value.selectedTools.map { it.name }).containsExactly("create_issue")
    }

    @Test
    fun `deleting the open server closes its sheet`() = runTest {
        val vm = vm()
        vm.openServer("github")

        vm.deleteServer("github")

        assertThat(vm.uiState.value.selectedServerName).isNull()
        assertThat(vm.uiState.value.selectedRow).isNull()
        assertThat(vm.uiState.value.allRows.map { it.name }).containsExactly("linear")
    }

    @Test
    fun `retrying a failed delete sends the delete again`() = runTest {
        coEvery { mcpRepository.deleteServer("linear") } returnsMany listOf(Result.Error(message = "boom"), Result.Success(Unit))
        val vm = vm()
        vm.deleteServer("linear")
        val retry = vm.uiState.value.errorRetry
        assertThat(retry).isEqualTo(McpRetry.Delete("linear"))

        vm.dismissError()
        vm.retry(retry)

        coVerify(exactly = 2) { mcpRepository.deleteServer("linear") }
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `the all-tools sheet names servers by title`() = runTest {
        servers.value = listOf(McpServer(name = "github_ab12", title = "GitHub"), McpServer(name = "linear"))
        val vm = vm()

        assertThat(vm.uiState.value.serverTitles).containsExactly("github_ab12", "GitHub", "linear", "linear")
    }

    private val token = McpUserVar("GH_TOKEN", "Token", "")

    private fun owedVariables() {
        servers.value = listOf(McpServer(name = "github", dbId = "1", customUserVars = listOf(token)))
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(mapOf("github" to McpServerStatus()))
        coEvery { mcpRepository.getUserVarFlags("github") } returns Result.Success(mapOf("GH_TOKEN" to false))
    }

    @Test
    fun `a server owed its variables needs setup and sorts first`() = runTest {
        owedVariables()

        val row = vm().uiState.value.rows.first()

        assertThat(row.status).isEqualTo(McpDisplayStatus.NEEDS_SETUP)
        assertThat(row.userVars).containsExactly(token)
    }

    @Test
    fun `a reconnect refused for missing variables opens their sheet instead of the server's message`() = runTest {
        owedVariables()
        coEvery { mcpRepository.reinitialize("github") } returns Result.Success(
            McpReinitializeResponse(
                success = false,
                message = "MCP server 'github' requires user-provided variable(s) [GH_TOKEN] which are not set",
                failureReason = McpFailureReasons.MISSING_CUSTOM_USER_VARS,
                missingUserVars = listOf("GH_TOKEN"),
            ),
        )
        val vm = vm()

        vm.reinitializeServer("github")

        assertThat(vm.uiState.value.variablesRow?.name).isEqualTo("github")
        assertThat(vm.uiState.value.successMessage).isNull()
    }

    @Test
    fun `opening the variables from the server sheet replaces it, and saving reconnects`() = runTest {
        owedVariables()
        coEvery { mcpRepository.saveUserVars("github", any()) } returns Result.Success(Unit)
        coEvery { mcpRepository.reinitialize("github") } returns Result.Success(McpReinitializeResponse(success = true))
        val vm = vm()
        vm.openServer("github")

        vm.openVariables("github")
        val saved = vm.saveVariables("github", mapOf("GH_TOKEN" to "secret"))

        assertThat(vm.uiState.value.selectedServerName).isNull()
        assertThat(saved).isTrue()
        coVerify { mcpRepository.saveUserVars("github", mapOf("GH_TOKEN" to "secret")) }
        coVerify { mcpRepository.reinitialize("github") }
    }

    @Test
    fun `a refused save keeps the sheet's fields and doesn't reconnect`() = runTest {
        owedVariables()
        coEvery { mcpRepository.saveUserVars("github", any()) } returns Result.Error(message = "denied")
        val vm = vm()

        assertThat(vm.saveVariables("github", mapOf("GH_TOKEN" to "secret"))).isFalse()

        coVerify(exactly = 0) { mcpRepository.reinitialize(any()) }
    }
}
