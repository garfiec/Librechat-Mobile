package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.BookmarksGate
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.TagEvent
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationPage
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.bookmark_delete_failed
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkChatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val conversationRepository = mockk<ConversationRepository>(relaxed = true)
    private val tags = MutableStateFlow(listOf(tag(SAVED_TAG), tag("Work", count = 2), tag("Taxes")))
    private val revision = MutableStateFlow(0L)
    private val tagEvents = MutableSharedFlow<TagEvent>(extraBufferCapacity = 8)
    private val tagRepository = mockk<TagRepository>(relaxed = true) {
        every { observeTags() } returns tags
        every { revision } returns this@BookmarkChatsViewModelTest.revision
        every { events } returns tagEvents
    }
    private val enabled = MutableStateFlow(true)
    private val gate = mockk<BookmarksGate> { every { enabled } returns this@BookmarkChatsViewModelTest.enabled }

    private fun tag(name: String, count: Int = 0, description: String? = null) =
        ConversationTag(tag = name, count = count, description = description)

    private fun page(vararg ids: String, next: String? = null) =
        Result.Success(ConversationPage(ids.map { Conversation(conversationId = it) }, next))

    private suspend fun MockKMatcherScope.chats(tag: String = "Work", cursor: String? = null) =
        conversationRepository.getConversationsForTag(tag, cursor, any())

    private fun viewModel() = BookmarkChatsViewModel(
        tag = "Work",
        conversationRepository = conversationRepository,
        tagRepository = tagRepository,
        shareLinkControllerFactory = mockk(relaxed = true),
        conversationExporter = mockk(relaxed = true),
        bookmarksGate = gate,
        roleRepository = mockk(relaxed = true) { every { userPermissions } returns MutableStateFlow(null) },
        configRepository = mockk(relaxed = true) {
            every { endpointConfigs } returns MutableStateFlow(emptyMap())
            every { startupConfig } returns MutableStateFlow(null)
            every { serverUrl } returns flowOf("https://srv")
        },
        dayBoundaries = flowOf(RelativeTimeReference.current()),
    )

    private fun ids(viewModel: BookmarkChatsViewModel) =
        viewModel.uiState.value.groupedConversations.flatMap { (_, rows) -> rows.map { it.conversationId } }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { chats() } returns page("a", "b")
        coEvery { tagRepository.refreshTags() } returns Result.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the bookmark's first page from the server, then the next with its cursor`() = runTest(testDispatcher) {
        coEvery { chats() } returns page("a", "b", next = "c1")
        coEvery { chats(cursor = "c1") } returns page("c")
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarkChatsContent.CHATS)
        assertThat(viewModel.uiState.value.hasMore).isTrue()
        assertThat(viewModel.uiState.value.bookmark?.count).isEqualTo(2)
        assertThat(viewModel.uiState.value.allNames).containsExactly("Work", "Taxes").inOrder()

        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(ids(viewModel)).containsExactly("a", "b", "c")
        assertThat(viewModel.uiState.value.hasMore).isFalse()
        coVerify(exactly = 1) { chats(cursor = "c1") }
    }

    @Test
    fun `nothing loads until the bookmarks gate answers`() = runTest(testDispatcher) {
        val gateFlow = MutableSharedFlow<Boolean>()
        every { gate.enabled } returns gateFlow
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarkChatsContent.LOADING)
        coVerify(exactly = 0) { chats() }

        gateFlow.emit(true)
        advanceUntilIdle()
        coVerify(exactly = 1) { chats() }
    }

    @Test
    fun `bookmarks turned off for the account closes the page`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        enabled.value = false
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.bookmarksEnabled).isFalse()
    }

    @Test
    fun `a bookmark with no chats shows the empty state`() = runTest(testDispatcher) {
        coEvery { chats() } returns page()
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarkChatsContent.EMPTY)
    }

    @Test
    fun `a failed first page with nothing loaded is the error state, and retry loads again`() = runTest(testDispatcher) {
        coEvery { chats() } returns Result.Error(RuntimeException("down"), "down")
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarkChatsContent.ERROR)

        coEvery { chats() } returns page("a")
        viewModel.retry()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarkChatsContent.CHATS)
    }

    @Test
    fun `unticking this bookmark in a row's checklist drops the row`() = runTest(testDispatcher) {
        coEvery { tagRepository.getConversationTags("a") } returns Result.Success(listOf("Work", SAVED_TAG))
        coEvery { tagRepository.setConversationTag("a", "Work", false) } returns Result.Success(listOf(SAVED_TAG))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openBookmarks("a")
        advanceUntilIdle()
        assertThat(ids(viewModel)).containsExactly("a", "b")

        viewModel.bookmarkPicker.toggle("Work")
        advanceUntilIdle()

        assertThat(viewModel.getConversation("a")).isNull()
        assertThat(ids(viewModel)).containsExactly("b")
    }

    @Test
    fun `an untick the server refused keeps the row`() = runTest(testDispatcher) {
        coEvery { tagRepository.getConversationTags("a") } returns Result.Success(listOf("Work"))
        coEvery { tagRepository.setConversationTag("a", "Work", false) } returns Result.Error(RuntimeException("x"), "x")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openBookmarks("a")
        advanceUntilIdle()
        viewModel.bookmarkPicker.toggle("Work")
        advanceUntilIdle()

        assertThat(viewModel.getConversation("a")).isNotNull()
    }

    @Test
    fun `ticking another bookmark keeps the row`() = runTest(testDispatcher) {
        coEvery { tagRepository.getConversationTags("a") } returns Result.Success(listOf("Work"))
        coEvery { tagRepository.setConversationTag("a", "Taxes", true) } returns Result.Success(listOf("Work", "Taxes"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openBookmarks("a")
        advanceUntilIdle()
        viewModel.bookmarkPicker.toggle("Taxes")
        advanceUntilIdle()

        assertThat(viewModel.getConversation("a")).isNotNull()
    }

    @Test
    fun `a tag change elsewhere reloads the loaded rows quietly`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        coEvery { chats() } returns page("b")

        revision.value = 1
        runCurrent()
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
        advanceUntilIdle()

        assertThat(ids(viewModel)).containsExactly("b")
        coVerify(exactly = 2) { chats() }
    }

    @Test
    fun `a rename elsewhere moves the page to the new name`() = runTest(testDispatcher) {
        coEvery { chats(tag = "Projects") } returns page("a")
        val viewModel = viewModel()
        advanceUntilIdle()

        tags.value = listOf(tag("Projects", count = 1, description = "Renamed"), tag("Taxes"))
        tagEvents.emit(TagEvent.Renamed(from = "Work", to = "Projects"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.name).isEqualTo("Projects")
        assertThat(viewModel.uiState.value.bookmark?.description).isEqualTo("Renamed")
        assertThat(ids(viewModel)).containsExactly("a")
        coVerify(atLeast = 1) { chats(tag = "Projects") }

        // Later loads ask for the new name, not the route's.
        viewModel.refresh()
        advanceUntilIdle()
        coVerify(exactly = 1) { chats(tag = "Work") }
        assertThat(viewModel.uiState.value.deleted).isFalse()
    }

    @Test
    fun `a rename of another bookmark leaves the page alone`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        tagEvents.emit(TagEvent.Renamed(from = "Taxes", to = "Receipts"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.name).isEqualTo("Work")
    }

    @Test
    fun `saving a rename from the Edit sheet follows the stored name`() = runTest(testDispatcher) {
        coEvery { tagRepository.updateTag("Work", "Jobs", null, null) } returns
            Result.Success(ConversationTag(tag = "Jobs"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openEdit()
        assertThat(viewModel.uiState.value.editor?.editing?.tag).isEqualTo("Work")
        viewModel.save("Jobs", "")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editor).isNull()
        assertThat(viewModel.uiState.value.name).isEqualTo("Jobs")
    }

    @Test
    fun `a deletion elsewhere closes the page`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        tagEvents.emit(TagEvent.Deleted("Work"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.deleted).isTrue()
    }

    @Test
    fun `a name gone from a fresh tag list closes the page on return`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        tags.value = listOf(tag("Taxes"))
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.deleted).isTrue()
    }

    @Test
    fun `a tag refresh that failed never closes the page`() = runTest(testDispatcher) {
        coEvery { tagRepository.refreshTags() } returns Result.Error(RuntimeException("offline"), "offline")
        val viewModel = viewModel()
        advanceUntilIdle()

        tags.value = emptyList()
        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.deleted).isFalse()
    }

    @Test
    fun `Delete closes the page once the server took it`() = runTest(testDispatcher) {
        coEvery { tagRepository.deleteTag("Work") } returns Result.Success(Unit)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.delete()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.deleted).isTrue()
    }

    @Test
    fun `a refused Delete keeps the page and says so`() = runTest(testDispatcher) {
        coEvery { tagRepository.deleteTag("Work") } returns Result.Error(RuntimeException("x"), "x")
        val viewModel = viewModel()
        advanceUntilIdle()
        val events = mutableListOf<ConversationListEvent>()
        val job = launch { viewModel.events.collect { events += it } }
        runCurrent()

        viewModel.delete()
        advanceUntilIdle()
        job.cancel()

        assertThat(viewModel.uiState.value.deleted).isFalse()
        assertThat(events).containsExactly(ConversationListEvent.ShowErrorRes(Res.string.bookmark_delete_failed))
    }

    @Test
    fun `the loading state stands until the first page lands`() = runTest(testDispatcher) {
        val slow = CompletableDeferred<Result<ConversationPage>>()
        coEvery { chats() } coAnswers { slow.await() }
        val viewModel = viewModel()
        runCurrent()
        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarkChatsContent.LOADING)

        slow.complete(page("a"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarkChatsContent.CHATS)
    }
}
