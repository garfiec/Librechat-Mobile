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
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
        coEvery { memoryRepository.getMemories() } returns Result.Success(emptyList())
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
    fun `the memories opt-out is read from the profile`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(
            testUser.copy(personalization = UserPersonalization(memories = false)),
        )
        viewModel = createViewModel()
        viewModel.refreshSummaries()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.memoriesEnabled).isFalse()
    }
}
