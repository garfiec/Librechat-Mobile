package com.garfiec.librechat.feature.chat.viewmodel.delegate

import android.content.Context
import android.net.Uri
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.pdf.PdfPasswordProtectedException
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ErrorOnlyHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import java.io.ByteArrayInputStream

/** A password-protected PDF attached in chat: the chip waits on a prompt, then retries or goes. */
class FileAttachmentPdfPasswordTest {

    private val uri = mockk<Uri>()
    private val fileRepository = mockk<FileRepository>()
    private val handle = mockk<ErrorOnlyHandle>(relaxed = true) {
        every { state } returns ChatUiState()
        every { scope } returns CoroutineScope(Dispatchers.Unconfined)
    }
    private val context = mockk<Context> {
        every { contentResolver } returns mockk {
            every { openInputStream(uri) } answers { ByteArrayInputStream("%PDF-1.4".encodeToByteArray()) }
        }
    }
    private val delegate = FileAttachmentDelegate(handle, context, fileRepository, Dispatchers.Unconfined)
    private val picked = PickedFile(ref = uri, name = "grades.pdf", mimeType = "application/pdf")

    private fun uploadAnswers(password: String?, result: Result<FileObject>) = coEvery {
        fileRepository.uploadFile(
            bytes = any(), filename = any(), type = any(), fileId = any(), endpoint = any(),
            model = any(), agentId = any(), toolResource = any(), messageFile = any(),
            width = any(), height = any(), onProgress = any(), pdfPassword = password,
        )
    } returns result

    private fun locked(incorrect: Boolean) = Result.Error(exception = PdfPasswordProtectedException(incorrect), message = "x")

    private fun attach() = delegate.onFilesSelected(listOf(RoutedFile(picked, UploadRoute.PROVIDER)))

    @Test
    fun `a password-protected PDF holds its chip and prompts instead of erroring`() {
        uploadAnswers(null, locked(incorrect = false))

        attach()

        assertThat(delegate.pdfPasswordPrompts.value.single().incorrectPassword).isFalse()
        // Pending, not failed: a send parked on the upload gate must keep waiting for the answer.
        assertThat(delegate.attachedFiles.value.single().uploadFailed).isFalse()
        assertThat(delegate.hasPendingUploads()).isTrue()
        verify(exactly = 0) { handle.setError(any()) }
    }

    @Test
    fun `a password for a prompt that is no longer pending is dropped`() {
        uploadAnswers(null, locked(incorrect = false))

        attach()
        val prompt = delegate.pdfPasswordPrompts.value.single()
        delegate.dismissPdfPassword()
        delegate.submitPdfPassword(prompt, "late")

        coVerify(exactly = 0) {
            fileRepository.uploadFile(
                bytes = any(), filename = any(), type = any(), fileId = any(), endpoint = any(),
                model = any(), agentId = any(), toolResource = any(), messageFile = any(),
                width = any(), height = any(), onProgress = any(), pdfPassword = "late",
            )
        }
    }

    @Test
    fun `the typed password re-uploads and the chip completes`() {
        uploadAnswers(null, locked(incorrect = false))
        uploadAnswers(
            "hunter2",
            Result.Success(FileObject(fileId = "f1", filename = "grades.pdf", filepath = "/f1", type = "application/pdf", bytes = 8)),
        )

        attach()
        delegate.submitPdfPassword(delegate.pdfPasswordPrompts.value.single(), "hunter2")

        assertThat(delegate.pdfPasswordPrompts.value).isEmpty()
        assertThat(delegate.attachedFiles.value.single().fileId).isEqualTo("f1")
    }

    @Test
    fun `a wrong password prompts again, flagged as incorrect`() {
        uploadAnswers(null, locked(incorrect = false))
        uploadAnswers("nope", locked(incorrect = true))

        attach()
        delegate.submitPdfPassword(delegate.pdfPasswordPrompts.value.single(), "nope")

        assertThat(delegate.pdfPasswordPrompts.value.single().incorrectPassword).isTrue()
        assertThat(delegate.attachedFiles.value).hasSize(1)
    }

    @Test
    fun `dismissing removes the chip without uploading`() {
        uploadAnswers(null, locked(incorrect = false))

        attach()
        delegate.dismissPdfPassword()

        assertThat(delegate.pdfPasswordPrompts.value).isEmpty()
        assertThat(delegate.attachedFiles.value).isEmpty()
        coVerify(exactly = 1) {
            fileRepository.uploadFile(
                bytes = any(), filename = any(), type = any(), fileId = any(), endpoint = any(),
                model = any(), agentId = any(), toolResource = any(), messageFile = any(),
                width = any(), height = any(), onProgress = any(), pdfPassword = any(),
            )
        }
    }

    @Test
    fun `removing the chip drops its prompt`() {
        uploadAnswers(null, locked(incorrect = false))

        attach()
        delegate.removeFile(delegate.attachedFiles.value.single())

        assertThat(delegate.pdfPasswordPrompts.value).isEmpty()
    }
}
