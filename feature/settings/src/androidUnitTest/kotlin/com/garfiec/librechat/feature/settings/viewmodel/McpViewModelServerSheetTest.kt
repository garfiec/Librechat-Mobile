package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.core.model.mcp.McpToolCatalog
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
        val vm = McpViewModel(mcpRepository, roleRepository, permissionsRepository, serverDataStore)

        vm.openServer("github")

        assertThat(vm.uiState.value.selectedRow?.name).isEqualTo("github")
        assertThat(vm.uiState.value.selectedTools.map { it.name }).containsExactly("create_issue")
    }

    @Test
    fun `deleting the open server closes its sheet`() = runTest {
        val vm = McpViewModel(mcpRepository, roleRepository, permissionsRepository, serverDataStore)
        vm.openServer("github")

        vm.deleteServer("github")

        assertThat(vm.uiState.value.selectedServerName).isNull()
        assertThat(vm.uiState.value.selectedRow).isNull()
        assertThat(vm.uiState.value.allRows.map { it.name }).containsExactly("linear")
    }

    @Test
    fun `retrying a failed delete sends the delete again`() = runTest {
        coEvery { mcpRepository.deleteServer("linear") } returnsMany listOf(Result.Error(message = "boom"), Result.Success(Unit))
        val vm = McpViewModel(mcpRepository, roleRepository, permissionsRepository, serverDataStore)
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
        val vm = McpViewModel(mcpRepository, roleRepository, permissionsRepository, serverDataStore)

        assertThat(vm.uiState.value.serverTitles).containsExactly("github_ab12", "GitHub", "linear", "linear")
    }
}
