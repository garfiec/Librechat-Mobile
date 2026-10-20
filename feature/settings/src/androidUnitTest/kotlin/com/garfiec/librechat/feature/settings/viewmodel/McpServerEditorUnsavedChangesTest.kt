package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.config.resolveLocalizedConfigText
import com.garfiec.librechat.core.model.mcp.McpServer
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpServerEditorUnsavedChangesTest {

    private val server = McpServer(name = "docs_mcp", url = "https://docs.example.test/mcp", title = "Docs")
    private val mcpRepository = mockk<McpRepository>(relaxed = true) {
        coEvery { listServers() } returns Result.Success(listOf(server))
    }
    private val config = MutableStateFlow<StartupConfig?>(null)
    private val configRepository = mockk<ConfigRepository> { every { startupConfig } returns config }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun editor(serverName: String? = server.name) = McpServerEditorViewModel(mcpRepository, configRepository, serverName)

    @Test
    fun `a loaded server is not an edit`() = runTest {
        val vm = editor()

        assertThat(vm.uiState.value.hasUnsavedChanges).isFalse()
        vm.onBackRequested()
        assertThat(vm.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `leaving with an edit asks first, and discarding leaves`() = runTest {
        val vm = editor()
        vm.update { it.copy(description = "Internal docs") }

        vm.onBackRequested()
        assertThat(vm.uiState.value.showDiscardConfirm).isTrue()
        assertThat(vm.uiState.value.exitRequested).isFalse()

        vm.discardChanges()
        assertThat(vm.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `undoing an edit makes the form clean again`() = runTest {
        val vm = editor()
        vm.update { it.copy(name = "Docs 2") }
        vm.update { it.copy(name = "Docs") }

        assertThat(vm.uiState.value.hasUnsavedChanges).isFalse()
    }

    /** Over a blank form, Save would take the create branch and add a second server. */
    @Test
    fun `a server that can't be read freezes Save`() = runTest {
        val vm = editor(serverName = "gone")

        assertThat(vm.uiState.value.loadFailed).isTrue()
        vm.update { it.copy(name = "x", url = "https://x.test") }
        assertThat(vm.uiState.value.canSave).isFalse()
        vm.save()
        coVerify(exactly = 0) { mcpRepository.createServer(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a new server can't be added until the user trusts it`() = runTest {
        val vm = editor(serverName = null)
        vm.update { it.copy(name = "Docs", url = "https://docs.example.test/mcp") }

        assertThat(vm.uiState.value.canSave).isFalse()
        vm.update { it.copy(trusted = true) }
        assertThat(vm.uiState.value.canSave).isTrue()
    }

    /** It was trusted when it was added, as upstream's form assumes for an existing server. */
    @Test
    fun `an existing server starts trusted`() = runTest {
        assertThat(editor().uiState.value.draft.trusted).isTrue()
        assertThat(editor().uiState.value.canSave).isTrue()
    }

    @Test
    fun `the deployment's trust wording reaches the form`() = runTest {
        config.value = StartupConfig(
            interfaceConfig = Json.decodeFromString<InterfaceConfig>(
                """{"mcpServers":{"trustCheckbox":{"label":"I accept the risk"}}}""",
            ),
        )

        val trust = editor(serverName = null).uiState.value.trustCheckbox
        assertThat(resolveLocalizedConfigText(trust?.label, "en")).isEqualTo("I accept the risk")
        assertThat(trust?.subLabel).isNull()
    }
}
