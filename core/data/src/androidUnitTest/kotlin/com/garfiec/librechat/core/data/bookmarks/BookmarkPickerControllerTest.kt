package com.garfiec.librechat.core.data.bookmarks

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AccountSwitchedException
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
        coEvery { refreshTags() } returns Result.Success(Unit)
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
    fun `open re-reads the tag list, so a bookmark gone from the server drops out before a tick`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        // Deleted on the web: the server's list no longer has "work".
        coEvery { repo.refreshTags() } coAnswers {
            tags.value = tags.value.filterNot { it.tag == "work" }
            Result.Success(Unit)
        }
        val controller = controller()

        controller.open("c1")
        runCurrent()

        coVerify(exactly = 1) { repo.refreshTags() }
        assertThat(controller.state.value.loading).isFalse()
        assertThat(controller.state.value.bookmarks.map { it.tag }).containsExactly("empty")
    }

    @Test
    fun `the checklist waits for the tag list, and a failed one still opens it on the cached list`() = runTest {
        val refreshed = CompletableDeferred<Result<Unit>>()
        coEvery { repo.getConversationTags("c1") } returns Result.Success(listOf("work"))
        coEvery { repo.refreshTags() } coAnswers { refreshed.await() }
        val controller = controller()
        val failures = failures(controller)

        controller.open("c1")
        runCurrent()
        assertThat(controller.state.value.loading).isTrue()

        refreshed.complete(Result.Error(message = "offline"))
        runCurrent()

        val state = controller.state.value
        assertThat(state.isOpen).isTrue()
        assertThat(state.loading).isFalse()
        assertThat(state.attached).containsExactly("work")
        assertThat(state.bookmarks.map { it.tag }).containsExactly("work", "empty").inOrder()
        assertThat(failures).isEmpty()
    }

    @Test
    fun `a bookmark whose name has surrounding spaces shows ticked and is sent back as it is`() = runTest {
        tags.value = listOf(ConversationTag(tag = "Work ", count = 1, position = 0))
        coEvery { repo.getConversationTags("c1") } returns Result.Success(listOf("Work "))
        coEvery { repo.setConversationTag("c1", "Work ", false) } returns Result.Success(emptyList())
        val controller = controller()

        controller.open("c1")
        runCurrent()
        assertThat(controller.state.value.attached).containsExactly("Work ")

        controller.toggle("Work ")
        runCurrent()

        coVerify(exactly = 1) { repo.setConversationTag("c1", "Work ", false) }
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
    fun `a failed save puts the tick back and says so in the sheet`() = runTest {
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
        assertThat(controller.state.value.error).isEqualTo(BookmarkPickerFailure.ToggleFailed("empty"))
        // Shown in the open sheet, so not as a snackbar behind it too.
        assertThat(failures).isEmpty()
    }

    @Test
    fun `the next tap clears the sheet's error`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.setConversationTag("c1", "work", true) } returns Result.Error(message = "boom")
        val pending = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.setConversationTag("c1", "empty", true) } coAnswers { pending.await() }
        val controller = controller()
        controller.open("c1")
        runCurrent()
        controller.toggle("work")
        runCurrent()
        assertThat(controller.state.value.error).isNotNull()

        controller.toggle("empty")
        runCurrent()
        assertThat(controller.state.value.error).isNull()

        controller.toggle("work")
        runCurrent()
        controller.openEditor()
        runCurrent()
        assertThat(controller.state.value.error).isNull()
    }

    @Test
    fun `a save that fails after the sheet closed is reported, since no sheet can show it`() = runTest {
        val answer = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.setConversationTag("c1", "work", true) } coAnswers { answer.await() }
        val controller = controller()
        val failures = failures(controller)
        controller.open("c1")
        runCurrent()
        controller.toggle("work")
        runCurrent()

        controller.close()
        answer.complete(Result.Error(message = "boom"))
        runCurrent()

        assertThat(failures).containsExactly(BookmarkPickerFailure.ToggleFailed("work"))
        assertThat(controller.state.value.error).isNull()
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
    fun `an older tick answering after a newer one doesn't untick what the newer one saved`() = runTest {
        val first = CompletableDeferred<Result<List<String>>>()
        val second = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.setConversationTag("c1", "work", true) } coAnswers { first.await() }
        coEvery { repo.setConversationTag("c1", "empty", true) } coAnswers { second.await() }
        val controller = controller()
        controller.open("c1")
        runCurrent()

        controller.toggle("work")
        controller.toggle("empty")
        runCurrent()
        // The second write, which went after the first, answers first: its list has both.
        second.complete(Result.Success(listOf("work", "empty")))
        runCurrent()
        // The first's list was read before the second write.
        first.complete(Result.Success(listOf("work")))
        runCurrent()

        assertThat(controller.state.value.attached).containsExactly("work", "empty")
        assertThat(controller.state.value.pending).isEmpty()
    }

    @Test
    fun `an older tick answering after a new bookmark was added keeps the new one ticked`() = runTest {
        val tick = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.setConversationTag("c1", "work", true) } coAnswers { tick.await() }
        coEvery { repo.createTag("Ideas", null, "c1") } returns Result.Success(CreatedTag(ConversationTag(tag = "Ideas")))
        val controller = controller()
        controller.open("c1")
        runCurrent()

        controller.toggle("work")
        controller.openEditor()
        controller.create("Ideas", description = "", addToChat = true)
        runCurrent()
        assertThat(controller.state.value.attached).containsExactly("work", "Ideas")

        // Read before "Ideas" was added.
        tick.complete(Result.Success(listOf("work")))
        runCurrent()

        assertThat(controller.state.value.attached).containsExactly("work", "Ideas")
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
        coEvery { repo.createTag("Ideas", null, "c1") } returns Result.Success(CreatedTag(ConversationTag(tag = "Ideas")))
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
        coEvery { repo.createTag("Ideas", "Later", null) } returns Result.Success(CreatedTag(ConversationTag(tag = "Ideas")))
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
    fun `a failed create keeps the editor up and says so in it`() = runTest {
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
        assertThat(controller.state.value.error).isEqualTo(BookmarkPickerFailure.CreateFailed("Ideas"))
        assertThat(failures).isEmpty()

        // Back to the checklist: the editor's error goes with it.
        controller.closeEditor()
        runCurrent()
        assertThat(controller.state.value.error).isNull()
    }

    @Test
    fun `a bookmark made but not added closes the editor and shows its row unticked`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(listOf("work"))
        coEvery { repo.createTag("Ideas", null, "c1") } returns
            Result.Success(CreatedTag(ConversationTag(tag = "Ideas"), attachFailed = true))
        val controller = controller()
        val failures = failures(controller)
        controller.open("c1")
        runCurrent()

        controller.openEditor()
        controller.create("Ideas", description = "", addToChat = true)
        runCurrent()

        val state = controller.state.value
        assertThat(state.editorOpen).isFalse()
        assertThat(state.creating).isFalse()
        assertThat(state.attached).containsExactly("work")
        assertThat(state.error).isEqualTo(BookmarkPickerFailure.ToggleFailed("Ideas"))
        assertThat(failures).isEmpty()
    }

    @Test
    fun `a tick that fails behind the editor stays out of it and shows when the checklist is back`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        val answer = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.setConversationTag("c1", "work", true) } coAnswers { answer.await() }
        val controller = controller()
        val failures = failures(controller)
        controller.open("c1")
        runCurrent()

        controller.toggle("work")
        runCurrent()
        controller.openEditor()
        runCurrent()
        answer.complete(Result.Error(message = "boom"))
        runCurrent()

        // Not the editor's failure: it would read as the new bookmark's.
        assertThat(controller.state.value.editorOpen).isTrue()
        assertThat(controller.state.value.error).isNull()
        assertThat(controller.state.value.attached).isEmpty()

        controller.closeEditor()
        runCurrent()
        assertThat(controller.state.value.editorOpen).isFalse()
        assertThat(controller.state.value.error).isEqualTo(BookmarkPickerFailure.ToggleFailed("work"))
        assertThat(failures).isEmpty()
    }

    @Test
    fun `a tick that failed behind the editor shows on the checklist once a create brings it back`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        val answer = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.setConversationTag("c1", "work", true) } coAnswers { answer.await() }
        coEvery { repo.createTag("Ideas", null, "c1") } returns Result.Success(CreatedTag(ConversationTag(tag = "Ideas")))
        val controller = controller()
        controller.open("c1")
        runCurrent()

        controller.toggle("work")
        runCurrent()
        controller.openEditor()
        runCurrent()
        answer.complete(Result.Error(message = "boom"))
        runCurrent()
        controller.create("Ideas", description = "", addToChat = true)
        runCurrent()

        val state = controller.state.value
        assertThat(state.editorOpen).isFalse()
        assertThat(state.attached).containsExactly("Ideas")
        assertThat(state.error).isEqualTo(BookmarkPickerFailure.ToggleFailed("work"))
    }

    @Test
    fun `a tick that failed behind the editor stays held through a failed create`() = runTest {
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        val answer = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.setConversationTag("c1", "work", true) } coAnswers { answer.await() }
        coEvery { repo.createTag(any(), any(), any()) } returns Result.Error(message = "500")
        val controller = controller()
        controller.open("c1")
        runCurrent()

        controller.toggle("work")
        runCurrent()
        controller.openEditor()
        runCurrent()
        answer.complete(Result.Error(message = "boom"))
        runCurrent()
        controller.create("Ideas", description = "", addToChat = true)
        runCurrent()
        assertThat(controller.state.value.error).isEqualTo(BookmarkPickerFailure.CreateFailed("Ideas"))

        controller.closeEditor()
        runCurrent()
        assertThat(controller.state.value.error).isEqualTo(BookmarkPickerFailure.ToggleFailed("work"))
    }

    @Test
    fun `a bookmark made but not added after the sheet closed is reported as a failed tick`() = runTest {
        val answer = CompletableDeferred<Result<CreatedTag>>()
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.createTag("Ideas", null, "c1") } coAnswers { answer.await() }
        val controller = controller()
        val failures = failures(controller)
        controller.open("c1")
        runCurrent()
        controller.openEditor()
        controller.create("Ideas", description = "", addToChat = true)
        runCurrent()

        controller.close()
        answer.complete(Result.Success(CreatedTag(ConversationTag(tag = "Ideas"), attachFailed = true)))
        runCurrent()

        assertThat(failures).containsExactly(BookmarkPickerFailure.ToggleFailed("Ideas"))
        assertThat(controller.state.value.isOpen).isFalse()
    }

    @Test
    fun `an account switch drops the ticks and creates still out, and reports none of them`() = runTest {
        val tick = CompletableDeferred<Result<List<String>>>()
        val made = CompletableDeferred<Result<CreatedTag>>()
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.setConversationTag("c1", "work", true) } coAnswers { tick.await() }
        coEvery { repo.createTag("Ideas", null, "c1") } coAnswers { made.await() }
        val controller = controller()
        val failures = failures(controller)
        controller.open("c1")
        runCurrent()
        controller.toggle("work")
        controller.openEditor()
        controller.create("Ideas", description = "", addToChat = true)
        runCurrent()

        controller.closeForAccountSwitch()
        tick.complete(Result.Error(message = "x"))
        made.complete(Result.Error(message = "x"))
        runCurrent()

        assertThat(failures).isEmpty()
        assertThat(controller.state.value.isOpen).isFalse()
    }

    @Test
    fun `a plain close keeps a tick still out, and reports its failure`() = runTest {
        val tick = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.setConversationTag("c1", "work", true) } coAnswers { tick.await() }
        val controller = controller()
        val failures = failures(controller)
        controller.open("c1")
        runCurrent()
        controller.toggle("work")
        runCurrent()

        controller.close()
        tick.complete(Result.Error(message = "x"))
        runCurrent()

        assertThat(failures).containsExactly(BookmarkPickerFailure.ToggleFailed("work"))
    }

    @Test
    fun `a tick the repository stopped for an account switch is not reported after a close`() = runTest {
        val tick = CompletableDeferred<Result<List<String>>>()
        coEvery { repo.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { repo.setConversationTag("c1", "work", true) } coAnswers { tick.await() }
        val controller = controller()
        val failures = failures(controller)
        controller.open("c1")
        runCurrent()
        controller.toggle("work")
        runCurrent()

        controller.close()
        tick.complete(Result.Error(AccountSwitchedException()))
        runCurrent()

        assertThat(failures).isEmpty()
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
