package com.garfiec.librechat.feature.settings.viewmodel

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The role changing while the page is open. The role is fetched only at session start, so a stale
 * cached role can be replaced by the live one after the page opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelRoleChangeTest : MemoriesViewModelTestBase() {

    @Test
    fun `READ granted while the page is open loads the list without passing through the empty state`() = runTest {
        role.value = memoriesRole("USE" to true, "READ" to false, "OPT_OUT" to true)
        coEvery { memoryRepository.getMemories() } returns page(memory("likes_tea"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.switchOnly).isTrue()
        coVerify(exactly = 0) { memoryRepository.getMemories() }

        val seen = mutableListOf<MemoriesUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(seen) }
        role.value = memoriesRole("USE" to true, "READ" to true, "OPT_OUT" to true)
        advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.getMemories() }
        assertThat(viewModel.state.memories.map { it.key }).containsExactly("likes_tea")
        // The page's empty state: the list allowed, settled, no error and nothing loaded.
        assertThat(seen.filter { !it.listDenied && !it.isFirstLoad && it.loadError == null && it.snapshot == null })
            .isEmpty()
    }

    @Test
    fun `READ granted again after the list loaded does not reload it`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(memory("likes_tea"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        role.value = memoriesRole("USE" to true, "READ" to false, "OPT_OUT" to true)
        advanceUntilIdle()
        role.value = memoriesRole("USE" to true, "READ" to true, "OPT_OUT" to true)
        advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.getMemories() }
        assertThat(viewModel.state.memories.map { it.key }).containsExactly("likes_tea")
    }
}
