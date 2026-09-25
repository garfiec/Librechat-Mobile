package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.core.model.trace.TraceAvailability
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Whether the trace entry point is ever reachable, which is a composition question and not a
 * delegate one.
 *
 * Six chunks of this sync produced the same class of defect: every part correct, the wiring between
 * them absent, every gate green. `observeTraceAvailability` is the only thing that turns
 * `interface.traceViewer` plus `/availability` into something a user can tap, so a build where it
 * is simply never called compiles, verifies and ships.
 *
 * The config is published AFTER the ViewModel exists, which is how it really arrives — a StateFlow
 * that starts null and fills from `/api/config`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTraceGateTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)

    /** Hot, so a test can hold a run open and then end it. */
    private val resumedStream = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fixture.stubDefaults()
        every { fixture.configRepository.startupConfig } returns startupConfig
        every { fixture.configRepository.detectedBackend } returns
            MutableStateFlow(DetectedBackend("0.8.8-rc3", BackendBuildClass.RC))
        every { fixture.traceRepository.isRuledOutForServer() } returns false
        coEvery { fixture.traceRepository.resolveAvailability(any()) } returns
            Result.Success(TraceAvailability(available = true))
        coEvery { fixture.chatRepository.checkStreamStatus(any(), any()) } returns
            ChatStatusResponse(active = false)
        every { fixture.chatRepository.resumeStream(any()) } returns resumedStream
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun publish(traceViewer: JsonObject?) {
        startupConfig.value =
            StartupConfig(interfaceConfig = InterfaceConfig(traceViewer = traceViewer))
    }

    private val enabled get() = buildJsonObject { put("enabled", true) }

    private fun traceGateTest(
        conversationId: String? = CONVERSATION_ID,
        body: (ChatViewModel) -> Unit,
    ) = runTest(timeout = TEST_TIMEOUT) {
        val viewModel = fixture.build(dispatcher, initialConversationId = conversationId)
        runCurrent()
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `an enabled server with an available trace offers the entry point`() = traceGateTest { vm ->
        publish(enabled)

        assertThat(vm.uiState.value.traceViewerAvailable).isTrue()
        coVerify(exactly = 1) { fixture.traceRepository.resolveAvailability(CONVERSATION_ID) }
    }

    @Test
    fun `an empty traceViewer section offers nothing and asks nothing`() = traceGateTest { vm ->
        // `{}` is OFF here, where `interface.schedules: {}` is ON. Routing this through the
        // schedules resolver compiles, and would open the feature on every server that has the
        // section at all.
        publish(buildJsonObject { })

        assertThat(vm.uiState.value.traceViewerAvailable).isFalse()
        coVerify(exactly = 0) { fixture.traceRepository.resolveAvailability(any()) }
    }

    @Test
    fun `a server that reports the trace unreadable offers nothing`() = traceGateTest { vm ->
        coEvery { fixture.traceRepository.resolveAvailability(any()) } returns
            Result.Success(TraceAvailability(available = false))

        publish(enabled)

        assertThat(vm.uiState.value.traceViewerAvailable).isFalse()
    }

    @Test
    fun `a server known to predate the routes is not asked`() = traceGateTest { vm ->
        every { fixture.traceRepository.isRuledOutForServer() } returns true

        publish(enabled)

        assertThat(vm.uiState.value.traceViewerAvailable).isFalse()
        coVerify(exactly = 0) { fixture.traceRepository.resolveAvailability(any()) }
    }

    @Test
    fun `a new chat asks nothing until it has an id`() = traceGateTest(conversationId = null) { vm ->
        publish(enabled)

        assertThat(vm.uiState.value.traceViewerAvailable).isFalse()
        coVerify(exactly = 0) { fixture.traceRepository.resolveAvailability(any()) }
    }

    @Test
    fun `nothing is asked mid-run and exactly one read follows the settle`() =
        runTest(timeout = TEST_TIMEOUT) {
            coEvery { fixture.chatRepository.checkStreamStatus(any(), any()) } returns
                ChatStatusResponse(active = true)
            val viewModel = fixture.build(dispatcher, initialConversationId = CONVERSATION_ID)
            runCurrent()
            try {
                assertThat(viewModel.uiState.value.isStreaming).isTrue()

                publish(enabled)
                // Mid-run the trace cannot have gained the turn yet, and the route the entry point
                // hangs off is the one with no limiter in front of it.
                coVerify(exactly = 0) { fixture.traceRepository.resolveAvailability(any()) }

                resumedStream.emit(StreamEvent.Error(message = "ended"))
                runCurrent()

                assertThat(viewModel.uiState.value.isStreaming).isFalse()
                // The settled turn is what can make a trace readable for the first time — one read
                // on the transition, not a poll.
                coVerify(exactly = 1) { fixture.traceRepository.resolveAvailability(CONVERSATION_ID) }
                assertThat(viewModel.uiState.value.traceViewerAvailable).isTrue()
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    private companion object {
        const val CONVERSATION_ID = "convo-1"
        val TEST_TIMEOUT = 15.seconds
    }
}
