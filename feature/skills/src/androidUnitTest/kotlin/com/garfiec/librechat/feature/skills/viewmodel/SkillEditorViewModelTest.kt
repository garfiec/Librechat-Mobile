package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.SkillCreateResult
import com.garfiec.librechat.core.data.repository.SkillUpdateResult
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.SkillWarning
import com.garfiec.librechat.core.model.request.CreateSkillRequest
import com.garfiec.librechat.core.model.request.UpdateSkillRequest
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.SkillSaveNotices
import com.garfiec.librechat.feature.skills.SkillValidation
import com.google.common.truth.Truth.assertThat
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkillEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val skillsRepository = mockk<SkillsRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val saveNotices = SkillSaveNotices()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads server categories into ui state on init (create mode)`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(
            listOf(Category(label = "com_ui_idea", value = "idea"), Category(label = "com_ui_code", value = "code")),
        )
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        advanceUntilIdle()

        assertThat(vm.uiState.value.availableCategories.mapNotNull { it.value })
            .containsExactly("idea", "code").inOrder()
    }

    @Test
    fun `category fetch failure is non-fatal — empty presets, no error`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Error(message = "offline")
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        advanceUntilIdle()

        assertThat(vm.uiState.value.availableCategories).isEmpty()
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `onCategoryChanged sets the persisted category value`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        advanceUntilIdle()

        vm.onCategoryChanged("write")
        assertThat(vm.uiState.value.category).isEqualTo("write")

        // Selecting "None" clears it (sent as null on save via ifBlank).
        vm.onCategoryChanged("")
        assertThat(vm.uiState.value.category).isEmpty()
    }

    // --- alwaysApply toggle (v0.8.6) ---

    @Test
    fun `onAlwaysApplyChanged sets and clears the flag`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        advanceUntilIdle()

        assertThat(vm.uiState.value.alwaysApply).isFalse()
        vm.onAlwaysApplyChanged(true)
        assertThat(vm.uiState.value.alwaysApply).isTrue()
        vm.onAlwaysApplyChanged(false)
        assertThat(vm.uiState.value.alwaysApply).isFalse()
    }

    @Test
    fun `create sends the explicit alwaysApply boolean`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        val reqSlot: CapturingSlot<CreateSkillRequest> = slot()
        coEvery { skillsRepository.createSkill(capture(reqSlot)) } returns
            SkillCreateResult.Success(Skill(id = "sk-1", name = "n", version = 1))

        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        advanceUntilIdle()
        vm.onNameChanged("my-skill")
        vm.onDescriptionChanged("d")
        vm.onBodyChanged("b")
        vm.onAlwaysApplyChanged(true)
        vm.save()
        advanceUntilIdle()

        coVerify { skillsRepository.createSkill(any()) }
        assertThat(reqSlot.captured.alwaysApply).isTrue()
    }

    @Test
    fun `edit hydrates alwaysApply from the loaded skill and re-sends it`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns
            Result.Success(Skill(id = "sk-1", name = "n", description = "d", body = "b", version = 3, alwaysApply = true))
        coEvery { skillsRepository.updateSkill(eq("sk-1"), any()) } returns
            SkillUpdateResult.Success(Skill(id = "sk-1", name = "n", version = 4))

        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        advanceUntilIdle()
        // Hydrated from the fetched skill (not reset to false).
        assertThat(vm.uiState.value.alwaysApply).isTrue()

        // Toggle OFF and save → the explicit false must be sent (so it persists).
        vm.onAlwaysApplyChanged(false)
        vm.save()
        advanceUntilIdle()
        coVerify {
            skillsRepository.updateSkill("sk-1", match { it.alwaysApply == false })
        }
    }

    // --- 409 split: a taken name is a Name field error, not a version conflict ---

    @Test
    fun `a create with a taken name flags the Name field until the name changes`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.createSkill(any()) } returns SkillCreateResult.NameTaken
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        advanceUntilIdle()
        vm.onNameChanged("taken")
        vm.onDescriptionChanged("d")
        vm.save()
        advanceUntilIdle()

        assertThat(vm.uiState.value.nameTaken).isTrue()
        assertThat(vm.uiState.value.showConflictNotice).isFalse()
        assertThat(vm.uiState.value.error).isNull()
        assertThat(vm.uiState.value.isSaving).isFalse()

        vm.onNameChanged("taken-2")
        assertThat(vm.uiState.value.nameTaken).isFalse()
    }

    @Test
    fun `a rename onto a taken name flags the Name field and keeps the version`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns
            Result.Success(Skill(id = "sk-1", name = "n", description = "d", version = 3))
        coEvery { skillsRepository.updateSkill(eq("sk-1"), any()) } returns SkillUpdateResult.NameTaken
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        advanceUntilIdle()
        vm.onNameChanged("taken")
        vm.save()
        advanceUntilIdle()

        assertThat(vm.uiState.value.nameTaken).isTrue()
        assertThat(vm.uiState.value.showConflictNotice).isFalse()
        assertThat(vm.uiState.value.loadedVersion).isEqualTo(3)
    }

    @Test
    fun `a taken name on create asks the page to reveal the Name field, once`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.createSkill(any()) } returns SkillCreateResult.NameTaken
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        val events = mutableListOf<SkillEditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        advanceUntilIdle()
        vm.onNameChanged("taken")
        vm.onDescriptionChanged("d")
        vm.save()
        advanceUntilIdle()

        assertThat(events).containsExactly(SkillEditorEvent.NameTaken)
    }

    @Test
    fun `a rename onto a taken name asks the page to reveal the Name field`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns
            Result.Success(Skill(id = "sk-1", name = "n", description = "d", version = 3))
        coEvery { skillsRepository.updateSkill(eq("sk-1"), any()) } returns SkillUpdateResult.NameTaken
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        val events = mutableListOf<SkillEditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        advanceUntilIdle()
        vm.onNameChanged("taken")
        vm.save()
        advanceUntilIdle()

        assertThat(events).containsExactly(SkillEditorEvent.NameTaken)
    }

    // --- typed errors: server text when sent, else the kind the UI words ---

    @Test
    fun `a conflict without the current skill whose re-read fails says so, worded by the UI`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns
            Result.Success(Skill(id = "sk-1", name = "n", description = "d", version = 3)) andThen
            Result.Error(message = "offline")
        coEvery { skillsRepository.updateSkill(eq("sk-1"), any()) } returns SkillUpdateResult.ConflictWithoutCurrent
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        advanceUntilIdle()
        vm.onBodyChanged("edited")
        vm.save()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo(SkillError(SkillErrorKind.CHANGED_ON_SERVER))
        assertThat(vm.uiState.value.showConflictNotice).isFalse()
        assertThat(vm.uiState.value.isSaving).isFalse()
        assertThat(vm.uiState.value.body).isEqualTo("edited")
    }

    /** Without the server's version every re-save 409s on the same stale one. */
    @Test
    fun `a conflict without the current skill re-reads it, keeps the edits, and the re-save targets its version`() =
        runTest(testDispatcher) {
            coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
            coEvery { skillsRepository.getSkill("sk-1") } returns
                Result.Success(Skill(id = "sk-1", name = "n", description = "d", version = 3)) andThen
                Result.Error(message = "offline") andThen
                Result.Success(Skill(id = "sk-1", name = "n", description = "d", displayTitle = "Theirs", version = 5))
            val sent = mutableListOf<UpdateSkillRequest>()
            coEvery { skillsRepository.updateSkill(eq("sk-1"), capture(sent)) } returns
                SkillUpdateResult.ConflictWithoutCurrent andThen
                SkillUpdateResult.ConflictWithoutCurrent andThen
                SkillUpdateResult.Success(Skill(id = "sk-1", name = "n", version = 6))
            val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
            advanceUntilIdle()
            vm.onBodyChanged("edited")
            vm.save() // 409, re-read fails
            advanceUntilIdle()
            vm.save() // 409, re-read lands
            advanceUntilIdle()

            assertThat(vm.uiState.value.loadedVersion).isEqualTo(5)
            assertThat(vm.uiState.value.showConflictNotice).isTrue()
            assertThat(vm.uiState.value.body).isEqualTo("edited")

            vm.save()
            advanceUntilIdle()
            assertThat(sent.map { it.expectedVersion }).containsExactly(3, 3, 5).inOrder()
            // The server now holds a title the form leaves blank: the re-save clears it.
            assertThat(sent[2].displayTitle).isEqualTo("")
        }

    /** A second back during the exit transition would pop the page behind too. */
    @Test
    fun `back on a clean form exits once however often it is tapped`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        val events = mutableListOf<SkillEditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        advanceUntilIdle()

        vm.onBackRequested()
        vm.onBackRequested()
        advanceUntilIdle()

        assertThat(events).containsExactly(SkillEditorEvent.Exit)
    }

    @Test
    fun `discard exits once, and back after it sends nothing more`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        val events = mutableListOf<SkillEditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        advanceUntilIdle()
        vm.onNameChanged("draft")
        vm.onBackRequested()
        vm.discardChanges()
        vm.onBackRequested()
        advanceUntilIdle()

        assertThat(events).containsExactly(SkillEditorEvent.Exit)
    }

    @Test
    fun `a failed update keeps the server's text, and a silent one leaves the wording to the UI`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns
            Result.Success(Skill(id = "sk-1", name = "n", description = "d", version = 3))
        coEvery { skillsRepository.updateSkill(eq("sk-1"), any()) } returns
            SkillUpdateResult.Error("Body must be 100000 characters or less")
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        advanceUntilIdle()
        vm.onBodyChanged("edited")
        vm.save()
        advanceUntilIdle()
        assertThat(vm.uiState.value.error)
            .isEqualTo(SkillError(SkillErrorKind.SAVE_SKILL, "Body must be 100000 characters or less"))

        coEvery { skillsRepository.updateSkill(eq("sk-1"), any()) } returns SkillUpdateResult.Error(null)
        vm.save()
        advanceUntilIdle()
        assertThat(vm.uiState.value.error).isEqualTo(SkillError(SkillErrorKind.SAVE_SKILL))
    }

    @Test
    fun `a failed create with blank text leaves the wording to the UI`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.createSkill(any()) } returns SkillCreateResult.Error("  ")
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        advanceUntilIdle()
        vm.onNameChanged("pdf-tools")
        vm.onDescriptionChanged("Extract tables.")
        vm.save()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo(SkillError(SkillErrorKind.CREATE_SKILL))
    }

    // --- PATCH clears with "": the wire Json omits nulls ---

    @Test
    fun `clearing the display title and category on edit sends empty strings`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns Result.Success(
            Skill(id = "sk-1", name = "n", description = "d", displayTitle = "Title", category = "code", version = 3),
        )
        val sent: CapturingSlot<UpdateSkillRequest> = slot()
        coEvery { skillsRepository.updateSkill(eq("sk-1"), capture(sent)) } returns
            SkillUpdateResult.Success(Skill(id = "sk-1", name = "n", version = 4))
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        advanceUntilIdle()
        vm.onDisplayTitleChanged("")
        vm.onCategoryChanged("")
        vm.save()
        advanceUntilIdle()

        assertThat(sent.captured.displayTitle).isEqualTo("")
        assertThat(sent.captured.category).isEqualTo("")
    }

    @Test
    fun `blank fields the skill never had stay omitted on edit`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns
            Result.Success(Skill(id = "sk-1", name = "n", description = "d", version = 3))
        val sent: CapturingSlot<UpdateSkillRequest> = slot()
        coEvery { skillsRepository.updateSkill(eq("sk-1"), capture(sent)) } returns
            SkillUpdateResult.Success(Skill(id = "sk-1", name = "n", version = 4))
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        advanceUntilIdle()
        vm.onBodyChanged("new body")
        vm.save()
        advanceUntilIdle()

        assertThat(sent.captured.displayTitle).isNull()
        assertThat(sent.captured.category).isNull()
    }

    @Test
    fun `a re-save after a conflict clears what the other writer set, as the form shows`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns
            Result.Success(Skill(id = "sk-1", name = "n", description = "d", version = 1))
        val sent = mutableListOf<UpdateSkillRequest>()
        coEvery { skillsRepository.updateSkill(eq("sk-1"), capture(sent)) } returns
            SkillUpdateResult.Conflict(
                Skill(id = "sk-1", name = "n", description = "d", displayTitle = "Theirs", category = "code", version = 2),
            ) andThen SkillUpdateResult.Success(Skill(id = "sk-1", name = "n", version = 3))
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        advanceUntilIdle()
        vm.onBodyChanged("mine")
        vm.save()
        advanceUntilIdle()
        assertThat(vm.uiState.value.showConflictNotice).isTrue()

        vm.save()
        advanceUntilIdle()

        assertThat(sent).hasSize(2)
        assertThat(sent[1].expectedVersion).isEqualTo(2)
        assertThat(sent[1].displayTitle).isEqualTo("")
        assertThat(sent[1].category).isEqualTo("")
    }

    @Test
    fun `a failed save asks the page to bring its reason into view`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns
            Result.Success(Skill(id = "sk-1", name = "n", description = "d", version = 3))
        coEvery { skillsRepository.updateSkill(eq("sk-1"), any()) } returns
            SkillUpdateResult.Error("Body is invalid") andThen
            SkillUpdateResult.ConflictWithoutCurrent andThen
            SkillUpdateResult.Conflict(Skill(id = "sk-1", name = "n", description = "d", version = 4))
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        val events = mutableListOf<SkillEditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        advanceUntilIdle()
        vm.onBodyChanged("edited")
        repeat(3) {
            vm.save()
            advanceUntilIdle()
        }

        assertThat(events).containsExactly(
            SkillEditorEvent.SaveFailed,
            SkillEditorEvent.SaveFailed,
            SkillEditorEvent.SaveFailed,
        )
    }

    @Test
    fun `a failed create asks the page to bring its reason into view`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.createSkill(any()) } returns SkillCreateResult.Error("offline")
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        val events = mutableListOf<SkillEditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        advanceUntilIdle()
        vm.onNameChanged("pdf-tools")
        vm.onDescriptionChanged("Extract tables.")
        vm.save()
        advanceUntilIdle()

        assertThat(events).containsExactly(SkillEditorEvent.SaveFailed)
    }

    @Test
    fun `a missing description and an overlong body each say why Save is off`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        advanceUntilIdle()
        vm.onNameChanged("pdf-tools")
        vm.onBodyChanged("body")
        assertThat(vm.uiState.value.canSave).isFalse()
        assertThat(vm.uiState.value.descriptionMissing).isTrue()
        assertThat(vm.uiState.value.bodyTooLong).isFalse()

        vm.onDescriptionChanged("Extract tables.")
        assertThat(vm.uiState.value.descriptionMissing).isFalse()
        assertThat(vm.uiState.value.canSave).isTrue()

        vm.onBodyChanged("x".repeat(SkillValidation.BODY_MAX_LENGTH + 1))
        assertThat(vm.uiState.value.canSave).isFalse()
        assertThat(vm.uiState.value.bodyTooLong).isTrue()
    }

    @Test
    fun `a new form says a description is needed only once the field or a valid name calls for it`() =
        runTest(testDispatcher) {
            coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
            val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
            advanceUntilIdle()
            assertThat(vm.uiState.value.showDescriptionRequired).isFalse()

            vm.onNameChanged("Bad Name")
            assertThat(vm.uiState.value.showDescriptionRequired).isFalse()

            vm.onNameChanged("pdf-tools")
            assertThat(vm.uiState.value.showDescriptionRequired).isTrue()

            vm.onNameChanged("")
            vm.onDescriptionChanged("Extract")
            assertThat(vm.uiState.value.showDescriptionRequired).isFalse()
            vm.onDescriptionChanged("")
            assertThat(vm.uiState.value.showDescriptionRequired).isTrue()
        }

    @Test
    fun `an edit can't be saved until something changes`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns
            Result.Success(Skill(id = "sk-1", name = "pdf-tools", description = "d", body = "b", version = 3))
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        advanceUntilIdle()
        assertThat(vm.uiState.value.canSave).isFalse()

        vm.onDescriptionChanged("d2")
        assertThat(vm.uiState.value.canSave).isTrue()

        vm.onDescriptionChanged("d")
        assertThat(vm.uiState.value.canSave).isFalse()
        vm.save()
        advanceUntilIdle()
        coVerify(exactly = 0) { skillsRepository.updateSkill(any(), any()) }
    }

    // --- server warnings ---

    private fun warning(message: String) = SkillWarning(field = "description", code = "TOO_SHORT", message = message)

    @Test
    fun `a create's warnings ride the Saved event and wait for the returning page`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.createSkill(any()) } returns SkillCreateResult.Success(
            Skill(
                id = "sk-new",
                name = "n",
                version = 1,
                warnings = listOf(
                    warning("Short descriptions may cause the agent to miss triggering opportunities."),
                    warning("  "),
                ),
            ),
        )
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        val events = mutableListOf<SkillEditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        advanceUntilIdle()
        vm.onNameChanged("pdf-tools")
        vm.onDescriptionChanged("Extract tables.")
        vm.save()
        advanceUntilIdle()

        val expected = listOf("Short descriptions may cause the agent to miss triggering opportunities.")
        assertThat(events).containsExactly(SkillEditorEvent.Saved("sk-new", expected))
        // The list page takes everything (it can't know the new id).
        assertThat(saveNotices.takeAll()).isEqualTo(expected)
    }

    @Test
    fun `an update's warnings are queued for that skill`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns Result.Success(
            Skill(id = "sk-1", name = "pdf-tools", description = "Extracts tables from PDF reports.", version = 3),
        )
        coEvery { skillsRepository.updateSkill(eq("sk-1"), any()) } returns SkillUpdateResult.Success(
            Skill(id = "sk-1", name = "pdf-tools", version = 4, warnings = listOf(warning("Consider a longer description."))),
        )
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        advanceUntilIdle()
        vm.onBodyChanged("new body")
        vm.save()
        advanceUntilIdle()

        assertThat(saveNotices.take("sk-1")).containsExactly("Consider a longer description.")
    }

    @Test
    fun `a save without warnings queues nothing`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.createSkill(any()) } returns
            SkillCreateResult.Success(Skill(id = "sk-new", name = "n", version = 1))
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        advanceUntilIdle()
        vm.onNameChanged("pdf-tools")
        vm.onDescriptionChanged("Extract tables.")
        vm.save()
        advanceUntilIdle()

        assertThat(saveNotices.takeAll()).isEmpty()
    }

    // --- events survive a collector gap (activity recreation mid-save) ---

    @Test
    fun `a Saved sent while no screen collects waits for the next collector`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.createSkill(any()) } returns
            SkillCreateResult.Success(Skill(id = "sk-new", name = "pdf-tools", version = 1))
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = null)
        advanceUntilIdle()
        vm.onNameChanged("pdf-tools")
        vm.onDescriptionChanged("Extract tables.")
        vm.save()
        advanceUntilIdle()

        val events = mutableListOf<SkillEditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        advanceUntilIdle()

        assertThat(events).containsExactly(SkillEditorEvent.Saved("sk-new"))
    }

    @Test
    fun `a landed save is the new baseline, so back after it asks nothing`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns
            Result.Success(Skill(id = "sk-1", name = "n", description = "d", body = "b", version = 3))
        coEvery { skillsRepository.updateSkill(eq("sk-1"), any()) } returns
            SkillUpdateResult.Success(Skill(id = "sk-1", name = "n", body = "new body", version = 4))
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        advanceUntilIdle()
        vm.onBodyChanged("new body")
        vm.save()
        advanceUntilIdle()

        assertThat(vm.uiState.value.hasUnsavedChanges).isFalse()
        assertThat(vm.uiState.value.loadedVersion).isEqualTo(4)
        vm.onBackRequested()
        assertThat(vm.uiState.value.showDiscardConfirm).isFalse()
    }

    /** The landing pops the editor, so an edit typed meanwhile would be lost unseen. */
    @Test
    fun `the form refuses edits while a save is in flight, and takes them again after it fails`() = runTest(testDispatcher) {
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
        coEvery { skillsRepository.getSkill("sk-1") } returns
            Result.Success(Skill(id = "sk-1", name = "n", description = "d", body = "b", category = "c", version = 3))
        val put = CompletableDeferred<SkillUpdateResult>()
        coEvery { skillsRepository.updateSkill(eq("sk-1"), any()) } coAnswers { put.await() }
        val vm = SkillEditorViewModel(skillsRepository, configRepository, saveNotices, initialSkillId = "sk-1")
        advanceUntilIdle()
        vm.onBodyChanged("long body")
        vm.save()
        advanceUntilIdle()
        assertThat(vm.uiState.value.isSaving).isTrue()

        vm.onNameChanged("other")
        vm.onDisplayTitleChanged("Title")
        vm.onDescriptionChanged("other d")
        vm.onBodyChanged("long body, typo fixed")
        vm.onCategoryChanged("")
        vm.onAlwaysApplyChanged(true)
        val during = vm.uiState.value
        assertThat(during.savedFields()).isEqualTo(
            SkillFields(name = "n", description = "d", body = "long body", category = "c", alwaysApply = false),
        )

        put.complete(SkillUpdateResult.Error("500"))
        advanceUntilIdle()
        vm.onBodyChanged("long body, typo fixed")
        assertThat(vm.uiState.value.body).isEqualTo("long body, typo fixed")
    }
}
