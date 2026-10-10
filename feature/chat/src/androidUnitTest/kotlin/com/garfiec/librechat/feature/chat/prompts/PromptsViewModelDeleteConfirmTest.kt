package com.garfiec.librechat.feature.chat.prompts

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.model.Prompt
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A prompt group delete is a server-side hard cascade over every version with no restore (#316),
 * so the trash icon only asks; the DELETE is issued by the dialog's confirm and nothing else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptsViewModelDeleteConfirmTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val promptRepository = mockk<PromptRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val permissionGate = mockk<PermissionGate>(relaxed = true)

    private val group = PromptGroup(
        id = "g-1",
        name = "First",
        author = "author-1",
        authorName = "Author",
        productionId = "g-1-p",
        prompts = listOf(
            Prompt(id = "g-1-p", groupId = "g-1", author = "author-1", prompt = "body", type = "text"),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        every { promptRepository.revision } returns MutableStateFlow(0L)
        coEvery { permissionGate.awaitRole() } returns UserRolePermissions(name = "USER")
        coEvery { promptRepository.getAllGroups() } returns Result.Success(listOf(group))
        coEvery { promptRepository.getGroup(any()) } returns Result.Success(group)
        coEvery { promptRepository.getPromptsByGroupId(any()) } returns Result.Success(emptyList())
        coEvery { promptRepository.delete(any()) } returns Result.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun openDetail(): PromptsViewModel {
        val viewModel = PromptsViewModel(promptRepository, roleRepository, permissionGate)
        viewModel.selectGroup("g-1")
        assertNotNull(viewModel.uiState.value.selectedGroup)
        return viewModel
    }

    @Test
    fun theTrashIconAloneDeletesNothing() = runTest(testDispatcher) {
        val viewModel = openDetail()

        viewModel.requestDeleteGroup("g-1")
        advanceUntilIdle()

        coVerify(exactly = 0) { promptRepository.delete(any()) }
        assertEquals("g-1", viewModel.uiState.value.pendingDeleteGroupId)
        assertNotNull(viewModel.uiState.value.selectedGroup)
    }

    @Test
    fun cancellingLeavesThePromptAndTheDetailView() = runTest(testDispatcher) {
        val viewModel = openDetail()

        viewModel.requestDeleteGroup("g-1")
        viewModel.dismissDeleteGroup()
        advanceUntilIdle()

        coVerify(exactly = 0) { promptRepository.delete(any()) }
        assertNull(viewModel.uiState.value.pendingDeleteGroupId)
        assertNotNull(viewModel.uiState.value.selectedGroup)
    }

    @Test
    fun confirmingDeletesExactlyTheRequestedGroup() = runTest(testDispatcher) {
        val viewModel = openDetail()

        viewModel.requestDeleteGroup("g-1")
        viewModel.confirmDeleteGroup()
        advanceUntilIdle()

        coVerify(exactly = 1) { promptRepository.delete("g-1") }
        assertNull(viewModel.uiState.value.pendingDeleteGroupId)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertNull(viewModel.uiState.value.selectedGroup)
    }

    @Test
    fun confirmWithNothingPendingIsANoOp() = runTest(testDispatcher) {
        val viewModel = openDetail()

        viewModel.confirmDeleteGroup()
        advanceUntilIdle()

        coVerify(exactly = 0) { promptRepository.delete(any()) }
    }

    @Test
    fun leavingTheDetailViewDropsThePendingConfirmation() = runTest(testDispatcher) {
        val viewModel = openDetail()

        viewModel.requestDeleteGroup("g-1")
        viewModel.clearSelectedGroup()
        viewModel.confirmDeleteGroup()
        advanceUntilIdle()

        coVerify(exactly = 0) { promptRepository.delete(any()) }
        assertNull(viewModel.uiState.value.pendingDeleteGroupId)
    }

    @Test
    fun aSecondRequestWhileTheDeleteIsInFlightIsIgnored() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Result<Unit>>()
        coEvery { promptRepository.delete(any()) } coAnswers { gate.await() }
        val viewModel = openDetail()

        viewModel.requestDeleteGroup("g-1")
        viewModel.confirmDeleteGroup()
        assertTrue(viewModel.uiState.value.isDeleting)

        viewModel.requestDeleteGroup("g-1")
        viewModel.confirmDeleteGroup()
        assertNull(viewModel.uiState.value.pendingDeleteGroupId)

        gate.complete(Result.Success(Unit))
        advanceUntilIdle()

        coVerify(exactly = 1) { promptRepository.delete(any()) }
        assertFalse(viewModel.uiState.value.isDeleting)
    }

    @Test
    fun aDeleteLandingAfterOpeningAnotherPromptLeavesThatPromptOpen() = runTest(testDispatcher) {
        val other = group.copy(id = "g-2", name = "Second", productionId = "g-2-p")
        coEvery { promptRepository.getGroup("g-2") } returns Result.Success(other)
        val gate = CompletableDeferred<Result<Unit>>()
        coEvery { promptRepository.delete(any()) } coAnswers { gate.await() }
        val viewModel = openDetail()

        viewModel.requestDeleteGroup("g-1")
        viewModel.confirmDeleteGroup()
        viewModel.clearSelectedGroup()
        viewModel.selectGroup("g-2")
        gate.complete(Result.Success(Unit))
        advanceUntilIdle()

        assertEquals("g-2", viewModel.uiState.value.selectedGroup?.id)
    }
}
