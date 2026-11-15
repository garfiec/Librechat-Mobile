package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.request.NO_PARENT
import com.garfiec.librechat.feature.chat.util.branchSelectionFor
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.viewmodel.ChatScreenState
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.MessageTreeHandle
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.RetryInfo
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.junit.Test

/**
 * Tests the in-memory finalize that drives the gap-free completion view for both
 * normal and temporary chats. The merge itself is covered by TemporaryChatMergeTest;
 * here we assert the delegate wires it into displayMessages, reconciles the optimistic
 * user message by id, preserves untouched instances, and marks the screen ACTIVE.
 */
class MessageTreeDelegateTest {

    private fun message(
        id: String,
        parentId: String? = null,
        text: String = "msg-$id",
        isUser: Boolean = false,
    ) = Message(
        messageId = id,
        conversationId = "conv-1",
        parentMessageId = parentId,
        text = text,
        isCreatedByUser = isUser,
    )

    private fun delegateWith(state: ChatUiState): Pair<MessageTreeDelegate, MutableStateFlow<ChatUiState>> {
        val flow = MutableStateFlow(state)
        val handle = ChatStateHandle(flow, CoroutineScope(Dispatchers.Unconfined))
        return MessageTreeDelegate(MessageTreeHandle(handle)) to flow
    }

    @Test
    fun `finalizeChatDisplay reconciles optimistic user message by id and appends the reply`() {
        val optimistic = message("u1", text = "hi", isUser = true)
        val (delegate, flow) = delegateWith(
            ChatUiState(
                content = MessagesState(
                    messages = listOf(optimistic),
                    displayMessages = buildActiveMessagePath(listOf(optimistic)),
                    isStreaming = true,
                ),
            ),
        )

        val event = StreamEvent.Final(
            requestMessage = message("u1", text = "hi", isUser = true),
            responseMessage = message("a1", parentId = "u1", text = "hello there"),
        )

        delegate.finalizeChatDisplay(event)

        val result = flow.value
        assertThat(result.messages.map { it.messageId }).containsExactly("u1", "a1").inOrder()
        assertThat(result.displayMessages.map { it.message.messageId }).containsExactly("u1", "a1").inOrder()
        assertThat(result.displayMessages.last().message.text).isEqualTo("hello there")
        assertThat(result.screenState).isEqualTo(ChatScreenState.ACTIVE)
    }

    @Test
    fun `finalizeChatDisplay clears a lingering retry banner`() {
        val optimistic = message("u1", isUser = true)
        val (delegate, flow) = delegateWith(
            ChatUiState(
                content = MessagesState(
                    messages = listOf(optimistic),
                    isStreaming = true,
                    retryInfo = RetryInfo(attempt = 2, maxAttempts = 5),
                ),
            ),
        )

        delegate.finalizeChatDisplay(
            StreamEvent.Final(responseMessage = message("a1", parentId = "u1")),
        )

        assertThat(flow.value.content.retryInfo).isNull()
    }

    @Test
    fun `finalizeChatDisplay preserves identity of untouched messages`() {
        val priorUser = message("u1", isUser = true)
        val priorAi = message("a1", parentId = "u1")
        val optimistic = message("u2", parentId = "a1", isUser = true)
        val (delegate, flow) = delegateWith(
            ChatUiState(
                content = MessagesState(
                    messages = listOf(priorUser, priorAi, optimistic),
                    isStreaming = true,
                ),
            ),
        )

        val event = StreamEvent.Final(
            requestMessage = message("u2", parentId = "a1", isUser = true),
            responseMessage = message("a2", parentId = "u2", text = "reply"),
        )

        delegate.finalizeChatDisplay(event)

        val merged = flow.value.messages
        // Untouched earlier turns keep their exact instances (no re-mount on recompose).
        assertThat(merged[0]).isSameInstanceAs(priorUser)
        assertThat(merged[1]).isSameInstanceAs(priorAi)
        assertThat(merged.map { it.messageId }).containsExactly("u1", "a1", "u2", "a2").inOrder()
    }

    @Test
    fun `finalizeChatDisplay with no final messages is a no-op`() {
        val optimistic = message("u1", isUser = true)
        val (delegate, flow) = delegateWith(
            ChatUiState(content = MessagesState(messages = listOf(optimistic), isStreaming = true)),
        )

        delegate.finalizeChatDisplay(StreamEvent.Final())

        assertThat(flow.value.messages).containsExactly(optimistic)
    }

