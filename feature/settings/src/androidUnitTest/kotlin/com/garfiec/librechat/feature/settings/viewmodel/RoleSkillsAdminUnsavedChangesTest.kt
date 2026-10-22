package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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

        assertThat(viewModel.uiState.value.saved).isTrue()
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
    }

    @Test
    fun `back during a save waits for it instead of offering a discard`() = runTest {
        val answer = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.updateRoleSkills("USER", any()) } coAnswers { answer.await() }
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()
        viewModel.onBackRequested()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.exitRequested).isFalse()

        answer.complete(Result.Success(role(use = true, create = true)))
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `back during a save that fails stays`() = runTest {
        val answer = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.updateRoleSkills("USER", any()) } coAnswers { answer.await() }
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()
        viewModel.onBackRequested()
        answer.complete(Result.Error(message = "boom"))

        assertThat(viewModel.uiState.value.error).isEqualTo(RolePermissionsAdminError.SAVE)
        assertThat(viewModel.uiState.value.exitRequested).isFalse()
    }

    @Test
    fun `the picker lists the server's roles, custom ones included`() = runTest {
        coEvery { roleRepository.listRoleNames() } returns Result.Success(listOf("ADMIN", "USER", "STAFF"))
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        assertThat(viewModel.uiState.value.roles).containsExactly("ADMIN", "USER", "STAFF").inOrder()
        assertThat(viewModel.uiState.value.selectedRole).isEqualTo("USER")
    }

    @Test
    fun `the picker falls back to USER and ADMIN when the role list fails or is empty`() = runTest {
        coEvery { roleRepository.listRoleNames() } returns Result.Error(message = "Forbidden")
        assertThat(RoleSkillsAdminViewModel(roleRepository).uiState.value.roles).containsExactly("USER", "ADMIN").inOrder()

        coEvery { roleRepository.listRoleNames() } returns Result.Success(emptyList())
        assertThat(RoleSkillsAdminViewModel(roleRepository).uiState.value.roles).containsExactly("USER", "ADMIN").inOrder()
    }

    @Test
    fun `a custom role loads and saves under its own name`() = runTest {
        coEvery { roleRepository.listRoleNames() } returns Result.Success(listOf("USER", "ADMIN", "STAFF"))
        coEvery { roleRepository.getRole("STAFF") } returns Result.Success(UserRolePermissions(name = "STAFF"))
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.selectRole("STAFF")
        viewModel.setUse(true)
        viewModel.save()

        coVerify(exactly = 1) { roleRepository.updateRoleSkills("STAFF", any()) }
    }

    /** Nothing differs from the loaded role, so there is nothing to send. */
    @Test
    fun `Save is off until a switch differs from the loaded role`() = runTest {
        val viewModel = RoleSkillsAdminViewModel(roleRepository)
        assertThat(viewModel.uiState.value.loaded).isTrue()
        assertThat(viewModel.uiState.value.canSave).isFalse()

        viewModel.save()
        coVerify(exactly = 0) { roleRepository.updateRoleSkills(any(), any()) }

        viewModel.setShare(true)
        assertThat(viewModel.uiState.value.canSave).isTrue()

        // Back to what the server holds: off again.
        viewModel.setShare(false)
        assertThat(viewModel.uiState.value.canSave).isFalse()
    }

    @Test
    fun `Save is off again once a save lands, and stays on after a failed one`() = runTest {
        coEvery { roleRepository.updateRoleSkills("USER", any()) } returns Result.Success(role(use = true, create = true))
        val viewModel = RoleSkillsAdminViewModel(roleRepository)
        viewModel.setCreate(true)
        viewModel.save()
        assertThat(viewModel.uiState.value.canSave).isFalse()

        coEvery { roleRepository.updateRoleSkills("USER", any()) } returns Result.Error(message = "offline")
        viewModel.setShare(true)
        viewModel.save()
        assertThat(viewModel.uiState.value.error).isEqualTo(RolePermissionsAdminError.SAVE)
        assertThat(viewModel.uiState.value.canSave).isTrue()
    }
}
