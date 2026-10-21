package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.model.PendingSteer
import com.garfiec.librechat.core.model.response.SteerResponse
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.SteeringHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Which re-homes tell the user a steer became a follow-up. Every route into the self-draining
 * queue does — the words are about to come back as a new turn. The parked routes (a Stop, a failed
 * run, a steer parked while nobody was attached) do not: they hold the queue behind "Send queued",
 * so "queued to send after it" would be untrue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SteeringDelegateFollowUpNoticeTest {

    private val chatRepository = mockk<ChatRepository>()
    private val followUps = mutableListOf<QueuedMessage>()
    private val parked = mutableListOf<QueuedMessage>()
    private val notices = mutableListOf<Int>()
    private var streaming = true

    private fun spec(text: String) = QueuedMessage(
        localId = "spec-$text",
        text = text,
        choice = ModelChoice.Raw("agents", "agent_abc"),
        dispatch = EndpointDispatch(endpointType = "agents", key = null, modelDisplayLabel = null),
    )

    private fun delegateWith(scope: TestScope) = SteeringDelegate(
        handle = SteeringHandle(
            ChatStateHandle(
                MutableStateFlow(ChatUiState(conversation = ConversationMetaState(conversationId = "conv-1"))),
                scope,
            ),
        ),
        chatRepository = chatRepository,
        buildFollowUp = { spec(it) },
        enqueueFollowUp = { followUps += it },
        enqueueParked = { parked += it },
        pauseQueue = {},
        holdQueueForRefusal = { _ -> },
        onQueuedAsFollowUp = { notices += it },
        isStreaming = { streaming },
        restageQuotes = {},
    )

    private fun accepted(id: String) = Result.Success(SteerResponse(status = "queued", steerId = id))

    @Test
    fun `steers handed back by a clean finish are announced once, with their count`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { chatRepository.steerChat(any()) } returnsMany listOf(accepted("s-1"), accepted("s-2"))
            val delegate = delegateWith(this)
            delegate.steer("conv-1", spec("one"))
            delegate.steer("conv-1", spec("two"))

            val reported = listOf(
                PendingSteer(steerId = "s-1", text = "one", createdAt = 1L),
                PendingSteer(steerId = "s-2", text = "two", createdAt = 2L),
            )
            delegate.reclaim(reported)
            // The same report again (a duplicate delivery) queues nothing, so says nothing.
            delegate.reclaim(reported)

            assertThat(followUps.map { it.text }).containsExactly("one", "two").inOrder()
            assertThat(notices).containsExactly(2)
        }

    @Test
    fun `a cancelled steer in the report is neither queued nor announced`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { chatRepository.steerChat(any()) } returns accepted("s-1")
            coEvery { chatRepository.cancelSteer(any()) } returns Result.Success(mockk(relaxed = true))
            val delegate = delegateWith(this)
            delegate.steer("conv-1", spec("one"))
            delegate.cancel("s-1")

            delegate.reclaim(listOf(PendingSteer(steerId = "s-1", text = "one", createdAt = 1L)))

            assertThat(followUps).isEmpty()
            assertThat(notices).isEmpty()
        }

    @Test
    fun `an ack that lands after the run ended is announced`() = runTest(UnconfinedTestDispatcher()) {
        val ack = CompletableDeferred<Result<SteerResponse>>()
        coEvery { chatRepository.steerChat(any()) } coAnswers { ack.await() }
        val delegate = delegateWith(this)
        delegate.steer("conv-1", spec("late"))

        streaming = false
        ack.complete(accepted("s-late"))

        assertThat(followUps.map { it.text }).containsExactly("late")
        assertThat(notices).containsExactly(1)
    }

    @Test
    fun `the parked routes hold the queue and say nothing`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returnsMany listOf(accepted("s-1"), accepted("s-2"))
        val delegate = delegateWith(this)
        delegate.steer("conv-1", spec("one"))
        delegate.steer("conv-1", spec("two"))

        delegate.reclaimAborted(listOf(PendingSteer(steerId = "s-1", text = "one", createdAt = 1L)))
        delegate.reclaimLocalChips()
        delegate.reclaimParked(listOf(PendingSteer(steerId = "s-3", text = "three", createdAt = 3L)))

        assertThat(parked.map { it.text }).containsExactly("one", "two", "three").inOrder()
        assertThat(followUps).isEmpty()
        assertThat(notices).isEmpty()
    }
}
