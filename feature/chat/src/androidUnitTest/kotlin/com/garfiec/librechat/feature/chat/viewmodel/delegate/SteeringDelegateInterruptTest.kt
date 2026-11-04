package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.model.PendingSteer
import com.garfiec.librechat.core.model.request.SteerArmRequest
import com.garfiec.librechat.core.model.request.SteerRequest
import com.garfiec.librechat.core.model.response.SteerArmResponse
import com.garfiec.librechat.core.model.response.SteerResponse
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.SteerNotice
import com.garfiec.librechat.feature.chat.viewmodel.SteeringHandle
import com.garfiec.librechat.feature.chat.viewmodel.interruptPending
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/** "Interrupt and steer": the `preempt` send, its echo, and "Interrupt now" (`/chat/steer/arm`). */
@OptIn(ExperimentalCoroutinesApi::class)
class SteeringDelegateInterruptTest {

    private val chatRepository = mockk<ChatRepository>()
    private val enqueued = mutableListOf<QueuedMessage>()
    private val notices = mutableListOf<SteerNotice>()

    private fun spec(text: String) = QueuedMessage(
        localId = "spec-$text",
        text = text,
        choice = ModelChoice.Raw("agents", "agent_abc"),
        dispatch = EndpointDispatch(endpointType = "agents", key = null, modelDisplayLabel = null),
    )

    private fun delegateWith(scope: TestScope): Pair<SteeringDelegate, MutableStateFlow<ChatUiState>> {
        val flow = MutableStateFlow(ChatUiState(conversation = ConversationMetaState(conversationId = "conv-1")))
        val delegate = SteeringDelegate(
            handle = SteeringHandle(ChatStateHandle(flow, scope)),
            chatRepository = chatRepository,
            buildFollowUp = { text -> spec(text) },
            enqueueFollowUp = { enqueued += it },
            enqueueParked = { enqueued += it },
            pauseQueue = {},
            holdQueueForRefusal = { _ -> },
            onQueuedAsFollowUp = { _ -> },
            isStreaming = { true },
            restageQuotes = {},
            onSteerNotice = { notices += it },
            armTimeoutMs = { ARM_TIMEOUT_MS },
        )
        return delegate to flow
    }

    @Test
    fun `an interrupt sends preempt and takes its look from the echo`() = runTest(UnconfinedTestDispatcher()) {
        val sent = slot<SteerRequest>()
        coEvery { chatRepository.steerChat(capture(sent)) } returns
            Result.Success(SteerResponse(status = "queued", steerId = "st-1", preempt = true))
        val (delegate, flow) = delegateWith(this)

        delegate.steer("conv-1", spec("make it Osaka"), interrupt = true)

        assertThat(sent.captured.preempt).isTrue()
        val chip = flow.value.pendingSteers.single()
        assertThat(chip.isInterrupting).isTrue()
        assertThat(chip.offersInterrupt).isFalse()
        assertThat(notices).isEmpty()
    }

    @Test
    fun `an ordinary steer sends no preempt`() = runTest(UnconfinedTestDispatcher()) {
        val sent = slot<SteerRequest>()
        coEvery { chatRepository.steerChat(capture(sent)) } returns Result.Success(SteerResponse(steerId = "st-1"))
        val (delegate, flow) = delegateWith(this)

        delegate.steer("conv-1", spec("be brief"))

        assertThat(sent.captured.preempt).isNull()
        assertThat(flow.value.pendingSteers.single().isInterrupting).isFalse()
    }

    @Test
    fun `a run that can't be interrupted keeps a plain steer and says so`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returns
            Result.Success(SteerResponse(status = "queued", steerId = "st-1", preempt = false))
        val (delegate, flow) = delegateWith(this)

        delegate.steer("conv-1", spec("make it Osaka"), interrupt = true)

