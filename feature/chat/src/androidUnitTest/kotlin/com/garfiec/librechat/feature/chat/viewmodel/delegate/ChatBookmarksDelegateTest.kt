package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.feature.chat.viewmodel.BookmarksHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.FeatureGatesState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatBookmarksDelegateTest {

    /** The cached row's tags, per chat. */
    private val cached = mutableMapOf<String, MutableStateFlow<List<String>>>()

    private val tagRepository = mockk<TagRepository>(relaxed = true) {
        every { observeTags() } returns MutableStateFlow(
            listOf(ConversationTag(tag = "work", position = 0), ConversationTag(tag = SAVED_TAG, position = 1)),
        )
        every { observeConversationTags(any()) } answers { cachedTags(firstArg()) }
    }

    private fun cachedTags(conversationId: String) = cached.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    private fun chat(
        conversationId: String? = "c1",
        temporary: Boolean = false,
        bookmarksEnabled: Boolean = true,
    ) = ChatUiState(
        conversation = ConversationMetaState(conversationId = conversationId, isTemporaryChat = temporary),
        gates = FeatureGatesState(bookmarksEnabled = bookmarksEnabled),
    )

    private class Harness(val root: ChatStateHandle, val delegate: ChatBookmarksDelegate)

    private fun TestScope.harness(initial: ChatUiState): Harness {
        val root = ChatStateHandle(MutableStateFlow(initial), backgroundScope)
        return Harness(root, ChatBookmarksDelegate(BookmarksHandle(root), tagRepository))
    }

    // ── the menu icon ─────────────────────────────────────────────────────

    @Test
    fun `a cached bookmark fills the icon, and Saved alone doesn't`() = runTest {
        cachedTags("c1").value = listOf(SAVED_TAG)
        val h = harness(chat())
        runCurrent()
        assertThat(h.root.state.hasBookmark).isFalse()

        cachedTags("c1").value = listOf(SAVED_TAG, "work")
        runCurrent()

        assertThat(h.root.state.hasBookmark).isTrue()
    }

    @Test
    fun `the checklist's server read wins over the cached row once it lands`() = runTest {
        val read = CompletableDeferred<Result<List<String>>>()
        coEvery { tagRepository.getConversationTags("c1") } coAnswers { read.await() }
        val h = harness(chat())
        runCurrent()
        assertThat(h.root.state.hasBookmark).isFalse()

        h.delegate.open()
        runCurrent()
        // Still reading: the cached row stands.
        assertThat(h.root.state.hasBookmark).isFalse()

        read.complete(Result.Success(listOf("work")))
        runCurrent()

        assertThat(h.root.state.hasBookmark).isTrue()
    }

    @Test
    fun `unticking the last bookmark empties the icon at once`() = runTest {
        cachedTags("c1").value = listOf("work")
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Success(listOf("work"))
        val saved = CompletableDeferred<Result<List<String>>>()
        coEvery { tagRepository.setConversationTag("c1", "work", false) } coAnswers { saved.await() }
        val h = harness(chat())
        h.delegate.open()
        runCurrent()
        assertThat(h.root.state.hasBookmark).isTrue()

        h.delegate.picker.toggle("work")
        runCurrent()

        assertThat(h.root.state.hasBookmark).isFalse()
    }

    @Test
    fun `another chat reads its own row`() = runTest {
        cachedTags("c1").value = listOf("work")
        val h = harness(chat())
        runCurrent()
        assertThat(h.root.state.hasBookmark).isTrue()

        h.root.stateFlow.update { it.copy(conversation = it.conversation.copy(conversationId = "c2")) }
        runCurrent()

        assertThat(h.root.state.hasBookmark).isFalse()
    }

    // ── which chats ───────────────────────────────────────────────────────

    @Test
    fun `a temporary chat is never read and never opens the checklist`() = runTest {
        val h = harness(chat(temporary = true))
        runCurrent()

        h.delegate.open()
        runCurrent()

        assertThat(h.delegate.picker.state.value.isOpen).isFalse()
        assertThat(h.root.state.hasBookmark).isFalse()
        verify(exactly = 0) { tagRepository.observeConversationTags(any()) }
        coVerify(exactly = 0) { tagRepository.getConversationTags(any()) }
    }

    @Test
    fun `a new chat has no checklist`() = runTest {
        val h = harness(chat(conversationId = null))
        runCurrent()

        h.delegate.open()
        runCurrent()

        assertThat(h.delegate.picker.state.value.isOpen).isFalse()
        coVerify(exactly = 0) { tagRepository.getConversationTags(any()) }
    }

    @Test
    fun `a chat whose first turn is still saving has no checklist`() = runTest {
        val streaming = chat().copy(
            content = MessagesState(messages = listOf(Message(messageId = "u1", conversationId = "c1")), isStreaming = true),
        )
        val h = harness(streaming)
        runCurrent()

        h.delegate.open()
        runCurrent()

        assertThat(h.delegate.picker.state.value.isOpen).isFalse()
    }

    @Test
    fun `bookmarks turned off hide the checklist and the icon`() = runTest {
        cachedTags("c1").value = listOf("work")
        val h = harness(chat(bookmarksEnabled = false))
        runCurrent()

        h.delegate.open()
        runCurrent()

        assertThat(h.delegate.picker.state.value.isOpen).isFalse()
        assertThat(h.root.state.hasBookmark).isFalse()
    }

    @Test
    fun `opening reads the chat's bookmarks from the server`() = runTest {
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Success(listOf("work", SAVED_TAG))
        val h = harness(chat())

        h.delegate.open()
        runCurrent()

        val state = h.delegate.picker.state.value
        assertThat(state.conversationId).isEqualTo("c1")
        assertThat(state.attached).containsExactly("work")
        assertThat(state.bookmarks.map { it.tag }).containsExactly("work")
    }

    // ── closing ───────────────────────────────────────────────────────────

    @Test
    fun `the checklist closes when bookmarks turn off`() = runTest {
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Success(emptyList())
        val h = harness(chat())
        h.delegate.open()
        runCurrent()
        assertThat(h.delegate.picker.state.value.isOpen).isTrue()

        h.root.stateFlow.update { it.copy(gates = it.gates.copy(bookmarksEnabled = false)) }
        runCurrent()

        assertThat(h.delegate.picker.state.value.isOpen).isFalse()
    }

    @Test
    fun `the checklist closes when the chat changes`() = runTest {
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Success(emptyList())
        val h = harness(chat())
        h.delegate.open()
        runCurrent()

        h.root.stateFlow.update { it.copy(conversation = it.conversation.copy(conversationId = "c2")) }
        runCurrent()

        assertThat(h.delegate.picker.state.value.isOpen).isFalse()
    }

    // ── failures ──────────────────────────────────────────────────────────

    @Test
    fun `a failed read closes the checklist and says so`() = runTest {
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Error(message = "offline")
        val h = harness(chat())
        runCurrent()

        h.delegate.open()
        runCurrent()

        assertThat(h.delegate.picker.state.value.isOpen).isFalse()
        assertThat(h.root.state.error).isEqualTo(ChatNotice.BOOKMARKS_LOAD_FAILED.marker())
    }

    @Test
    fun `a failed tick is put back and says so`() = runTest {
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { tagRepository.setConversationTag("c1", "work", true) } returns Result.Error(message = "offline")
        val h = harness(chat())
        runCurrent()
        h.delegate.open()
        runCurrent()

        h.delegate.picker.toggle("work")
        runCurrent()

        assertThat(h.delegate.picker.state.value.attached).isEmpty()
        assertThat(h.root.state.hasBookmark).isFalse()
        assertThat(h.root.state.error).isEqualTo(ChatNotice.BOOKMARK_UPDATE_FAILED.marker())
    }

    @Test
    fun `a failed create says so`() = runTest {
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { tagRepository.createTag("trips", null, "c1") } returns Result.Error(message = "offline")
        val h = harness(chat())
        runCurrent()
        h.delegate.open()
        runCurrent()
        h.delegate.picker.openEditor()

        h.delegate.picker.create("trips", "", addToChat = true)
        runCurrent()

        assertThat(h.root.state.error).isEqualTo(ChatNotice.BOOKMARK_CREATE_FAILED.marker())
    }
}
