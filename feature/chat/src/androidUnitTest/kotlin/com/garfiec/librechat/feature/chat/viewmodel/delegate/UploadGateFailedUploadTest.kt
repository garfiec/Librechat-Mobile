package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.UploadIntakeHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The gate alone; that every send path goes through it is checked in `ChatViewModelQueuedSkillsTest`. */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadGateFailedUploadTest {

    private val uploaded = AttachedFile(uri = "a", name = "a.pdf", uploadProgress = 1f, fileId = "a")
    private val failed = AttachedFile(uri = "b", name = "b.pdf", uploadFailed = true)
    private val uploading = AttachedFile(uri = "c", name = "c.pdf", uploadProgress = 0.3f)

    private fun gateTest(
        initial: List<AttachedFile>,
        body: TestScope.(
            FakeFileHandler,
            UploadIntakeDelegate,
            MutableStateFlow<ChatUiState>,
            MutableList<String>,
        ) -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        val flow = MutableStateFlow(ChatUiState())
        val scope = TestScope(testScheduler)
        val files = FakeFileHandler(initial)
        val delegate = UploadIntakeDelegate(
            handle = UploadIntakeHandle(ChatStateHandle(flow, scope)),
            fileHandler = files,
            settingsDataStore = mockk<SettingsDataStore>(relaxed = true),
            awaitAgentProvider = {},
            serverUrl = { "" },
        )
        val sent = mutableListOf<String>()
        try {
            scope.body(files, delegate, flow, sent)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a failed upload in the tray holds the send`() = gateTest(listOf(uploaded, failed)) { _, delegate, flow, sent ->
        delegate.withUploadGate("hello") { sent += it }

        assertThat(sent).isEmpty()
        assertThat(flow.value.composer.isAwaitingUploadSend).isFalse()
    }

    @Test
    fun `an upload that fails while a send waits on it holds the send`() =
        gateTest(listOf(uploaded, uploading)) { files, delegate, flow, sent ->
            delegate.withUploadGate("hello") { sent += it }
            assertThat(flow.value.composer.isAwaitingUploadSend).isTrue()

            files.files.value = listOf(uploaded, uploading.copy(uploadFailed = true, uploadProgress = null))
            advanceTimeBy(1_000)
            runCurrent()

            assertThat(sent).isEmpty()
            assertThat(flow.value.composer.isAwaitingUploadSend).isFalse()
        }

    @Test
    fun `an upload that lands while a send waits on it lets the send go`() =
        gateTest(listOf(uploaded, uploading)) { files, delegate, _, sent ->
            delegate.withUploadGate("hello") { sent += it }

            files.files.value = listOf(uploaded, uploading.copy(fileId = "c", uploadProgress = 1f))
            advanceTimeBy(1_000)
            runCurrent()

            assertThat(sent).containsExactly("hello")
        }

    @Test
    fun `a send goes once the failed file is removed`() = gateTest(listOf(uploaded, failed)) { files, delegate, _, sent ->
        files.removeFile(failed)

        delegate.withUploadGate("hello") { sent += it }

        assertThat(sent).containsExactly("hello")
    }
}
