package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.model.SkillFile
import com.garfiec.librechat.core.model.response.DeleteSkillFileResponse
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.SkillFileChanges
import com.garfiec.librechat.feature.skills.components.PickedDocument
import com.google.common.truth.Truth.assertThat
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkillFilesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val skillsRepository = mockk<SkillsRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private val fileChanges = SkillFileChanges()

    private fun vm() = SkillFilesViewModel(skillsRepository, fileChanges, "sk-1")

    private fun doc(filename: String) =
        PickedDocument(bytes = byteArrayOf(1, 2, 3), filename = filename, mimeType = "text/plain")

    private fun uploadSlot(): CapturingSlot<String> {
        val pathSlot = slot<String>()
        coEvery {
            skillsRepository.uploadSkillFile(
                skillId = "sk-1",
                relativePath = capture(pathSlot),
                bytes = any(),
                filename = any(),
                mimeType = any(),
            )
        } returns Result.Success(SkillFile(relativePath = "x"))
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(emptyList())
        return pathSlot
    }

    // --- sanitizeRelativePath (security-adjacent: no traversal / absolute / folders) ---

    @Test
    fun `strips parent-traversal segments to a safe basename`() = runTest(testDispatcher) {
        val pathSlot = uploadSlot()
        vm().upload(doc("../../etc/passwd"))
        advanceUntilIdle()
        assertThat(pathSlot.captured).isEqualTo("passwd")
        assertThat(pathSlot.captured).doesNotContain("..")
        assertThat(pathSlot.captured).doesNotContain("/")
    }

    @Test
    fun `strips absolute path to basename`() = runTest(testDispatcher) {
        val pathSlot = uploadSlot()
        vm().upload(doc("/var/secret/notes.md"))
        advanceUntilIdle()
        assertThat(pathSlot.captured).isEqualTo("notes.md")
        assertThat(pathSlot.captured.startsWith("/")).isFalse()
    }

    @Test
    fun `strips backslash windows path to basename`() = runTest(testDispatcher) {
        val pathSlot = uploadSlot()
        vm().upload(doc("..\\..\\windows\\system32\\a.txt"))
        advanceUntilIdle()
        assertThat(pathSlot.captured).isEqualTo("a.txt")
        assertThat(pathSlot.captured).doesNotContain("\\")
    }

    @Test
    fun `replaces disallowed chars and trims leading dots`() = runTest(testDispatcher) {
        val pathSlot = uploadSlot()
        vm().upload(doc("...hidden file!.md"))
        advanceUntilIdle()
        // leading dots trimmed; space + '!' → '_'; allowed [a-zA-Z0-9._-] kept.
        assertThat(pathSlot.captured).isEqualTo("hidden_file_.md")
        assertThat(pathSlot.captured.startsWith(".")).isFalse()
    }

    @Test
    fun `blank-after-sanitize falls back to file`() = runTest(testDispatcher) {
        val pathSlot = uploadSlot()
        vm().upload(doc("/"))
        advanceUntilIdle()
        assertThat(pathSlot.captured).isEqualTo("file")
    }

    // --- the server's charset is ASCII; isLetterOrDigit admits any script ---

    @Test
    fun `non-ASCII letters are replaced so the server's charset accepts the path`() = runTest(testDispatcher) {
        val pathSlot = uploadSlot()
        vm().upload(doc("résumé 文档.md"))
        advanceUntilIdle()
        assertThat(pathSlot.captured).isEqualTo("r_sum____.md")
        assertThat(pathSlot.captured).matches("^[a-zA-Z0-9._\\-/]+$")
    }

    @Test
    fun `an overlong name is cut to 500 characters keeping its extension`() = runTest(testDispatcher) {
        val pathSlot = uploadSlot()
        vm().upload(doc("a".repeat(600) + ".py"))
        advanceUntilIdle()
        assertThat(pathSlot.captured).hasLength(500)
        assertThat(pathSlot.captured).endsWith(".py")
    }

    /** Starts collecting like the page does: what was sent before this arrives first, then the rest. */
    private fun TestScope.collectEvents(viewModel: SkillFilesViewModel): List<SkillFilesEvent> {
        val events = mutableListOf<SkillFilesEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return events
    }

    @Test
    fun `a file named SKILL md in any case is refused before upload`() = runTest(testDispatcher) {
        uploadSlot()
        val viewModel = vm()
        viewModel.upload(doc("skill.MD"))
        advanceUntilIdle()
        coVerify(exactly = 0) { skillsRepository.uploadSkillFile(any(), any(), any(), any(), any()) }
        assertThat(collectEvents(viewModel)).containsExactly(SkillFilesEvent.ActionFailed(SkillError(SkillErrorKind.SKILL_MD_UPLOAD)))
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.isUploading).isFalse()
    }

    @Test
    fun `the SKILL md refusal survives the reload that follows the picker`() = runTest(testDispatcher) {
        uploadSlot()
        val viewModel = vm()
        viewModel.upload(doc("SKILL.md"))
        // ON_RESUME runs after the picker's result on Android, before the page collects again.
        viewModel.load()
        advanceUntilIdle()
        assertThat(collectEvents(viewModel)).containsExactly(SkillFilesEvent.ActionFailed(SkillError(SkillErrorKind.SKILL_MD_UPLOAD)))
    }

    @Test
    fun `a failed remove is reported once and leaves nothing behind after a retry that works`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(listOf(handbook))
        coEvery { skillsRepository.deleteSkillFile("sk-1", handbook.relativePath) } returns
            Result.Error(message = "nope") andThen
            Result.Success(DeleteSkillFileResponse(relativePath = handbook.relativePath, deleted = true))
        val viewModel = vm().apply { grantEdit() }
        viewModel.load()
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.delete(handbook)
        advanceUntilIdle()
        assertThat(events).containsExactly(SkillFilesEvent.ActionFailed(SkillError(SkillErrorKind.DELETE_FILE, "nope")))
        assertThat(viewModel.uiState.value.error).isNull()

        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(emptyList())
        viewModel.delete(handbook)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(events).hasSize(1)
    }

    @Test
    fun `a reload clears a previous load failure`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Error(message = "boom")
        val viewModel = vm()
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error?.kind).isEqualTo(SkillErrorKind.LOAD_FILES)

        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(emptyList())
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `a nested SKILL md basename is refused too`() = runTest(testDispatcher) {
        uploadSlot()
        vm().upload(doc("docs/SKILL.md"))
        advanceUntilIdle()
        coVerify(exactly = 0) { skillsRepository.uploadSkillFile(any(), any(), any(), any(), any()) }
    }

    // --- canEditFiles fail-closed gate (resolved by SkillAccess on the detail page) ---

    @Test
    fun `canEditFiles is false until the detail page grants it`() = runTest(testDispatcher) {
        val viewModel = vm()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canEditFiles).isFalse()
        viewModel.setCanEditFiles(true)
        assertThat(viewModel.uiState.value.canEditFiles).isTrue()
        viewModel.setCanEditFiles(false)
        assertThat(viewModel.uiState.value.canEditFiles).isFalse()
    }

    private fun SkillFilesViewModel.grantEdit() = setCanEditFiles(true)

    // --- a save on the file screen reloads the list, wherever that screen went ---

    @Test
    fun `a file saved elsewhere reloads this skill's list and not another's`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(emptyList())
        val viewModel = vm()
        advanceUntilIdle()

        fileChanges.post("sk-other")
        advanceUntilIdle()
        coVerify(exactly = 0) { skillsRepository.listSkillFiles(any()) }

        fileChanges.post("sk-1")
        advanceUntilIdle()
        coVerify(exactly = 1) { skillsRepository.listSkillFiles("sk-1") }
        assertThat(viewModel.uiState.value.error).isNull()
    }

    // --- load / delete result handling ---

    @Test
    fun `load surfaces files on success and error on failure`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns
            Result.Success(listOf(SkillFile(relativePath = "a.md")))
        val viewModel = vm()
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.files.map { it.relativePath }).containsExactly("a.md")

        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Error(message = "boom")
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isEqualTo(SkillError(SkillErrorKind.LOAD_FILES, "boom"))
    }

    @Test
    fun `delete reloads on success`() = runTest(testDispatcher) {
        coEvery { skillsRepository.deleteSkillFile("sk-1", "a.md") } returns
            Result.Success(DeleteSkillFileResponse(relativePath = "a.md", deleted = true))
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(emptyList())
        val viewModel = vm()
        viewModel.delete(SkillFile(relativePath = "a.md"))
        advanceUntilIdle()
        coVerify { skillsRepository.listSkillFiles("sk-1") }
    }

    // --- remove asks first ---

    private val handbook = SkillFile(relativePath = "reference/handbook.pdf")

    @Test
    fun `remove waits for confirmation before deleting`() = runTest(testDispatcher) {
        val viewModel = vm().apply { grantEdit() }
        viewModel.requestRemove(handbook)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.pendingRemoval).isEqualTo(handbook)
        coVerify(exactly = 0) { skillsRepository.deleteSkillFile(any(), any()) }

        viewModel.confirmRemove()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.pendingRemoval).isNull()
        coVerify(exactly = 1) { skillsRepository.deleteSkillFile("sk-1", "reference/handbook.pdf") }
    }

    @Test
    fun `dismissing the confirmation deletes nothing`() = runTest(testDispatcher) {
        val viewModel = vm().apply { grantEdit() }
        viewModel.requestRemove(handbook)
        viewModel.dismissRemove()
        viewModel.confirmRemove()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.pendingRemoval).isNull()
        coVerify(exactly = 0) { skillsRepository.deleteSkillFile(any(), any()) }
    }

    @Test
    fun `remove is not offered without edit access`() = runTest(testDispatcher) {
        val viewModel = vm()
        viewModel.requestRemove(handbook)
        assertThat(viewModel.uiState.value.pendingRemoval).isNull()
    }

    // --- a failed list is not an empty one ---

    @Test
    fun `a failed first load is a list failure, and a retry that succeeds clears it`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Error(message = null)
        val viewModel = vm()
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loaded).isFalse()
        assertThat(viewModel.uiState.value.listFailed).isTrue()

        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(emptyList())
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loaded).isTrue()
        assertThat(viewModel.uiState.value.listFailed).isFalse()
    }

    @Test
    fun `an upload failure on an empty list is not a list failure`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(emptyList())
        coEvery { skillsRepository.uploadSkillFile(any(), any(), any(), any(), any()) } returns Result.Error(message = "413")
        val viewModel = vm()
        viewModel.load()
        advanceUntilIdle()
        viewModel.upload(doc("notes.md"))
        advanceUntilIdle()
        assertThat(collectEvents(viewModel)).containsExactly(SkillFilesEvent.ActionFailed(SkillError(SkillErrorKind.UPLOAD_FILE, "413")))
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.listFailed).isFalse()
        assertThat(viewModel.uiState.value.loaded).isTrue()
    }

    @Test
    fun `an older list fetch landing last can't bring back a removed file`() = runTest(testDispatcher) {
        val other = SkillFile(relativePath = "notes.md")
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(listOf(handbook, other))
        val viewModel = vm().apply { grantEdit() }
        viewModel.load()
        advanceUntilIdle()

        // A resume starts a fetch that answers slowly, with the file still there.
        val staleList = CompletableDeferred<Result<List<SkillFile>>>()
        coEvery { skillsRepository.listSkillFiles("sk-1") } coAnswers { staleList.await() } andThen
            Result.Success(listOf(other))
        coEvery { skillsRepository.deleteSkillFile("sk-1", handbook.relativePath) } returns
            Result.Success(DeleteSkillFileResponse(relativePath = handbook.relativePath, deleted = true))
        viewModel.load()
        advanceUntilIdle()

        viewModel.delete(handbook)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.files).containsExactly(other)

        staleList.complete(Result.Success(listOf(handbook, other)))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.files).containsExactly(other)
    }

    /** A second Remove during the first DELETE would 404 and report a false failure. */
    @Test
    fun `a file being removed can't be removed again, and its row goes once the delete lands`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(listOf(handbook))
        val deleted = CompletableDeferred<Result<DeleteSkillFileResponse>>()
        coEvery { skillsRepository.deleteSkillFile("sk-1", handbook.relativePath) } coAnswers { deleted.await() }
        val viewModel = vm().apply { grantEdit() }
        viewModel.load()
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.requestRemove(handbook)
        viewModel.confirmRemove()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.removing).containsExactly(handbook.relativePath)

        viewModel.requestRemove(handbook)
        assertThat(viewModel.uiState.value.pendingRemoval).isNull()
        viewModel.delete(handbook)
        advanceUntilIdle()
        coVerify(exactly = 1) { skillsRepository.deleteSkillFile("sk-1", handbook.relativePath) }

        val reload = CompletableDeferred<Result<List<SkillFile>>>()
        coEvery { skillsRepository.listSkillFiles("sk-1") } coAnswers { reload.await() }
        deleted.complete(Result.Success(DeleteSkillFileResponse(relativePath = handbook.relativePath, deleted = true)))
        advanceUntilIdle()
        // Gone before the confirming reload lands, so nothing is left to tap.
        assertThat(viewModel.uiState.value.files).isEmpty()
        assertThat(viewModel.uiState.value.removing).isEmpty()
        assertThat(events).isEmpty()
    }

    @Test
    fun `a remove that fails frees the row for another try`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(listOf(handbook))
        coEvery { skillsRepository.deleteSkillFile("sk-1", handbook.relativePath) } returns Result.Error(message = "nope")
        val viewModel = vm().apply { grantEdit() }
        viewModel.load()
        advanceUntilIdle()

        viewModel.delete(handbook)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.removing).isEmpty()
        assertThat(viewModel.uiState.value.files).containsExactly(handbook)
        viewModel.requestRemove(handbook)
        assertThat(viewModel.uiState.value.pendingRemoval).isEqualTo(handbook)
    }

    // --- Add never replaces a file unasked ---

    @Test
    fun `adding a file whose name the skill already has waits for the user to confirm the replace`() =
        runTest(testDispatcher) {
            val pathSlot = uploadSlot()
            coEvery { skillsRepository.listSkillFiles("sk-1") } returns
                Result.Success(listOf(SkillFile(relativePath = "notes.md")))
            val viewModel = vm()
            viewModel.load()
            advanceUntilIdle()

            viewModel.upload(doc("Downloads/notes.md"))
            advanceUntilIdle()
            coVerify(exactly = 0) { skillsRepository.uploadSkillFile(any(), any(), any(), any(), any()) }
            assertThat(viewModel.uiState.value.pendingReplacement).isEqualTo("notes.md")

            viewModel.confirmReplace()
            advanceUntilIdle()
            assertThat(pathSlot.captured).isEqualTo("notes.md")
            coVerify(exactly = 1) { skillsRepository.uploadSkillFile(any(), any(), any(), any(), any()) }
            assertThat(viewModel.uiState.value.pendingReplacement).isNull()
        }

    @Test
    fun `cancelling the replace sends nothing, and a new name goes straight up`() = runTest(testDispatcher) {
        val pathSlot = uploadSlot()
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns
            Result.Success(listOf(SkillFile(relativePath = "notes.md")))
        val viewModel = vm()
        viewModel.load()
        advanceUntilIdle()

        viewModel.upload(doc("notes.md"))
        viewModel.dismissReplace()
        viewModel.confirmReplace()
        advanceUntilIdle()
        coVerify(exactly = 0) { skillsRepository.uploadSkillFile(any(), any(), any(), any(), any()) }
        assertThat(viewModel.uiState.value.pendingReplacement).isNull()

        viewModel.upload(doc("other.md"))
        advanceUntilIdle()
        assertThat(pathSlot.captured).isEqualTo("other.md")
    }
}
