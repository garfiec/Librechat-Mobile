package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.MemoryRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.response.MemoriesResponse
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
    private val conversationRepository = mockk<ConversationRepository>(relaxed = true)
    private val mcpRepository = mockk<McpRepository>(relaxed = true)
    private val memoryRepository = mockk<MemoryRepository>(relaxed = true)
    private val keyRepository = mockk<KeyRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val permissionGate = mockk<PermissionGate>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val diagnosticLogRepository = mockk<DiagnosticLogRepository>(relaxed = true)

    private val memoriesEnabled = MutableStateFlow<Boolean?>(null)

    private lateinit var viewModel: DataSettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Setup default API responses
        every { memoryRepository.memoriesEnabled } returns memoriesEnabled
        coEvery { memoryRepository.refreshMemoriesPreference() } coAnswers {
            memoriesEnabled.value = true
            Result.Success(true)
        }
        coEvery { mcpRepository.listServers() } returns Result.Success(emptyList())
        coEvery { mcpRepository.getConnectionStatus() } returns Result.Success(emptyMap())
        coEvery { memoryRepository.getMemories() } returns Result.Success(MemoriesResponse())

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

    @Test
    fun `summaries are read on refresh, not on creation`() = runTest {
        coEvery { mcpRepository.listServers() } returns Result.Success(
            listOf(
                McpServer(name = "a", url = "http://a"),
                McpServer(name = "b", url = "http://b"),
            ),
        )
        coEvery { memoryRepository.getMemories() } returns Result.Success(MemoriesResponse())
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.mcpServerCount).isNull()
        coVerify(exactly = 0) { mcpRepository.listServers() }

        viewModel.refreshSummaries()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.mcpServerCount).isEqualTo(2)
        assertThat(viewModel.uiState.value.memoryCount).isEqualTo(0)
        assertThat(viewModel.uiState.value.memoriesEnabled).isTrue()
    }

    @Test
    fun `the memories opt-out is read through the repository on refresh`() = runTest {
        coEvery { memoryRepository.refreshMemoriesPreference() } coAnswers {
            memoriesEnabled.value = false
            Result.Success(false)
        }
        viewModel = createViewModel()
        viewModel.refreshSummaries()
        advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.refreshMemoriesPreference() }
        assertThat(viewModel.uiState.value.memoriesEnabled).isFalse()
    }

    /** The Memories page writes the same flow, so its switch reaches this row without a re-read. */
    @Test
    fun `the row follows the shared opt-out flow`() = runTest {
        viewModel = createViewModel()
        memoriesEnabled.value = true
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.memoriesEnabled).isTrue()

        memoriesEnabled.value = false
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.memoriesEnabled).isFalse()

        // Unknown (an account switch reset it) reads neither as "Off" nor as on.
        memoriesEnabled.value = null
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.memoriesEnabled).isNull()
    }

    /**
     * An opted-out user whose opt-out could not be read must not see a count, as if memory were on:
     * unknown stays unknown rather than defaulting to on.
     */
    @Test
    fun `an unread opt-out is unknown, not on, even once the count loads`() = runTest {
        coEvery { memoryRepository.refreshMemoriesPreference() } returns Result.Error(message = "offline")
        coEvery { memoryRepository.getMemories() } returns
            Result.Success(MemoriesResponse(memories = listOf(Memory(key = "a", value = "1"))))
        viewModel = createViewModel()
        viewModel.refreshSummaries()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.memoryCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.memoriesEnabled).isNull()
    }

    @Test
    fun `the memory count is the size of the listed memories`() = runTest {
        coEvery { memoryRepository.getMemories() } returns Result.Success(
            MemoriesResponse(
                memories = listOf(Memory(key = "a", value = "1"), Memory(key = "b", value = "2")),
                totalTokens = 9,
                tokenLimit = 100,
            ),
        )
        viewModel = createViewModel()
        viewModel.refreshSummaries()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.memoryCount).isEqualTo(2)
    }

    /** Without READ or OPT_OUT the page has nothing for the role: the row and its count are hidden. */
    @Test
    fun `a role without MEMORIES READ or OPT_OUT hides the row and skips the count`() = runTest {
        val role = UserRolePermissions(
            name = "USER",
            permissions = mapOf("MEMORIES" to mapOf("USE" to true, "READ" to false, "OPT_OUT" to false)),
        )
        every { roleRepository.userPermissions } returns MutableStateFlow(role)
        coEvery { permissionGate.awaitRole() } returns role
        viewModel = createViewModel()
        viewModel.refreshSummaries()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.serverMemoriesEnabled).isFalse()
        assertThat(viewModel.uiState.value.memoryCount).isNull()
        coVerify(exactly = 0) { memoryRepository.getMemories() }
    }

    /**
     * The opt-out route needs USE and OPT_OUT only, so the row stays for the switch; the count needs
     * the list route, which READ gates, so it is not fetched.
     */
    @Test
    fun `a role with OPT_OUT but no READ keeps the row and skips the count`() = runTest {
        val role = UserRolePermissions(
            name = "USER",
            permissions = mapOf("MEMORIES" to mapOf("USE" to true, "READ" to false, "OPT_OUT" to true)),
        )
        every { roleRepository.userPermissions } returns MutableStateFlow(role)
        coEvery { permissionGate.awaitRole() } returns role
        viewModel = createViewModel()
        viewModel.refreshSummaries()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.serverMemoriesEnabled).isTrue()
        assertThat(viewModel.uiState.value.memoryCount).isNull()
        coVerify(exactly = 0) { memoryRepository.getMemories() }
    }

    @Test
    fun `a role without MEMORIES USE hides the row even with READ`() = runTest {
        val role = UserRolePermissions(
            name = "USER",
            permissions = mapOf("MEMORIES" to mapOf("USE" to false, "READ" to true)),
        )
        every { roleRepository.userPermissions } returns MutableStateFlow(role)
        coEvery { permissionGate.awaitRole() } returns role
        viewModel = createViewModel()
        viewModel.refreshSummaries()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.serverMemoriesEnabled).isFalse()
        coVerify(exactly = 0) { memoryRepository.getMemories() }
    }

    /** An older server whose role JSON has no READ key is permissive, like every other gate. */
    @Test
    fun `a role JSON without a READ key keeps the row`() = runTest {
        val role = UserRolePermissions(
            name = "USER",
            permissions = mapOf("MEMORIES" to mapOf("USE" to true)),
        )
        every { roleRepository.userPermissions } returns MutableStateFlow(role)
        coEvery { permissionGate.awaitRole() } returns role
        viewModel = createViewModel()
        viewModel.refreshSummaries()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.serverMemoriesEnabled).isTrue()
        assertThat(viewModel.uiState.value.memoryCount).isEqualTo(0)
    }

    /**
     * A return from the Memories page while the first refresh still runs: that refresh may have read
     * the count before the edits, so the second one replaces it and its count is what shows.
     */
    @Test
    fun `a refresh while one is running replaces it, so the newer count wins`() = runTest {
        val stale = CompletableDeferred<Result<MemoriesResponse>>()
        coEvery { memoryRepository.getMemories() } coAnswers { stale.await() }
        viewModel = createViewModel()
        viewModel.refreshSummaries()
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns Result.Success(
            MemoriesResponse(memories = listOf(Memory(key = "a", value = "1"), Memory(key = "b", value = "2"))),
        )
        viewModel.refreshSummaries()
        advanceUntilIdle()
        // The first one's answer, had it been left running, would land late and put back the old count.
        stale.complete(Result.Success(MemoriesResponse()))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.memoryCount).isEqualTo(2)
        coVerify(exactly = 2) { memoryRepository.getMemories() }
    }
}
