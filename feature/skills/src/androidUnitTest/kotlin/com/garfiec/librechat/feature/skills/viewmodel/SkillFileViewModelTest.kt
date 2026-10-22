package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.SkillFileEditResult
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.PermissionBits
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.SkillFile
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.response.SkillFileContentResponse
import com.garfiec.librechat.feature.skills.SkillFileChanges
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkillFileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repo = mockk<SkillsRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val fileChanges = SkillFileChanges()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val me = "65f0c0ffee0000000000abcd"
    private val someoneElse = "65f0c0ffee00000000002222"

    /** `GET /api/user` as the server sends it. */
    private fun user(id: String = me) = json.decodeFromString(
        User.serializer(),
        """{"id":"$id","name":"Ada","email":"ada@example.com","role":"USER","provider":"local"}""",
    )

    /** `GET /api/skills/:id` as `serializeSkill` sends it. */
    private fun skill(author: String = me, source: String = "inline") = json.decodeFromString(
        Skill.serializer(),
        """{"_id":"sk-1","name":"pdf-tools","description":"d","body":"b","author":"$author",""" +
            """"authorName":"Ada","version":1,"source":"$source","fileCount":1,"isPublic":false}""",
    )

    /** `GET /api/skills/:id/files/:relativePath` as the JSON route sends it. */
    private fun content(
        fileId: String? = "rev-1",
        text: String? = "hello",
        path: String = "notes.md",
        mime: String = "text/markdown",
        isBinary: Boolean = false,
    ) = json.decodeFromString(
        SkillFileContentResponse.serializer(),
        buildString {
            append("""{"relativePath":"$path","filename":"${path.substringAfterLast('/')}","mimeType":"$mime",""")
            append(""""bytes":5,"isBinary":$isBinary""")
            if (fileId != null) append(""","fileId":"$fileId"""")
            if (text != null) append(""","content":"$text"""")
            append("}")
        },
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill())
        coEvery { repo.getSkillEffectivePermissions() } returns Result.Success(emptyMap())
        coEvery { userRepository.getUser() } returns Result.Success(user())
        coEvery { repo.getSkillFileContent("sk-1", "notes.md") } returns Result.Success(content())
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun TestScope.opened(path: String = "notes.md"): SkillFileViewModel {
        val viewModel = SkillFileViewModel(repo, userRepository, fileChanges, "sk-1", path)
        advanceUntilIdle()
        return viewModel
    }

    // --- access (fail-closed, resolved here) ---

    @Test
    fun `the owner of an inline skill can edit a text file with a revision`() = runTest(testDispatcher) {
        val state = opened().uiState.value
        assertThat(state.kind).isEqualTo(SkillFileKind.TEXT)
        assertThat(state.canEditFiles).isTrue()
        assertThat(state.isEditable).isTrue()
        assertThat(state.readOnlyReason).isNull()
    }

    @Test
    fun `a shared viewer without EDIT reads the file and is told why`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(author = someoneElse))
        coEvery { repo.getSkillEffectivePermissions() } returns Result.Success(mapOf("sk-1" to PermissionBits.VIEW))
        val state = opened().uiState.value
        assertThat(state.isEditable).isFalse()
        assertThat(state.readOnlyReason).isEqualTo(SkillFileReadOnlyReason.NO_ACCESS)
    }

    @Test
    fun `a shared editor with the EDIT bit can edit`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(author = someoneElse))
        coEvery { repo.getSkillEffectivePermissions() } returns
            Result.Success(mapOf("sk-1" to (PermissionBits.VIEW or PermissionBits.EDIT)))
        assertThat(opened().uiState.value.isEditable).isTrue()
    }

    @Test
    fun `a deployment skill's files are read-only even for the owner`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(source = "deployment"))
        assertThat(opened().uiState.value.isEditable).isFalse()
    }

    @Test
    fun `nothing is editable until access answers`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Result<User>>()
        coEvery { userRepository.getUser() } coAnswers { gate.await() }
        val viewModel = opened()
        assertThat(viewModel.uiState.value.kind).isEqualTo(SkillFileKind.TEXT)
        assertThat(viewModel.uiState.value.isEditable).isFalse()
        // No "you can't edit" note while the answer is still out.
        assertThat(viewModel.uiState.value.readOnlyReason).isNull()

        gate.complete(Result.Success(user()))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isEditable).isTrue()
    }

    @Test
    fun `a failed user fetch grants no owner privilege`() = runTest(testDispatcher) {
        coEvery { userRepository.getUser() } returns Result.Error(message = "boom")
        assertThat(opened().uiState.value.isEditable).isFalse()
    }

    /** A flaky fetch must not lock the owner out of their own file until they reopen it. */
    @Test
    fun `a failed access check is not a denial, and reload checks again`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns Result.Error(message = "timeout")
        val viewModel = opened()
        assertThat(viewModel.uiState.value.isEditable).isFalse()
        assertThat(viewModel.uiState.value.accessResolved).isFalse()
        assertThat(viewModel.uiState.value.readOnlyReason).isEqualTo(SkillFileReadOnlyReason.ACCESS_UNKNOWN)

        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill())
        viewModel.reload()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isEditable).isTrue()
        assertThat(viewModel.uiState.value.readOnlyReason).isNull()
        coVerify(exactly = 2) { repo.getSkill("sk-1") }
    }

    @Test
    fun `a failed bits fetch is not a denial either`() = runTest(testDispatcher) {
        coEvery { repo.getSkillEffectivePermissions() } returns Result.Error(message = "503")
        val state = opened().uiState.value
        assertThat(state.accessResolved).isFalse()
        assertThat(state.readOnlyReason).isEqualTo(SkillFileReadOnlyReason.ACCESS_UNKNOWN)
    }

    @Test
    fun `access that answered is not asked again on reload`() = runTest(testDispatcher) {
        val viewModel = opened()
        viewModel.reload()
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.getSkill("sk-1") }
        coVerify(exactly = 1) { userRepository.getUser() }
    }

    // --- read-only files ---

    @Test
    fun `SKILL md has no revision and opens read-only as the skill body`() = runTest(testDispatcher) {
        coEvery { repo.getSkillFileContent("sk-1", "SKILL.md") } returns
            Result.Success(content(fileId = null, path = "SKILL.md"))
        val state = opened("SKILL.md").uiState.value
        assertThat(state.kind).isEqualTo(SkillFileKind.TEXT)
        assertThat(state.isEditable).isFalse()
        assertThat(state.readOnlyReason).isEqualTo(SkillFileReadOnlyReason.SKILL_BODY)
    }

    @Test
    fun `a file without a revision opens read-only`() = runTest(testDispatcher) {
        coEvery { repo.getSkillFileContent("sk-1", "notes.md") } returns Result.Success(content(fileId = null))
        val state = opened().uiState.value
        assertThat(state.draft).isEqualTo("hello")
        assertThat(state.isEditable).isFalse()
        assertThat(state.readOnlyReason).isEqualTo(SkillFileReadOnlyReason.NO_REVISION)
    }

    // --- raw bytes: images, over-cap text, binary ---

    @Test
    fun `an inline image type is fetched raw and previewed`() = runTest(testDispatcher) {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        coEvery { repo.getSkillFileContent("sk-1", "logo.png") } returns
            Result.Success(content(text = null, path = "logo.png", mime = "image/png", isBinary = true))
        coEvery { repo.getSkillFileRaw("sk-1", "logo.png") } returns Result.Success(png)
        val state = opened("logo.png").uiState.value
        assertThat(state.kind).isEqualTo(SkillFileKind.IMAGE)
        assertThat(state.image!!.bytes).isEqualTo(png)
        assertThat(state.isEditable).isFalse()
    }

    @Test
    fun `an image the device can't decode falls back to the binary note`() = runTest(testDispatcher) {
        coEvery { repo.getSkillFileContent("sk-1", "pic.avif") } returns
            Result.Success(content(text = null, path = "pic.avif", mime = "image/avif", isBinary = true))
        coEvery { repo.getSkillFileRaw("sk-1", "pic.avif") } returns Result.Success(byteArrayOf(1, 2))
        val viewModel = opened("pic.avif")
        viewModel.onImageFailed()
        assertThat(viewModel.uiState.value.kind).isEqualTo(SkillFileKind.BINARY)
        assertThat(viewModel.uiState.value.image).isNull()
    }

    @Test
    fun `binary that isn't an inline image is never downloaded`() = runTest(testDispatcher) {
        coEvery { repo.getSkillFileContent("sk-1", "doc.pdf") } returns
            Result.Success(content(text = null, path = "doc.pdf", mime = "application/pdf", isBinary = true))
        val state = opened("doc.pdf").uiState.value
        assertThat(state.kind).isEqualTo(SkillFileKind.BINARY)
        coVerify(exactly = 0) { repo.getSkillFileRaw(any(), any()) }
        coVerify(exactly = 0) { repo.getSkillFileRawPrefix(any(), any(), any()) }
    }

    @Test
    fun `text over the JSON cap is read raw and shown read-only`() = runTest(testDispatcher) {
        coEvery { repo.getSkillFileContent("sk-1", "big.csv") } returns
            Result.Success(content(text = null, path = "big.csv", mime = "text/csv"))
        coEvery { repo.getSkillFileRawPrefix("sk-1", "big.csv", any()) } returns Result.Success("a,b\nç,文".encodeToByteArray())
        val state = opened("big.csv").uiState.value
        assertThat(state.kind).isEqualTo(SkillFileKind.TEXT)
        assertThat(state.draft).isEqualTo("a,b\nç,文")
        assertThat(state.isEditable).isFalse()
        assertThat(state.isTruncated).isFalse()
        assertThat(state.readOnlyReason).isEqualTo(SkillFileReadOnlyReason.TOO_LARGE)
    }

    @Test
    fun `text past the preview cap is fetched only up to it and shown cut, with a note`() = runTest(testDispatcher) {
        val cap = SkillFileViewModel.PREVIEW_MAX_BYTES
        coEvery { repo.getSkillFileContent("sk-1", "run.log") } returns
            Result.Success(content(text = null, path = "run.log", mime = "text/plain"))
        // The server would send 8 MB; the prefix read hands back one byte past the cap.
        coEvery { repo.getSkillFileRawPrefix("sk-1", "run.log", cap + 1L) } returns
            Result.Success(ByteArray(cap + 1) { 'x'.code.toByte() })
        val state = opened("run.log").uiState.value
        assertThat(state.kind).isEqualTo(SkillFileKind.TEXT)
        assertThat(state.draft.length).isEqualTo(cap)
        assertThat(state.isTruncated).isTrue()
        assertThat(state.readOnlyReason).isEqualTo(SkillFileReadOnlyReason.TRUNCATED)
        assertThat(state.isEditable).isFalse()
        coVerify(exactly = 0) { repo.getSkillFileRaw(any(), any()) }
    }

    @Test
    fun `a cut through a multi-byte character still reads as text`() = runTest(testDispatcher) {
        val cap = SkillFileViewModel.PREVIEW_MAX_BYTES
        // ASCII up to two bytes before the cap, then a three-byte character the cap splits.
        val bytes = ByteArray(cap - 2) { 'x'.code.toByte() } + "文".encodeToByteArray()
        coEvery { repo.getSkillFileContent("sk-1", "run.log") } returns
            Result.Success(content(text = null, path = "run.log", mime = "text/plain"))
        coEvery { repo.getSkillFileRawPrefix("sk-1", "run.log", cap + 1L) } returns Result.Success(bytes)
        val state = opened("run.log").uiState.value
        assertThat(state.kind).isEqualTo(SkillFileKind.TEXT)
        assertThat(state.draft).isEqualTo("x".repeat(cap - 2))
        assertThat(state.isTruncated).isTrue()
    }

    @Test
    fun `over-cap bytes that aren't UTF-8 show the binary note`() = runTest(testDispatcher) {
        coEvery { repo.getSkillFileContent("sk-1", "big.dat") } returns
            Result.Success(content(text = null, path = "big.dat", mime = "application/octet-stream"))
        coEvery { repo.getSkillFileRawPrefix("sk-1", "big.dat", any()) } returns
            Result.Success(byteArrayOf(0xC3.toByte(), 0x28, 0xFF.toByte()))
        assertThat(opened("big.dat").uiState.value.kind).isEqualTo(SkillFileKind.BINARY)
    }

    @Test
    fun `a failed raw read is a load failure that reload retries`() = runTest(testDispatcher) {
        coEvery { repo.getSkillFileContent("sk-1", "logo.png") } returns
            Result.Success(content(text = null, path = "logo.png", mime = "image/png", isBinary = true))
        coEvery { repo.getSkillFileRaw("sk-1", "logo.png") } returns Result.Error(message = "boom")
        val viewModel = opened("logo.png")
        assertThat(viewModel.uiState.value.loadFailed).isTrue()
        assertThat(viewModel.uiState.value.loadError).isEqualTo("boom")

        coEvery { repo.getSkillFileRaw("sk-1", "logo.png") } returns Result.Success(byteArrayOf(1))
        viewModel.reload()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadFailed).isFalse()
        assertThat(viewModel.uiState.value.kind).isEqualTo(SkillFileKind.IMAGE)
        // The resolved access survives the reload.
        assertThat(viewModel.uiState.value.accessResolved).isTrue()
    }

    // --- dirty, save, conflict ---

    @Test
    fun `editing marks the file dirty and enables save, undoing clears it`() = runTest(testDispatcher) {
        val viewModel = opened()
        assertThat(viewModel.uiState.value.canSave).isFalse()
        viewModel.onDraftChange("hello world")
        assertThat(viewModel.uiState.value.isDirty).isTrue()
        assertThat(viewModel.uiState.value.canSave).isTrue()
        viewModel.onDraftChange("hello")
        assertThat(viewModel.uiState.value.isDirty).isFalse()
    }

    @Test
    fun `a read-only file ignores edits`() = runTest(testDispatcher) {
        coEvery { repo.getSkillFileContent("sk-1", "notes.md") } returns Result.Success(content(fileId = null))
        val viewModel = opened()
        viewModel.onDraftChange("changed")
        assertThat(viewModel.uiState.value.draft).isEqualTo("hello")
        viewModel.save()
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.editSkillFile(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a save sends the revision it read and a 409 blocks until reload`() = runTest(testDispatcher) {
        coEvery { repo.editSkillFile("sk-1", "notes.md", "rev-1", "edited", "notes.md", "text/markdown") } returns
            SkillFileEditResult.Conflict
        val viewModel = opened()
        viewModel.onDraftChange("edited")
        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.conflict).isTrue()
        assertThat(viewModel.uiState.value.isEditable).isFalse()
        assertThat(viewModel.uiState.value.canSave).isFalse()
        assertThat(viewModel.uiState.value.saved).isFalse()

        // After a conflict, Reload discards the draft without asking.
        coEvery { repo.getSkillFileContent("sk-1", "notes.md") } returns Result.Success(content(fileId = "rev-2", text = "theirs"))
        viewModel.reload()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.discardPrompt).isNull()
        assertThat(viewModel.uiState.value.fileId).isEqualTo("rev-2")
        assertThat(viewModel.uiState.value.draft).isEqualTo("theirs")
        assertThat(viewModel.uiState.value.conflict).isFalse()
        assertThat(viewModel.uiState.value.isEditable).isTrue()
    }

    @Test
    fun `a saved edit closes the screen and tells the list to reload`() = runTest(testDispatcher) {
        coEvery { repo.editSkillFile(any(), any(), any(), any(), any(), any()) } returns
            SkillFileEditResult.Saved(SkillFile(relativePath = "notes.md"))
        var posted = false
        // Subscribed before the save: the signal has no replay.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fileChanges.changesTo("sk-1").first()
            posted = true
        }
        val viewModel = opened()
        viewModel.onDraftChange("edited")
        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.saved).isTrue()
        assertThat(posted).isTrue()
    }

    @Test
    fun `a failed save keeps the draft and reports the server's message`() = runTest(testDispatcher) {
        coEvery { repo.editSkillFile(any(), any(), any(), any(), any(), any()) } returns SkillFileEditResult.Failed("Too big")
        val viewModel = opened()
        viewModel.onDraftChange("edited")
        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.saveFailed).isTrue()
        assertThat(viewModel.uiState.value.saveError).isEqualTo("Too big")
        assertThat(viewModel.uiState.value.draft).isEqualTo("edited")
        assertThat(viewModel.uiState.value.canSave).isTrue()
    }

    @Test
    fun `a second save while one is in flight is refused`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<SkillFileEditResult>()
        coEvery { repo.editSkillFile(any(), any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        val viewModel = opened()
        viewModel.onDraftChange("edited")
        viewModel.save()
        viewModel.save()
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.editSkillFile(any(), any(), any(), any(), any(), any()) }
        gate.complete(SkillFileEditResult.Saved(SkillFile(relativePath = "notes.md")))
        advanceUntilIdle()
    }

    // --- leaving and reloading with unsaved edits ---

    @Test
    fun `back on a clean file leaves at once`() = runTest(testDispatcher) {
        val viewModel = opened()
        viewModel.onBackRequested()
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
        assertThat(viewModel.uiState.value.discardPrompt).isNull()
    }

    @Test
    fun `back on a dirty file asks, and discarding leaves`() = runTest(testDispatcher) {
        val viewModel = opened()
        viewModel.onDraftChange("edited")
        viewModel.onBackRequested()
        assertThat(viewModel.uiState.value.discardPrompt).isEqualTo(SkillFileDiscardTarget.EXIT)
        assertThat(viewModel.uiState.value.exitRequested).isFalse()

        viewModel.dismissDiscard()
        assertThat(viewModel.uiState.value.discardPrompt).isNull()
        assertThat(viewModel.uiState.value.draft).isEqualTo("edited")

        viewModel.onBackRequested()
        viewModel.confirmDiscard()
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `back is ignored while a save is in flight`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<SkillFileEditResult>()
        coEvery { repo.editSkillFile(any(), any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        val viewModel = opened()
        viewModel.onDraftChange("edited")
        viewModel.save()
        advanceUntilIdle()
        viewModel.onBackRequested()
        assertThat(viewModel.uiState.value.exitRequested).isFalse()
        assertThat(viewModel.uiState.value.discardPrompt).isNull()
        gate.complete(SkillFileEditResult.Saved(SkillFile(relativePath = "notes.md")))
        advanceUntilIdle()
    }

    @Test
    fun `reload on a dirty file asks before discarding`() = runTest(testDispatcher) {
        val viewModel = opened()
        viewModel.onDraftChange("edited")
        viewModel.reload()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.discardPrompt).isEqualTo(SkillFileDiscardTarget.RELOAD)
        assertThat(viewModel.uiState.value.draft).isEqualTo("edited")
        coVerify(exactly = 1) { repo.getSkillFileContent("sk-1", "notes.md") }

        viewModel.confirmDiscard()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.discardPrompt).isNull()
        assertThat(viewModel.uiState.value.draft).isEqualTo("hello")
        assertThat(viewModel.uiState.value.exitRequested).isFalse()
        coVerify(exactly = 2) { repo.getSkillFileContent("sk-1", "notes.md") }
    }
}
