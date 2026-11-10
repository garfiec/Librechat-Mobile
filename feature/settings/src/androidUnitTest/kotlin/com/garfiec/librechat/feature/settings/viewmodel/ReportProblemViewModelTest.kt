package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.DistributionChannel
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.core.logging.PlatformInfo
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.ui.media.EmailComposeResult
import com.google.common.truth.Truth.assertThat
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
class ReportProblemViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val logs = FakeLogRepository()
    private val configRepository = mockk<ConfigRepository>(relaxed = true) {
        every { startupConfig } returns MutableStateFlow<StartupConfig?>(null)
        every { detectedBackendVersion } returns MutableStateFlow("0.8.7")
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ReportProblemViewModel(
        diagnosticLogRepository = logs,
        configRepository = configRepository,
        appInfo = object : AppInfo {
            override val versionName = "0.1.0"
            override val versionCode = 1L
            override val gitSha = "testsha0"
            override val channel = DistributionChannel.OPEN_SOURCE
        },
        platformInfo = object : PlatformInfo {
            override val osName = "Android"
            override val osVersion = "17"
            override val deviceModel = "Google Pixel 9 Pro Fold"
        },
    )

    @Test
    fun `subject takes the kind's prefix and only the first line`() {
        assertThat(reportSubject(ReportKind.PROBLEM, "\n  Crash on send  \nmore detail"))
            .isEqualTo("Switchboard problem: Crash on send")
        assertThat(reportSubject(ReportKind.SUGGESTION, "Dark mode for code"))
            .isEqualTo("Switchboard suggestion: Dark mode for code")
    }

    @Test
    fun `subject cuts a long first line at 60 characters`() {
        val line = "a".repeat(59) + " bcdef"
        assertThat(reportSubject(ReportKind.PROBLEM, line))
            .isEqualTo("Switchboard problem: " + "a".repeat(59) + "…")
        assertThat(reportSubject(ReportKind.PROBLEM, "b".repeat(60)))
            .isEqualTo("Switchboard problem: " + "b".repeat(60))
        assertThat(reportSubject(ReportKind.PROBLEM, "c".repeat(59) + "\uD83D\uDE00 more"))
            .isEqualTo("Switchboard problem: " + "c".repeat(59) + "…")
    }

    @Test
    fun `body carries the text, the details and whether the log is attached`() {
        assertThat(reportBody("It froze", "Device: X", logIncluded = true))
            .isEqualTo("It froze\n\n——\nDevice: X\nDiagnostic log: attached")
        assertThat(reportBody("It froze", "Device: X", logIncluded = false))
            .isEqualTo("It froze\n\n——\nDevice: X\nDiagnostic log: not included")
    }

    @Test
    fun `details are About's diagnostics and the log size is loaded`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertThat(state.details).contains("Switchboard version: 0.1.0")
        assertThat(state.details).contains("LibreChat version: 0.8.7")
        assertThat(state.details).contains("Device: Android 17 · Google Pixel 9 Pro Fold")
        assertThat(state.logBytes).isEqualTo(42L)
    }

    @Test
    fun `continue needs text that isn't only whitespace`() = runTest {
        val viewModel = createViewModel()
        assertThat(viewModel.uiState.value.canContinue).isFalse()
        viewModel.setText("   \n ")
        assertThat(viewModel.uiState.value.canContinue).isFalse()
        viewModel.setText("It froze")
        assertThat(viewModel.uiState.value.canContinue).isTrue()
    }

    @Test
    fun `back with a draft asks first, and leaves once the mail app has opened`() = runTest {
        val viewModel = createViewModel()
        viewModel.onBackRequested()
        assertThat(viewModel.uiState.value.exitRequested).isTrue()

        val drafted = createViewModel()
        drafted.setText("It froze")
        drafted.onBackRequested()
        assertThat(drafted.uiState.value.showDiscardConfirm).isTrue()
        assertThat(drafted.uiState.value.exitRequested).isFalse()
        drafted.dismissDiscardConfirmation()

        drafted.onComposeResult(EmailComposeResult.Opened)
        drafted.onBackRequested()
        assertThat(drafted.uiState.value.showDiscardConfirm).isFalse()
        assertThat(drafted.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `editing after the mail app opened asks again on back`() = runTest {
        val viewModel = createViewModel()
        viewModel.setText("It froze")
        viewModel.onComposeResult(EmailComposeResult.Opened)
        viewModel.setText("It froze after I switched servers")
        viewModel.onBackRequested()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isTrue()
        assertThat(viewModel.uiState.value.exitRequested).isFalse()
    }

    @Test
    fun `compose results route to the right outcome`() = runTest {
        val viewModel = createViewModel()
        viewModel.setText("It froze")
        viewModel.onComposeResult(EmailComposeResult.Cancelled)
        with(viewModel.uiState.value) {
            assertThat(mailOpened || sheet != null || exitRequested || showDiscardConfirm).isFalse()
            assertThat(text).isEqualTo("It froze")
        }

        viewModel.onComposeResult(EmailComposeResult.NoMailApp)
        assertThat(viewModel.uiState.value.sheet).isEqualTo(ReportSheet.SEND_ANOTHER_WAY)

        viewModel.onComposeResult(EmailComposeResult.Sent)
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `the draft attaches the log when included`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setText("It froze")
        val draft = viewModel.buildDraft()
        assertThat(draft.to).isEqualTo(SUPPORT_EMAIL)
        assertThat(draft.subject).isEqualTo("Switchboard problem: It froze")
        assertThat(draft.body).endsWith("Diagnostic log: attached")
        val attachment = checkNotNull(draft.attachment)
        assertThat(attachment.fileName).matches("switchboard-logs-\\d+\\.log")
        assertThat(attachment.mimeType).isEqualTo("text/plain")
        assertThat(attachment.bytes.decodeToString()).isEqualTo(FakeLogRepository.LOG)
        assertThat(viewModel.uiState.value.preparing).isFalse()
    }

    @Test
    fun `the draft never reads the log when it is switched off`() = runTest {
        val viewModel = createViewModel()
        viewModel.setText("It froze")
        viewModel.setIncludeLog(false)
        val draft = viewModel.buildDraft()
        assertThat(logs.exportCalls).isEqualTo(0)
        assertThat(draft.attachment).isNull()
        assertThat(draft.body).endsWith("Diagnostic log: not included")
    }

    private class FakeLogRepository : DiagnosticLogRepository {
        var exportCalls = 0

        override suspend fun exportText(): String {
            exportCalls++
            return LOG
        }

        override suspend fun clear() = Unit

        override suspend fun bufferSizeBytes(): Long = 42L

        companion object {
            const val LOG = "{\"level\":\"W\"}\n"
        }
    }
}
