package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.pdf.PdfPasswordProtectedException
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.feature.agents.util.ContentReader
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.garfiec.librechat.feature.agents.viewmodel.AgentFileSlot
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A password-protected PDF picked into an agent's file slot: prompt, retry with the password, give up. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentFilePdfPasswordTest {

    private val fileRepository = mockk<FileRepository>()
    private val contentReader = mockk<ContentReader> {
        every { readBytes(any()) } returns ByteArray(10)
        every { getFileName(any()) } returns "grades.pdf"
        every { getMimeType(any()) } returns "application/pdf"
    }

    private fun delegateWith(scope: TestScope): Pair<AgentFilesDelegate, MutableStateFlow<AgentEditorUiState>> {
        val flow = MutableStateFlow(AgentEditorUiState(isEditMode = true, agentId = "agent_1"))
        val delegate = AgentFilesDelegate(
            stateHandle = AgentEditorStateHandle(flow, scope),
            agentRepository = mockk<AgentRepository>(relaxed = true),
            fileRepository = fileRepository,
            contentReader = contentReader,
            ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        )
        return delegate to flow
    }

    private fun uploadAnswers(password: String?, result: Result<FileObject>) = coEvery {
        fileRepository.uploadFile(
            bytes = any(), filename = any(), type = any(), fileId = any(), endpoint = any(),
            model = any(), agentId = any(), toolResource = any(), messageFile = any(),
            width = any(), height = any(), onProgress = any(), pdfPassword = password,
        )
    } returns result

    private fun locked(incorrect: Boolean) = Result.Error(exception = PdfPasswordProtectedException(incorrect))

    @Test
    fun `a password-protected PDF prompts instead of showing an error`() = runTest(UnconfinedTestDispatcher()) {
        uploadAnswers(null, locked(incorrect = false))
        val (delegate, flow) = delegateWith(this)

        delegate.uploadAgentFile("content://picked", AgentFileSlot.KNOWLEDGE)

        assertThat(flow.value.pdfPasswordPrompt?.filename).isEqualTo("grades.pdf")
        assertThat(flow.value.pdfPasswordPrompt?.incorrectPassword).isFalse()
        assertThat(flow.value.error).isNull()
    }

    @Test
    fun `the typed password retries the same file into the same slot`() = runTest(UnconfinedTestDispatcher()) {
        uploadAnswers(null, locked(incorrect = false))
        uploadAnswers(
            "hunter2",
            Result.Success(FileObject(fileId = "f1", filename = "grades.pdf", filepath = "/f1", type = "application/pdf", bytes = 10)),
        )
        val (delegate, flow) = delegateWith(this)

        delegate.uploadAgentFile("content://picked", AgentFileSlot.KNOWLEDGE)
        delegate.submitPdfPassword("hunter2")

        assertThat(flow.value.pdfPasswordPrompt).isNull()
        assertThat(flow.value.knowledgeFiles.map { it.fileId }).containsExactly("f1")
        coVerify(exactly = 1) {
            fileRepository.uploadFile(
                bytes = any(), filename = any(), type = any(), fileId = any(), endpoint = any(),
                model = any(), agentId = "agent_1", toolResource = AgentFileSlot.KNOWLEDGE.wire, messageFile = any(),
                width = any(), height = any(), onProgress = any(), pdfPassword = "hunter2",
            )
        }
    }

    @Test
    fun `a wrong password prompts again, flagged as incorrect`() = runTest(UnconfinedTestDispatcher()) {
        uploadAnswers(null, locked(incorrect = false))
        uploadAnswers("nope", locked(incorrect = true))
        val (delegate, flow) = delegateWith(this)

        delegate.uploadAgentFile("content://picked", AgentFileSlot.KNOWLEDGE)
        delegate.submitPdfPassword("nope")

        assertThat(flow.value.pdfPasswordPrompt?.incorrectPassword).isTrue()
    }

    @Test
    fun `dismissing drops the file without uploading`() = runTest(UnconfinedTestDispatcher()) {
        uploadAnswers(null, locked(incorrect = false))
        val (delegate, flow) = delegateWith(this)

        delegate.uploadAgentFile("content://picked", AgentFileSlot.KNOWLEDGE)
        delegate.dismissPdfPassword()
        delegate.submitPdfPassword("late")

        assertThat(flow.value.pdfPasswordPrompt).isNull()
        assertThat(flow.value.knowledgeFiles).isEmpty()
        coVerify(exactly = 1) {
            fileRepository.uploadFile(
                bytes = any(), filename = any(), type = any(), fileId = any(), endpoint = any(),
                model = any(), agentId = any(), toolResource = any(), messageFile = any(),
                width = any(), height = any(), onProgress = any(), pdfPassword = any(),
            )
        }
    }

    @Test
    fun `two locked PDFs in different slots are each prompted for, in turn`() = runTest(UnconfinedTestDispatcher()) {
        every { contentReader.getFileName("content://a") } returns "a.pdf"
        every { contentReader.getFileName("content://b") } returns "b.pdf"
        uploadAnswers(null, locked(incorrect = false))
        uploadAnswers(
            "pa",
            Result.Success(FileObject(fileId = "fa", filename = "a.pdf", filepath = "/fa", type = "application/pdf", bytes = 10)),
        )
        uploadAnswers(
            "pb",
            Result.Success(FileObject(fileId = "fb", filename = "b.pdf", filepath = "/fb", type = "application/pdf", bytes = 10)),
        )
        val (delegate, flow) = delegateWith(this)

        delegate.uploadAgentFile("content://a", AgentFileSlot.KNOWLEDGE)
        delegate.uploadAgentFile("content://b", AgentFileSlot.CODE)
        val first = flow.value.pdfPasswordPrompt
        delegate.submitPdfPassword("pa")
        val second = flow.value.pdfPasswordPrompt
        delegate.submitPdfPassword("pb")

        assertThat(first?.filename).isEqualTo("a.pdf")
        assertThat(second?.filename).isEqualTo("b.pdf")
        assertThat(second?.id).isNotEqualTo(first?.id)
        assertThat(flow.value.pdfPasswordPrompt).isNull()
        assertThat(flow.value.knowledgeFiles.map { it.fileId }).containsExactly("fa")
        assertThat(flow.value.codeFiles.map { it.fileId }).containsExactly("fb")
    }
}
