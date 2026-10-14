package com.garfiec.librechat.feature.auth.diagnostics

import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticLogExportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<DiagnosticLogRepository>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `export hands the buffer to the saver and reports a completed save`() = runTest {
        coEvery { repository.exportText() } returns "{\"msg\":\"line\"}\n"
        val viewModel = DiagnosticLogExportViewModel(repository)

        viewModel.export()
        advanceUntilIdle()

        val file = viewModel.state.value.pendingFile
        assertThat(file?.content).isEqualTo("{\"msg\":\"line\"}\n")
        assertThat(file?.fileName).matches("switchboard-logs-\\d+\\.log")
        assertThat(viewModel.state.value.isExporting).isTrue()

        viewModel.consumePendingFile()
        viewModel.onSaveFinished(success = true, cancelled = false)

        assertThat(viewModel.state.value).isEqualTo(
            DiagnosticLogExportState(outcome = DiagnosticLogOutcome.Exported),
        )
    }

    @Test
    fun `a cancelled save reports nothing`() = runTest {
        coEvery { repository.exportText() } returns "x"
        val viewModel = DiagnosticLogExportViewModel(repository)
        viewModel.export()
        advanceUntilIdle()
        viewModel.consumePendingFile()

        viewModel.onSaveFinished(success = false, cancelled = true)

        assertThat(viewModel.state.value).isEqualTo(DiagnosticLogExportState())
    }

    @Test
    fun `a failed read or write is reported as failed`() = runTest {
        coEvery { repository.exportText() } throws IOException("disk")
        val viewModel = DiagnosticLogExportViewModel(repository)

        viewModel.export()
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(
            DiagnosticLogExportState(outcome = DiagnosticLogOutcome.Failed),
        )

        coEvery { repository.exportText() } returns "x"
        viewModel.export()
        advanceUntilIdle()
        viewModel.consumePendingFile()
        viewModel.onSaveFinished(success = false, cancelled = false)

        assertThat(viewModel.state.value.outcome).isEqualTo(DiagnosticLogOutcome.Failed)
    }

    @Test
    fun `a second tap while exporting does not read the buffer twice`() = runTest {
        coEvery { repository.exportText() } returns "x"
        val viewModel = DiagnosticLogExportViewModel(repository)

        viewModel.export()
        viewModel.export()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.exportText() }
    }

    @Test
    fun `hiding the link forgets the outcome so the next error starts clean`() = runTest {
        coEvery { repository.exportText() } returns "x"
        val viewModel = DiagnosticLogExportViewModel(repository)
        viewModel.export()
        advanceUntilIdle()
        viewModel.consumePendingFile()
        viewModel.onSaveFinished(success = true, cancelled = false)

        viewModel.onHidden()

        assertThat(viewModel.state.value).isEqualTo(DiagnosticLogExportState())
    }

    @Test
    fun `hiding the link drops a file the saver never took`() = runTest {
        coEvery { repository.exportText() } returns "x"
        val viewModel = DiagnosticLogExportViewModel(repository)

        viewModel.export()
        advanceUntilIdle()
        viewModel.onHidden()

        assertThat(viewModel.state.value).isEqualTo(DiagnosticLogExportState())
    }

    @Test
    fun `hiding the link mid-read cancels the read`() = runTest {
        coEvery { repository.exportText() } returns "x"
        val viewModel = DiagnosticLogExportViewModel(repository)

        viewModel.export()
        viewModel.onHidden()
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(DiagnosticLogExportState())
    }
}
