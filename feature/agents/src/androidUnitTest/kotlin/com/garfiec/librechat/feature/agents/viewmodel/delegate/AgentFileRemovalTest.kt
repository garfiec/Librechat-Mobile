package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.model.AgentFile
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.request.DeleteFileEntry
import com.garfiec.librechat.core.model.response.DeleteFilesResponse
import com.garfiec.librechat.feature.agents.util.ContentReader
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.garfiec.librechat.feature.agents.viewmodel.AgentFileSlot
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unlinking an agent's file, end to end through the delegate.
 *
 * `DELETE /api/files` drops every entry whose `filepath` is falsy **before** it looks at
 * `agent_id`, so an empty one is not a harmless placeholder: the whole request is filtered away
 * and the route answers 204. The file stays attached to the agent while the chip disappears from
 * the editor, which is the shape this file exists to prevent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentFileRemovalTest {

    private val fileRepository = mockk<FileRepository>()
    private val agentRepository = mockk<AgentRepository>(relaxed = true)
    private val contentReader = mockk<ContentReader> {
        every { readBytes(any()) } returns ByteArray(10)
        every { getFileName(any()) } returns "notes.pdf"
        every { getMimeType(any()) } returns "application/pdf"
    }

    private fun delegateWith(
        scope: TestScope,
        files: List<AgentFile>,
    ): Pair<AgentFilesDelegate, MutableStateFlow<AgentEditorUiState>> {
        val flow = MutableStateFlow(
            AgentEditorUiState(isEditMode = true, agentId = AGENT_ID, knowledgeFiles = files),
        )
        val delegate = AgentFilesDelegate(
            stateHandle = AgentEditorStateHandle(flow, scope),
            agentRepository = agentRepository,
            fileRepository = fileRepository,
            contentReader = contentReader,
            ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        )
        return delegate to flow
    }

    private fun fileObject(id: String, path: String) = FileObject(
        fileId = id,
        filename = "notes.pdf",
        filepath = path,
        type = "application/pdf",
        bytes = 10,
    )

    @Test
    fun `an unlink carries the filepath the enrichment reported`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { fileRepository.getAgentFiles(AGENT_ID) } returns
            Result.Success(listOf(fileObject(FILE_ID, FILE_PATH)))
        val entries = slot<List<DeleteFileEntry>>()
        coEvery { fileRepository.deleteFiles(capture(entries), any(), any()) } returns
            Result.Success(DeleteFilesResponse(deletedFileIds = listOf(FILE_ID)))
        val (delegate, flow) = delegateWith(
            this,
            listOf(AgentFile(fileId = FILE_ID, originResource = "file_search")),
        )

        // What the editor does on open: the agent payload carries only file_ids, so the paths
        // arrive with this.
        delegate.loadAgentFiles(AGENT_ID)
        delegate.removeAgentFile(FILE_ID, AgentFileSlot.KNOWLEDGE)

        assertThat(entries.captured.single().filepath).isEqualTo(FILE_PATH)
        assertThat(flow.value.knowledgeFiles).isEmpty()
    }

    /** A file uploaded in this session never goes through the enrichment; the POST answered with its path. */
    @Test
    fun `an unlink of a freshly uploaded file carries the upload's own filepath`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery {
                fileRepository.uploadFile(
                    bytes = any(), filename = any(), type = any(), fileId = any(), endpoint = any(),
                    model = any(), agentId = any(), toolResource = any(), messageFile = any(),
                    width = any(), height = any(), onProgress = any(),
                )
            } returns Result.Success(fileObject(FILE_ID, FILE_PATH))
            val entries = slot<List<DeleteFileEntry>>()
            coEvery { fileRepository.deleteFiles(capture(entries), any(), any()) } returns
                Result.Success(DeleteFilesResponse(deletedFileIds = listOf(FILE_ID)))
            val (delegate, flow) = delegateWith(this, emptyList())

            delegate.uploadAgentFile(fileRef = "content://picked", slot = AgentFileSlot.KNOWLEDGE)
            assertThat(flow.value.knowledgeFiles).hasSize(1)

            delegate.removeAgentFile(FILE_ID, AgentFileSlot.KNOWLEDGE)

            assertThat(entries.captured.single().filepath).isEqualTo(FILE_PATH)
        }

    /**
     * With no path there is nothing the route would accept, so the request is not made at all and
     * the chip goes back. Sending one anyway is a 204 the user reads as a successful removal.
     */
    @Test
    fun `an unlink with no known filepath is refused rather than silently dropped`() =
        runTest(UnconfinedTestDispatcher()) {
            val (delegate, flow) = delegateWith(
                this,
                listOf(AgentFile(fileId = FILE_ID, originResource = "file_search")),
            )

            delegate.removeAgentFile(FILE_ID, AgentFileSlot.KNOWLEDGE)

            coVerify(exactly = 0) { fileRepository.deleteFiles(any(), any(), any()) }
            assertThat(flow.value.knowledgeFiles.map { it.fileId }).containsExactly(FILE_ID)
            assertThat(flow.value.error).isNotNull()
        }

    private companion object {
        const val AGENT_ID = "agent_abc"
        const val FILE_ID = "file-1"
        const val FILE_PATH = "/uploads/user-1/file-1"
    }
}