    @Test
    fun `finalizeChatDisplay returns the merged instances for caching`() {
        // The optimistic message is richer than the frame's skeletal request; what gets cached
        // must be the merged (gap-filled) copy the screen shows, not the frame's.
        val optimistic = message("u1", text = "hi", isUser = true).copy(sender = "User", createdAt = "t0")
        val (delegate, _) = delegateWith(
            ChatUiState(content = MessagesState(messages = listOf(optimistic), isStreaming = true)),
        )
        val event = StreamEvent.Final(
            requestMessage = message("u1", text = "hi", isUser = true), // skeletal
            responseMessage = message("a1", parentId = "u1", text = "reply"),
        )

        val turn = delegate.finalizeChatDisplay(event)

        assertThat(turn.map { it.messageId }).containsExactly("u1", "a1").inOrder()
        assertThat(turn.first().sender).isEqualTo("User")
        assertThat(turn.first().createdAt).isEqualTo("t0")
    }

    @Test
    fun `finalizeChatDisplay shows a regenerated reply over a search-jump pin and drops only that pin in a temp chat`() {
        val rows = listOf(
            message("u1", isUser = true),
            message("u2", isUser = true),
            message("a1", parentId = "u1"),
            message("a2", parentId = "u1"),
        )
        val (delegate, flow) = delegateWith(
            ChatUiState(
                content = MessagesState(
                    messages = rows,
                    activeBranches = branchSelectionFor(rows, "a1")!!,
                    isStreaming = true,
                ),
                conversation = ConversationMetaState(conversationId = "conv-1", isTemporaryChat = true),
            ),
        )

        delegate.finalizeChatDisplay(StreamEvent.Final(responseMessage = message("a3", parentId = "u1")))

        assertThat(flow.value.displayMessages.map { it.message.messageId }).containsExactly("u1", "a3").inOrder()
        assertThat(flow.value.activeBranches).isEqualTo(mapOf(NO_PARENT to 0))
    }

    @Test
    fun `finalizeChatDisplay shows an edited version over a pinned branch without writing the pin in a normal chat`() {
        val rows = listOf(
            message("u1", isUser = true),
            message("a1", parentId = "u1"),
            message("u1e", isUser = true),
        )
        val pins = branchSelectionFor(rows, "a1")!!
        val (delegate, flow) = delegateWith(
            ChatUiState(
                content = MessagesState(messages = rows, activeBranches = pins, isStreaming = true),
                conversation = ConversationMetaState(conversationId = "conv-1"),
            ),
        )

        delegate.finalizeChatDisplay(
            StreamEvent.Final(
                requestMessage = message("u1e", isUser = true),
                responseMessage = message("a1e", parentId = "u1e"),
            ),
            optimisticUserMessageId = "u1e",
        )

        assertThat(flow.value.displayMessages.map { it.message.messageId }).containsExactly("u1e", "a1e").inOrder()
        // Room still holds the pre-turn rows; a branch write here would rebuild from them.
        assertThat(flow.value.activeBranches).isEqualTo(pins)
    }

    @Test
    fun `finalizeChatDisplay shows an edit stopped before its reply was saved over a pinned branch`() {
        val rows = listOf(
            message("u1", isUser = true),
            message("a1", parentId = "u1"),
            message("u1e", isUser = true),
        )
        val pins = branchSelectionFor(rows, "a1")!!
        val (delegate, flow) = delegateWith(
            ChatUiState(
                content = MessagesState(messages = rows, activeBranches = pins, isStreaming = true),
                conversation = ConversationMetaState(conversationId = "conv-1"),
            ),
        )

        delegate.finalizeChatDisplay(
            StreamEvent.Final(requestMessage = message("u1e", isUser = true)),
            optimisticUserMessageId = "u1e",
        )

        assertThat(flow.value.displayMessages.map { it.message.messageId }).containsExactly("u1e")
    }

