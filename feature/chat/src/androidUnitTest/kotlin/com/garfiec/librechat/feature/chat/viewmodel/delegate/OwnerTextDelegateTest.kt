package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.response.OwnerText
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.OwnerTextHandle
import com.garfiec.librechat.feature.chat.viewmodel.OwnerTextState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OwnerTextDelegateTest {

    private val repository = mockk<MessageRepository>()
    private val turn = Message("u1", "c1", text = "mail [EMAIL]", isCreatedByUser = true, privacyRevision = "r1")
    private val original = OwnerText("u1", "r1", "mail [EMAIL]", "mail maya@x.com")

    private fun state(streaming: Boolean = false, conversationId: String = "c1") = ChatUiState(
        conversation = ConversationMetaState(conversationId = conversationId),
        content = MessagesState(messages = listOf(turn), isStreaming = streaming),
    )

    private fun TestScope.start(initial: ChatUiState): MutableStateFlow<ChatUiState> {
        val flow = MutableStateFlow(initial)
        OwnerTextDelegate(OwnerTextHandle(ChatStateHandle(flow, backgroundScope)), repository).start()
        return flow
    }

    @Test
    fun `the original is read once the chat settles, never mid-stream`() = runTest {
        coEvery { repository.getOwnerTexts("c1", listOf("u1")) } returns Result.Success(listOf(original))
        val flow = start(state(streaming = true))
        runCurrent()
        coVerify(exactly = 0) { repository.getOwnerTexts(any(), any()) }

        flow.update { it.copy(content = it.content.copy(isStreaming = false)) }
        runCurrent()

        assertThat(flow.value.ownerTexts["u1"]).isEqualTo(OwnerTextState.Loaded("mail maya@x.com", "r1", "mail [EMAIL]"))
    }

    @Test
    fun `a failure is unavailable until Retry, and a switch of conversation forgets everything`() = runTest {
        coEvery { repository.getOwnerTexts(any(), any()) } returns Result.Error(message = "503")
        val flow = MutableStateFlow(state())
        val delegate = OwnerTextDelegate(OwnerTextHandle(ChatStateHandle(flow, backgroundScope)), repository)
        delegate.start()
        runCurrent()
        assertThat(flow.value.ownerTexts["u1"]).isEqualTo(OwnerTextState.Unavailable)

        coEvery { repository.getOwnerTexts(any(), any()) } returns Result.Success(listOf(original))
        delegate.retry()
        runCurrent()
        assertThat(flow.value.ownerTexts["u1"]).isInstanceOf(OwnerTextState.Loaded::class.java)

        flow.update { it.copy(conversation = it.conversation.copy(conversationId = "c2"), content = MessagesState()) }
        runCurrent()
        assertThat(flow.value.ownerTexts).isEmpty()
    }
}
