package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.request.UpdateRoleMemoriesRequest
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

/** The role-memories editor: loads a role's five MEMORIES flags, asks before discarding, saves them all. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoleMemoriesAdminViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val ownRole = MutableStateFlow<UserRolePermissions?>(null)

    private fun role(
        name: String = "USER",
        use: Boolean = true,
        create: Boolean = false,
        update: Boolean = true,
        read: Boolean = true,
        optOut: Boolean = false,
    ) = UserRolePermissions(
        name = name,
        permissions = mapOf(
            "MEMORIES" to mapOf("USE" to use, "CREATE" to create, "UPDATE" to update, "READ" to read, "OPT_OUT" to optOut),
            // Another type's flags must not leak into the memories switches.
            "SKILLS" to mapOf("CREATE" to true, "OPT_OUT" to true),
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
    fun `a loaded role shows its five memories flags`() = runTest {
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        assertThat(viewModel.uiState.value.loaded).isTrue()
        assertThat(viewModel.uiState.value.flags)
            .isEqualTo(MemoriesFlags(use = true, create = false, update = true, read = true, optOut = false))
    }

    @Test
    fun `a freshly loaded role leaves without asking`() = runTest {
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
        viewModel.onBackRequested()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `a toggle asks before leaving, and discarding then leaves`() = runTest {
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setOptOut(true)
        viewModel.onBackRequested()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isTrue()
        assertThat(viewModel.uiState.value.exitRequested).isFalse()

        viewModel.discardChanges()
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `keeping the edits closes the prompt and stays`() = runTest {
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.onBackRequested()
        viewModel.dismissDiscardConfirmation()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.exitRequested).isFalse()
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isTrue()
    }

    @Test
    fun `flipping a toggle back is clean again`() = runTest {
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setRead(false)
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isTrue()
        viewModel.setRead(true)

        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
    }

    @Test
    fun `save sends all five flags for the selected role`() = runTest {
        val sent = slot<UpdateRoleMemoriesRequest>()
        coEvery { roleRepository.updateRoleMemories("USER", capture(sent)) } returns Result.Success(role(update = false))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setUpdate(false)
        viewModel.save()

        assertThat(sent.captured).isEqualTo(
            UpdateRoleMemoriesRequest(use = true, create = false, update = false, read = true, optOut = false),
        )
    }

    /** The screen stays open after a save, so what was saved becomes the clean state. */
    @Test
    fun `a save makes the server's flags the clean state`() = runTest {
        // The server answers with something other than what was sent: the switches follow it.
        coEvery { roleRepository.updateRoleMemories("USER", any()) } returns
            Result.Success(role(create = true, optOut = true))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()

        val state = viewModel.uiState.value
        assertThat(state.saved).isTrue()
        assertThat(state.isSaving).isFalse()
        assertThat(state.flags.optOut).isTrue()
        assertThat(state.hasUnsavedChanges).isFalse()
    }

    @Test
    fun `the next change clears the saved line`() = runTest {
        coEvery { roleRepository.updateRoleMemories("USER", any()) } returns Result.Success(role(create = true))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()
        viewModel.setUse(false)

        assertThat(viewModel.uiState.value.saved).isFalse()
    }

    @Test
    fun `a failed save keeps the edits and reports a save error`() = runTest {
        coEvery { roleRepository.updateRoleMemories("USER", any()) } returns Result.Error(message = "Invalid memory permissions.")
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()

        val state = viewModel.uiState.value
        assertThat(state.error).isEqualTo(RolePermissionsAdminError.SAVE)
        assertThat(state.saved).isFalse()
        assertThat(state.flags.create).isTrue()
        assertThat(state.hasUnsavedChanges).isTrue()
    }

    @Test
    fun `a failed load reports a load error and offers no save`() = runTest {
        coEvery { roleRepository.getRole("USER") } returns Result.Error(message = "Unauthorized")
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        assertThat(viewModel.uiState.value.error).isEqualTo(RolePermissionsAdminError.LOAD)
        assertThat(viewModel.uiState.value.canSave).isFalse()
    }

    // Refreshing the admin's own cached role after a save is the repository's job (it applies the
    // PUT's answer inside the call, so leaving the screen can't cancel it): RoleRepositoryImplTest.

    @Test
    fun `a role's late answer does not overwrite the role picked after it`() = runTest {
        val userAnswer = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.getRole("USER") } coAnswers { userAnswer.await() }
        coEvery { roleRepository.getRole("ADMIN") } returns Result.Success(role(name = "ADMIN", create = true, optOut = true))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.selectRole("ADMIN")
        userAnswer.complete(Result.Success(role(name = "USER")))

        val state = viewModel.uiState.value
        assertThat(state.selectedRole).isEqualTo("ADMIN")
        assertThat(state.flags.create).isTrue()
        assertThat(state.flags.optOut).isTrue()
    }

    @Test
    fun `retry after a failed load loads the role again`() = runTest {
        coEvery { roleRepository.getRole("USER") } returns Result.Error(message = "offline")
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)
        assertThat(viewModel.uiState.value.error).isEqualTo(RolePermissionsAdminError.LOAD)

        coEvery { roleRepository.getRole("USER") } returns Result.Success(role(create = true))
        viewModel.retryLoad()

        val state = viewModel.uiState.value
        assertThat(state.error).isNull()
        assertThat(state.loaded).isTrue()
        assertThat(state.flags.create).isTrue()
        // Editable again: the first change offers Save.
        viewModel.setOptOut(true)
        assertThat(viewModel.uiState.value.canSave).isTrue()
        coVerify(exactly = 2) { roleRepository.getRole("USER") }
    }

    @Test
    fun `retry does nothing without a load error`() = runTest {
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.retryLoad()

        coVerify(exactly = 1) { roleRepository.getRole("USER") }
    }

    @Test
    fun `a failed load after a role switch shows none of the previous role's switches`() = runTest {
        // USER has every flag on, so a leftover would show as an on switch under ADMIN.
        coEvery { roleRepository.getRole("USER") } returns
            Result.Success(role(use = true, create = true, update = true, read = true, optOut = true))
        coEvery { roleRepository.getRole("ADMIN") } returns Result.Error(message = "offline")
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.selectRole("ADMIN")

        val state = viewModel.uiState.value
        assertThat(state.selectedRole).isEqualTo("ADMIN")
        assertThat(state.error).isEqualTo(RolePermissionsAdminError.LOAD)
        assertThat(state.flags)
            .isEqualTo(MemoriesFlags(use = false, create = false, update = false, read = false, optOut = false))
        assertThat(state.savedFlags).isNull()
        assertThat(state.hasUnsavedChanges).isFalse()
        // The editor draws switches only while loaded, so this is what hides USER's switches.
        assertThat(state.loaded).isFalse()
        assertThat(state.canSave).isFalse()
    }

    @Test
    fun `a role's first load answering after it was picked again is dropped`() = runTest {
        val firstUserAnswer = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.getRole("USER") } coAnswers { firstUserAnswer.await() }
        coEvery { roleRepository.getRole("ADMIN") } returns Result.Success(role(name = "ADMIN"))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.selectRole("ADMIN")
        coEvery { roleRepository.getRole("USER") } returns Result.Success(role(create = true))
        viewModel.selectRole("USER")
        viewModel.setOptOut(true)
        // The first USER request answers last, with flags the second one has already replaced.
        firstUserAnswer.complete(Result.Success(role(create = false)))

        val state = viewModel.uiState.value
        assertThat(state.flags.create).isTrue()
        assertThat(state.flags.optOut).isTrue()
        assertThat(state.hasUnsavedChanges).isTrue()
    }

    @Test
    fun `picking another role with unsaved switches asks first and loads nothing`() = runTest {
        coEvery { roleRepository.getRole("ADMIN") } returns Result.Success(role(name = "ADMIN", create = true))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.setUpdate(false)
        viewModel.selectRole("ADMIN")

        with(viewModel.uiState.value) {
            assertThat(showDiscardConfirm).isTrue()
            assertThat(pendingRole).isEqualTo("ADMIN")
            assertThat(selectedRole).isEqualTo("USER")
            assertThat(flags.create).isTrue()
            assertThat(flags.update).isFalse()
            assertThat(hasUnsavedChanges).isTrue()
            assertThat(exitRequested).isFalse()
        }
        coVerify(exactly = 0) { roleRepository.getRole("ADMIN") }
    }

    @Test
    fun `keeping the edits on a role switch prompt stays on the role, and a later back still leaves`() = runTest {
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.selectRole("ADMIN")
        viewModel.dismissDiscardConfirmation()

        with(viewModel.uiState.value) {
            assertThat(showDiscardConfirm).isFalse()
            assertThat(pendingRole).isNull()
            assertThat(selectedRole).isEqualTo("USER")
            assertThat(flags.create).isTrue()
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
            Result.Success(role(name = "ADMIN", create = true, optOut = true))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setUse(false)
        viewModel.selectRole("ADMIN")
        viewModel.discardChanges()

        with(viewModel.uiState.value) {
            assertThat(showDiscardConfirm).isFalse()
            assertThat(pendingRole).isNull()
            assertThat(exitRequested).isFalse()
            assertThat(selectedRole).isEqualTo("ADMIN")
            assertThat(flags)
                .isEqualTo(MemoriesFlags(use = true, create = true, update = true, read = true, optOut = true))
            assertThat(hasUnsavedChanges).isFalse()
        }
        coVerify(exactly = 1) { roleRepository.getRole("ADMIN") }
    }

    @Test
    fun `picking another role with no changes switches without asking`() = runTest {
        coEvery { roleRepository.getRole("ADMIN") } returns Result.Success(role(name = "ADMIN"))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.selectRole("ADMIN")

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.selectedRole).isEqualTo("ADMIN")
        coVerify(exactly = 1) { roleRepository.getRole("ADMIN") }
    }

    @Test
    fun `a save landing after a role switch leaves the new role's switches alone`() = runTest {
        val saveAnswer = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.updateRoleMemories("USER", any()) } coAnswers { saveAnswer.await() }
        coEvery { roleRepository.getRole("ADMIN") } returns
            Result.Success(role(name = "ADMIN", use = false, create = true, update = false, read = false))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setOptOut(true)
        viewModel.save()
        // The picker is locked while saving; this is the ViewModel's own guard.
        viewModel.selectRole("ADMIN")
        saveAnswer.complete(Result.Success(role(optOut = true)))

        val state = viewModel.uiState.value
        assertThat(state.selectedRole).isEqualTo("ADMIN")
        assertThat(state.flags)
            .isEqualTo(MemoriesFlags(use = false, create = true, update = false, read = false, optOut = false))
        assertThat(state.saved).isFalse()
        assertThat(state.isSaving).isFalse()
        assertThat(state.hasUnsavedChanges).isFalse()
    }

    @Test
    fun `a save failing after a role switch reports nothing on the new role`() = runTest {
        val saveAnswer = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.updateRoleMemories("USER", any()) } coAnswers { saveAnswer.await() }
        coEvery { roleRepository.getRole("ADMIN") } returns Result.Success(role(name = "ADMIN"))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()
        viewModel.selectRole("ADMIN")
        saveAnswer.complete(Result.Error(message = "offline"))

        val state = viewModel.uiState.value
        assertThat(state.error).isNull()
        assertThat(state.isSaving).isFalse()
        assertThat(state.loaded).isTrue()
    }

    /**
     * Back while the PUT runs. The discard prompt would offer to throw away a change already being
     * sent, and confirming it cancelled the PUT with the screen, leaving the server's state unknown.
     */
    @Test
    fun `back during a save waits for it instead of offering a discard`() = runTest {
        val answer = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.updateRoleMemories("USER", any()) } coAnswers { answer.await() }
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setOptOut(true)
        viewModel.save()
        viewModel.onBackRequested()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.exitRequested).isFalse()

        answer.complete(Result.Success(role(optOut = true)))
        assertThat(viewModel.uiState.value.saved).isTrue()
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `back during a save that fails stays, so the error is seen`() = runTest {
        val answer = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.updateRoleMemories("USER", any()) } coAnswers { answer.await() }
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setOptOut(true)
        viewModel.save()
        viewModel.onBackRequested()
        answer.complete(Result.Error(message = "boom"))

        with(viewModel.uiState.value) {
            assertThat(error).isEqualTo(RolePermissionsAdminError.SAVE)
            assertThat(exitRequested).isFalse()
            assertThat(showDiscardConfirm).isFalse()
        }
        // The next back is an ordinary one: the change is still unsaved, so it asks.
        viewModel.onBackRequested()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isTrue()
    }

    @Test
    fun `the picker lists the server's roles, custom ones included`() = runTest {
        coEvery { roleRepository.listRoleNames() } returns Result.Success(listOf("ADMIN", "USER", "STAFF"))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        assertThat(viewModel.uiState.value.roles).containsExactly("ADMIN", "USER", "STAFF").inOrder()
        assertThat(viewModel.uiState.value.selectedRole).isEqualTo("USER")
    }

    @Test
    fun `the picker falls back to USER and ADMIN when the role list fails or is empty`() = runTest {
        coEvery { roleRepository.listRoleNames() } returns Result.Error(message = "Forbidden")
        assertThat(RoleMemoriesAdminViewModel(roleRepository).uiState.value.roles).containsExactly("USER", "ADMIN").inOrder()

        coEvery { roleRepository.listRoleNames() } returns Result.Success(emptyList())
        assertThat(RoleMemoriesAdminViewModel(roleRepository).uiState.value.roles).containsExactly("USER", "ADMIN").inOrder()
    }

    @Test
    fun `a custom role loads and saves under its own name`() = runTest {
        coEvery { roleRepository.listRoleNames() } returns Result.Success(listOf("USER", "ADMIN", "STAFF"))
        coEvery { roleRepository.getRole("STAFF") } returns Result.Success(UserRolePermissions(name = "STAFF"))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.selectRole("STAFF")
        viewModel.setUse(true)
        viewModel.save()

        coVerify(exactly = 1) { roleRepository.updateRoleMemories("STAFF", any()) }
    }

    /** Nothing differs from the loaded role, so there is nothing to send. */
    @Test
    fun `Save is off until a switch differs from the loaded role`() = runTest {
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)
        assertThat(viewModel.uiState.value.loaded).isTrue()
        assertThat(viewModel.uiState.value.canSave).isFalse()

        viewModel.save()
        coVerify(exactly = 0) { roleRepository.updateRoleMemories(any(), any()) }

        viewModel.setCreate(true)
        assertThat(viewModel.uiState.value.canSave).isTrue()

        // Back to what the server holds: off again.
        viewModel.setCreate(false)
        assertThat(viewModel.uiState.value.canSave).isFalse()
    }

    @Test
    fun `Save is off again once a save lands`() = runTest {
        coEvery { roleRepository.updateRoleMemories("USER", any()) } returns Result.Success(role(create = true))
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()

        assertThat(viewModel.uiState.value.saved).isTrue()
        assertThat(viewModel.uiState.value.canSave).isFalse()
    }

    /** A refused save keeps the change, so Save stays on as its retry. */
    @Test
    fun `Save stays on after a failed save`() = runTest {
        coEvery { roleRepository.updateRoleMemories("USER", any()) } returns Result.Error(message = "offline")
        val viewModel = RoleMemoriesAdminViewModel(roleRepository)

        viewModel.setCreate(true)
        viewModel.save()

        assertThat(viewModel.uiState.value.error).isEqualTo(RolePermissionsAdminError.SAVE)
        assertThat(viewModel.uiState.value.canSave).isTrue()
    }

    // A save of the admin's own role refreshes the cached permissions in the repository, whatever
    // the screen did meanwhile: RoleRepositoryImplTest.
}
