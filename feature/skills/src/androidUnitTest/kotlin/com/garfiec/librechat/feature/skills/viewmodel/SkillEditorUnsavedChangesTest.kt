package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.SkillUpdateResult
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.response.Category
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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

/** Back from the skill editor asks before throwing edits away — and only then. */
@OptIn(ExperimentalCoroutinesApi::class)
class SkillEditorUnsavedChangesTest {

    private val dispatcher = StandardTestDispatcher()
    private val skillsRepository = mockk<SkillsRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)

    private val skill = Skill(
        id = "skill-1",
        name = "summarize",
        displayTitle = null,
        description = "Summarizes things",
        body = "Be brief.",
        category = null,
        version = 3,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { skillsRepository.getSkill(skill.id) } returns Result.Success(skill)
        coEvery { configRepository.getCategories() } returns
            Result.Success(listOf(Category(label = "com_ui_idea", value = "idea")))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun editor(skillId: String? = skill.id) = SkillEditorViewModel(skillsRepository, configRepository, skillId)

    private fun TestScope.eventsOf(viewModel: SkillEditorViewModel): List<SkillEditorEvent> {
        val events = mutableListOf<SkillEditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return events
    }

    @Test
    fun `a freshly loaded skill leaves without asking`() = runTest(dispatcher) {
        val viewModel = editor()
        val events = eventsOf(viewModel)
        advanceUntilIdle()

        // The category presets land after the skill does; they are not an edit.
        assertThat(viewModel.uiState.value.availableCategories).isNotEmpty()
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
        viewModel.onBackRequested()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(events).containsExactly(SkillEditorEvent.Exit)
    }

    @Test
    fun `an edit asks before leaving, and discarding then leaves`() = runTest(dispatcher) {
        val viewModel = editor()
        val events = eventsOf(viewModel)
        advanceUntilIdle()

        viewModel.onBodyChanged("Be thorough.")
        viewModel.onBackRequested()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isTrue()
        assertThat(events).isEmpty()

        viewModel.discardChanges()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(events).containsExactly(SkillEditorEvent.Exit)
    }

    @Test
    fun `cancelling the prompt stays in the editor`() = runTest(dispatcher) {
        val viewModel = editor()
        val events = eventsOf(viewModel)
        advanceUntilIdle()
        viewModel.onAlwaysApplyChanged(true)
        viewModel.onBackRequested()

        viewModel.dismissDiscardConfirmation()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.alwaysApply).isTrue()
        assertThat(events).isEmpty()
    }

    @Test
    fun `undoing an edit by hand is clean again`() = runTest(dispatcher) {
        val viewModel = editor()
        advanceUntilIdle()

        viewModel.onCategoryChanged("idea")
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isTrue()
        viewModel.onCategoryChanged("")

        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
    }

    @Test
    fun `an untouched create form leaves without asking, a named one asks`() = runTest(dispatcher) {
        val viewModel = editor(skillId = null)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()

        viewModel.onNameChanged("new-skill")
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isTrue()
    }

    /** A 409 keeps the user's edits on screen; leaving then must still ask, or they vanish. */
    @Test
    fun `edits kept through a save conflict still count as unsaved`() = runTest(dispatcher) {
        coEvery { skillsRepository.updateSkill(skill.id, any()) } returns
            SkillUpdateResult.Conflict(current = skill.copy(version = 4, body = "Server body."))
        val viewModel = editor()
        advanceUntilIdle()

        viewModel.onBodyChanged("Be thorough.")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.conflictNotice).isNotNull()
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isTrue()
    }

    /** A save navigates when it lands; leaving first would let it pop the screen behind this one. */
    @Test
    fun `back is held while a save is in flight`() = runTest(dispatcher) {
        coEvery { skillsRepository.updateSkill(skill.id, any()) } coAnswers { awaitCancellation() }
        val viewModel = editor()
        val events = eventsOf(viewModel)
        advanceUntilIdle()
        viewModel.onBodyChanged("Be thorough.")
        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isSaving).isTrue()

        viewModel.onBackRequested()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(events).isEmpty()
    }
}
