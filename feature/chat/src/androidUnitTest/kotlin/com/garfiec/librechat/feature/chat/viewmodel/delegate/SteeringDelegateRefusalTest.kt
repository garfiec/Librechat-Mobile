package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.model.steer.SteerRejectionCodes
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.SteeringHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A steer the server's middleware refused (`ChatRefusal`) is held for the user rather than
 * degraded into the self-draining queue. Which enqueue it took is all these fakes can see; that the
 * hold survives a clean finish is `ChatViewModelDuringRunSendTest`'s, on the real queue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SteeringDelegateRefusalTest {

    private val chatRepository = mockk<ChatRepository>()
    private val followUps = mutableListOf<QueuedMessage>()
    private val parked = mutableListOf<QueuedMessage>()
    private var heldForRefusal = false
    private val queuedNotices = mutableListOf<Int>()

    private fun spec(text: String) = QueuedMessage(
        localId = "spec-$text",
        text = text,
        endpoint = "agents",
        model = "agent_abc",
        agentId = "agent_abc",
        dispatch = EndpointDispatch(endpointType = "agents", key = null, modelDisplayLabel = null),
    )

    private fun delegateWith(scope: TestScope): Pair<SteeringDelegate, MutableStateFlow<ChatUiState>> {
        val flow = MutableStateFlow(ChatUiState(conversation = ConversationMetaState(conversationId = "conv-1")))
        val delegate = SteeringDelegate(
            handle = SteeringHandle(ChatStateHandle(flow, scope)),
            chatRepository = chatRepository,
            buildFollowUp = { spec(it) },
            enqueueFollowUp = { followUps += it },
            enqueueParked = { parked += it },
            pauseQueue = {},
            holdQueueForRefusal = { _ -> heldForRefusal = true },
            onQueuedAsFollowUp = { queuedNotices += it },
            isStreaming = { true },
            restageQuotes = {},
        )
        return delegate to flow
    }

    @Test
    fun `a refused steer is held in the queue and says why`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returns Result.Error(
            ApiException(
                statusCode = 200,
                message = "The server refused this message.",
                body = """{"type":"message_limit","max":2,"windowInMinutes":1,"retryAfterSeconds":42}""",
            ),
        )
        val (delegate, flow) = delegateWith(this)

        delegate.steer("conv-1", spec("be brief"))

        assertThat(parked.map { it.text }).containsExactly("be brief")
        assertThat(followUps).isEmpty()
        assertThat(heldForRefusal).isTrue()
        assertThat(flow.value.pendingSteers).isEmpty()
        assertThat(flow.value.error).isEqualTo(ChatNotice.MESSAGE_LIMIT_RETRY.marker(42L))
        // Held, not queued to send: the "queued to send after it" notice would be untrue here.
        assertThat(queuedNotices).isEmpty()
    }

    @Test
    fun `a prose refusal is held and shows the server's sentence`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returns Result.Error(
            ApiException(
                statusCode = 200,
                message = "error in moderation check",
                body = "error in moderation check",
                serverAuthored = true,
            ),
        )
        val (delegate, flow) = delegateWith(this)

        delegate.steer("conv-1", spec("be brief"))

        assertThat(parked.map { it.text }).containsExactly("be brief")
        assertThat(heldForRefusal).isTrue()
        assertThat(flow.value.error).isEqualTo("error in moderation check")
    }

    @Test
    fun `an ordinary rejection still degrades to the follow-up queue`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returns Result.Error(
            ApiException(statusCode = 409, message = "rejected", body = """{"code":"${SteerRejectionCodes.RUN_PAUSED}"}"""),
        )
        val (delegate, flow) = delegateWith(this)

        delegate.steer("conv-1", spec("be brief"))

        assertThat(followUps.map { it.text }).containsExactly("be brief")
        assertThat(parked).isEmpty()
        assertThat(heldForRefusal).isFalse()
        assertThat(flow.value.error).isNull()
        assertThat(queuedNotices).containsExactly(1)
    }
}
