package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.toSafeError
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Sort and "My agents" (v0.8.9-rc1): gated on the server, reset the cursor, recover a 409 once. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentMarketplaceSortTest : AgentMarketplaceViewModelTestBase() {

    private fun mismatch(): Result.Error = ApiException(
        statusCode = 409,
        message = "cursor_ordering_mismatch",
        body = """{"error":"cursor_ordering_mismatch"}""",
    ).toSafeError()

    @Test
    fun `below rc1 the row is hidden and neither param is sent`() = runTest(testDispatcher) {
        versionFlow.value = "0.8.8"
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.sortAvailable).isFalse()
        coVerify {
            agentRepository.getAgentsPaginated(any(), null, any(), any(), any(), any(), sort = null, mine = false)
        }
        coVerify(exactly = 0) {
            agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any(), sort = "newest", mine = any())
        }
    }

    @Test
    fun `on rc1 the first read asks for newest first`() = runTest(testDispatcher) {
        versionFlow.value = "0.8.9-rc1"
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.sortAvailable).isTrue()
        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(any(), null, any(), any(), any(), any(), sort = "newest", mine = false)
        }
    }

    @Test
    fun `a new order reloads from page one`() = runTest(testDispatcher) {
        versionFlow.value = "0.8.9-rc1"
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSortSelected(AgentSort.POPULAR)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.sort).isEqualTo(AgentSort.POPULAR)
        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(any(), null, any(), any(), any(), any(), sort = "popular", mine = false)
        }
        assertThat(viewModel.uiState.value.shownQuery?.sort).isEqualTo(AgentSort.POPULAR)
    }

    @Test
    fun `my agents under Top picks moves to All`() = runTest(testDispatcher) {
        versionFlow.value = "0.8.9-rc1"
        coEvery { agentRepository.getAgentCategories() } returns Result.Success(categoriesWithSentinels)
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.TopPicks)

        viewModel.onMineToggled(true)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.All)
        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(any(), null, any(), any(), promoted = false, any(), sort = "newest", mine = true)
        }
    }

    @Test
    fun `my agents with nothing authored is its own empty state`() = runTest(testDispatcher) {
        versionFlow.value = "0.8.9-rc1"
        viewModel = createViewModel()
        advanceUntilIdle()
        coEvery {
            agentRepository.getAgentsPaginated(any(), null, any(), any(), any(), any(), any(), mine = true)
        } returns page(emptyList())

        viewModel.onMineToggled(true)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.listState()).isEqualTo(AgentListState.MineEmpty)
        assertThat(viewModel.uiState.value.showsNewAgentFab).isFalse()
    }

    @Test
    fun `a cursor ordering mismatch restarts the list once, then fails the page`() = runTest(testDispatcher) {
        versionFlow.value = "0.8.9-rc1"
        stubPage("c1") returns mismatch()
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        // Restarted from page one rather than failing the footer.
        coVerify(exactly = 2) { agentRepository.getAgentsPaginated(any(), null, any(), any(), any(), any(), any(), any()) }
        assertThat(viewModel.uiState.value.loadMoreFailed).isFalse()

        viewModel.loadMore()
        advanceUntilIdle()

        // The second mismatch for the same query is the ordinary load-more error.
        coVerify(exactly = 2) { agentRepository.getAgentsPaginated(any(), null, any(), any(), any(), any(), any(), any()) }
        assertThat(viewModel.uiState.value.loadMoreFailed).isTrue()
    }
}
