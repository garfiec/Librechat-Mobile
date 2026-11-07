package com.garfiec.librechat.feature.chat.viewmodel.delegate

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.model.response.toolResource
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ErrorOnlyHandle
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream

class FileAttachmentDelegateTest {

    /** Unconfined, so an upload runs inline up to its first suspension. */
    private fun uploadingDelegate(
        fileRepository: FileRepository,
        state: MutableStateFlow<ChatUiState> = MutableStateFlow(ChatUiState()),
    ): FileAttachmentDelegate {
        val resolver = mockk<ContentResolver>(relaxed = true) {
            every { openInputStream(any()) } answers { ByteArrayInputStream("plain text".encodeToByteArray()) }
        }
        val context = mockk<Context>(relaxed = true) { every { contentResolver } returns resolver }
        return FileAttachmentDelegate(
            handle = ErrorOnlyHandle(ChatStateHandle(state, CoroutineScope(Dispatchers.Unconfined))),
            appContext = context,
            fileRepository = fileRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun serverRecord(name: String) =
        FileObject(fileId = "server-$name", filename = name, filepath = "/f/$name", type = "text/plain", bytes = 10)

    @Test
    fun `removing a file mid-upload cancels its upload`() {
        var cancelled = false
        val repository = mockk<FileRepository> {
            coEvery {
                uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        val delegate = uploadingDelegate(repository)
        delegate.onFilesSelected(listOf(RoutedFile(PickedFile(mockk<Uri>(), "notes.txt", "text/plain"), UploadRoute.TEXT)))
        val inFlight = delegate.attachedFiles.value.single()
        assertThat(inFlight.isUploading).isTrue()

        delegate.removeFile(inFlight)

        assertThat(cancelled).isTrue()
        assertThat(delegate.attachedFiles.value).isEmpty()
    }

    @Test
    fun `a retry keeps the file's place and the route it went with`() {
        val toolResources = mutableListOf<String?>()
        var calls = 0
        val repository = mockk<FileRepository> {
            coEvery {
                uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } answers {
                toolResources += arg<String?>(7)
                val name = arg<String>(1)
                if (++calls == 1) Result.Error(message = "offline") else Result.Success(serverRecord(name))
            }
        }
        val delegate = uploadingDelegate(repository)
        val first = mockk<Uri>()
        val second = mockk<Uri>()
        delegate.onFilesSelected(
            listOf(
                RoutedFile(PickedFile(first, "notes.txt", "text/plain"), UploadRoute.TEXT),
                RoutedFile(PickedFile(second, "other.txt", "text/plain"), UploadRoute.PROVIDER),
            ),
        )
        val failed = delegate.attachedFiles.value.first()
        assertThat(failed.uploadFailed).isTrue()

        delegate.retryUpload(failed)

        val tray = delegate.attachedFiles.value
        assertThat(tray.map { it.name }).containsExactly("notes.txt", "other.txt").inOrder()
        assertThat(tray.first().fileId).isEqualTo("server-notes.txt")
        assertThat(tray.first().route).isEqualTo(UploadRoute.TEXT)
        // The retry went with the route on the row, not one re-derived from the selection.
        assertThat(toolResources.last()).isEqualTo(UploadRoute.TEXT.toolResource())
    }

    @Test
    fun `a file picked twice is two entries, and removing one leaves the other uploading`() {
        val cancelled = mutableListOf<String>()
        val repository = mockk<FileRepository> {
            coEvery {
                uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } coAnswers {
                val fileId = arg<String>(3)
                try {
                    awaitCancellation()
                } finally {
                    cancelled += fileId
                }
            }
        }
        val delegate = uploadingDelegate(repository)
        val uri = mockk<Uri>()
        val pick = RoutedFile(PickedFile(uri, "notes.txt", "text/plain"), UploadRoute.TEXT)
        delegate.onFilesSelected(listOf(pick, pick))
        val (first, second) = delegate.attachedFiles.value
        assertThat(first.key).isNotEqualTo(second.key)

        delegate.removeFile(first)

        assertThat(delegate.attachedFiles.value).containsExactly(second)
        assertThat(cancelled).hasSize(1)
    }

    @Test
    fun `retrying one of a file picked twice uploads that one again and leaves the other`() {
        var calls = 0
        val repository = mockk<FileRepository> {
            coEvery {
                uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } answers {
                when (++calls) {
                    1 -> Result.Error(message = "offline")
                    2 -> Result.Success(serverRecord("first"))
                    else -> Result.Success(serverRecord("retried"))
                }
            }
        }
        val delegate = uploadingDelegate(repository)
        val uri = mockk<Uri>()
        val pick = RoutedFile(PickedFile(uri, "notes.txt", "text/plain"), UploadRoute.TEXT)
        delegate.onFilesSelected(listOf(pick, pick))
        val (failed, uploaded) = delegate.attachedFiles.value
        assertThat(failed.uploadFailed).isTrue()
        assertThat(uploaded.fileId).isEqualTo("server-first")

        delegate.retryUpload(failed)

        val tray = delegate.attachedFiles.value
        assertThat(tray.map { it.key }).containsExactly(failed.key, uploaded.key).inOrder()
        assertThat(tray.map { it.fileId }).containsExactly("server-retried", "server-first").inOrder()
    }

    @Test
    fun `a retry after the selection moved re-routes a route the new selection can't use`() {
        val toolResources = mutableListOf<String?>()
        var calls = 0
        val repository = mockk<FileRepository> {
            coEvery {
                uploadFile(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } answers {
                toolResources += arg<String?>(7)
                if (++calls == 1) Result.Error(message = "offline") else Result.Success(serverRecord(arg(1)))
            }
        }
        // Anthropic reads a PDF natively, so it went as PROVIDER (no tool resource).
        val state = MutableStateFlow(selectionOn("anthropic"))
        val delegate = uploadingDelegate(repository, state)
        delegate.onFilesSelected(
            listOf(RoutedFile(PickedFile(mockk<Uri>(), "notes.pdf", "application/pdf"), UploadRoute.PROVIDER)),
        )
        val failed = delegate.attachedFiles.value.single()
        assertThat(failed.uploadFailed).isTrue()

        // An agent on a provider that can't read PDFs: replaying PROVIDER would be ignored.
        state.value = selectionOn(EndpointConstants.AGENTS, agentProvider = "ollama")
        delegate.retryUpload(failed)

        assertThat(toolResources).containsExactly(null, UploadRoute.TEXT.toolResource()).inOrder()
        assertThat(delegate.attachedFiles.value.single().route).isEqualTo(UploadRoute.TEXT)
    }

    private fun selectionOn(endpoint: String, agentProvider: String? = null) = ChatUiState(
        selection = ModelSelectionState(
            choice = ModelChoice.Raw(endpoint, null),
            selectedAgentProvider = agentProvider,
            endpointConfigs = mapOf(endpoint to EndpointConfig()),
        ),
    )

    @Test
    fun `insertAt puts a file back at its index, clamped`() {
        val delegate = createDelegate()
        delegate.restoreAttachedFiles(listOf(serverFile("a"), serverFile("c")))

        delegate.insertAt(1, serverFile("b"))
        delegate.insertAt(99, serverFile("d"))

        assertThat(delegate.attachedFiles.value.mapNotNull { it.fileId }).containsExactly("a", "b", "c", "d").inOrder()
    }

    private fun createDelegate(handle: ErrorOnlyHandle = mockk(relaxed = true)) = FileAttachmentDelegate(
        handle = handle,
        appContext = mockk(relaxed = true),
        fileRepository = mockk(relaxed = true),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun serverFile(id: String) = AttachedFile(
        uri = id,
        name = "$id.png",
        isImage = true,
        uploadProgress = 1f,
        fileId = id,
        filepath = "/files/$id.png",
        type = "image/png",
    )

    /** A file still uploading: no server fileId yet, not failed. */
    private fun pendingFile(id: String) = AttachedFile(
        uri = id,
        name = "$id.png",
        isImage = true,
        uploadProgress = 0.5f,
        fileId = null,
    )

    @Test
    fun `addPreUploadedFiles appends to existing tray`() {
        val delegate = createDelegate()
        delegate.addPreUploadedFiles(listOf(serverFile("a")))
        delegate.addPreUploadedFiles(listOf(serverFile("b")))

        assertThat(delegate.attachedFiles.value.mapNotNull { it.fileId })
            .containsExactly("a", "b")
    }

    @Test
    fun `addPreUploadedFiles dedupes by fileId`() {
        val delegate = createDelegate()
        delegate.addPreUploadedFiles(listOf(serverFile("a"), serverFile("b")))
        // Re-adding "a" (already attached) plus a new "c" should only add "c".
        delegate.addPreUploadedFiles(listOf(serverFile("a"), serverFile("c")))

        assertThat(delegate.attachedFiles.value.mapNotNull { it.fileId })
            .containsExactly("a", "b", "c")
    }

    @Test
    fun `waitForUploadsAndSend aborts with an error when an upload never completes`() = runTest {
        val handle = mockk<ErrorOnlyHandle>(relaxed = true)
        val delegate = createDelegate(handle)
        delegate.restoreAttachedFiles(listOf(pendingFile("stuck")))

        var sent = false
        delegate.waitForUploadsAndSend("hello") { sent = true }

        // The send must not fire (buildSendSpec would silently drop the not-yet-uploaded file);
        // the user gets a visible error instead.
        assertThat(sent).isFalse()
        verify { handle.setError(any()) }
    }

    @Test
    fun `waitForUploadsAndSend sends once every upload has completed`() = runTest {
        val handle = mockk<ErrorOnlyHandle>(relaxed = true)
        val delegate = createDelegate(handle)
        delegate.restoreAttachedFiles(listOf(serverFile("done")))

        var sentText: String? = null
        delegate.waitForUploadsAndSend("hello") { sentText = it }

        assertThat(sentText).isEqualTo("hello")
        verify(exactly = 0) { handle.setError(any()) }
    }
}
