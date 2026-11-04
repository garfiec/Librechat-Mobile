package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.DraftRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationLoadHandle
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** `loadConversation`'s `onSettled`: what Keep and continue waits on before it continues. */
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
        val delegate = ConversationLoadDelegate(
            handle = ConversationLoadHandle(ChatStateHandle(flow, scope)),
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

    private companion object {
        const val CONVERSATION_ID = "conv-1"
    }
}
