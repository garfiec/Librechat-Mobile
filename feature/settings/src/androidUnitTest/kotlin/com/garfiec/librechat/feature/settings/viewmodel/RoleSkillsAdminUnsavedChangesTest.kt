package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Back from the role-skills screen asks before throwing pending toggles away — and only then. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoleSkillsAdminUnsavedChangesTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val roleRepository = mockk<RoleRepository>(relaxed = true)

    private fun role(use: Boolean, create: Boolean) = UserRolePermissions(
        name = "USER",
        permissions = mapOf("SKILLS" to mapOf("USE" to use, "CREATE" to create)),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        coEvery { roleRepository.getRole("USER") } returns Result.Success(role(use = true, create = false))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a freshly loaded role leaves without asking`() = runTest {
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
        viewModel.onBackRequested()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `a toggle asks before leaving, and discarding then leaves`() = runTest {
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.onBackRequested()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isTrue()
        assertThat(viewModel.uiState.value.exitRequested).isFalse()

        viewModel.discardChanges()
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `flipping a toggle back is clean again`() = runTest {
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setUse(false)
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isTrue()
        viewModel.setUse(true)

        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
    }

    /** The screen stays open after a save, so what was saved becomes the clean state. */
    @Test
    fun `a save makes the saved flags the clean state`() = runTest {
        coEvery { roleRepository.updateRoleSkills("USER", any()) } returns
            Result.Success(role(use = true, create = true))
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()

        assertThat(viewModel.uiState.value.savedMessage).isNotNull()
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
    }
}
