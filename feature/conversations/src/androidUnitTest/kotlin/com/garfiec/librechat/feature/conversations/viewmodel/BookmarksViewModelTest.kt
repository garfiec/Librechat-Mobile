package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.BookmarksGate
import com.garfiec.librechat.core.data.repository.CreatedTag
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tags = MutableStateFlow<List<ConversationTag>>(emptyList())
    private val revision = MutableStateFlow(0L)
    private val enabled = MutableStateFlow(true)
    private val tagRepository = mockk<TagRepository>(relaxed = true) {
        every { observeTags() } returns tags
        every { revision } returns this@BookmarksViewModelTest.revision
    }
    private val gate = mockk<BookmarksGate> { every { enabled } returns this@BookmarksViewModelTest.enabled }

    private fun tag(name: String, position: Int = 0, count: Int = 0, description: String? = null) =
        ConversationTag(tag = name, position = position, count = count, description = description)

    private fun names(viewModel: BookmarksViewModel) = viewModel.uiState.value.shown.map { it.tag }

    private fun viewModel() = BookmarksViewModel(tagRepository, gate)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { tagRepository.refreshTags() } returns Result.Success(Unit)
        // A relaxed mock answers with a mocked Result, which reads as neither success nor failure.
        coEvery { tagRepository.reorderAfter(any(), any()) } returns Result.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `placeholders until the first refresh settles, then the bookmarks without Saved`() = runTest(testDispatcher) {
        val refreshed = CompletableDeferred<Result<Unit>>()
        coEvery { tagRepository.refreshTags() } coAnswers { refreshed.await() }
        val viewModel = viewModel()
        runCurrent()
        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarksContent.PLACEHOLDERS)

        tags.value = listOf(tag(SAVED_TAG, 0), tag("Work", 1), tag("Taxes", 2))
        refreshed.complete(Result.Success(Unit))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarksContent.CARDS)
        assertThat(names(viewModel)).containsExactly("Work", "Taxes").inOrder()
    }

    @Test
    fun `an account with no bookmarks shows the empty state once loaded`() = runTest(testDispatcher) {
        tags.value = listOf(tag(SAVED_TAG))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarksContent.EMPTY)
    }

    @Test
    fun `a failed first load with nothing cached is the error state, and retry loads again`() = runTest(testDispatcher) {
        coEvery { tagRepository.refreshTags() } returns Result.Error(RuntimeException("down"), "down")
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarksContent.ERROR)

        coEvery { tagRepository.refreshTags() } returns Result.Success(Unit)
        tags.value = listOf(tag("Work"))
        viewModel.retry()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarksContent.CARDS)
        coVerify(exactly = 2) { tagRepository.refreshTags() }
    }

    @Test
    fun `a failed refresh over cached cards keeps them and reports it`() = runTest(testDispatcher) {
        tags.value = listOf(tag("Work"))
        val refreshed = CompletableDeferred<Result<Unit>>()
        coEvery { tagRepository.refreshTags() } coAnswers { refreshed.await() }
        val viewModel = viewModel()
        val events = mutableListOf<BookmarksEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        runCurrent()
        refreshed.complete(Result.Error(RuntimeException("down"), "down"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarksContent.CARDS)
        assertThat(events).containsExactly(BookmarksEvent.Failed(BookmarksFailure.LOAD))
    }

    @Test
    fun `search matches names and descriptions ignoring case, and says when nothing matches`() = runTest(testDispatcher) {
        tags.value = listOf(tag("Work"), tag("Recipes", description = "Weekend COOKING"), tag("Taxes"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChange("cook")
        assertThat(names(viewModel)).containsExactly("Recipes")
        viewModel.onSearchQueryChange("WOR")
        assertThat(names(viewModel)).containsExactly("Work")
        viewModel.onSearchQueryChange("zzz")
        assertThat(viewModel.uiState.value.content).isEqualTo(BookmarksContent.NO_RESULTS)
    }

    @Test
    fun `the search field shows from eight bookmarks and stays while a search is typed`() = runTest(testDispatcher) {
        tags.value = (1..7).map { tag("Tag $it", it) }
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showsSearch).isFalse()

        tags.value = (1..8).map { tag("Tag $it", it) }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showsSearch).isTrue()

        viewModel.onSearchQueryChange("zzz")
        tags.value = (1..3).map { tag("Tag $it", it) }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showsSearch).isTrue()
    }

    @Test
    fun `reordering is off while searching or with a single bookmark`() = runTest(testDispatcher) {
        tags.value = listOf(tag("Work"))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canReorder).isFalse()

        tags.value = listOf(tag("Work", 0), tag("Taxes", 1))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canReorder).isTrue()

        viewModel.onSearchQueryChange("w")
        assertThat(viewModel.uiState.value.canReorder).isFalse()
    }

    @Test
    fun `a drop shows the new order at once and hands the repository the card it now sits below`() =
        runTest(testDispatcher) {
            tags.value = listOf(tag("A", 0), tag("B", 1), tag("C", 2))
            val saved = CompletableDeferred<Result<Unit>>()
            coEvery { tagRepository.reorderAfter(any(), any()) } coAnswers { saved.await() }
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onDrop(listOf("C", "A", "B"))
            runCurrent()

            assertThat(names(viewModel)).containsExactly("C", "A", "B").inOrder()
            coVerify(exactly = 1) { tagRepository.reorderAfter("C", null) }

            // The repository applies the move to the cache before it answers.
            tags.value = listOf(tag("C", 0), tag("A", 1), tag("B", 2))
            saved.complete(Result.Success(Unit))
            advanceUntilIdle()
            assertThat(names(viewModel)).containsExactly("C", "A", "B").inOrder()
        }

    @Test
    fun `a drop names the card above where it landed`() = runTest(testDispatcher) {
        tags.value = listOf(tag("A", 0), tag("B", 1), tag("C", 2), tag("D", 3))
        val viewModel = viewModel()
        advanceUntilIdle()

        // D just below B.
        viewModel.onDrop(listOf("A", "B", "D", "C"))
        advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.reorderAfter("D", "B") }
        // The repository works out the slot from the server's order; the screen never sends an index.
        coVerify(exactly = 0) { tagRepository.reorderTag(any(), any()) }
    }

    @Test
    fun `a refused move falls back to the cache's order and reports it`() = runTest(testDispatcher) {
        tags.value = listOf(tag("A", 0), tag("B", 1), tag("C", 2))
        coEvery { tagRepository.reorderAfter(any(), any()) } returns Result.Error(RuntimeException("500"), "500")
        val viewModel = viewModel()
        val events = mutableListOf<BookmarksEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        advanceUntilIdle()

        viewModel.onDrop(listOf("B", "C", "A"))
        advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.reorderAfter("A", "C") }
        assertThat(names(viewModel)).containsExactly("A", "B", "C").inOrder()
        assertThat(events).containsExactly(BookmarksEvent.Failed(BookmarksFailure.MOVE))
    }

    @Test
    fun `a second drop while the first is saving holds its own order until both settle`() =
        runTest(testDispatcher) {
            tags.value = listOf(tag("A", 0), tag("B", 1), tag("C", 2), tag("D", 3))
            val first = CompletableDeferred<Result<Unit>>()
            val second = CompletableDeferred<Result<Unit>>()
            coEvery { tagRepository.reorderAfter("B", any()) } coAnswers { first.await() }
            coEvery { tagRepository.reorderAfter("D", any()) } coAnswers { second.await() }
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onDrop(listOf("B", "A", "C", "D"))
            runCurrent()
            // Read against the order the first drop left on screen, not the cache's.
            viewModel.onDrop(listOf("B", "D", "A", "C"))
            runCurrent()

            assertThat(names(viewModel)).containsExactly("B", "D", "A", "C").inOrder()
            coVerify(exactly = 1) { tagRepository.reorderAfter("B", null) }
            coVerify(exactly = 1) { tagRepository.reorderAfter("D", "B") }

            // The first lands: the screen keeps the second's order while it runs.
            tags.value = listOf(tag("B", 0), tag("A", 1), tag("C", 2), tag("D", 3))
            first.complete(Result.Success(Unit))
            runCurrent()
            assertThat(names(viewModel)).containsExactly("B", "D", "A", "C").inOrder()

            tags.value = listOf(tag("B", 0), tag("D", 1), tag("A", 2), tag("C", 3))
            second.complete(Result.Success(Unit))
            advanceUntilIdle()
            assertThat(names(viewModel)).containsExactly("B", "D", "A", "C").inOrder()

            // Both settled: the list follows the cache again.
            tags.value = listOf(tag("A", 0), tag("B", 1), tag("C", 2), tag("D", 3))
            advanceUntilIdle()
            assertThat(names(viewModel)).containsExactly("A", "B", "C", "D").inOrder()
        }

    @Test
    fun `when the first of two drops is refused, the second's order stays up and the list then follows the cache`() =
        runTest(testDispatcher) {
            tags.value = listOf(tag("A", 0), tag("B", 1), tag("C", 2), tag("D", 3))
            val first = CompletableDeferred<Result<Unit>>()
            val second = CompletableDeferred<Result<Unit>>()
            coEvery { tagRepository.reorderAfter("B", any()) } coAnswers { first.await() }
            coEvery { tagRepository.reorderAfter("D", any()) } coAnswers { second.await() }
            val viewModel = viewModel()
            val events = mutableListOf<BookmarksEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
            advanceUntilIdle()

            viewModel.onDrop(listOf("B", "A", "C", "D"))
            runCurrent()
            viewModel.onDrop(listOf("D", "B", "A", "C"))
            runCurrent()

            first.complete(Result.Error(RuntimeException("500"), "500"))
            runCurrent()
            assertThat(events).containsExactly(BookmarksEvent.Failed(BookmarksFailure.MOVE))
            assertThat(names(viewModel)).containsExactly("D", "B", "A", "C").inOrder()

            // The repository moved D to the top of the restored order.
            tags.value = listOf(tag("D", 0), tag("A", 1), tag("B", 2), tag("C", 3))
            second.complete(Result.Success(Unit))
            advanceUntilIdle()
            assertThat(names(viewModel)).containsExactly("D", "A", "B", "C").inOrder()
        }

    @Test
    fun `move up and down use the full list's order, also while searching`() = runTest(testDispatcher) {
        tags.value = listOf(tag("Alpha", 0), tag("Beta", 1), tag("Gamma", 2))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChange("gamma")
        viewModel.moveBy("Gamma", -1)
        advanceUntilIdle()
        coVerify(exactly = 1) { tagRepository.reorderAfter("Gamma", "Alpha") }

        viewModel.moveBy("Alpha", -1)
        advanceUntilIdle()
        coVerify(exactly = 0) { tagRepository.reorderAfter("Alpha", any()) }
    }

    @Test
    fun `a drop that didn't move a single card sends nothing`() = runTest(testDispatcher) {
        tags.value = listOf(tag("A", 0), tag("B", 1), tag("C", 2))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDrop(listOf("A", "B", "C"))
        viewModel.onDrop(listOf("C", "B", "A", "D"))
        advanceUntilIdle()

        coVerify(exactly = 0) { tagRepository.reorderAfter(any(), any()) }
    }

    @Test
    fun `creating sends the name and a blank description as none, and closes on success`() = runTest(testDispatcher) {
        coEvery { tagRepository.createTag(any(), any(), any()) } returns Result.Success(CreatedTag(tag("Work")))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openCreate()
        viewModel.save("Work", "")
        assertThat(viewModel.uiState.value.editor?.saving).isTrue()
        advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.createTag("Work", null, null) }
        assertThat(viewModel.uiState.value.editor).isNull()
    }

    @Test
    fun `a failed create keeps the sheet open and says so in it`() = runTest(testDispatcher) {
        coEvery { tagRepository.createTag(any(), any(), any()) } returns Result.Error(RuntimeException("500"), "500")
        val viewModel = viewModel()
        val events = mutableListOf<BookmarksEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        advanceUntilIdle()

        viewModel.openCreate()
        viewModel.save("Work", "Client threads")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editor).isEqualTo(BookmarkEditorState(error = BookmarksFailure.CREATE))
        // A snackbar would sit behind the sheet.
        assertThat(events).isEmpty()

        // The next try clears it while it runs.
        val retry = CompletableDeferred<Result<CreatedTag>>()
        coEvery { tagRepository.createTag(any(), any(), any()) } coAnswers { retry.await() }
        viewModel.save("Work", "Client threads")
        runCurrent()
        assertThat(viewModel.uiState.value.editor).isEqualTo(BookmarkEditorState(saving = true))
        retry.complete(Result.Success(CreatedTag(tag("Work"))))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.editor).isNull()
    }

    @Test
    fun `an edit sends only the fields that changed`() = runTest(testDispatcher) {
        tags.value = listOf(tag("Work", description = "Client threads"))
        coEvery { tagRepository.updateTag(any(), any(), any(), any()) } returns Result.Success(tag("Jobs"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openEdit("Work")
        viewModel.save("Jobs", "Client threads")
        advanceUntilIdle()
        coVerify(exactly = 1) { tagRepository.updateTag("Work", "Jobs", null, null) }

        viewModel.openEdit("Work")
        viewModel.save("Work", "")
        advanceUntilIdle()
        // An emptied description is sent as "", which clears it.
        coVerify(exactly = 1) { tagRepository.updateTag("Work", null, "", null) }
    }

    @Test
    fun `editing only the description of a name with surrounding spaces doesn't rename it`() = runTest(testDispatcher) {
        // Web stores names as typed; the sheet sends its fields trimmed.
        tags.value = listOf(tag("Work ", description = "Client threads"))
        coEvery { tagRepository.updateTag(any(), any(), any(), any()) } returns Result.Success(tag("Work "))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openEdit("Work ")
        viewModel.save("Work", "Client threads, 2026")
        advanceUntilIdle()

        coVerify(exactly = 1) { tagRepository.updateTag("Work ", null, "Client threads, 2026", null) }
    }

    @Test
    fun `an edit that changed nothing closes without a request`() = runTest(testDispatcher) {
        tags.value = listOf(tag("Work", description = "Client threads"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openEdit("Work")
        viewModel.save("Work", "Client threads")
        advanceUntilIdle()

        coVerify(exactly = 0) { tagRepository.updateTag(any(), any(), any(), any()) }
        assertThat(viewModel.uiState.value.editor).isNull()
    }

    @Test
    fun `a failed edit says so in its sheet, a failed delete in a snackbar`() = runTest(testDispatcher) {
        tags.value = listOf(tag("Work"))
        coEvery { tagRepository.updateTag(any(), any(), any(), any()) } returns Result.Error(RuntimeException("404"), "404")
        coEvery { tagRepository.deleteTag(any()) } returns Result.Error(RuntimeException("500"), "500")
        val viewModel = viewModel()
        val events = mutableListOf<BookmarksEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        advanceUntilIdle()

        viewModel.openEdit("Work")
        viewModel.save("Jobs", "")
        viewModel.delete("Work")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editor?.saving).isFalse()
        assertThat(viewModel.uiState.value.editor?.error).isEqualTo(BookmarksFailure.SAVE)
        assertThat(events).containsExactly(BookmarksEvent.Failed(BookmarksFailure.DELETE))
    }

    @Test
    fun `the sheet can't be closed while a save is running`() = runTest(testDispatcher) {
        val created = CompletableDeferred<Result<CreatedTag>>()
        coEvery { tagRepository.createTag(any(), any(), any()) } coAnswers { created.await() }
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openCreate()
        viewModel.save("Work", "")
        runCurrent()
        viewModel.closeEditor()
        viewModel.save("Work", "")
        runCurrent()

        assertThat(viewModel.uiState.value.editor?.saving).isTrue()
        coVerify(exactly = 1) { tagRepository.createTag(any(), any(), any()) }
        created.complete(Result.Success(CreatedTag(tag("Work"))))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.editor).isNull()
    }

    @Test
    fun `a change elsewhere and a return to the screen refresh the list`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        coVerify(exactly = 1) { tagRepository.refreshTags() }

        revision.value = 1
        advanceUntilIdle()
        coVerify(exactly = 2) { tagRepository.refreshTags() }

        viewModel.refreshOnReturn()
        advanceUntilIdle()
        coVerify(exactly = 3) { tagRepository.refreshTags() }
    }

    @Test
    fun `a return while a refresh is running doesn't start another`() = runTest(testDispatcher) {
        val refreshed = CompletableDeferred<Result<Unit>>()
        coEvery { tagRepository.refreshTags() } coAnswers { refreshed.await() }
        val viewModel = viewModel()
        runCurrent()

        viewModel.refreshOnReturn()
        viewModel.refresh()
        runCurrent()

        coVerify(exactly = 1) { tagRepository.refreshTags() }
        refreshed.complete(Result.Success(Unit))
        advanceUntilIdle()
    }

    @Test
    fun `turned off, it neither loads nor refreshes, and says so`() = runTest(testDispatcher) {
        enabled.value = false
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.refreshOnReturn()
        revision.value = 1
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.enabled).isFalse()
        coVerify(exactly = 0) { tagRepository.refreshTags() }
    }

    @Test
    fun `singleMove finds the one card a drag moved`() {
        val old = listOf("A", "B", "C", "D")
        assertThat(singleMove(old, listOf("D", "A", "B", "C"))).isEqualTo("D" to 0)
        assertThat(singleMove(old, listOf("B", "C", "D", "A"))).isEqualTo("A" to 3)
        assertThat(singleMove(old, listOf("A", "C", "B", "D"))).isNotNull()
        assertThat(singleMove(old, listOf("B", "A", "D", "C"))).isNull()
        assertThat(singleMove(old, old)).isNull()
    }
}
