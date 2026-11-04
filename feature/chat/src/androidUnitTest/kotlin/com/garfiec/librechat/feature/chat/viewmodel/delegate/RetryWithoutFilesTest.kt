package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
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

/** "Retry without files" resends the failed turn as a new version, keeping everything but its files. */
class RetryWithoutFilesTest {

    private val high = ReasoningOverride.level("reasoning_effort", "high")
    private val userTurn = Message(
        messageId = "u1",
        conversationId = "c1",
        parentMessageId = "a0",
        text = "summarize these",
        isCreatedByUser = true,
        files = listOf(FileReference(fileId = "f1")),
        quotes = listOf("quoted"),
        manualSkills = listOf("skill"),
        reasoningOverride = high,
    )
    private val failed =
        Message(messageId = "a1", conversationId = "c1", parentMessageId = "u1", text = "too long", error = true)

    private fun fixture(streaming: Boolean = false): Pair<RecordingChatRepository, MutableStateFlow<ChatUiState>> {
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = "c1"),
                content = MessagesState(
                    messages = listOf(userTurn, failed),
                    displayMessages = buildActiveMessagePath(listOf(userTurn, failed)),
                    isStreaming = streaming,
                ),
                selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", "gpt-5")),
                gates = FeatureGatesState(backendVersion = "0.8.9-rc1"),
            ),
        )
        val repository = RecordingChatRepository()
        MessageEditingDelegate(
            handle = MessageEditingHandle(ChatStateHandle(flow, CoroutineScope(Dispatchers.Unconfined))),
            chatRepository = repository,
            messageRepository = mockk(relaxed = true),
            treeDelegate = mockk(relaxed = true),
            streamingManager = mockk(relaxed = true),
            requestBuilder = mockk<ChatRequestBuilder>(relaxed = true),
            runWhenSendReady = { _, action -> action() },
            onComparisonSendStart = {},
            buildAddedConvo = { _, _, _ -> null },
        ).retryWithoutFiles("a1")
        return repository to flow
    }

    @Test
    fun `the turn goes again beside the old one with no files and everything else kept`() {
        val (repository, flow) = fixture()

        val sent = repository.sent.single()
        assertThat(sent.text).isEqualTo("summarize these")
        assertThat(sent.parentMessageId).isEqualTo("a0")
        assertThat(sent.files).isNull()
        assertThat(sent.quotes).containsExactly("quoted")
        assertThat(sent.manualSkills).containsExactly("skill")
        assertThat(sent.reasoningOverride).isEqualTo(high)
        assertThat(sent.isEdited).isFalse()
        val optimistic = flow.value.messages.single { it.messageId == sent.userMessageId }
        assertThat(optimistic.files).isNull()
    }

    @Test
    fun `nothing is sent mid-stream`() {
        val (repository, _) = fixture(streaming = true)
        assertThat(repository.sent).isEmpty()
    }
}
