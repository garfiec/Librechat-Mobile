package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.MessageEditingHandle
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Issue #579: nothing in the editor may close before the server has answered, and an assistant
 * rerun must carry the edit rather than quietly regenerating.
 *
 * The repository fakes RETURN `Result.Error` instead of throwing, because the real implementations
 * are `safeApiCall`-wrapped — a test that threw would exercise a path that cannot happen.
 */
class MessageEditingDelegateTest {

    private val userTurn = Message(
        messageId = "u1",
        conversationId = "c1",
        text = "summarize this",
        isCreatedByUser = true,
    )

    private fun reply(vararg parts: MessageContentPart) = Message(
        messageId = "a1",
        conversationId = "c1",
        parentMessageId = "u1",
        text = if (parts.isEmpty()) "the original answer" else "",
        content = parts.toList().ifEmpty { null },
    )

    private fun textPart(text: String) = MessageContentPart(type = ContentType.TEXT, text = text)
    private fun thinkPart(text: String) = MessageContentPart(type = ContentType.THINK, think = text)
    private fun toolPart() = MessageContentPart(type = ContentType.TOOL_CALL)

    private class Fixture(
        messages: List<Message>,
        isStreaming: Boolean = false,
        refuseSend: Boolean = false,
        val messageRepository: MessageRepository = mockk(relaxed = true),
    ) {
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = "c1"),
                content = MessagesState(messages = messages, isStreaming = isStreaming),
            ),
        )
        val chatRepository = RecordingChatRepository()
        val delegate = MessageEditingDelegate(
            handle = MessageEditingHandle(ChatStateHandle(flow, CoroutineScope(Dispatchers.Unconfined))),
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            treeDelegate = mockk(relaxed = true),
            streamingManager = mockk(relaxed = true),
            requestBuilder = mockk<ChatRequestBuilder>(relaxed = true),
            // The readiness gate normally lets the send through; `refuseSend` exercises the path
            // where it does not (no model selected, a readiness timeout, Compare setup opening).
            runWhenSendReady = { onRefused, action -> if (refuseSend) onRefused() else action() },
        )
        val state get() = flow.value
    }

    // ── Save: the editor survives a refusal ──────────────────────────

    @Test
    fun `a failed save keeps the editor open with the draft and reports the reason`() = runTest {
        val f = Fixture(listOf(userTurn))
        coEvery { f.messageRepository.updateMessageText(any(), any(), any()) } returns
            Result.Error(message = "flagged by the server")

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("summarize this briefly")
        f.delegate.saveEdit()

        assertThat(f.state.editingMessageId).isEqualTo("u1")
        assertThat(f.state.editingText).isEqualTo("summarize this briefly")
        assertThat(f.state.isSavingEdit).isFalse()
        // The server's own sentence, not the generic notice: a content filter says why.
        assertThat(f.state.error).isEqualTo("flagged by the server")
    }

    @Test
    fun `a failed save with no reason falls back to the notice`() = runTest {
        val f = Fixture(listOf(userTurn))
        coEvery { f.messageRepository.updateMessageText(any(), any(), any()) } returns
            Result.Error(message = null)

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("edited")
        f.delegate.saveEdit()

        assertThat(f.state.error).isEqualTo(ChatNotice.MESSAGE_SAVE_FAILED.marker())
        assertThat(f.state.editingText).isEqualTo("edited")
    }

    @Test
    fun `a successful save closes the editor`() = runTest {
        val f = Fixture(listOf(userTurn))
        coEvery { f.messageRepository.updateMessageText(any(), any(), any()) } returns Result.Success(Unit)

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("edited")
        f.delegate.saveEdit()

        assertThat(f.state.editingMessageId).isNull()
        assertThat(f.state.editingText).isEmpty()
        assertThat(f.state.error).isNull()
    }

    @Test
    fun `a second tap while a save is in flight issues one request`() = runTest {
        val f = Fixture(listOf(userTurn))
        val held = CompletableDeferred<Result<Unit>>()
        coEvery { f.messageRepository.updateMessageText(any(), any(), any()) } coAnswers { held.await() }

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("edited")
        f.delegate.saveEdit()
        assertThat(f.state.isSavingEdit).isTrue()
        f.delegate.saveEdit()

        coVerify(exactly = 1) { f.messageRepository.updateMessageText(any(), any(), any()) }
        held.complete(Result.Success(Unit))
    }

    @Test
    fun `a keystroke while a save is in flight is ignored`() = runTest {
        val f = Fixture(listOf(userTurn))
        val held = CompletableDeferred<Result<Unit>>()
        coEvery { f.messageRepository.updateMessageText(any(), any(), any()) } coAnswers { held.await() }

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("edited")
        f.delegate.saveEdit()
        f.delegate.onEditTextChanged("edited again")

        assertThat(f.state.editingText).isEqualTo("edited")
        held.complete(Result.Success(Unit))
    }

    // ── Save: the Room write must not land mid-stream ────────────────

    @Test
    fun `a save while streaming does not reach the repository`() = runTest {
        val f = Fixture(listOf(userTurn), isStreaming = true)

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("edited")
        f.delegate.saveEdit()

        // The write would re-emit through the loadConversation observer and un-truncate the
        // in-place streaming view (see the streaming-anchor invariant).
        coVerify(exactly = 0) { f.messageRepository.updateMessageText(any(), any(), any()) }
        assertThat(f.state.editingText).isEqualTo("edited")
    }

    @Test
    fun `a rerun while streaming keeps the draft instead of clearing it`() = runTest {
        val f = Fixture(listOf(userTurn), isStreaming = true)

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("edited")
        f.delegate.rerunEdit()

        assertThat(f.state.editingMessageId).isEqualTo("u1")
        assertThat(f.state.editingText).isEqualTo("edited")
        assertThat(f.chatRepository.sent).isEmpty()
    }

    // ── Seeding ──────────────────────────────────────────────────────

    @Test
    fun `a reply is seeded per editable part, with reasoning in its own field`() = runTest {
        val message = reply(thinkPart("let me think"), toolPart(), textPart("the answer"))
        val f = Fixture(listOf(userTurn, message))

        f.delegate.startEditing("a1")

        // Keyed by RAW content index, so the tool call at 1 is skipped but does not shift the rest.
        assertThat(f.state.editingPartDrafts).containsExactly(0, "let me think", 2, "the answer")
        // Nothing is folded into the flat-text field — that prefill used to concatenate the two.
        assertThat(f.state.editingText).isEmpty()
    }

    @Test
    fun `a reply with no content parts is seeded as flat text`() = runTest {
        val f = Fixture(listOf(userTurn, reply()))

        f.delegate.startEditing("a1")

        assertThat(f.state.editingText).isEqualTo("the original answer")
        assertThat(f.state.editingPartDrafts).isEmpty()
    }

    @Test
    fun `a reply with nothing editable does not open an editor`() = runTest {
        val f = Fixture(listOf(userTurn, reply(toolPart())))

        f.delegate.startEditing("a1")

        assertThat(f.state.editingMessageId).isNull()
    }

    // ── Per-part save ────────────────────────────────────────────────

    @Test
    fun `saving a changed part sends its index`() = runTest {
        val f = Fixture(listOf(userTurn, reply(thinkPart("thought"), textPart("answer"))))
        coEvery { f.messageRepository.updateMessageContentPart(any(), any(), any(), any()) } returns
            Result.Success(Unit)

        f.delegate.startEditing("a1")
        f.delegate.onEditPartTextChanged(1, "a better answer")
        f.delegate.saveEdit()

        coVerify(exactly = 1) {
            f.messageRepository.updateMessageContentPart("c1", "a1", 1, "a better answer")
        }
        assertThat(f.state.editingMessageId).isNull()
    }

    @Test
    fun `an unchanged part is not saved`() = runTest {
        val f = Fixture(listOf(userTurn, reply(textPart("answer"))))

        f.delegate.startEditing("a1")
        f.delegate.saveEdit()

        coVerify(exactly = 0) {
            f.messageRepository.updateMessageContentPart(any(), any(), any(), any())
        }
    }

    @Test
    fun `a partial save leaves only the refused part marked as changed`() = runTest {
        val f = Fixture(listOf(userTurn, reply(textPart("first"), textPart("second"))))
        coEvery { f.messageRepository.updateMessageContentPart("c1", "a1", 0, any()) } returns
            Result.Success(Unit)
        coEvery { f.messageRepository.updateMessageContentPart("c1", "a1", 1, any()) } returns
            Result.Error(message = "nope")

        f.delegate.startEditing("a1")
        f.delegate.onEditPartTextChanged(0, "first edited")
        f.delegate.onEditPartTextChanged(1, "second edited")
        f.delegate.saveEdit()

        // Nothing rolls a write back, so the editor has to reconcile with what actually landed:
        // part 0 is saved (no longer changed), part 1 still asks to be saved.
        assertThat(f.state.editingMessageId).isEqualTo("a1")
        assertThat(f.state.editChangedPartIndices).containsExactly(1)
        assertThat(f.state.editingPartDrafts[0]).isEqualTo("first edited")
        assertThat(f.state.error).isEqualTo("nope")
    }

    @Test
    fun `an emptied part is not saved`() = runTest {
        val f = Fixture(listOf(userTurn, reply(textPart("answer"))))

        f.delegate.startEditing("a1")
        f.delegate.onEditPartTextChanged(0, "   ")
        f.delegate.saveEdit()

        coVerify(exactly = 0) {
            f.messageRepository.updateMessageContentPart(any(), any(), any(), any())
        }
    }

    // ── Rerun ────────────────────────────────────────────────────────

    @Test
    fun `a rerun with one changed text part carries it and names the edited reply`() = runTest {
        val f = Fixture(listOf(userTurn, reply(thinkPart("thought"), textPart("answer"))))

        f.delegate.startEditing("a1")
        f.delegate.onEditPartTextChanged(1, "a better answer")
        f.delegate.rerunEdit()

        val sent = f.chatRepository.sent.single()
        assertThat(sent.editedContent?.index).isEqualTo(1)
        assertThat(sent.editedContent?.text).isEqualTo("a better answer")
        assertThat(sent.editedContent?.type).isEqualTo(ContentType.TEXT)
        // Without this the server resolves an unrelated branch and the prefix is dropped.
        assertThat(sent.responseMessageId).isEqualTo("a1")
        assertThat(sent.isEdited).isTrue()
        assertThat(sent.isRegenerate).isTrue()
        assertThat(sent.overrideParentMessageId).isEqualTo("u1")
        assertThat(sent.text).isEqualTo("summarize this")
    }

    @Test
    fun `a rerun with nothing changed is a plain regenerate`() = runTest {
        val f = Fixture(listOf(userTurn, reply(textPart("answer"))))

        f.delegate.startEditing("a1")
        f.delegate.rerunEdit()

        val sent = f.chatRepository.sent.single()
        assertThat(sent.editedContent).isNull()
        // Replaying the unchanged part as editedContent would keep this answer and append a second.
        assertThat(sent.responseMessageId).isNull()
        assertThat(sent.isEdited).isFalse()
        assertThat(sent.isRegenerate).isTrue()
    }

    @Test
    fun `a rerun with two changed parts sends nothing and waits for a save`() = runTest {
        val f = Fixture(listOf(userTurn, reply(textPart("first"), textPart("second"))))

        f.delegate.startEditing("a1")
        f.delegate.onEditPartTextChanged(0, "first edited")
        f.delegate.onEditPartTextChanged(1, "second edited")
        f.delegate.rerunEdit()

        assertThat(f.chatRepository.sent).isEmpty()
        assertThat(f.state.editingMessageId).isEqualTo("a1")
    }

    @Test
    fun `a rerun with a changed reasoning part sends nothing`() = runTest {
        val f = Fixture(listOf(userTurn, reply(thinkPart("thought"), textPart("answer"))))

        f.delegate.startEditing("a1")
        f.delegate.onEditPartTextChanged(0, "a different thought")
        f.delegate.rerunEdit()

        // Servers in the supported range read `text` out of editedContent whatever its type says,
        // so a think payload would wipe the reasoning prefix. Save handles it instead.
        assertThat(f.chatRepository.sent).isEmpty()
    }

    @Test
    fun `a flat-text rerun on a reply regenerates without carrying the draft`() = runTest {
        val f = Fixture(listOf(userTurn, reply()))

        f.delegate.startEditing("a1")
        f.delegate.onEditTextChanged("a hand-written answer")
        f.delegate.rerunEdit()

        val sent = f.chatRepository.sent.single()
        assertThat(sent.editedContent).isNull()
        assertThat(sent.text).isEqualTo("summarize this")
        assertThat(sent.isRegenerate).isTrue()
    }

    @Test
    fun `editing a user turn resubmits the draft as a new sibling`() = runTest {
        val f = Fixture(listOf(userTurn))

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("summarize this briefly")
        f.delegate.rerunEdit()

        val sent = f.chatRepository.sent.single()
        assertThat(sent.text).isEqualTo("summarize this briefly")
        assertThat(sent.isEdited).isTrue()
        assertThat(sent.isRegenerate).isFalse()
        assertThat(sent.editedContent).isNull()
        assertThat(sent.userMessageId).isNotNull()
    }

    @Test
    fun `a rerun with no user turn to replay sends nothing`() = runTest {
        // An imported reply chained onto another reply: its parent is not the user's.
        val orphan = Message(messageId = "a2", conversationId = "c1", parentMessageId = "a1", text = "chained")
        val f = Fixture(listOf(reply(), orphan))

        f.delegate.startEditing("a2")
        f.delegate.rerunEdit()

        assertThat(f.chatRepository.sent).isEmpty()
    }

    // ── Baselines ────────────────────────────────────────────────────

    @Test
    fun `the flat-text editor records what it opened with`() = runTest {
        val f = Fixture(listOf(userTurn))

        f.delegate.startEditing("u1")

        // The UI measures "unsaved changes" against this, not against the live message, so a
        // background revalidate landing mid-edit cannot read the user's typing as unchanged.
        assertThat(f.state.editing.editingTextOriginal).isEqualTo("summarize this")
        assertThat(f.state.editing.isEditingParts).isFalse()
    }

    @Test
    fun `a refused rerun restores the draft and its baseline`() = runTest {
        val f = Fixture(listOf(userTurn), refuseSend = true)

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("summarize this briefly")
        f.delegate.rerunEdit()

        assertThat(f.state.editingText).isEqualTo("summarize this briefly")
        // Without the baseline the reopened editor would read as unchanged and grey out Save.
        assertThat(f.state.editing.editingTextOriginal).isEqualTo("summarize this")
        assertThat(f.chatRepository.sent).isEmpty()
    }

    @Test
    fun `a refused part rerun restores the drafts and their baselines`() = runTest {
        val f = Fixture(listOf(userTurn, reply(textPart("answer"))), refuseSend = true)

        f.delegate.startEditing("a1")
        f.delegate.onEditPartTextChanged(0, "a better answer")
        f.delegate.rerunEdit()

        assertThat(f.state.editingPartDrafts).containsExactly(0, "a better answer")
        assertThat(f.state.editing.partOriginals).containsExactly(0, "answer")
        assertThat(f.state.editChangedPartIndices).containsExactly(0)
    }

    @Test
    fun `the editing state says which editor is open`() = runTest {
        val f = Fixture(listOf(userTurn, reply(textPart("answer"))))

        f.delegate.startEditing("a1")

        // The one discriminator both the delegate's routing and the renderer read.
        assertThat(f.state.editing.isEditingParts).isTrue()
    }

    // ── A user turn that carries content parts ───────────────────────

    @Test
    fun `a user turn with content parts reruns from its joined text parts`() = runTest {
        // The Assistants thread sync persists a user turn this way, so it opens the parts editor.
        val userParts = Message(
            messageId = "u2",
            conversationId = "c1",
            text = "",
            isCreatedByUser = true,
            content = listOf(textPart("first line"), thinkPart("not the user's"), textPart("second line")),
        )
        val f = Fixture(listOf(userParts))

        f.delegate.startEditing("u2")
        f.delegate.onEditPartTextChanged(0, "first line edited")
        f.delegate.rerunEdit()

        val sent = f.chatRepository.sent.single()
        // Newline-joined, in content order, reasoning left out — a user turn does not reason.
        assertThat(sent.text).isEqualTo("first line edited\nsecond line")
        assertThat(sent.editedContent).isNull()
        assertThat(sent.isRegenerate).isFalse()
        assertThat(sent.userMessageId).isNotNull()
    }

    @Test
    fun `a user turn with two changed parts still reruns`() = runTest {
        val userParts = Message(
            messageId = "u2",
            conversationId = "c1",
            isCreatedByUser = true,
            content = listOf(textPart("a"), textPart("b")),
        )
        val f = Fixture(listOf(userParts))

        f.delegate.startEditing("u2")
        f.delegate.onEditPartTextChanged(0, "a edited")
        f.delegate.onEditPartTextChanged(1, "b edited")
        f.delegate.rerunEdit()

        // The one-part limit is an `editedContent` constraint, and a user turn sends none.
        assertThat(f.chatRepository.sent.single().text).isEqualTo("a edited\nb edited")
    }

    @Test
    fun `a user turn emptied of text and files does not rerun`() = runTest {
        val userParts = Message(
            messageId = "u2",
            conversationId = "c1",
            isCreatedByUser = true,
            content = listOf(textPart("a")),
        )
        val f = Fixture(listOf(userParts))

        f.delegate.startEditing("u2")
        f.delegate.onEditPartTextChanged(0, "   ")
        f.delegate.rerunEdit()

        assertThat(f.chatRepository.sent).isEmpty()
    }

    // ── The editor may move while a save is in flight ────────────────

    @Test
    fun `a save landing after the editor moved on does not close the new editor`() = runTest {
        val f = Fixture(listOf(userTurn, reply(textPart("answer"))))
        val held = CompletableDeferred<Result<Unit>>()
        coEvery { f.messageRepository.updateMessageText(any(), any(), any()) } coAnswers { held.await() }

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("edited")
        f.delegate.saveEdit()
        // Edit tapped on another row while the first save is still out.
        f.delegate.startEditing("a1")
        f.delegate.onEditPartTextChanged(0, "answer edited")
        held.complete(Result.Success(Unit))

        // Closing here would discard the second message's draft — the loss #579 is about.
        assertThat(f.state.editingMessageId).isEqualTo("a1")
        assertThat(f.state.editingPartDrafts).containsExactly(0, "answer edited")
    }

    @Test
    fun `a failed save landing after the editor moved on reports without touching the drafts`() = runTest {
        // Two parts, the first saved and the second held: `saved` has to be NON-empty for the
        // splice to be observable at all — with nothing saved the unguarded handler merges an
        // empty map and the assertion passes either way.
        val f = Fixture(listOf(userTurn, reply(textPart("first"), textPart("second"))))
        val held = CompletableDeferred<Result<Unit>>()
        coEvery { f.messageRepository.updateMessageContentPart("c1", "a1", 0, any()) } returns Result.Success(Unit)
        coEvery { f.messageRepository.updateMessageContentPart("c1", "a1", 1, any()) } coAnswers { held.await() }

        f.delegate.startEditing("a1")
        f.delegate.onEditPartTextChanged(0, "first edited")
        f.delegate.onEditPartTextChanged(1, "second edited")
        f.delegate.saveEdit()
        // Edit tapped on the user turn while part 1 is still out.
        f.delegate.startEditing("u1")
        held.complete(Result.Error(message = "nope"))

        assertThat(f.state.error).isEqualTo("nope")
        assertThat(f.state.editingMessageId).isEqualTo("u1")
        // The reply's saved part must not become a baseline of the user turn's editor, where its
        // index means nothing and would mark untouched text as unsaved.
        assertThat(f.state.editing.partOriginals).isEmpty()
        assertThat(f.state.editingText).isEqualTo("summarize this")
        assertThat(f.state.isSavingEdit).isFalse()
    }

    @Test
    fun `a second Edit on the row being saved neither reopens it nor lets a second save through`() = runTest {
        val f = Fixture(listOf(userTurn))
        val held = CompletableDeferred<Result<Unit>>()
        coEvery { f.messageRepository.updateMessageText(any(), any(), any()) } coAnswers { held.await() }

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("edited")
        f.delegate.saveEdit()
        // Edit tapped again on the SAME row while its save is still out.
        f.delegate.startEditing("u1")

        // Reseeding here would clear `isSavingEdit` and re-enable Save over a write already in
        // flight, and the in-flight result would then close an editor holding a different draft.
        assertThat(f.state.isSavingEdit).isTrue()
        assertThat(f.state.editingText).isEqualTo("edited")
        f.delegate.saveEdit()
        coVerify(exactly = 1) { f.messageRepository.updateMessageText(any(), any(), any()) }

        held.complete(Result.Success(Unit))
        assertThat(f.state.editingMessageId).isNull()
    }

    @Test
    fun `a run starting part-way through a part save stops the remaining writes`() = runTest {
        val f = Fixture(listOf(userTurn, reply(textPart("first"), textPart("second"))))
        coEvery { f.messageRepository.updateMessageContentPart("c1", "a1", 0, any()) } coAnswers {
            // A queued message drains, or another client's run is adopted, mid-sequence.
            f.flow.value = f.flow.value.let { it.copy(content = it.content.copy(isStreaming = true)) }
            Result.Success(Unit)
        }

        f.delegate.startEditing("a1")
        f.delegate.onEditPartTextChanged(0, "first edited")
        f.delegate.onEditPartTextChanged(1, "second edited")
        f.delegate.saveEdit()

        // Each write reaches Room, which re-emits through the loadConversation observer and
        // un-truncates the streaming anchor, so the sequence stops instead of finishing.
        coVerify(exactly = 0) { f.messageRepository.updateMessageContentPart("c1", "a1", 1, any()) }
        assertThat(f.state.editChangedPartIndices).containsExactly(1)
        assertThat(f.state.isSavingEdit).isFalse()
    }

    @Test
    fun `an image-only user turn still reruns on its attachments alone`() = runTest {
        // Flat-text editor, blank draft, files retained: the composer would send this, so the
        // rerun must too rather than leaving an enabled button that does nothing.
        val imageTurn = userTurn.copy(text = "", files = listOf(FileReference(fileId = "file_1")))
        val f = Fixture(listOf(imageTurn))

        f.delegate.startEditing("u1")
        f.delegate.rerunEdit()

        val sent = f.chatRepository.sent.single()
        assertThat(sent.text).isEmpty()
        assertThat(sent.isEdited).isTrue()
    }

    @Test
    fun `cancelling clears every draft`() = runTest {
        val f = Fixture(listOf(userTurn, reply(textPart("answer"))))

        f.delegate.startEditing("a1")
        f.delegate.onEditPartTextChanged(0, "edited")
        f.delegate.cancelEditing()

        assertThat(f.state.editingMessageId).isNull()
        assertThat(f.state.editingPartDrafts).isEmpty()
        assertThat(f.state.editChangedPartIndices).isEmpty()
    }
}
