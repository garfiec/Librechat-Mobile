package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.response.AvailableProjectFile
import com.garfiec.librechat.core.model.response.AvailableProjectFilesResponse
import com.garfiec.librechat.core.model.response.ChatProjectFile
import com.garfiec.librechat.feature.conversations.platform.PickedProjectFile
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectFilesControllerTest {

    @After
    fun tearDown() {
        controllerScope?.cancel()
    }

    private val projectRepository = mockk<ProjectRepository>(relaxed = true)
    private val fileRepository = mockk<FileRepository>(relaxed = true)
    private val updated = mutableListOf<ChatProject>()
    private var gone = false
    private var maxFiles = 50

    private var controllerScope: CoroutineScope? = null

    /** Foreground on the test scheduler: advanceUntilIdle never runs backgroundScope work on its own. */
    private fun TestScope.controller(): ProjectFilesController {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        controllerScope = scope
        return ProjectFilesController(
            scope = scope,
            projectId = "p1",
            projectRepository = projectRepository,
            fileRepository = fileRepository,
            maxFiles = { maxFiles },
            onProjectUpdated = { updated += it },
            onProjectGone = { gone = true },
        )
    }

    private fun picked(name: String, bytes: ByteArray? = byteArrayOf(1)) =
        PickedProjectFile(name = name, mimeType = "application/pdf") { bytes }

    private fun uploaded(id: String, embedded: Boolean? = true) = Result.Success(
        FileObject(fileId = id, filename = "f", filepath = "/f", type = "application/pdf", bytes = 1, embedded = embedded),
    )

    private suspend fun MockKMatcherScope.upload() = fileRepository.uploadFile(
        bytes = any(),
        filename = any(),
        type = any(),
        endpoint = "agents",
        toolResource = "file_search",
        messageFile = true,
    )

    private fun project(revision: Int) = Result.Success(ChatProject(id = "p1", name = "P", contextRevision = revision))

    @Test
    fun `an upload attaches, hands back the new revision and reloads the list`() = runTest {
        coEvery { upload() } returns uploaded("f1")
        coEvery { projectRepository.addProjectFile("p1", "f1") } returns project(5)
        coEvery { projectRepository.listProjectFiles("p1") } returns
            Result.Success(listOf(ChatProjectFile("f1", "a.pdf", availability = "ready")))
        val files = controller()
        val messages = mutableListOf<ProjectFilesMessage>()
        controllerScope!!.launch { files.messages.collect { messages += it } }
        advanceUntilIdle()

        files.upload(listOf(picked("a.pdf")))
        advanceUntilIdle()

        assertThat(updated.single().contextRevision).isEqualTo(5)
        assertThat(files.state.value.rows.map { it.fileId }).containsExactly("f1")
        assertThat(files.state.value.rows.single().status).isEqualTo(ProjectFileStatus.READY)
        assertThat(messages.filterIsInstance<ProjectFilesMessage.Announce>()).hasSize(1)
    }

    @Test
    fun `a failed attach keeps the file id and its retry repeats only the attach`() = runTest {
        coEvery { upload() } returns uploaded("f1")
        coEvery { projectRepository.addProjectFile("p1", "f1") } returns Result.Error(ApiException(500, "x"))
        val files = controller()

        files.upload(listOf(picked("a.pdf")))
        advanceUntilIdle()
        val failed = files.state.value.rows.single()
        assertThat(failed.status).isEqualTo(ProjectFileStatus.FAILED)
        assertThat(failed.failure).isEqualTo(ProjectUploadFailure.ATTACH_FAILED)
        assertThat(failed.canRetry).isTrue()

        coEvery { projectRepository.addProjectFile("p1", "f1") } returns project(2)
        files.retry(failed.key)
        advanceUntilIdle()

        coVerify(exactly = 1) { upload() }
        coVerify(exactly = 2) { projectRepository.addProjectFile("p1", "f1") }
        assertThat(files.state.value.rows.none { it.isUpload }).isTrue()
    }

    @Test
    fun `an upload the RAG API did not index is never attached`() = runTest {
        coEvery { upload() } returns uploaded("f1", embedded = false)
        val files = controller()

        files.upload(listOf(picked("a.pdf")))
        advanceUntilIdle()

        val row = files.state.value.rows.single()
        assertThat(row.failure).isEqualTo(ProjectUploadFailure.NOT_SEARCHABLE)
        assertThat(row.canRetry).isFalse()
        coVerify(exactly = 0) { projectRepository.addProjectFile(any(), any()) }
    }

    @Test
    fun `a 409 on the attach is the file limit`() = runTest {
        coEvery { upload() } returns uploaded("f1")
        coEvery { projectRepository.addProjectFile("p1", "f1") } returns
            Result.Error(ApiException(409, "x", body = """{"error":"Project file limit reached"}"""))
        val files = controller()

        files.upload(listOf(picked("a.pdf")))
        advanceUntilIdle()

        assertThat(files.state.value.rows.single().failure).isEqualTo(ProjectUploadFailure.FILE_LIMIT)
    }

    @Test
    fun `picking more files than fit takes what fits and reports the rest`() = runTest {
        maxFiles = 2
        coEvery { projectRepository.listProjectFiles("p1") } returns
            Result.Success(listOf(ChatProjectFile("f0", "x", availability = "ready")))
        coEvery { upload() } coAnswers { kotlinx.coroutines.awaitCancellation() }
        val files = controller()
        val messages = mutableListOf<ProjectFilesMessage>()
        controllerScope!!.launch { files.messages.collect { messages += it } }
        files.load()
        advanceUntilIdle()

        files.upload(listOf(picked("a"), picked("b"), picked("c")))
        advanceUntilIdle()

        assertThat(files.state.value.rows.count { it.isUpload }).isEqualTo(1)
        assertThat(files.state.value.usedCount).isEqualTo(2)
        assertThat(messages).contains(ProjectFilesMessage.Excess(2))
    }

    @Test
    fun `remove waits for the server and hands back the project`() = runTest {
        coEvery { projectRepository.listProjectFiles("p1") } returns
            Result.Success(listOf(ChatProjectFile("f1", "a", availability = "unavailable")))
        coEvery { projectRepository.removeProjectFile("p1", "f1") } returns Result.Error(ApiException(500, "x"))
        val files = controller()
        files.load()
        advanceUntilIdle()
        assertThat(files.state.value.unavailableCount).isEqualTo(1)

        files.remove("f1")
        advanceUntilIdle()
        assertThat(files.state.value.rows.map { it.fileId }).containsExactly("f1")

        coEvery { projectRepository.removeProjectFile("p1", "f1") } returns project(9)
        files.remove("f1")
        advanceUntilIdle()
        assertThat(files.state.value.rows).isEmpty()
        assertThat(updated.single().contextRevision).isEqualTo(9)
    }

    @Test
    fun `a 404 on a file call closes the project`() = runTest {
        coEvery { projectRepository.listProjectFiles("p1") } returns Result.Error(ApiException(404, "x"))
        val files = controller()
        files.load()
        advanceUntilIdle()
        assertThat(gone).isTrue()
    }

    @Test
    fun `the picker follows an empty page with a cursor and adds a pick`() = runTest {
        coEvery { projectRepository.listAvailableProjectFiles("p1", null, null, 20) } returns
            Result.Success(AvailableProjectFilesResponse(emptyList(), nextCursor = "c1"))
        coEvery { projectRepository.listAvailableProjectFiles("p1", "c1", null, 20) } returns
            Result.Success(AvailableProjectFilesResponse(listOf(AvailableProjectFile("f7", "b.txt")), nextCursor = null))
        coEvery { projectRepository.addProjectFile("p1", "f7") } returns project(3)
        val files = controller()

        files.openPicker()
        advanceUntilIdle()
        assertThat(files.available.value.files.map { it.fileId }).containsExactly("f7")

        files.addExisting("f7")
        advanceUntilIdle()
        assertThat(files.available.value.isOpen).isFalse()
        assertThat(updated.single().contextRevision).isEqualTo(3)
        coVerify { projectRepository.listProjectFiles("p1") }
    }

    @Test
    fun `an unreadable pick fails without an upload`() = runTest {
        val files = controller()
        files.upload(listOf(picked("a", bytes = null)))
        advanceUntilIdle()
        assertThat(files.state.value.rows.single().failure).isEqualTo(ProjectUploadFailure.UNREADABLE)
        coVerify(exactly = 0) { upload() }
    }
}
