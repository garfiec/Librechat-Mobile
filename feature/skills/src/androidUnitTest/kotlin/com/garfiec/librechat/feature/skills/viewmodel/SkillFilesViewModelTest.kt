package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillFileEditResult
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.model.SkillFile
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.response.DeleteSkillFileResponse
import com.garfiec.librechat.core.model.response.SkillFileContentResponse
import com.garfiec.librechat.feature.skills.components.PickedDocument
import com.google.common.truth.Truth.assertThat
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
class SkillFilesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val skillsRepository = mockk<SkillsRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun vm() = SkillFilesViewModel(skillsRepository, roleRepository, "sk-1")

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

    // --- canEditFiles fail-closed gate ---

    @Test
    fun `canEditFiles is false for null role (fail-closed)`() = runTest(testDispatcher) {
        val viewModel = vm()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canEditFiles).isFalse()
    }

    @Test
    fun `canEditFiles is true only when SKILLS CREATE explicitly granted`() = runTest(testDispatcher) {
        every { roleRepository.userPermissions } returns MutableStateFlow(
            UserRolePermissions(name = "USER", permissions = mapOf("SKILLS" to mapOf("CREATE" to true))),
        )
        val viewModel = vm()
        viewModel.setSkillSource(null)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canEditFiles).isTrue()
    }

    // --- v0.8.8: externally managed skills are read-only ---

    private fun grantCreate() {
        every { roleRepository.userPermissions } returns MutableStateFlow(
            UserRolePermissions(name = "USER", permissions = mapOf("SKILLS" to mapOf("CREATE" to true))),
        )
    }

    @Test
    fun `an externally managed skill offers no file edits even with CREATE`() = runTest(testDispatcher) {
        grantCreate()
        val viewModel = vm()
        viewModel.setSkillSource("github")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canEditFiles).isFalse()
    }

    @Test
    fun `file edits wait for the skill's source to load`() = runTest(testDispatcher) {
        grantCreate()
        val viewModel = vm()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canEditFiles).isFalse()
        viewModel.setSkillSource("inline")
        assertThat(viewModel.uiState.value.canEditFiles).isTrue()
    }

    // --- v0.8.8: conditional in-place editor ---

    private fun content(fileId: String?) = SkillFileContentResponse(
        fileId = fileId,
        content = "hello",
        mimeType = "text/markdown",
        relativePath = "notes.md",
        filename = "notes.md",
    )

    @Test
    fun `a file without a revision opens read-only`() = runTest(testDispatcher) {
        grantCreate()
        coEvery { skillsRepository.getSkillFileContent("sk-1", "notes.md") } returns Result.Success(content(null))
        val viewModel = vm()
        viewModel.setSkillSource("inline")
        viewModel.openFile(SkillFile(relativePath = "notes.md"))
        advanceUntilIdle()

        val editor = viewModel.uiState.value.editor!!
        assertThat(editor.content).isEqualTo("hello")
        assertThat(editor.isEditable(viewModel.uiState.value.canEditFiles)).isFalse()
    }

    @Test
    fun `a save sends the revision it read and a 409 blocks until reload`() = runTest(testDispatcher) {
        grantCreate()
        coEvery { skillsRepository.getSkillFileContent("sk-1", "notes.md") } returns Result.Success(content("rev-1"))
        coEvery {
            skillsRepository.editSkillFile("sk-1", "notes.md", "rev-1", "edited", "notes.md", "text/markdown")
        } returns SkillFileEditResult.Conflict
        val viewModel = vm()
        viewModel.setSkillSource("inline")
        viewModel.openFile(SkillFile(relativePath = "notes.md"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.editor!!.isEditable(true)).isTrue()

        viewModel.saveEditor("edited")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.editor!!.conflict).isTrue()
        assertThat(viewModel.uiState.value.editor!!.isEditable(true)).isFalse()

        coEvery { skillsRepository.getSkillFileContent("sk-1", "notes.md") } returns Result.Success(content("rev-2"))
        viewModel.reloadEditor()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.editor!!.fileId).isEqualTo("rev-2")
        assertThat(viewModel.uiState.value.editor!!.conflict).isFalse()
    }

    @Test
    fun `a saved edit closes the editor and reloads the list`() = runTest(testDispatcher) {
        grantCreate()
        coEvery { skillsRepository.getSkillFileContent("sk-1", "notes.md") } returns Result.Success(content("rev-1"))
        coEvery { skillsRepository.editSkillFile(any(), any(), any(), any(), any(), any()) } returns
            SkillFileEditResult.Saved(SkillFile(relativePath = "notes.md"))
        coEvery { skillsRepository.listSkillFiles("sk-1") } returns Result.Success(emptyList())
        val viewModel = vm()
        viewModel.setSkillSource("inline")
        viewModel.openFile(SkillFile(relativePath = "notes.md"))
        advanceUntilIdle()

        viewModel.saveEditor("edited")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.editor).isNull()
        coVerify { skillsRepository.listSkillFiles("sk-1") }
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
        assertThat(viewModel.uiState.value.error).isEqualTo("boom")
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
}
