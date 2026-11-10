package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
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
    private val conversationRepository = mockk<ConversationRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val diagnosticLogRepository = mockk<DiagnosticLogRepository>(relaxed = true)

    private lateinit var viewModel: DataSettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { configRepository.startupConfig } returns MutableStateFlow(null)
        // uiState combines these, so a relaxed (never-emitting) stub would stall it.
        every { configRepository.detectedBackendVersion } returns MutableStateFlow(null)
        every { configRepository.detectedBackend } returns MutableStateFlow(null)
        every { configRepository.endpointConfigs } returns MutableStateFlow(emptyMap())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DataSettingsViewModel(
        cacheCleaner = cacheCleaner,
        conversationRepository = conversationRepository,
        diagnosticLogRepository = diagnosticLogRepository,
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
}
