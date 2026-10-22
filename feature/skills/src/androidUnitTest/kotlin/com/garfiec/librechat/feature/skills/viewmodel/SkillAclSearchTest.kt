package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The sharing sheet's people search between a keystroke and its answer. */
@OptIn(ExperimentalCoroutinesApi::class)
class SkillAclSearchTest {

    private val testDispatcher = StandardTestDispatcher()
    private val permissionsRepository = mockk<PermissionsRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)

    private val ada = Principal(type = "user", id = "u-ada", name = "Ada")
    private val bob = Principal(type = "user", id = "u-bob", name = "Bob")

    private val sharerWithPicker = UserRolePermissions(
        name = "USER",
        permissions = mapOf(
            "SKILLS" to mapOf("USE" to true, "SHARE" to true),
            "PEOPLE_PICKER" to mapOf("VIEW_USERS" to true),
        ),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(sharerWithPicker)
        coEvery { permissionsRepository.getResourceRoles(ResourceType.SKILL) } returns
            Result.Success(listOf(AccessRole(accessRoleId = "skill_viewer", permissionBits = 1)))
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns
            Result.Success(ResourcePermissions())
        coEvery { permissionsRepository.searchPrincipals("ada", any()) } returns Result.Success(listOf(ada))
        coEvery { permissionsRepository.searchPrincipals("bob", any()) } returns Result.Success(listOf(bob))
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun TestScope.sharing(): SkillAclViewModel {
        val viewModel = SkillAclViewModel(permissionsRepository, roleRepository)
        viewModel.setCanShare("sk-1", true)
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `a changed query drops the last results at once, not when its own answer lands`() = runTest(testDispatcher) {
        val viewModel = sharing()
        viewModel.onSearchQueryChanged("ada")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchResults).containsExactly(ada)

        viewModel.onSearchQueryChanged("bob")
        runCurrent()
        // Inside the debounce: nothing from "ada" stays addable, and it isn't "No one found" either.
        assertThat(viewModel.uiState.value.searchResults).isEmpty()
        assertThat(viewModel.uiState.value.isSearching).isTrue()

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchResults).containsExactly(bob)
        assertThat(viewModel.uiState.value.isSearching).isFalse()
    }

    @Test
    fun `retry searches at once, without the debounce wait`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.searchPrincipals("ada", any()) } returns
            Result.Error(message = "timeout") andThen Result.Success(listOf(ada))
        val viewModel = sharing()
        viewModel.onSearchQueryChanged("ada")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchFailed).isTrue()

        viewModel.retrySearch()
        advanceTimeBy(1)
        runCurrent()

        coVerify(exactly = 2) { permissionsRepository.searchPrincipals("ada", any()) }
        assertThat(viewModel.uiState.value.searchResults).containsExactly(ada)
        assertThat(viewModel.uiState.value.searchFailed).isFalse()
    }
}
