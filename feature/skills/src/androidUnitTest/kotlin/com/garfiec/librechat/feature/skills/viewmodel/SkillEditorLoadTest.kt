package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.SkillSaveNotices
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Edit mode before the skill has loaded: no form, no save, and a retry when the load fails. */
@OptIn(ExperimentalCoroutinesApi::class)
class SkillEditorLoadTest {

    private val testDispatcher = StandardTestDispatcher()
    private val skillsRepository = mockk<SkillsRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)

    private val loaded = Skill(id = "sk-1", name = "pdf-tools", description = "d", body = "b", version = 3)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun editor() = SkillEditorViewModel(skillsRepository, configRepository, SkillSaveNotices(), initialSkillId = "sk-1")

    @Test
    fun `a skill that failed to load can't be saved, however the form is filled`() = runTest(testDispatcher) {
        coEvery { skillsRepository.getSkill("sk-1") } returns Result.Error(message = "offline")
        val viewModel = editor()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.loadFailed).isTrue()
        assertThat(state.error).isEqualTo(SkillError(SkillErrorKind.LOAD_SKILL, "offline"))

        viewModel.onNameChanged("pdf-tools")
        viewModel.onDescriptionChanged("does things")
        viewModel.onBodyChanged("body")
        assertThat(viewModel.uiState.value.canSave).isFalse()
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()

        viewModel.save()
        advanceUntilIdle()
        coVerify(exactly = 0) { skillsRepository.updateSkill(any(), any()) }
    }

    @Test
    fun `the first frame of an edit is loading, not an empty form`() = runTest(testDispatcher) {
        coEvery { skillsRepository.getSkill("sk-1") } returns Result.Success(loaded)
        val viewModel = editor()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
        assertThat(viewModel.uiState.value.canSave).isFalse()
    }

    @Test
    fun `retry after a failed load fills the form and allows a save at the loaded version`() = runTest(testDispatcher) {
        coEvery { skillsRepository.getSkill("sk-1") } returns Result.Error(message = "offline") andThen Result.Success(loaded)
        val viewModel = editor()
        advanceUntilIdle()

        viewModel.retryLoad()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.loadFailed).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.name).isEqualTo("pdf-tools")
        assertThat(state.loadedVersion).isEqualTo(3)
        viewModel.onDescriptionChanged("edited")
        assertThat(viewModel.uiState.value.canSave).isTrue()
    }

    @Test
    fun `retry does nothing once the skill has loaded`() = runTest(testDispatcher) {
        coEvery { skillsRepository.getSkill("sk-1") } returns Result.Success(loaded)
        val viewModel = editor()
        advanceUntilIdle()
        viewModel.onNameChanged("renamed")

        viewModel.retryLoad()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.name).isEqualTo("renamed")
        coVerify(exactly = 1) { skillsRepository.getSkill("sk-1") }
    }
}
