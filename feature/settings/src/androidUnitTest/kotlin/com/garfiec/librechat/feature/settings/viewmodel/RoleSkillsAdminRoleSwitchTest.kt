package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.FailureKind
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

/**
 * The skills editor's answers belong to the role they were asked for: a load or save that lands
 * after the admin picked another role changes nothing on screen, and results are typed (the editor
 * renders them from string resources).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoleSkillsAdminRoleSwitchTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val ownRole = MutableStateFlow<UserRolePermissions?>(null)

    private fun role(
        name: String = "USER",
        use: Boolean = true,
        create: Boolean = false,
        share: Boolean = false,
        sharePublic: Boolean = false,
    ) = UserRolePermissions(
        name = name,
        permissions = mapOf(
            "SKILLS" to mapOf("USE" to use, "CREATE" to create, "SHARE" to share, "SHARE_PUBLIC" to sharePublic),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { roleRepository.userPermissions } returns ownRole
        coEvery { roleRepository.getRole("USER") } returns Result.Success(role())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a failed load after a role switch shows none of the previous role's switches`() = runTest {
        // USER has every flag on, so a leftover would show as an on switch under ADMIN.
        coEvery { roleRepository.getRole("USER") } returns
            Result.Success(role(use = true, create = true, share = true, sharePublic = true))
        coEvery { roleRepository.getRole("ADMIN") } returns Result.Error(kind = FailureKind.Network)
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.selectRole("ADMIN")

        with(viewModel.uiState.value) {
            assertThat(selectedRole).isEqualTo("ADMIN")
            assertThat(error).isEqualTo(RolePermissionsAdminError.LOAD)
            assertThat(listOf(use, create, share, sharePublic)).containsExactly(false, false, false, false)
            assertThat(savedFlags).isNull()
            assertThat(hasUnsavedChanges).isFalse()
            assertThat(loaded).isFalse()
            assertThat(canSave).isFalse()
        }
    }

    @Test
    fun `a role's first load answering after it was picked again is dropped`() = runTest {
        val firstUserAnswer = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.getRole("USER") } coAnswers { firstUserAnswer.await() }
        coEvery { roleRepository.getRole("ADMIN") } returns Result.Success(role(name = "ADMIN"))
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.selectRole("ADMIN")
        coEvery { roleRepository.getRole("USER") } returns Result.Success(role(create = true))
        viewModel.selectRole("USER")
        viewModel.setShare(true)
        // The first USER request answers last, with flags the second one has already replaced.
        firstUserAnswer.complete(Result.Success(role(create = false)))

        with(viewModel.uiState.value) {
            assertThat(selectedRole).isEqualTo("USER")
            assertThat(create).isTrue()
            assertThat(share).isTrue()
            assertThat(hasUnsavedChanges).isTrue()
        }
    }

    @Test
    fun `picking another role with unsaved switches asks first and loads nothing`() = runTest {
        coEvery { roleRepository.getRole("ADMIN") } returns Result.Success(role(name = "ADMIN", create = true))
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setShare(true)
        viewModel.selectRole("ADMIN")

        with(viewModel.uiState.value) {
            assertThat(showDiscardConfirm).isTrue()
            assertThat(pendingRole).isEqualTo("ADMIN")
            assertThat(selectedRole).isEqualTo("USER")
            assertThat(share).isTrue()
            assertThat(hasUnsavedChanges).isTrue()
            assertThat(exitRequested).isFalse()
        }
        coVerify(exactly = 0) { roleRepository.getRole("ADMIN") }
    }

    @Test
    fun `keeping the edits on a role switch prompt stays on the role, and a later back still leaves`() = runTest {
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setShare(true)
        viewModel.selectRole("ADMIN")
        viewModel.dismissDiscardConfirmation()

        with(viewModel.uiState.value) {
            assertThat(showDiscardConfirm).isFalse()
            assertThat(pendingRole).isNull()
            assertThat(selectedRole).isEqualTo("USER")
            assertThat(share).isTrue()
        }

        // The next prompt is Back's own: discarding it leaves rather than switching to ADMIN.
        viewModel.onBackRequested()
        viewModel.discardChanges()
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
        coVerify(exactly = 0) { roleRepository.getRole("ADMIN") }
    }

    @Test
    fun `discarding on a role switch prompt loads the picked role and stays on the screen`() = runTest {
        coEvery { roleRepository.getRole("ADMIN") } returns
            Result.Success(role(name = "ADMIN", use = true, create = true, share = true))
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setSharePublic(true)
        viewModel.selectRole("ADMIN")
        viewModel.discardChanges()

        with(viewModel.uiState.value) {
            assertThat(showDiscardConfirm).isFalse()
            assertThat(pendingRole).isNull()
            assertThat(exitRequested).isFalse()
            assertThat(selectedRole).isEqualTo("ADMIN")
            assertThat(listOf(use, create, share, sharePublic)).containsExactly(true, true, true, false).inOrder()
            assertThat(hasUnsavedChanges).isFalse()
        }
        coVerify(exactly = 1) { roleRepository.getRole("ADMIN") }
    }

    @Test
    fun `a save landing after a role switch leaves the new role's switches alone`() = runTest {
        val saveAnswer = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.updateRoleSkills("USER", any()) } coAnswers { saveAnswer.await() }
        coEvery { roleRepository.getRole("ADMIN") } returns
            Result.Success(role(name = "ADMIN", use = false, create = true))
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setShare(true)
        viewModel.save()
        // The picker is locked while saving; this is the ViewModel's own guard.
        viewModel.selectRole("ADMIN")
        saveAnswer.complete(Result.Success(role(use = true, share = true, sharePublic = true)))

        with(viewModel.uiState.value) {
            assertThat(selectedRole).isEqualTo("ADMIN")
            assertThat(use).isFalse()
            assertThat(create).isTrue()
            assertThat(sharePublic).isFalse()
            assertThat(saved).isFalse()
            assertThat(isSaving).isFalse()
            assertThat(hasUnsavedChanges).isFalse()
        }
    }

    @Test
    fun `a save failing after a role switch reports nothing on the new role`() = runTest {
        val saveAnswer = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.updateRoleSkills("USER", any()) } coAnswers { saveAnswer.await() }
        coEvery { roleRepository.getRole("ADMIN") } returns Result.Success(role(name = "ADMIN"))
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()
        viewModel.selectRole("ADMIN")
        saveAnswer.complete(Result.Error(kind = FailureKind.Network))

        with(viewModel.uiState.value) {
            assertThat(error).isNull()
            assertThat(isSaving).isFalse()
            assertThat(loaded).isTrue()
        }
    }

    // A save of the admin's own role refreshes the cached permissions in the repository, whatever
    // the screen did meanwhile: RoleRepositoryImplTest.

    @Test
    fun `a landed save reports saved, and the next change clears it`() = runTest {
        coEvery { roleRepository.updateRoleSkills("USER", any()) } returns Result.Success(role(create = true))
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()
        assertThat(viewModel.uiState.value.saved).isTrue()
        assertThat(viewModel.uiState.value.error).isNull()

        viewModel.setUse(false)
        assertThat(viewModel.uiState.value.saved).isFalse()
    }

    @Test
    fun `a refused save reports a save error and keeps the edits`() = runTest {
        coEvery { roleRepository.updateRoleSkills("USER", any()) } returns
            Result.Error(message = "Invalid skill permissions.")
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()

        with(viewModel.uiState.value) {
            assertThat(error).isEqualTo(RolePermissionsAdminError.SAVE)
            assertThat(loadFailed).isFalse()
            assertThat(saved).isFalse()
            assertThat(create).isTrue()
            assertThat(hasUnsavedChanges).isTrue()
        }
    }

    @Test
    fun `a failed load reports a load error`() = runTest {
        coEvery { roleRepository.getRole("USER") } returns Result.Error(message = "Unauthorized")
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        assertThat(viewModel.uiState.value.error).isEqualTo(RolePermissionsAdminError.LOAD)
        assertThat(viewModel.uiState.value.loadFailed).isTrue()
    }
}
