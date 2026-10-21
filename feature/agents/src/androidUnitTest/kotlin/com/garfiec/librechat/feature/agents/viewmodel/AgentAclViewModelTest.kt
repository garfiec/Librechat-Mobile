package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
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

@OptIn(ExperimentalCoroutinesApi::class)
class AgentAclViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val permissionsRepository = mockk<PermissionsRepository>(relaxed = true)
    private val body = slot<UpdateResourcePermissionsRequest>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { permissionsRepository.getResourceRoles(ResourceType.AGENT) } returns Result.Success(emptyList())
        // A public agent: a grant or revoke that sent `public` would risk turning it off.
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.AGENT, "ag-1") } returns
            Result.Success(ResourcePermissions(public = true, publicAccessRoleId = "agent_viewer"))
        coEvery {
            permissionsRepository.updateResourcePermissions(ResourceType.AGENT, "ag-1", capture(body))
        } returns Result.Success(ResourcePermissions())
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun loaded() = AgentAclViewModel(permissionsRepository).also { it.load("ag-1") }

    @Test
    fun `a grant leaves public access out of the body`() = runTest(testDispatcher) {
        val viewModel = loaded()
        advanceUntilIdle()

        viewModel.grant(Principal(type = "user", id = "u1"), "agent_editor")
        advanceUntilIdle()

        assertThat(body.captured.updated.single().accessRoleId).isEqualTo("agent_editor")
        assertThat(body.captured.public).isNull()
        assertThat(body.captured.publicAccessRoleId).isNull()
    }

    @Test
    fun `a revoke leaves public access out of the body`() = runTest(testDispatcher) {
        val viewModel = loaded()
        advanceUntilIdle()

        viewModel.revoke(Principal(type = "user", id = "u1"))
        advanceUntilIdle()

        assertThat(body.captured.removed.single().id).isEqualTo("u1")
        assertThat(body.captured.public).isNull()
        assertThat(body.captured.publicAccessRoleId).isNull()
    }

    @Test
    fun `the public toggle sends public access`() = runTest(testDispatcher) {
        val viewModel = loaded()
        advanceUntilIdle()

        viewModel.setPublic(enabled = false, accessRoleId = null)
        advanceUntilIdle()

        assertThat(body.captured.public).isFalse()
    }
}
