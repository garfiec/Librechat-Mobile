package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.request.AddedConversation
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.FeatureGatesState
import com.garfiec.librechat.feature.chat.viewmodel.MessageEditingHandle
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

/**
 * In a comparison, an edit or regenerate re-runs both models, so it carries Compare's second model
 * and resets both panes first. Compaction summarizes the branch and stays the primary's
 * alone, and so does a continue (a comparison's panes keep one lane before continuing it).
 */
class MessageEditingComparisonTest {

    private val userTurn = Message(
        messageId = "u1",
        conversationId = "c1",
        parentMessageId = "root",
        text = "compare these",
        isCreatedByUser = true,
    )
    private val reply = Message(messageId = "a1", conversationId = "c1", parentMessageId = "u1", text = "both answers")

    private class Fixture(messages: List<Message>, comparing: Boolean) {
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = "c1"),
                content = MessagesState(
                    messages = messages,
                    displayMessages = messages.map { MessageNode(it, emptyList(), 0, 1) },
                ),
                selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", "gpt-4o")),
                gates = FeatureGatesState(compactionEnabled = true),
            ),
        )
        val chatRepository = RecordingChatRepository()
        val events = mutableListOf<String>()
        val delegate = MessageEditingDelegate(
            handle = MessageEditingHandle(ChatStateHandle(flow, CoroutineScope(Dispatchers.Unconfined))),
            chatRepository = chatRepository,
            messageRepository = mockk(relaxed = true),
            treeDelegate = mockk(relaxed = true),
            streamingManager = mockk(relaxed = true),
            requestBuilder = mockk<ChatRequestBuilder>(relaxed = true),
            runWhenSendReady = { _, action -> action() },
            onComparisonSendStart = { events += "start" },
            buildAddedConvo = { parent ->
                events += "build"
                if (comparing) AddedConversation(endpoint = "anthropic", model = "claude", parentMessageId = parent) else null
            },
        )
        val sent get() = chatRepository.sent.single()
    }

    @Test
    fun `a regenerate while comparing re-runs the second model too`() {
        val f = Fixture(listOf(userTurn, reply), comparing = true)

        f.delegate.regenerateMessage("a1")

        assertThat(f.sent.addedConvo?.endpoint).isEqualTo("anthropic")
        // Parented as the primary is, on the replayed turn's parent.
        assertThat(f.sent.addedConvo?.parentMessageId).isEqualTo("root")
        // Both panes reset before the second model's request is built.
        assertThat(f.events).containsExactly("start", "build").inOrder()
    }

    @Test
    fun `a continue never carries the second model`() {
        val f = Fixture(listOf(userTurn, reply), comparing = true)

        f.delegate.continueGeneration()

        // A continue carrying both lanes makes the server rewrite both; the panes keep one first.
        assertThat(f.sent.isContinued).isTrue()
        assertThat(f.sent.addedConvo).isNull()
        assertThat(f.events).isEmpty()
    }

    @Test
    fun `an edited user turn while comparing carries the second model`() {
        val f = Fixture(listOf(userTurn, reply), comparing = true)

        f.delegate.startEditing("u1")
        f.delegate.onEditTextChanged("compare these again")
        f.delegate.rerunEdit()

        assertThat(f.sent.isEdited).isTrue()
        assertThat(f.sent.addedConvo).isNotNull()
    }

    @Test
    fun `compaction stays the primary's alone`() {
        val f = Fixture(listOf(userTurn, reply), comparing = true)

        f.delegate.compactConversation()

        assertThat(f.sent.compact).isTrue()
        assertThat(f.sent.addedConvo).isNull()
        assertThat(f.events).isEmpty()
    }

    @Test
    fun `outside a comparison a resubmit carries nothing extra`() {
        val f = Fixture(listOf(userTurn, reply), comparing = false)

        f.delegate.regenerateMessage("a1")

        assertThat(f.sent.addedConvo).isNull()
    }
}
