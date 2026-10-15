package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.model.Conversation
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationListStateHolderTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var scope: CoroutineScope

    private val conversationRepository = mockk<ConversationRepository>(relaxed = true)

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
}