    @Test
    fun `finalizeChatDisplay shows a re-attached edit over a pinned branch with no optimistic id`() {
        val rows = listOf(
            message("u1", isUser = true),
            message("a1", parentId = "u1"),
            message("u1e", isUser = true),
        )
        val (delegate, flow) = delegateWith(
            ChatUiState(
                content = MessagesState(messages = rows, activeBranches = branchSelectionFor(rows, "a1")!!, isStreaming = true),
                conversation = ConversationMetaState(conversationId = "conv-1"),
            ),
        )

        delegate.finalizeChatDisplay(
            StreamEvent.Final(
                requestMessage = message("u1e", isUser = true),
                responseMessage = message("a1e", parentId = "u1e"),
            ),
        )

        assertThat(flow.value.displayMessages.map { it.message.messageId }).containsExactly("u1e", "a1e").inOrder()
    }

    @Test
    fun `finalizeChatDisplay keeps a compaction summary on the pinned branch it was made on`() {
        val rows = listOf(
            message("u1", isUser = true),
            message("a1", parentId = "u1"),
            message("a2", parentId = "u1"),
        )
        val pins = mapOf("u1" to 0)
        val (delegate, flow) = delegateWith(
            ChatUiState(
                content = MessagesState(messages = rows, activeBranches = pins, isStreaming = true, isCompacting = true),
                conversation = ConversationMetaState(conversationId = "conv-1"),
            ),
        )

        delegate.finalizeChatDisplay(
            StreamEvent.Final(
                requestMessage = message("a1", parentId = "u1", text = ""),
                responseMessage = message("s1", parentId = "a1"),
            ),
        )

        assertThat(flow.value.displayMessages.map { it.message.messageId }).containsExactly("u1", "a1", "s1").inOrder()
    }

    @Test
    fun `finalizeChatDisplay keeps the pin when the reply was already in the tree`() {
        val rows = listOf(
            message("u1", isUser = true),
            message("a1", parentId = "u1"),
            message("a2", parentId = "u1"),
        )
        val pins = branchSelectionFor(rows, "a1")!!
        val (delegate, flow) = delegateWith(
            ChatUiState(
                content = MessagesState(messages = rows, activeBranches = pins, isStreaming = true),
                conversation = ConversationMetaState(conversationId = "conv-1", isTemporaryChat = true),
            ),
        )

        delegate.finalizeChatDisplay(StreamEvent.Final(responseMessage = message("a1", parentId = "u1", text = "more")))

        assertThat(flow.value.displayMessages.map { it.message.messageId }).containsExactly("u1", "a1").inOrder()
        assertThat(flow.value.activeBranches).isEqualTo(pins)
    }

    @Test
    fun `unsendOptimisticTurn removes the minted message and clears streaming in one emission`() {
        val prior = message("p1", isUser = true)
        val optimistic = message("u1", parentId = "p1", text = "unsent", isUser = true)
        val (delegate, flow) = delegateWith(
            ChatUiState(
                content = MessagesState(
                    messages = listOf(prior, optimistic),
                    displayMessages = buildActiveMessagePath(listOf(prior, optimistic)),
                    isStreaming = true,
                    streamingContent = "half",
                ),
            ),
        )
        // Count state emissions during the call: removal + path rebuild + streaming clear must
        // be ONE update (the completion-flash discipline, #169). An Unconfined collector on a
        // StateFlow observes every assignment synchronously.
        var emissions = 0
        val collector = CoroutineScope(Dispatchers.Unconfined).launch {
            flow.drop(1).collect { emissions++ }
        }
        delegate.unsendOptimisticTurn("u1")
        collector.cancel()

        val after = flow.value
        assertThat(emissions).isEqualTo(1)
        assertThat(after.messages.map { it.messageId }).containsExactly("p1")
        assertThat(after.displayMessages.map { it.message.messageId }).containsExactly("p1")
        assertThat(after.isStreaming).isFalse()
        assertThat(after.streamingContent).isEmpty()
    }

    @Test
    fun `unsendOptimisticTurn with a null id keeps the tree and only clears streaming`() {
        // Regenerate/continue/edit-AI: the turn's user message is a persisted row — never remove it.
        val persisted = message("u1", isUser = true)
        val (delegate, flow) = delegateWith(
            ChatUiState(
                content = MessagesState(
                    messages = listOf(persisted),
                    isStreaming = true,
                    streamingContent = "half",
                ),
            ),
        )

        delegate.unsendOptimisticTurn(null)

        assertThat(flow.value.messages).containsExactly(persisted)
        assertThat(flow.value.isStreaming).isFalse()
        assertThat(flow.value.streamingContent).isEmpty()
    }
}
