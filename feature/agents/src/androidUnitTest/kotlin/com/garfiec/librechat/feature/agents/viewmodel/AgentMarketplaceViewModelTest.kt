package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.PaginatedAgents
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test

/** The load pipeline: first load, paging, refresh, the quiet reload, cards. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentMarketplaceViewModelTest : AgentMarketplaceViewModelTestBase() {

    // --- First load ---

    @Test
    fun `initial state loads agents`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agents).hasSize(3)
        assertThat(state.isLoading).isFalse()
        assertThat(state.hasMore).isTrue()
        assertThat(state.nextCursor).isEqualTo("c1")
    }

    @Test
    fun `the first frame is loading, so the screen opens on placeholders`() = runTest {
        viewModel = createViewModel()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
        assertThat(viewModel.uiState.value.agents).isEmpty()
    }

    @Test
    fun `initial state loads categories`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.categories).hasSize(3)
        assertThat(state.categories.map { it.value }).containsExactly("coding", "writing", "education")
    }

    @Test
    fun `agents display data has correct names`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val names = viewModel.uiState.value.agents.map { it.name }
        assertThat(names).containsExactly("Coding Assistant", "Writer", "Math Tutor")
    }

    @Test
    fun `an agent without a name is left for the screen to label`() = runTest {
        // "Unnamed agent" is a translated string the card resolves; the VM carries no English.
        stubPage(null) returns page(listOf(Agent(id = "agent-x", name = null), Agent(id = "agent-y", name = "  ")))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agents.map { it.name }).containsExactly(null, null)
    }

    @Test
    fun `a failed first load is the error page, and Retry recovers`() = runTest {
        stubPage(null) returns Result.Error(message = "Network error") andThen page(testAgents)

        viewModel = createViewModel()
        advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertThat(failed.loadError).isEqualTo(AgentListError("Network error"))
        assertThat(failed.agents).isEmpty()
        assertThat(failed.isLoading).isFalse()

        viewModel.retry()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadError).isNull()
        assertThat(viewModel.uiState.value.agents).hasSize(3)
    }

    @Test
    fun `categories failure is non-critical and does not show error`() = runTest {
        coEvery { agentRepository.getAgentCategories() } returns
            Result.Error(message = "Categories unavailable")

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadError).isNull()
        assertThat(viewModel.uiState.value.categories).isEmpty()
        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.All)
        assertThat(viewModel.uiState.value.agents).hasSize(3)
    }

    // --- Paging ---

    @Test
    fun `loadMore fetches next page and appends agents`() = runTest {
        stubPage("c1") returns page(listOf(Agent(id = "agent-4", name = "Translator")))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agents).hasSize(4)
        assertThat(state.nextCursor).isNull()
        assertThat(state.hasMore).isFalse()
        assertThat(state.isLoadingMore).isFalse()
    }

    @Test
    fun `loadMore reads by the previous page's after`() = runTest {
        stubPage("c1") returns page(listOf(Agent(id = "agent-4")), next = "c2")
        stubPage("c2") returns page(listOf(Agent(id = "agent-5")))

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        coVerifyOrder {
            agentRepository.getAgentsPaginated(20, null, null, null, false, null)
            agentRepository.getAgentsPaginated(20, "c1", null, null, false, null)
            agentRepository.getAgentsPaginated(20, "c2", null, null, false, null)
        }
        assertThat(viewModel.uiState.value.agents.map { it.id })
            .containsExactly("agent-1", "agent-2", "agent-3", "agent-4", "agent-5").inOrder()
        assertThat(viewModel.uiState.value.hasMore).isFalse()
    }

    @Test
    fun `a filter change reads from the first page again`() = runTest {
        stubPage("c1") returns page(listOf(Agent(id = "agent-4")), next = "c2")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.nextCursor).isEqualTo("c2")

        viewModel.onChipSelected(AgentChip.Category("coding"))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(any(), cursor = null, any(), category = "coding", any(), any())
        }
        coVerify(exactly = 0) {
            agentRepository.getAgentsPaginated(any(), cursor = "c2", any(), any(), any(), any())
        }
        assertThat(viewModel.uiState.value.nextCursor).isEqualTo("c1")
    }

    @Test
    fun `a page still loading when the chip changes is dropped`() = runTest {
        coEvery { agentRepository.getAgentsPaginated(any(), "c1", any(), any(), any(), any()) } coAnswers {
            delay(5_000)
            page(listOf(Agent(id = "old-page-2")))
        }
        coEvery { agentRepository.getAgentsPaginated(any(), null, any(), "coding", any(), any()) } returns
            page(listOf(Agent(id = "coder")))

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        runCurrent()
        assertThat(viewModel.uiState.value.isLoadingMore).isTrue()

        viewModel.onChipSelected(AgentChip.Category("coding"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agents.map { it.id }).containsExactly("coder")
        assertThat(viewModel.uiState.value.isLoadingMore).isFalse()
    }

    @Test
    fun `loadMore without a cursor reads nothing`() = runTest {
        // has_more with no `after` can only re-read the first page; the repository reports it as
        // the end, but a page that says more without a cursor must still not be re-read.
        stubPage(null) returns Result.Success(PaginatedAgents(testAgents, hasMore = true, nextCursor = null))

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `first page drops repeated agents`() = runTest {
        // The grid keys cards by id, so a repeat would crash it (#541).
        stubPage(null) returns page(testAgents + testAgents[0])

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agents.map { it.id })
            .containsExactly("agent-1", "agent-2", "agent-3").inOrder()
    }

    @Test
    fun `refresh drops repeated agents`() = runTest {
        stubPage(null) returns page(testAgents, next = "c1") andThen
            page(testAgents + testAgents[1], next = "c1")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agents.map { it.id })
            .containsExactly("agent-1", "agent-2", "agent-3").inOrder()
    }

    @Test
    fun `loadMore drops an agent repeated across the page boundary`() = runTest {
        stubPage("c1") returns page(listOf(testAgents[2], Agent(id = "agent-4")))

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agents.map { it.id })
            .containsExactly("agent-1", "agent-2", "agent-3", "agent-4").inOrder()
    }

    @Test
    fun `loadMore is no-op when hasMore is false`() = runTest {
        stubPage(null) returns page(testAgents)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        // Only the first page is read.
        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed page stops paging until Retry`() = runTest {
        stubPage("c1") returns Result.Error(message = "Load more failed") andThen page(listOf(Agent(id = "agent-4")))

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertThat(failed.loadMoreFailed).isTrue()
        assertThat(failed.isLoadingMore).isFalse()
        assertThat(failed.loadError).isNull()
        assertThat(failed.agents).hasSize(3)

        // The scroll trigger re-arms as soon as the spinner goes; it must not read again.
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()
        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), "c1", any(), any(), any(), any()) }

        viewModel.retryLoadMore()
        advanceUntilIdle()

        coVerify(exactly = 2) { agentRepository.getAgentsPaginated(any(), "c1", any(), any(), any(), any()) }
        assertThat(viewModel.uiState.value.loadMoreFailed).isFalse()
        assertThat(viewModel.uiState.value.agents).hasSize(4)
    }

    // --- Refresh and return ---

    @Test
    fun `refresh reads from the first page again`() = runTest {
        stubPage("c1") returns page(listOf(Agent(id = "agent-4")), next = "c2")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isRefreshing).isFalse()
        coVerify(exactly = 2) { agentRepository.getAgentsPaginated(any(), cursor = null, any(), any(), any(), any()) }
        assertThat(state.nextCursor).isEqualTo("c1")
        assertThat(state.agents.map { it.id }).containsExactly("agent-1", "agent-2", "agent-3").inOrder()
    }

    @Test
    fun `a failed refresh keeps the list and reports the error`() = runTest {
        stubPage(null) returns page(testAgents, next = "c1") andThen
            Result.Error(message = "Refresh failed")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.loadError).isEqualTo(AgentListError("Refresh failed"))
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.agents).hasSize(3)

        viewModel.dismissError()
        assertThat(viewModel.uiState.value.loadError).isNull()
    }

    @Test
    fun `the quiet reload on return keeps as many agents as were loaded, without the spinner`() = runTest {
        stubPage("c1") returns page(listOf(Agent(id = "agent-4")), next = "c2")
        stubPage("c2") returns page(listOf(Agent(id = "agent-5")))

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.agents).hasSize(4)

        viewModel.refreshOnReturn()
        runCurrent()
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        advanceUntilIdle()

        // Two pages covered the four that were loaded; the third is left for scrolling.
        val state = viewModel.uiState.value
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.agents.map { it.id }).containsExactly("agent-1", "agent-2", "agent-3", "agent-4").inOrder()
        assertThat(state.nextCursor).isEqualTo("c2")
        coVerify(exactly = 0) { agentRepository.getAgentsPaginated(any(), "c2", any(), any(), any(), any()) }
    }

    @Test
    fun `the quiet reload walks the cursor until it covers what was loaded`() = runTest {
        val first = (1..20).map { Agent(id = "a$it") }
        val second = (21..40).map { Agent(id = "a$it") }
        val third = (41..60).map { Agent(id = "a$it") }
        stubPage(null) returns page(first, next = "c1")
        stubPage("c1") returns page(second, next = "c2")
        stubPage("c2") returns page(third, next = "c3")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.agents).hasSize(40)

        viewModel.refreshOnReturn()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agents).hasSize(40)
        assertThat(state.nextCursor).isEqualTo("c2")
        coVerify(exactly = 2) { agentRepository.getAgentsPaginated(any(), "c1", any(), any(), any(), any()) }
        coVerify(exactly = 0) { agentRepository.getAgentsPaginated(any(), "c2", any(), any(), any(), any()) }
    }

    @Test
    fun `a failed quiet reload keeps the list and stays silent`() = runTest {
        stubPage(null) returns page(testAgents, next = "c1") andThen Result.Error(message = "offline")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agents).hasSize(3)
        assertThat(state.loadError).isNull()
    }

    @Test
    fun `a quiet reload that succeeds clears a standing error`() = runTest {
        stubPage(null) returns Result.Error(message = "offline") andThen page(testAgents)

        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadError).isNotNull()

        viewModel.refreshOnReturn()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadError).isNull()
        assertThat(viewModel.uiState.value.agents).hasSize(3)
    }

    @Test
    fun `a failed load for a new chip doesn't leave the old chip's agents standing`() = runTest {
        coEvery { agentRepository.getAgentsPaginated(any(), null, any(), "coding", any(), any()) } returns
            Result.Error(message = "boom")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onChipSelected(AgentChip.Category("coding"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agents).isEmpty()
        assertThat(state.loadError).isEqualTo(AgentListError("boom"))
        assertThat(state.shownQuery).isEqualTo(AgentListQuery(AgentChip.Category("coding"), ""))
    }

    // --- Cards ---

    @Test
    fun `cards carry plain-text descriptions, the category, the Top pick mark and a contact`() = runTest {
        val agent = Agent(
            id = "agent-h",
            name = "SQL Helper",
            description = "<p>Writes <b>read-only</b> queries &amp; reports.</p>",
            category = "code",
            isPromoted = true,
            author = "6ad0e1a7db029854749d8540",
            ownerContact = buildJsonObject { put("name", JsonPrimitive("Data Platform team")) },
        )
        stubPage(null) returns page(listOf(agent))

        viewModel = createViewModel()
        advanceUntilIdle()

        val card = viewModel.uiState.value.agents.single()
        assertThat(card.description).isEqualTo("Writes read-only queries & reports.")
        assertThat(card.categoryValue).isEqualTo("code")
        assertThat(card.isPromoted).isTrue()
        assertThat(card.contactName).isEqualTo("Data Platform team")
    }
}
