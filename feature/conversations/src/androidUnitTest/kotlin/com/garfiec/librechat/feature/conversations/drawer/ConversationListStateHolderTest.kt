package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationListFilters
import com.garfiec.librechat.core.model.ConversationPage
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationListStateHolderTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var scope: CoroutineScope

    private val conversationRepository = mockk<ConversationRepository>(relaxed = true)
    private var nowMs = 1_000_000_000L

    private val convos = listOf(
        Conversation(conversationId = "c1", title = "Alpha One", updatedAt = Instant.parse("2026-02-19T10:00:00.000Z")),
        Conversation(conversationId = "c2", title = "Alpha Two", updatedAt = Instant.parse("2026-02-19T09:00:00.000Z")),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        scope = CoroutineScope(testDispatcher)
    }

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun visibleIds(holder: ConversationListStateHolder) =
        holder.groupedConversations.value
            .flatMap { it.second }
            .mapNotNull { it.conversationId }

    private fun staleHolder() =
        ConversationListStateHolder(conversationRepository, scope, dayBoundaries = emptyFlow(), nowMillis = { nowMs })

    @Test
    fun `delete during active drawer search removes the row without a query change`() = runTest {
        // Punted Bug #25 (drawer side): the drawer search is a client-side filter over live Room
        // data, so a delete (Room re-emits) must vanish from the visible results immediately, not
        // wait for the next keystroke.
        val roomFlow = MutableStateFlow<Result<List<Conversation>>>(Result.Success(convos))
        every { conversationRepository.observeConversations(any()) } returns roomFlow

        val holder = ConversationListStateHolder(conversationRepository, scope, dayBoundaries = emptyFlow())
        advanceUntilIdle()

        holder.onSearchQueryChanged("Alpha")
        advanceUntilIdle() // clears the search debounce
        assertThat(visibleIds(holder)).containsExactly("c1", "c2")

        // c1 deleted from the drawer: Room re-emits without it while the search is still active.
        roomFlow.value = Result.Success(convos.filter { it.conversationId == "c2" })
        advanceUntilIdle()

        assertThat(visibleIds(holder)).containsExactly("c2")
    }

    @Test
    fun `a running search match is lifted into Running, pinned or not`() = runTest {
        // Upstream Conversations.tsx passes `includePinned: isArchivedView` (not the search-aware
        // flag) to groupConversationsWithRunning, so search still promotes live matches. Pins stay
        // in the search results because the Pinned section is hidden while searching.
        val pinned = convos.map { if (it.conversationId == "c2") it.copy(pinned = true) else it }
        every { conversationRepository.observeConversations(any()) } returns
            MutableStateFlow<Result<List<Conversation>>>(Result.Success(pinned))

        val holder = ConversationListStateHolder(conversationRepository, scope, dayBoundaries = emptyFlow())
        advanceUntilIdle()
        assertThat(holder.groupedConversations.value.withRunningFirst(setOf("c2")).map { it.first })
            .doesNotContain(DrawerGroupKey.Running)

        holder.onSearchQueryChanged("Alpha")
        advanceUntilIdle()

        val grouped = holder.groupedConversations.value.withRunningFirst(setOf("c2"))
        assertThat(grouped.first().first).isEqualTo(DrawerGroupKey.Running)
        assertThat(grouped.first().second.map { it.conversationId }).containsExactly("c2")
    }

    @Test
    fun `rename during active drawer search updates the visible title`() = runTest {
        val roomFlow = MutableStateFlow<Result<List<Conversation>>>(Result.Success(convos))
        every { conversationRepository.observeConversations(any()) } returns roomFlow

        val holder = ConversationListStateHolder(conversationRepository, scope, dayBoundaries = emptyFlow())
        advanceUntilIdle()

        holder.onSearchQueryChanged("Alpha")
        advanceUntilIdle()

        roomFlow.value = Result.Success(
            convos.map { if (it.conversationId == "c1") it.copy(title = "Alpha One Renamed") else it },
        )
        advanceUntilIdle()

        val titles = holder.groupedConversations.value.flatMap { it.second }
            .associate { it.conversationId to it.title }
        assertThat(titles["c1"]).isEqualTo("Alpha One Renamed")
        assertThat(titles.keys).containsExactly("c1", "c2")
    }

    @Test
    fun `server title hits join the local ones without duplicates`() = runTest {
        every { conversationRepository.observeConversations(any()) } returns MutableStateFlow(Result.Success(convos))
        val server = listOf(
            Conversation(conversationId = "c1", title = "Alpha One", updatedAt = Instant.parse("2026-02-19T10:00:00.000Z")),
            Conversation(conversationId = "s1", title = "Alpha on the server", updatedAt = Instant.parse("2026-02-18T10:00:00.000Z")),
        )
        val holder = ConversationListStateHolder(
            conversationRepository,
            scope,
            dayBoundaries = emptyFlow(),
            remoteTitleSearch = { server },
        )
        advanceUntilIdle()

        holder.onSearchQueryChanged("Alpha")
        advanceUntilIdle()

        assertThat(visibleIds(holder)).containsExactly("c1", "c2", "s1")
    }

    @Test
    fun `a server conversation found by a message body is not a title hit`() = runTest {
        every { conversationRepository.observeConversations(any()) } returns MutableStateFlow(Result.Success(convos))
        val server = listOf(Conversation(conversationId = "s-body", title = "Unrelated title"))
        val holder = ConversationListStateHolder(
            conversationRepository,
            scope,
            dayBoundaries = emptyFlow(),
            remoteTitleSearch = { server },
        )
        advanceUntilIdle()

        holder.onSearchQueryChanged("Alpha")
        advanceUntilIdle()

        assertThat(visibleIds(holder)).doesNotContain("s-body")
    }

    @Test
    fun `titles match word by word like messages do`() = runTest {
        every { conversationRepository.observeConversations(any()) } returns MutableStateFlow(Result.Success(convos))
        val holder = ConversationListStateHolder(conversationRepository, scope, dayBoundaries = emptyFlow())
        advanceUntilIdle()

        holder.onSearchQueryChanged("one alpha")
        advanceUntilIdle()

        assertThat(visibleIds(holder)).containsExactly("c1")
    }

    @Test
    fun `the filter holds through a resubmitted query, a cache emission and a page load`() = runTest {
        val cache = MutableStateFlow<Result<List<Conversation>>>(Result.Success(convos))
        every { conversationRepository.observeConversations(any()) } returns cache
        val holder = ConversationListStateHolder(conversationRepository, scope, dayBoundaries = emptyFlow())
        advanceUntilIdle()
        holder.onSearchQueryChanged("one")
        advanceUntilIdle()

        holder.onSearchQueryChanged("one") // the keyboard's action re-sending the same text
        cache.value = Result.Success(convos + Conversation(conversationId = "c9", title = "Unrelated"))
        holder.loadMoreConversations()
        advanceUntilIdle()

        assertThat(visibleIds(holder)).containsExactly("c1")
    }

    @Test
    fun `a server reply for a query the user has changed is dropped`() = runTest {
        every { conversationRepository.observeConversations(any()) } returns MutableStateFlow(Result.Success(convos))
        val gate = CompletableDeferred<Unit>()
        val holder = ConversationListStateHolder(
            conversationRepository,
            scope,
            dayBoundaries = emptyFlow(),
            remoteTitleSearch = { query ->
                gate.await()
                listOf(Conversation(conversationId = "s-$query", title = "$query remote"))
            },
        )
        advanceUntilIdle()
        holder.onSearchQueryChanged("Alpha")
        advanceUntilIdle()

        holder.reset() // an account switch blanks the query while the request is out
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(visibleIds(holder)).doesNotContain("s-Alpha")
    }

    @Test
    fun `short queries never ask the server`() = runTest {
        every { conversationRepository.observeConversations(any()) } returns MutableStateFlow(Result.Success(convos))
        var asked = 0
        val holder = ConversationListStateHolder(
            conversationRepository,
            scope,
            dayBoundaries = emptyFlow(),
            remoteTitleSearch = { asked++; emptyList() },
        )
        advanceUntilIdle()

        holder.onSearchQueryChanged("Al")
        advanceUntilIdle()

        assertThat(asked).isEqualTo(0)
    }

    @Test
    fun `filters page from the server and leave the cached list alone`() = runTest {
        val roomFlow = MutableStateFlow<Result<List<Conversation>>>(Result.Success(convos))
        every { conversationRepository.observeConversations(any()) } returns roomFlow
        val filters = ConversationListFilters(hasFiles = true)
        val server = Conversation(
            conversationId = "f1",
            title = "Has a PDF",
            updatedAt = Instant.parse("2026-02-18T10:00:00.000Z"),
        )
        coEvery { conversationRepository.getConversationsFiltered(filters, null, null, any()) } returns
            Result.Success(ConversationPage(listOf(server, convos[1]), nextCursor = null))
        val holder = ConversationListStateHolder(conversationRepository, scope, dayBoundaries = emptyFlow())
        advanceUntilIdle()

        holder.setFilters(filters)
        advanceUntilIdle()
        assertThat(visibleIds(holder)).containsExactly("c2", "f1")
        assertThat(holder.hasMore.value).isFalse()

        // A delete reaches the filtered list through Room; a row Room never held stays.
        roomFlow.value = Result.Success(convos.filter { it.conversationId == "c1" })
        advanceUntilIdle()
        assertThat(visibleIds(holder)).containsExactly("f1")

        holder.setFilters(null)
        advanceUntilIdle()
        assertThat(visibleIds(holder)).containsExactly("c1")
    }

    @Test
    fun `a failed first page is a failure, and one that lands empty is loaded`() = runTest {
        every { conversationRepository.observeConversations(any()) } returns
            MutableStateFlow<Result<List<Conversation>>>(Result.Success(emptyList()))
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Error(IllegalStateException("offline"))
        val holder = ConversationListStateHolder(conversationRepository, scope, dayBoundaries = emptyFlow())
        assertThat(holder.listLoad.value).isEqualTo(ListLoad.PENDING)
        advanceUntilIdle()

        assertThat(holder.listLoad.value).isEqualTo(ListLoad.FAILED)

        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Success(null)
        holder.refreshConversations()
        advanceUntilIdle()

        assertThat(holder.listLoad.value).isEqualTo(ListLoad.LOADED)
    }

    @Test
    fun `a failed filtered load says so instead of reading as no matches`() = runTest {
        every { conversationRepository.observeConversations(any()) } returns MutableStateFlow(Result.Success(convos))
        val filters = ConversationListFilters(sharedOnly = true)
        coEvery { conversationRepository.getConversationsFiltered(filters, null, null, any()) } returns
            Result.Error(IllegalStateException("400"))
        val holder = ConversationListStateHolder(conversationRepository, scope, dayBoundaries = emptyFlow())
        advanceUntilIdle()

        holder.setFilters(filters)
        advanceUntilIdle()

        assertThat(visibleIds(holder)).isEmpty()
        assertThat(holder.filterLoadFailed.value).isTrue()
        assertThat(holder.isRefreshing.value).isFalse()
    }

    @Test
    fun `a query over a filtered list searches the server with the same filters`() = runTest {
        every { conversationRepository.observeConversations(any()) } returns MutableStateFlow(Result.Success(convos))
        val filters = ConversationListFilters(endpoints = listOf("openAI"))
        coEvery { conversationRepository.getConversationsFiltered(filters, null, null, any()) } returns
            Result.Success(ConversationPage(listOf(convos[0]), nextCursor = null))
        val remote = Conversation(
            conversationId = "r1",
            title = "Alpha far back",
            updatedAt = Instant.parse("2025-01-01T10:00:00.000Z"),
        )
        coEvery { conversationRepository.getConversationsFiltered(filters, null, "Alpha", any()) } returns
            Result.Success(ConversationPage(listOf(remote), nextCursor = null))
        var unfilteredAsked = 0
        val holder = ConversationListStateHolder(
            conversationRepository,
            scope,
            dayBoundaries = emptyFlow(),
            remoteTitleSearch = { unfilteredAsked++; emptyList() },
        )
        advanceUntilIdle()
        holder.setFilters(filters)
        advanceUntilIdle()

        holder.onSearchQueryChanged("Alpha")
        advanceUntilIdle()

        assertThat(visibleIds(holder)).containsExactly("c1", "r1")
        assertThat(unfilteredAsked).isEqualTo(0)
    }

    @Test
    fun `a list older than five minutes reloads page 1 quietly`() = runTest {
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Success("p2")
        val holder = staleHolder()
        advanceUntilIdle()
        var refreshed = false
        backgroundScope.launch { holder.isRefreshing.collect { if (it) refreshed = true } }

        nowMs += (5.minutes + 1.seconds).inWholeMilliseconds
        holder.refreshIfStale()
        advanceUntilIdle()

        coVerify(exactly = 2) { conversationRepository.loadNextPage(cursor = null) }
        assertThat(refreshed).isFalse()
    }

    @Test
    fun `a fresh list does not reload`() = runTest {
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Success("p2")
        val holder = staleHolder()
        advanceUntilIdle()

        nowMs += 4.minutes.inWholeMilliseconds
        holder.refreshIfStale()
        advanceUntilIdle()

        coVerify(exactly = 1) { conversationRepository.loadNextPage(cursor = null) }
    }

    @Test
    fun `a wall clock set back since the last load counts as stale`() = runTest {
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Success("p2")
        val holder = staleHolder()
        advanceUntilIdle()

        nowMs -= 1.minutes.inWholeMilliseconds
        holder.refreshIfStale()
        advanceUntilIdle()

        coVerify(exactly = 2) { conversationRepository.loadNextPage(cursor = null) }
    }

    @Test
    fun `filters or a search skip the stale reload`() = runTest {
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Success("p2")
        val filters = ConversationListFilters(hasFiles = true)
        coEvery { conversationRepository.getConversationsFiltered(filters, null, null, any()) } returns
            Result.Success(ConversationPage(emptyList(), nextCursor = null))
        val holder = staleHolder()
        advanceUntilIdle()

        holder.setFilters(filters)
        advanceUntilIdle()
        nowMs += 6.minutes.inWholeMilliseconds
        holder.refreshIfStale()
        advanceUntilIdle()
        coVerify(exactly = 1) { conversationRepository.loadNextPage(cursor = null) }
        coVerify(exactly = 1) { conversationRepository.getConversationsFiltered(filters, null, null, any()) }

        holder.setFilters(null)
        advanceUntilIdle()
        nowMs += 6.minutes.inWholeMilliseconds
        holder.onSearchQueryChanged("x")
        holder.refreshIfStale()
        advanceUntilIdle()
        coVerify(exactly = 2) { conversationRepository.loadNextPage(cursor = null) }
    }

    @Test
    fun `a failed reload leaves the list stale so the next one retries`() = runTest {
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Success("p2")
        val holder = staleHolder()
        advanceUntilIdle()
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Error(IllegalStateException("offline"))

        nowMs += 6.minutes.inWholeMilliseconds
        holder.refreshIfStale()
        advanceUntilIdle()
        holder.refreshIfStale()
        advanceUntilIdle()

        coVerify(exactly = 3) { conversationRepository.loadNextPage(cursor = null) }
        assertThat(holder.listLoad.value).isEqualTo(ListLoad.LOADED)
    }

    @Test
    fun `a quiet reload keeps the deeper cursor`() = runTest {
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Success("p2")
        coEvery { conversationRepository.loadNextPage(cursor = "p2") } returns Result.Success("p3")
        val holder = staleHolder()
        advanceUntilIdle()
        holder.loadMoreConversations()
        advanceUntilIdle()

        nowMs += 6.minutes.inWholeMilliseconds
        holder.refreshIfStale()
        advanceUntilIdle()
        holder.loadMoreConversations()
        advanceUntilIdle()

        coVerify(exactly = 1) { conversationRepository.loadNextPage(cursor = "p2") }
        coVerify(exactly = 1) { conversationRepository.loadNextPage(cursor = "p3") }
    }

    @Test
    fun `the launch load in flight counts as fresh`() = runTest {
        val gate = CompletableDeferred<Result<String?>>()
        coEvery { conversationRepository.loadNextPage(cursor = null) } coAnswers { gate.await() }
        val holder = staleHolder()
        runCurrent()

        holder.refreshIfStale()
        gate.complete(Result.Success("p2"))
        advanceUntilIdle()

        coVerify(exactly = 1) { conversationRepository.loadNextPage(cursor = null) }
        assertThat(holder.listLoad.value).isEqualTo(ListLoad.LOADED)
    }

    @Test
    fun `a reload still in flight at an account switch is dropped`() = runTest {
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Success("p2")
        val holder = staleHolder()
        advanceUntilIdle()
        val gate = CompletableDeferred<Result<String?>>()
        coEvery { conversationRepository.loadNextPage(cursor = null) } coAnswers { gate.await() }

        nowMs += 6.minutes.inWholeMilliseconds
        holder.refreshIfStale()
        runCurrent()
        holder.reset()
        gate.complete(Result.Success("old"))
        advanceUntilIdle()

        assertThat(holder.listLoad.value).isEqualTo(ListLoad.PENDING)
        assertThat(holder.hasMore.value).isTrue()
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Success(null)
        holder.loadMoreConversations()
        advanceUntilIdle()
        coVerify(exactly = 0) { conversationRepository.loadNextPage(cursor = "old") }
    }

    @Test
    fun `a list whose first page failed retries without waiting`() = runTest {
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Error(IllegalStateException("offline"))
        coEvery { conversationRepository.loadNextPage(cursor = "p2") } returns Result.Success(null)
        val holder = staleHolder()
        advanceUntilIdle()
        assertThat(holder.listLoad.value).isEqualTo(ListLoad.FAILED)
        coEvery { conversationRepository.loadNextPage(cursor = null) } returns Result.Success("p2")

        holder.refreshIfStale()
        advanceUntilIdle()

        assertThat(holder.listLoad.value).isEqualTo(ListLoad.LOADED)
        assertThat(holder.hasMore.value).isTrue()
        holder.loadMoreConversations()
        advanceUntilIdle()
        coVerify(exactly = 1) { conversationRepository.loadNextPage(cursor = "p2") }
    }
}