        val chip = flow.value.pendingSteers.single()
        assertThat(chip.isInterrupting).isFalse()
        assertThat(notices).containsExactly(SteerNotice.INTERRUPT_UNAVAILABLE)
        assertThat(enqueued).isEmpty()
    }

    @Test
    fun `arming a waiting steer turns it into an interrupt in place`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returns Result.Success(SteerResponse(steerId = "st-1"))
        coEvery { chatRepository.armSteer(any()) } returns Result.Success(SteerArmResponse(armed = true))
        val (delegate, flow) = delegateWith(this)
        delegate.steer("conv-1", spec("skip red-eyes"))

        delegate.arm("st-1")

        coVerify { chatRepository.armSteer(SteerArmRequest("conv-1", "st-1")) }
        val chip = flow.value.pendingSteers.single()
        assertThat(chip.steerId).isEqualTo("st-1")
        assertThat(chip.isInterrupting).isTrue()
        assertThat(chip.isArming).isFalse()
        assertThat(notices).isEmpty()
    }

    @Test
    fun `only one interrupt at a time`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returnsMany listOf(
            Result.Success(SteerResponse(steerId = "st-1", preempt = true)),
            Result.Success(SteerResponse(steerId = "st-2")),
        )
        coEvery { chatRepository.armSteer(any()) } returns Result.Success(SteerArmResponse(armed = true))
        val (delegate, flow) = delegateWith(this)
        delegate.steer("conv-1", spec("osaka"), interrupt = true)
        delegate.steer("conv-1", spec("skip red-eyes"))

        assertThat(flow.value.pendingSteers.interruptPending).isTrue()
        delegate.arm("st-2")

        coVerify(exactly = 0) { chatRepository.armSteer(any()) }
    }

    @Test
    fun `an arm in flight shows on the chip and blocks a second`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returnsMany listOf(
            Result.Success(SteerResponse(steerId = "st-1")),
            Result.Success(SteerResponse(steerId = "st-2")),
        )
        val reply = CompletableDeferred<Result<SteerArmResponse>>()
        coEvery { chatRepository.armSteer(any()) } coAnswers { reply.await() }
        val (delegate, flow) = delegateWith(this)
        delegate.steer("conv-1", spec("one"))
        delegate.steer("conv-1", spec("two"))

        delegate.arm("st-1")
        assertThat(flow.value.pendingSteers.first { it.steerId == "st-1" }.isArming).isTrue()
        delegate.arm("st-2")

        coVerify(exactly = 1) { chatRepository.armSteer(any()) }
        reply.complete(Result.Success(SteerArmResponse(armed = true)))
        runCurrent()
    }

    @Test
    fun `a lost arm race leaves the chip and says it may already be in`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returns Result.Success(SteerResponse(steerId = "st-1"))
        coEvery { chatRepository.armSteer(any()) } returns Result.Success(SteerArmResponse(armed = false))
        val (delegate, flow) = delegateWith(this)
        delegate.steer("conv-1", spec("x"))

        delegate.arm("st-1")

        assertThat(flow.value.pendingSteers.single().isInterrupting).isFalse()
        assertThat(flow.value.pendingSteers.single().isArming).isFalse()
        assertThat(notices).containsExactly(SteerNotice.ARM_FAILED)
    }

    @Test
    fun `an arm the run can't take says it can't be interrupted`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returns Result.Success(SteerResponse(steerId = "st-1"))
        coEvery { chatRepository.armSteer(any()) } returns
            Result.Success(SteerArmResponse(armed = false, code = SteerArmResponse.CODE_PREEMPT_UNSUPPORTED))
        val (delegate, _) = delegateWith(this)
        delegate.steer("conv-1", spec("x"))

        delegate.arm("st-1")

        assertThat(notices).containsExactly(SteerNotice.INTERRUPT_UNAVAILABLE)
    }

    @Test
    fun `a refused arm is a no, a transport failure is unconfirmed`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returnsMany listOf(
            Result.Success(SteerResponse(steerId = "st-1")),
            Result.Success(SteerResponse(steerId = "st-2")),
        )
        coEvery { chatRepository.armSteer(SteerArmRequest("conv-1", "st-1")) } returns
            Result.Error(ApiException(statusCode = 409, message = "replaced", body = """{"code":"RUN_REPLACED"}"""))
        coEvery { chatRepository.armSteer(SteerArmRequest("conv-1", "st-2")) } returns
            Result.Error(IOException("offline"))
        val (delegate, _) = delegateWith(this)
        delegate.steer("conv-1", spec("one"))
        delegate.steer("conv-1", spec("two"))

        delegate.arm("st-1")
        delegate.arm("st-2")

        assertThat(notices).containsExactly(SteerNotice.ARM_FAILED, SteerNotice.ARM_UNCONFIRMED).inOrder()
    }

    @Test
    fun `no reply within the confirmation window is unconfirmed, once`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returns Result.Success(SteerResponse(steerId = "st-1"))
        coEvery { chatRepository.armSteer(any()) } coAnswers { awaitCancellation() }
        val (delegate, flow) = delegateWith(this)
        delegate.steer("conv-1", spec("x"))

        delegate.arm("st-1")
        advanceTimeBy(ARM_TIMEOUT_MS + 1)
        runCurrent()

        assertThat(notices).containsExactly(SteerNotice.ARM_UNCONFIRMED)
        val chip = flow.value.pendingSteers.single()
        assertThat(chip.isArming).isFalse()
        assertThat(chip.isInterrupting).isFalse()
        coVerify(exactly = 1) { chatRepository.armSteer(any()) }
    }

    @Test
    fun `a reconnect keeps the interrupting look the server reports`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, flow) = delegateWith(this)

        delegate.onPendingSteersSynced(
            listOf(
                PendingSteer(steerId = "st-1", text = "osaka", createdAt = 1, preempt = true, preemptRevision = 1),
                PendingSteer(steerId = "st-2", text = "skip red-eyes", createdAt = 2),
            ),
        )

        val chips = flow.value.pendingSteers
        assertThat(chips.map { it.isInterrupting }).containsExactly(true, false).inOrder()
    }

    @Test
    fun `a later report updates a known chip's interrupt flag`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { chatRepository.steerChat(any()) } returns Result.Success(SteerResponse(steerId = "st-1"))
        val (delegate, flow) = delegateWith(this)
        delegate.steer("conv-1", spec("x"))

        delegate.onPendingSteersSynced(listOf(PendingSteer(steerId = "st-1", text = "x", createdAt = 1, preempt = true)))

        assertThat(flow.value.pendingSteers.single().isInterrupting).isTrue()
    }

    private companion object {
        const val ARM_TIMEOUT_MS = 10_000L
    }
}
