package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ComposerState
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
 * Edit, regenerate and continue send the user turn's own `reasoningOverride` (web's
 * `overrideReasoning`), never the composer's pending choice, and drop one the current model refuses.
 */
class ReasoningOverrideReplayTest {

    private val high = ReasoningOverride.level("reasoning_effort", "high")
    private val pending = ReasoningOverride.level("reasoning_effort", "low")

    private val userTurn = Message(
        messageId = "u1",
        conversationId = "c1",
        text = "think hard",
        isCreatedByUser = true,
        reasoningOverride = high,
    )
    private val reply = Message(messageId = "a1", conversationId = "c1", parentMessageId = "u1", text = "answer")

    private fun fixture(model: String = "gpt-5", version: String? = "0.8.9-rc1"): Pair<RecordingChatRepository, MessageEditingDelegate> {
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = "c1"),
                content = MessagesState(
                    messages = listOf(userTurn, reply),
                    displayMessages = buildActiveMessagePath(listOf(userTurn, reply)),
                ),
                selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", model)),
                gates = FeatureGatesState(backendVersion = version),
                composer = ComposerState(pendingReasoningOverride = pending),
            ),
        )
        val repository = RecordingChatRepository()
        val delegate = MessageEditingDelegate(
            handle = MessageEditingHandle(ChatStateHandle(flow, CoroutineScope(Dispatchers.Unconfined))),
            chatRepository = repository,
            messageRepository = mockk(relaxed = true),
            treeDelegate = mockk(relaxed = true),
            streamingManager = mockk(relaxed = true),
            requestBuilder = mockk<ChatRequestBuilder>(relaxed = true),
            runWhenSendReady = { _, action -> action() },
            onComparisonSendStart = {},
            buildAddedConvo = { _, _, _ -> null },
        )
        return repository to delegate
    }

    @Test
    fun `regenerate replays the user turn's choice, not the composer's`() {
        val (repository, delegate) = fixture()
        delegate.regenerateMessage("a1")
        assertThat(repository.sent.single().reasoningOverride).isEqualTo(high)
    }

    @Test
    fun `continue replays the user turn's choice`() {
        val (repository, delegate) = fixture()
        delegate.continueGeneration()
        assertThat(repository.sent.single().isContinued).isTrue()
        assertThat(repository.sent.single().reasoningOverride).isEqualTo(high)
    }

    @Test
    fun `a choice the current model refuses is not sent`() {
        val (repository, delegate) = fixture(model = "gpt-4o")
        delegate.regenerateMessage("a1")
        assertThat(repository.sent.single().reasoningOverride).isNull()
    }

    @Test
    fun `an older server gets no choice`() {
        val (repository, delegate) = fixture(version = "0.8.8")
        delegate.regenerateMessage("a1")
        assertThat(repository.sent.single().reasoningOverride).isNull()
    }
}
