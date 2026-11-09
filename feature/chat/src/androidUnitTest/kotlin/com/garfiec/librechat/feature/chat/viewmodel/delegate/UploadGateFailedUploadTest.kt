package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.PendingUploadFile
import com.garfiec.librechat.feature.chat.viewmodel.PendingUploadRouting
import com.garfiec.librechat.feature.chat.viewmodel.UploadIntakeHandle
import com.garfiec.librechat.feature.chat.viewmodel.UploadRoutingContext
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

    private val turnedOn = mutableListOf<String>()

    private fun gateTest(
        initial: List<AttachedFile>,
        state: ChatUiState = ChatUiState(),
        body: TestScope.(
            FakeFileHandler,
            UploadIntakeDelegate,
            MutableStateFlow<ChatUiState>,
            MutableList<String>,
        ) -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        val flow = MutableStateFlow(state)
        val scope = TestScope(testScheduler)
        val files = FakeFileHandler(initial)
        val delegate = UploadIntakeDelegate(
            handle = UploadIntakeHandle(ChatStateHandle(flow, scope)),
            fileHandler = files,
            settingsDataStore = mockk<SettingsDataStore>(relaxed = true),
            awaitAgentProvider = {},
            serverUrl = { "" },
            turnOnToolForUpload = { turnedOn += it },
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

    private val openAi = ChatUiState(selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", "gpt-4o")))
    private val notes = PickedFile(ref = "notes", name = "notes.docx", mimeType = "application/msword")

    private fun stageFileSearchPick(flow: MutableStateFlow<ChatUiState>) {
        val routes = listOf(UploadRoute.PROVIDER, UploadRoute.TEXT, UploadRoute.FILE_SEARCH)
        flow.value = flow.value.copy(
            composer = flow.value.composer.copy(
                pendingUploadRouting = PendingUploadRouting(
                    files = listOf(PendingUploadFile(notes, UploadRoute.FILE_SEARCH, routes)),
                    context = UploadRoutingContext(endpoint = "openAI", endpointType = null, agentProvider = null),
                ),
            ),
        )
    }

    @Test
    fun `a file-search pick turns File Search on at confirm, before its upload starts`() =
        gateTest(emptyList(), openAi) { files, delegate, flow, _ ->
            stageFileSearchPick(flow)
            delegate.confirmPendingUploadRouting()

            assertThat(files.files.value.single().route).isEqualTo(UploadRoute.FILE_SEARCH)
            assertThat(turnedOn).containsExactly(ToolConstants.FILE_SEARCH)
        }

    @Test
    fun `a file-search pick no longer offered at confirm is routed as auto would, without the tool`() =
        gateTest(emptyList(), openAi) { files, delegate, flow, _ ->
            stageFileSearchPick(flow)
            flow.value = flow.value.copy(gates = flow.value.gates.copy(fileSearchEnabled = false))
            delegate.confirmPendingUploadRouting()

            assertThat(files.files.value.single().route).isEqualTo(flow.value.uploadRouteFor(notes.mimeType))
            assertThat(turnedOn).isEmpty()
        }

    @Test
    fun `a retried file-search upload turns File Search on`() {
        val failedSearch = AttachedFile(
            uri = "d",
            name = "d.pdf",
            type = "application/pdf",
            uploadFailed = true,
            route = UploadRoute.FILE_SEARCH,
        )
        gateTest(listOf(failedSearch), openAi) { files, delegate, _, _ ->
            delegate.retryUpload(failedSearch)
            runCurrent()

            assertThat(files.retried).containsExactly(failedSearch)
            assertThat(turnedOn).containsExactly(ToolConstants.FILE_SEARCH)
        }
    }
}
