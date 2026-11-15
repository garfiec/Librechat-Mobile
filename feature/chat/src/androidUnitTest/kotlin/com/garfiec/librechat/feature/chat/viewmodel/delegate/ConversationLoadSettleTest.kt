package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.DraftRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.request.NO_PARENT
import com.garfiec.librechat.feature.chat.util.branchSelectionFor
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationLoadHandle
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.MessageTreeHandle
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * `loadConversation`'s `onSettled` (what Keep and continue waits on before it continues), and how its
 * Room reconcile settles a just-finalized turn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationLoadSettleTest {

    @Test
    fun `a load that replaces another before it settles runs its onSettled`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fetches = ArrayDeque<CompletableDeferred<Unit>>()
        val messageRepository = mockk<MessageRepository>(relaxed = true)
        every { messageRepository.observeMessages(CONVERSATION_ID) } returns MutableStateFlow(emptyList<Message>())
        coEvery { messageRepository.getMessages(CONVERSATION_ID, any()) } coAnswers {
            CompletableDeferred<Unit>().also(fetches::addLast).await()
            Result.Success(emptyList())
        }
        val flow = MutableStateFlow(ChatUiState(conversation = ConversationMetaState(conversationId = CONVERSATION_ID)))
        val delegate = conversationLoadDelegate(ChatStateHandle(flow, scope), messageRepository, dispatcher)
        var settled = 0

        delegate.loadConversation(CONVERSATION_ID) { settled++ }
        runCurrent()
        // Another load (a stream end, a refresh) takes over while the first fetch is still out.
        delegate.loadConversation(CONVERSATION_ID)
        runCurrent()
        assertThat(settled).isEqualTo(0)

        fetches.last().complete(Unit)
        runCurrent()

        assertThat(settled).isEqualTo(1)
        scope.cancel()
    }

    @Test
    fun `a regenerate that finalizes over a pin stays on the new reply through the Room write`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val rows = listOf(
            message("u1", parentId = null, isUser = true),
            message("u2", parentId = null, isUser = true),
            message("a1", parentId = "u1"),
            message("a2", parentId = "u1"),
        )
        val reply = message("a3", parentId = "u1")
        val room = MutableStateFlow(rows)
        val messageRepository = mockk<MessageRepository>(relaxed = true)
        every { messageRepository.observeMessages(CONVERSATION_ID) } returns room
        coEvery { messageRepository.getMessages(CONVERSATION_ID, any()) } returns Result.Success(rows)
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = CONVERSATION_ID),
                content = MessagesState(
                    messages = rows,
                    activeBranches = branchSelectionFor(rows, "a1")!!,
                    isStreaming = true,
                ),
            ),
        )
        val root = ChatStateHandle(flow, scope)
        val loader = conversationLoadDelegate(root, messageRepository, dispatcher)
        val tree = MessageTreeDelegate(MessageTreeHandle(root))
        loader.loadConversation(CONVERSATION_ID)
        runCurrent()

        tree.finalizeChatDisplay(StreamEvent.Final(responseMessage = reply))
        // Every emission until the user pages again, unconflated: one frame on the old reply is the flash.
        val shown = mutableListOf<List<String>>()
        val recording = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { state -> shown += state.displayMessages.map { it.message.messageId } }
        }
        runCurrent()
        // Room has not caught up with the turn yet: the finished reply must still be the one shown.
        assertThat(flow.value.displayMessages.map { it.message.messageId }).containsExactly("u1", "a3").inOrder()
        assertThat(flow.value.messages.map { it.messageId }).contains("a3")

        room.value = rows + reply
        runCurrent()
        assertThat(flow.value.displayMessages.map { it.message.messageId }).containsExactly("u1", "a3").inOrder()
        assertThat(flow.value.activeBranches).isEqualTo(mapOf(NO_PARENT to 0))
        recording.cancel()
        assertThat(shown).isNotEmpty()
        assertThat(shown.map { it.lastOrNull() }.distinct()).containsExactly("a3")

        // Paging back afterwards is the user's pin again, not undone by the turn that just settled.
        tree.switchBranch("u1", 0)
        runCurrent()
        assertThat(flow.value.displayMessages.map { it.message.messageId }).containsExactly("u1", "a1").inOrder()
        scope.cancel()
    }

    private fun conversationLoadDelegate(
        root: ChatStateHandle,
        messageRepository: MessageRepository,
        dispatcher: CoroutineDispatcher,
    ) = ConversationLoadDelegate(
        handle = ConversationLoadHandle(root),
        messageRepository = messageRepository,
        conversationRepository = mockk<ConversationRepository>(relaxed = true),
        draftRepository = mockk<DraftRepository>(relaxed = true),
        defaultDispatcher = dispatcher,
        applyConversationModel = { false },
        onModelLoadSettled = {},
        rehydrateComparison = {},
        onMessagesSettled = {},
        currentModelSpecs = { null },
        currentAccountId = { "srv:user-1" },
    )

    private fun message(id: String, parentId: String?, isUser: Boolean = false) = Message(
        messageId = id,
        conversationId = CONVERSATION_ID,
        parentMessageId = parentId,
        text = "msg-$id",
        isCreatedByUser = isUser,
    )

    private companion object {
        const val CONVERSATION_ID = "conv-1"
    }
}
