package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.SearchCapabilityRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.share.ShareLinkController
import com.garfiec.librechat.core.data.share.ShareLinkControllerFactory
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.response.ForkConversationResponse
import com.garfiec.librechat.core.model.response.ShareLinkLookup
import com.garfiec.librechat.core.model.response.SharedConversationSnapshot
import com.garfiec.librechat.core.model.response.SharedLinksResponse
import com.garfiec.librechat.core.model.share.MAX_SHARED_LINK_SEARCH_LENGTH
import com.garfiec.librechat.core.model.share.SharedLinksSort
import com.garfiec.librechat.feature.settings.model.SharedLinkDisplayData
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedLinksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = FakeShareRepository()
    private val searchEnabled = MutableStateFlow<Boolean?>(null)
    private val searchCapability = object : SearchCapabilityRepository {
        override val serverSearchEnabled = searchEnabled
        override suspend fun refresh() = Unit
    }
    private val controller = mockk<ShareLinkController>(relaxed = true)
    private val controllerFactory = mockk<ShareLinkControllerFactory> {
        every { create(any()) } returns controller
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SharedLinksViewModel(repository, controllerFactory, searchCapability)

    private fun ids(viewModel: SharedLinksViewModel) = viewModel.uiState.value.links.map { it.shareId }

    @Test
    fun `opens loading, then shows the newest-first first page`() = runTest {
        repository.page(null) { page("a", "b", next = "n1") }
        val viewModel = createViewModel()
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(ids(viewModel)).containsExactly("a", "b").inOrder()
        assertThat(state.hasNextPage).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(repository.calls.single()).isEqualTo(Call(null, SharedLinksSort.NEWEST, ""))
    }

    @Test
    fun `rows carry the conversation, the share url and a null title when untitled`() = runTest {
        repository.page(null) {
            Result.Success(
                SharedLinksResponse(
                    links = listOf(
                        SharedLink(conversationId = "c1", shareId = "s1", title = "  ", createdAt = "2026-10-01T00:00:00Z"),
                        // No id: can't be opened, copied or deleted, so it isn't shown.
                        SharedLink(conversationId = "c2", shareId = null, title = "Ghost"),
                    ),
                ),
            )
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.links).containsExactly(
            SharedLinkDisplayData(
                shareId = "s1",
                conversationId = "c1",
                title = null,
                createdAt = "2026-10-01T00:00:00Z",
                url = "https://chat.example.com/share/s1",
            ),
        )
    }

    @Test
    fun `loadMore appends the next page with the same sort and search`() = runTest {
        repository.page(null) { page("a", "b", next = "n1") }
        repository.page("n1") { page("c") }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(ids(viewModel)).containsExactly("a", "b", "c").inOrder()
        assertThat(viewModel.uiState.value.hasNextPage).isFalse()
        assertThat(repository.calls.last()).isEqualTo(Call("n1", SharedLinksSort.NEWEST, ""))
    }

    @Test
    fun `a sort change starts again from the first page and drops the old sort's page`() = runTest {
        repository.page(null) { page("a", "b", next = "n1") }
        val viewModel = createViewModel()
        advanceUntilIdle()

        val stale = CompletableDeferred<Result<SharedLinksResponse>>()
        repository.page("n1") { stale.await() }
        viewModel.loadMore()
        runCurrent()

        repository.pageBy(null) { sort, _ -> if (sort == SharedLinksSort.TITLE) page("z") else page("a", "b") }
        viewModel.setSort(SharedLinksSort.TITLE)
        advanceUntilIdle()
        stale.complete(page("stale"))
        advanceUntilIdle()

        assertThat(ids(viewModel)).containsExactly("z")
        assertThat(viewModel.uiState.value.sort).isEqualTo(SharedLinksSort.TITLE)
        assertThat(repository.calls.last()).isEqualTo(Call(null, SharedLinksSort.TITLE, ""))
    }

    @Test
    fun `search waits out the debounce, is capped, and starts from the first page`() = runTest {
        searchEnabled.value = true
        repository.pageBy(null) { _, search -> if (search.isEmpty()) page("a", next = "n1") else page("hit") }
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchEnabled).isTrue()

        val long = " " + "x".repeat(MAX_SHARED_LINK_SEARCH_LENGTH + 10)
        viewModel.onSearchQueryChange(long)
        advanceTimeBy(299)
        runCurrent()
        assertThat(repository.calls).hasSize(1)

        advanceUntilIdle()
        val query = "x".repeat(MAX_SHARED_LINK_SEARCH_LENGTH - 1)
        assertThat(viewModel.uiState.value.searchQuery).hasLength(MAX_SHARED_LINK_SEARCH_LENGTH)
        assertThat(repository.calls.last()).isEqualTo(Call(null, SharedLinksSort.NEWEST, query))
        assertThat(ids(viewModel)).containsExactly("hit")
        assertThat(viewModel.uiState.value.shownQuery).isEqualTo(query)
    }

    @Test
    fun `clear search reloads the unfiltered list`() = runTest {
        searchEnabled.value = true
        repository.pageBy(null) { _, search -> if (search.isEmpty()) page("a") else page() }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSearchQueryChange("nothing")
        advanceUntilIdle()
        assertThat(ids(viewModel)).isEmpty()
        assertThat(viewModel.uiState.value.shownQuery).isEqualTo("nothing")

        viewModel.clearSearch()
        advanceUntilIdle()

        assertThat(ids(viewModel)).containsExactly("a")
        assertThat(viewModel.uiState.value.shownQuery).isEmpty()
    }

    @Test
    fun `search turning off clears the query and reloads`() = runTest {
        searchEnabled.value = true
        repository.pageBy(null) { _, search -> if (search.isEmpty()) page("a") else page("hit") }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onSearchQueryChange("hit")
        advanceUntilIdle()

        searchEnabled.value = false
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.searchEnabled).isFalse()
        assertThat(state.searchQuery).isEmpty()
        assertThat(ids(viewModel)).containsExactly("a")
    }

    @Test
    fun `search stays hidden while the server's search is unknown`() = runTest {
        repository.page(null) { page("a") }
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.searchEnabled).isFalse()
    }

    @Test
    fun `a failed first load is an error state, and Retry loads again`() = runTest {
        repository.page(null) { Result.Error(exception = ApiException(statusCode = 500, message = "boom")) }
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadFailed).isTrue()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(ids(viewModel)).isEmpty()

        repository.page(null) { page("a") }
        viewModel.retry()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadFailed).isFalse()
        assertThat(ids(viewModel)).containsExactly("a")
    }

    @Test
    fun `a failed next page stops loading on scroll until retried`() = runTest {
        repository.page(null) { page("a", "b", next = "n1") }
        repository.page("n1") { Result.Error(exception = ApiException(statusCode = 500, message = "boom")) }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertThat(failed.loadMoreFailed).isTrue()
        assertThat(failed.shouldLoadMore(lastVisibleIndex = 1, totalItems = 2)).isFalse()

        repository.page("n1") { page("c") }
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadMoreFailed).isFalse()
        assertThat(ids(viewModel)).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `a failed pull to refresh keeps the rows and says so`() = runTest {
        repository.page(null) { page("a") }
        val viewModel = createViewModel()
        advanceUntilIdle()

        repository.page(null) { Result.Error(exception = ApiException(statusCode = 500, message = "boom")) }
        viewModel.refresh()
        advanceUntilIdle()

        assertThat(ids(viewModel)).containsExactly("a")
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
        assertThat(viewModel.messages.first()).isEqualTo(SharedLinksMessage.REFRESH_FAILED)
    }

    @Test
    fun `a return to the page reloads quietly, keeping as many rows as were loaded`() = runTest {
        val first = (1..25).map { "p$it" }
        repository.page(null) { page(*first.toTypedArray(), next = "n1") }
        repository.page("n1") { page("q1", "q2", next = "n2") }
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(ids(viewModel)).hasSize(27)
        repository.calls.clear()

        viewModel.refreshOnReturn()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        advanceUntilIdle()

        assertThat(repository.calls.map { it.cursor }).containsExactly(null, "n1").inOrder()
        assertThat(ids(viewModel)).hasSize(27)
        assertThat(viewModel.uiState.value.hasNextPage).isTrue()
    }

    @Test
    fun `a return while the first page is still loading doesn't load it twice`() = runTest {
        repository.page(null) { page("a") }
        val viewModel = createViewModel()
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        assertThat(repository.calls).hasSize(1)
    }

    @Test
    fun `a change to the user's links anywhere reloads the list`() = runTest {
        repository.page(null) { page("a") }
        val viewModel = createViewModel()
        advanceUntilIdle()

        repository.page(null) { page("new", "a") }
        repository.revision.value += 1
        advanceUntilIdle()

        assertThat(ids(viewModel)).containsExactly("new", "a").inOrder()
    }

    @Test
    fun `delete removes the row`() = runTest {
        repository.page(null) { page("a", "b") }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.delete("a")
        advanceUntilIdle()

        assertThat(repository.deleted).containsExactly("a")
        assertThat(ids(viewModel)).containsExactly("b")
    }

    @Test
    fun `a failed delete keeps the row and says so`() = runTest {
        repository.page(null) { page("a") }
        repository.deleteResult = Result.Error(exception = ApiException(statusCode = 500, message = "boom"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.delete("a")
        advanceUntilIdle()

        assertThat(ids(viewModel)).containsExactly("a")
        assertThat(viewModel.messages.first()).isEqualTo(SharedLinksMessage.DELETE_FAILED)
    }

    @Test
    fun `a card opens the share sheet focused on its link`() = runTest {
        repository.page(null) { page("a") }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openLink(viewModel.uiState.value.links.single())

        verify { controller.open("c-a", null, "a") }
    }

    @Test
    fun `load more asks only near the end of a settled list with more to load`() {
        val links = listOf(SharedLinkDisplayData("a", "c", null, null, "u"))
        val ready = SharedLinksUiState(links = links, isLoading = false, hasNextPage = true)

        assertThat(ready.shouldLoadMore(lastVisibleIndex = 9, totalItems = 12)).isTrue()
        assertThat(ready.shouldLoadMore(lastVisibleIndex = 8, totalItems = 12)).isFalse()
        assertThat(ready.copy(hasNextPage = false).shouldLoadMore(9, 12)).isFalse()
        assertThat(ready.copy(isLoading = true).shouldLoadMore(9, 12)).isFalse()
        assertThat(ready.copy(isRefreshing = true).shouldLoadMore(9, 12)).isFalse()
        assertThat(ready.copy(isLoadingMore = true).shouldLoadMore(9, 12)).isFalse()
        assertThat(ready.copy(loadMoreFailed = true).shouldLoadMore(9, 12)).isFalse()
        assertThat(ready.copy(links = emptyList()).shouldLoadMore(9, 12)).isFalse()
    }

    private fun page(vararg shareIds: String, next: String? = null): Result<SharedLinksResponse> =
        Result.Success(
            SharedLinksResponse(
                links = shareIds.map { SharedLink(conversationId = "c-$it", shareId = it, title = "Chat $it") },
                nextCursor = next,
                hasNextPage = next != null,
            ),
        )

    private data class Call(val cursor: String?, val sort: SharedLinksSort, val search: String)

    private class FakeShareRepository : ShareRepository {
        val calls = mutableListOf<Call>()
        val deleted = mutableListOf<String>()
        var deleteResult: Result<Unit> = Result.Success(Unit)
        private val pages = mutableMapOf<String?, suspend (SharedLinksSort, String) -> Result<SharedLinksResponse>>()

        override val revision = MutableStateFlow(0L)

        fun page(cursor: String?, answer: suspend () -> Result<SharedLinksResponse>) {
            pages[cursor] = { _, _ -> answer() }
        }

        fun pageBy(cursor: String?, answer: suspend (SharedLinksSort, String) -> Result<SharedLinksResponse>) {
            pages[cursor] = answer
        }

        override fun shareUrl(shareId: String) = "https://chat.example.com/share/$shareId"

        override suspend fun getSharedLinksPaginated(
            cursor: String?,
            sort: SharedLinksSort,
            search: String?,
        ): Result<SharedLinksResponse> {
            val query = search.orEmpty()
            calls += Call(cursor, sort, query)
            val answer = pages[cursor] ?: error("no page for $cursor")
            return answer(sort, query)
        }

        override suspend fun deleteShareLink(shareId: String): Result<Unit> {
            deleted += shareId
            return deleteResult
        }

        override suspend fun lookupShareLink(conversationId: String): Result<ShareLinkLookup> = error("unused")

        override suspend fun createShareLink(
            conversationId: String,
            targetMessageId: String?,
            snapshotFiles: Boolean?,
        ): Result<String> = error("unused")

        override suspend fun updateShareLink(
            shareId: String,
            targetMessageId: String?,
            snapshotFiles: Boolean?,
        ): Result<SharedLink> = error("unused")

        override suspend fun getSharedSnapshot(shareId: String): Result<SharedConversationSnapshot> = error("unused")

        override suspend fun downloadSharedFile(shareId: String, fileId: String): Result<ByteArray> = error("unused")

        override suspend fun forkSharedConversation(
            shareId: String,
            targetMessageIndex: Int?,
            shareRevision: String?,
        ): Result<ForkConversationResponse> = error("unused")
    }
}
