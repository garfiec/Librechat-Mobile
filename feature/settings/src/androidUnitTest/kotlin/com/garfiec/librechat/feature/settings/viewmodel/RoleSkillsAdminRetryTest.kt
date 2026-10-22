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
 * A role whose load failed draws no switches, and picking the same role again is a no-op, so the
 * screen's Retry is the only way back to an editable screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoleSkillsAdminRetryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val roleRepository = mockk<RoleRepository>(relaxed = true)

    private fun role(name: String = "USER", use: Boolean) = UserRolePermissions(
        name = name,
        permissions = mapOf("SKILLS" to mapOf("USE" to use)),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `Retry after a failed load loads the role`() = runTest {
        coEvery { roleRepository.getRole("USER") } returns Result.Error(kind = FailureKind.Network)
        val viewModel = RoleSkillsAdminViewModel(roleRepository)
        assertThat(viewModel.uiState.value.loadFailed).isTrue()
        assertThat(viewModel.uiState.value.loaded).isFalse()

        coEvery { roleRepository.getRole("USER") } returns Result.Success(role(use = true))
        viewModel.retryLoad()

        with(viewModel.uiState.value) {
            assertThat(loaded).isTrue()
            assertThat(loadFailed).isFalse()
            assertThat(error).isNull()
            assertThat(use).isTrue()
        }
    }

    @Test
    fun `Retry does nothing after a save failure, keeping the unsaved switches`() = runTest {
        coEvery { roleRepository.getRole("USER") } returns Result.Success(role(use = true))
        coEvery { roleRepository.updateRoleSkills(any(), any()) } returns Result.Error(kind = FailureKind.Network)
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.setUse(false)
        viewModel.save()
        assertThat(viewModel.uiState.value.error).isNotNull()
        assertThat(viewModel.uiState.value.loadFailed).isFalse()
        viewModel.retryLoad()

        coVerify(exactly = 1) { roleRepository.getRole("USER") }
        assertThat(viewModel.uiState.value.use).isFalse()
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isTrue()
    }

    @Test
    fun `Retry while a load runs sends nothing more`() = runTest {
        coEvery { roleRepository.getRole("USER") } returns Result.Error(kind = FailureKind.Network)
        val viewModel = RoleSkillsAdminViewModel(roleRepository)
        val gate = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.getRole("USER") } coAnswers { gate.await() }

        viewModel.retryLoad()
        viewModel.retryLoad()
        gate.complete(Result.Success(role(use = true)))

        coVerify(exactly = 2) { roleRepository.getRole("USER") }
        assertThat(viewModel.uiState.value.loaded).isTrue()
    }

    @Test
    fun `a load that answers after another role was picked is dropped`() = runTest {
        val userGate = CompletableDeferred<Result<UserRolePermissions>>()
        coEvery { roleRepository.getRole("USER") } coAnswers { userGate.await() }
        coEvery { roleRepository.getRole("ADMIN") } returns Result.Success(role(name = "ADMIN", use = false))
        val viewModel = RoleSkillsAdminViewModel(roleRepository)

        viewModel.selectRole("ADMIN")
        userGate.complete(Result.Success(role(use = true)))

        with(viewModel.uiState.value) {
            assertThat(selectedRole).isEqualTo("ADMIN")
            assertThat(loaded).isTrue()
            assertThat(use).isFalse()
        }
    }
}
