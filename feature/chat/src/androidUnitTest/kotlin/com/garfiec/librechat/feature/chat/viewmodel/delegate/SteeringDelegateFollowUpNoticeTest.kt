package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.model.PendingSteer
import com.garfiec.librechat.core.model.endpoint.EndpointDispatch
import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.PromotedSteer
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnSteer
import com.garfiec.librechat.core.model.request.SteerRequest
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
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Where a steer's words go when the run ends before taking it, and which re-homes tell the user a
 * steer became a follow-up. Every route into the self-draining queue does — the words are about to
 * come back as a new turn. The parked routes (a Stop, a failed run, a steer parked while nobody was
 * attached) do not: they hold the queue behind "Send queued", so "queued to send after it" would be
 * untrue. A steer the server promoted to a durable queued turn (`promoteLeftover`,
 * `final.promotedSteers`) is not re-homed at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SteeringDelegateFollowUpNoticeTest {

    private val chatRepository = mockk<ChatRepository>()
    private val followUps = mutableListOf<QueuedMessage>()
    private val parked = mutableListOf<QueuedMessage>()
    private val notices = mutableListOf<Int>()
    private val adoptedTurns = mutableListOf<AgentQueuedTurnReceipt>()
    private val state = MutableStateFlow(ChatUiState(conversation = ConversationMetaState(conversationId = "conv-1")))
    private var streaming = true

    private fun spec(text: String) = QueuedMessage(
        localId = "spec-$text",
        text = text,
        choice = ModelChoice.Raw("agents", "agent_abc"),
        dispatch = EndpointDispatch(endpointType = "agents", key = null, modelDisplayLabel = null),
    )

    private fun delegateWith(scope: TestScope, promotable: Boolean = false) = SteeringDelegate(
        handle = SteeringHandle(ChatStateHandle(state, scope)),
        chatRepository = chatRepository,
        buildFollowUp = { spec(it) },
        enqueueFollowUp = { followUps += it },
        enqueueParked = { parked += it },
        pauseQueue = {},
        holdQueueForRefusal = { _ -> },
        onQueuedAsFollowUp = { notices += it },
        isStreaming = { streaming },
        restageQuotes = {},
        promotable = { promotable },
        adoptPromotedTurns = { adoptedTurns += it },
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

    @Test
    fun `a promotable steer asks the server to keep its words if the run ends first`() =
        runTest(UnconfinedTestDispatcher()) {
            val sent = slot<SteerRequest>()
            coEvery { chatRepository.steerChat(capture(sent)) } returns accepted("st-1")

            delegateWith(this, promotable = true).steer("conv-1", spec("be brief"))

            assertThat(sent.captured.promoteLeftover).isTrue()
        }

    @Test
    fun `a steer that can't be promoted sends no flag`() = runTest(UnconfinedTestDispatcher()) {
        val sent = slot<SteerRequest>()
        coEvery { chatRepository.steerChat(capture(sent)) } returns accepted("st-1")

        delegateWith(this).steer("conv-1", spec("be brief"))

        assertThat(sent.captured.promoteLeftover).isNull()
        assertThat(sent.captured.codeApprovalMode).isNull()
    }

    @Test
    fun `a steer the server promoted is never re-homed locally`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returns accepted("st-1")
        val delegate = delegateWith(this)
        delegate.steer("conv-1", spec("be brief"))
        val receipt = AgentQueuedTurnReceipt(
            queuedTurnId = "qt-1",
            clientRequestId = "steer-leftover:local-1",
            text = "be brief",
            status = "queued",
        )

        delegate.adoptPromoted(listOf(PromotedSteer(steerId = "st-1", clientSteerId = "local-1", receipt = receipt)))
        assertThat(adoptedTurns.single().steer).isEqualTo(QueuedTurnSteer("st-1", "local-1"))
        // The queued-turn delegate claims the steer the receipt names, which retires its chip.
        assertThat(delegate.claimPromoted(adoptedTurns.single().steer!!)).isNull()
        assertThat(state.value.pendingSteers).isEmpty()
        // A later report of the same steer, or an error path's sweep, finds nothing live to re-home.
        delegate.reclaim(listOf(PendingSteer(steerId = "st-1", text = "be brief")))
        delegate.reclaimLocalChips()

        assertThat(followUps).isEmpty()
        assertThat(parked).isEmpty()
    }

    @Test
    fun `a promoted steer already re-homed names the queue row its words went to`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { chatRepository.steerChat(any()) } returns accepted("st-1")
            val delegate = delegateWith(this)
            delegate.steer("conv-1", spec("be brief"))
            delegate.reclaimLocalChips()
            assertThat(parked.map { it.localId }).containsExactly("spec-be brief")

            assertThat(delegate.claimPromoted(QueuedTurnSteer("st-1", "local-1"))).isEqualTo("spec-be brief")
        }
}
