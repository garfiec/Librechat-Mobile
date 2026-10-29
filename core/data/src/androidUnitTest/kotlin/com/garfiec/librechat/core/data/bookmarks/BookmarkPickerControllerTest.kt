package com.garfiec.librechat.core.data.bookmarks

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkPickerControllerTest {

    private val tags = MutableStateFlow(
        listOf(
            ConversationTag(tag = "work", count = 3, position = 0),
            ConversationTag(tag = SAVED_TAG, count = 5, position = 1),
            ConversationTag(tag = "empty", count = 0, position = 2),
        ),
    )
    private val repo = mockk<TagRepository> {
        every { observeTags() } returns tags
    }

    // In backgroundScope (its state is shared eagerly), so runCurrent() rather than
    // advanceUntilIdle(), which leaves background work alone.
    private fun TestScope.controller(): BookmarkPickerController =
        BookmarkPickerController(repo, backgroundScope)

    private fun TestScope.failures(controller: BookmarkPickerController): List<BookmarkPickerFailure> {
        val seen = mutableListOf<BookmarkPickerFailure>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { controller.failures.collect { seen += it } }
        return seen
    }

    @Test
    fun `open shows loading, then the chat's server tags without Saved, and every bookmark incl count 0`() = runTest {
        val answer = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.getConversationTags("c1") } coAnswers { answer.await() }
        val controller = controller()

        controller.open("c1")
        runCurrent()
        assertThat(controller.state.value.loading).isTrue()
        assertThat(controller.state.value.attached).isEmpty()

        answer.complete(Result.Success(listOf("work", SAVED_TAG, "web-only")))
        runCurrent()

        val state = controller.state.value
        assertThat(state.loading).isFalse()
        assertThat(state.attached).containsExactly("work", "web-only")
        assertThat(state.bookmarks.map { it.tag }).containsExactly("work", "empty").inOrder()
    }

    @Test
    fun `a failed load closes the checklist and reports it`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Error(message = "offline")
        val controller = controller()
        val failures = failures(controller)

        controller.open("c1")
        runCurrent()

        assertThat(controller.state.value.isOpen).isFalse()
        assertThat(failures).containsExactly(BookmarkPickerFailure.LoadFailed)
    }

    @Test
    fun `a tap saves at once and keeps the server's answer`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(listOf("work"))
        coEvery { repo.setConversationTag("c1", "empty", true) } returns
            Result.Success(listOf("work", "empty", "web-only"))
        val controller = controller()
        controller.open("c1")
        runCurrent()

        controller.toggle("empty")
        runCurrent()

        coVerify(exactly = 1) { repo.setConversationTag("c1", "empty", true) }
        assertThat(controller.state.value.attached).containsExactly("work", "empty", "web-only")
        assertThat(controller.state.value.pending).isEmpty()
    }

    @Test
    fun `the row is ticked and disabled while its save is out, and a second tap is ignored`() = runTest {
        val answer = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.getConversationTags("c1") } returns Result.Success(listOf("work"))
        coEvery { repo.setConversationTag("c1", "work", false) } coAnswers { answer.await() }
        val controller = controller()
        controller.open("c1")
        runCurrent()

        controller.toggle("work")
        runCurrent()
        assertThat(controller.state.value.attached).doesNotContain("work")
        assertThat(controller.state.value.pending).containsExactly("work")

        controller.toggle("work")
        runCurrent()
        coVerify(exactly = 1) { repo.setConversationTag(any(), any(), any()) }

        answer.complete(Result.Success(emptyList()))
        runCurrent()
        assertThat(controller.state.value.pending).isEmpty()
        assertThat(controller.state.value.attached).isEmpty()
    }

    @Test
    fun `a failed save puts the tick back and reports it`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(listOf("work"))
        coEvery { repo.setConversationTag("c1", any(), any()) } returns Result.Error(message = "boom")
        val controller = controller()
        val failures = failures(controller)
        controller.open("c1")
        runCurrent()

        controller.toggle("work")
        controller.toggle("empty")
        runCurrent()

        assertThat(controller.state.value.attached).containsExactly("work")
        assertThat(controller.state.value.pending).isEmpty()
        assertThat(failures).containsExactly(
            BookmarkPickerFailure.ToggleFailed("work"),
            BookmarkPickerFailure.ToggleFailed("empty"),
        )
    }

    @Test
    fun `an answer for one row leaves another row's save in flight alone`() = runTest {
        val slow = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.setConversationTag("c1", "work", true) } coAnswers { slow.await() }
        // Answered before "work" was written, so it doesn't list it yet.
        coEvery { repo.setConversationTag("c1", "empty", true) } returns Result.Success(listOf("empty"))
        val controller = controller()
        controller.open("c1")
        runCurrent()

        controller.toggle("work")
        controller.toggle("empty")
        runCurrent()

        assertThat(controller.state.value.attached).containsExactly("work", "empty")
        assertThat(controller.state.value.pending).containsExactly("work")
    }

    @Test
    fun `taps are ignored while the chat's tags load`() = runTest {
        coEvery { repo.getConversationTags("c1") } coAnswers { CompletableDeferred<Result<List<String>>>().await() }
        val controller = controller()
        controller.open("c1")
        runCurrent()

        controller.toggle("work")
        runCurrent()

        coVerify(exactly = 0) { repo.setConversationTag(any(), any(), any()) }
    }

    @Test
    fun `Saved is never touched by the checklist`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(listOf(SAVED_TAG))
        coEvery { repo.setConversationTag("c1", "work", true) } returns Result.Success(listOf(SAVED_TAG, "work"))
        val controller = controller()
        controller.open("c1")
        runCurrent()

        controller.toggle("work")
        runCurrent()

        coVerify(exactly = 0) { repo.setConversationTag(any(), SAVED_TAG, any()) }
        assertThat(controller.state.value.attached).containsExactly("work")
    }

    @Test
    fun `New bookmark with Add to this chat creates it on the chat and shows it ticked`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.createTag("Ideas", null, "c1") } returns Result.Success(ConversationTag(tag = "Ideas"))
        val controller = controller()
        controller.open("c1")
        runCurrent()

        controller.openEditor()
        runCurrent()
        assertThat(controller.state.value.editorOpen).isTrue()
        controller.create("Ideas", description = " ", addToChat = true)
        runCurrent()

        coVerify(exactly = 1) { repo.createTag("Ideas", null, "c1") }
        assertThat(controller.state.value.editorOpen).isFalse()
        assertThat(controller.state.value.attached).containsExactly("Ideas")
    }

    @Test
    fun `New bookmark without Add to this chat leaves the chat alone`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.createTag("Ideas", "Later", null) } returns Result.Success(ConversationTag(tag = "Ideas"))
        val controller = controller()
        controller.open("c1")
        runCurrent()

        controller.openEditor()
        controller.create("Ideas", description = "Later", addToChat = false)
        runCurrent()

        assertThat(controller.state.value.attached).isEmpty()
        assertThat(controller.state.value.editorOpen).isFalse()
    }

    @Test
    fun `a failed create keeps the editor up and reports it`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.createTag(any(), any(), any()) } returns Result.Error(message = "500")
        val controller = controller()
        val failures = failures(controller)
        controller.open("c1")
        runCurrent()

        controller.openEditor()
        controller.create("Ideas", description = "", addToChat = true)
        runCurrent()

        assertThat(controller.state.value.editorOpen).isTrue()
        assertThat(controller.state.value.creating).isFalse()
        assertThat(failures).containsExactly(BookmarkPickerFailure.CreateFailed("Ideas"))
    }

    @Test
    fun `an answer for a closed or reopened checklist doesn't reach the new one`() = runTest {
        val first = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.getConversationTags("c1") } coAnswers { first.await() }
        coEvery { repo.getConversationTags("c2") } returns Result.Success(listOf("empty"))
        val controller = controller()

        controller.open("c1")
        runCurrent()
        controller.open("c2")
        runCurrent()
        first.complete(Result.Success(listOf("work")))
        runCurrent()

        assertThat(controller.state.value.conversationId).isEqualTo("c2")
        assertThat(controller.state.value.attached).containsExactly("empty")

        controller.close()
        runCurrent()
        assertThat(controller.state.value.isOpen).isFalse()
        assertThat(controller.state.value.bookmarks).isEmpty()
    }
}
