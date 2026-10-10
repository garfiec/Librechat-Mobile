package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.ModelTokenomics
import com.garfiec.librechat.core.model.usage.TokenBudgetBreakdown
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ContextProjectionHandle
import com.garfiec.librechat.feature.chat.viewmodel.ContextUsageSource
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.FeatureGatesState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Before
import org.junit.Test

/**
 * How [ContextProjectionDelegate] resolves the gauge outside a stream: from the snapshot the
 * server saves on each response, falling back to the projection endpoint (older servers) and then
 * to an on-device estimate, recomputed on every emission of the displayed branch.
 *
 * The cases that matter most are the cache-first ones: a cached branch and the network refresh
 * that follows it usually share a tail id and differ only in `metadata`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextProjectionDelegateTest {

    private val endpointTokenRepository = mockk<EndpointTokenRepository>(relaxed = true)
    private val agentRepository = mockk<AgentRepository>(relaxed = true)

    private val earlier = snapshot(remaining = 7_000)
    private val later = snapshot(remaining = 5_000)
    private val live = ContextUsage(
        breakdown = TokenBudgetBreakdown(maxContextTokens = 8_000, instructionTokens = 500),
        remainingContextTokens = 6_000,
    )

    @Before
    fun setUp() {
        // A v0.8.8+ server: the projection answers null without a request.
        coEvery { endpointTokenRepository.getContextProjection(any()) } returns Result.Success(null)
        coEvery { endpointTokenRepository.getTokenConfig() } returns Result.Error(message = "offline")
        every { endpointTokenRepository.contextOverhead(any()) } returns 0
    }

    private fun snapshot(remaining: Int) = ContextUsage(
        breakdown = TokenBudgetBreakdown(maxContextTokens = 8_000, instructionTokens = 500, messageTokens = 300),
        remainingContextTokens = remaining,
    )

    private fun metadataOf(usage: ContextUsage): JsonObject = buildJsonObject {
        put("contextUsage", Json.encodeToJsonElement(ContextUsage.serializer(), usage).jsonObject)
    }

    private fun msg(id: String, parent: String? = null, tokens: Int = 100, saved: ContextUsage? = null) = Message(
        messageId = id,
        conversationId = "c1",
        parentMessageId = parent,
        isCreatedByUser = id.startsWith("u"),
        tokenCount = tokens,
        metadata = saved?.let(::metadataOf),
    )

    private fun node(message: Message) =
        MessageNode(message = message, children = emptyList(), siblingIndex = 0, siblingCount = 1)

    private fun state(
        branch: List<Message>,
        streaming: Boolean = false,
        usage: ContextUsage? = null,
        source: ContextUsageSource? = null,
        windowOverride: Int? = 8_000,
        conversationId: String = "c1",
    ) = ChatUiState(
        gates = FeatureGatesState(contextUsageEnabled = true),
        conversation = ConversationMetaState(conversationId = conversationId),
        selection = ModelSelectionState(
            selectedEndpoint = "openai",
            selectedModel = "gpt-4o",
            modelParameters = ModelParameters.DEFAULT.copy(maxContextTokens = windowOverride),
        ),
        content = MessagesState(
            displayMessages = branch.map(::node),
            isStreaming = streaming,
            contextUsage = usage,
            contextUsageSource = source,
        ),
    )

    private fun TestScope.started(initial: ChatUiState): MutableStateFlow<ChatUiState> {
        val flow = MutableStateFlow(initial)
        start(flow, backgroundScope)
        advanceUntilIdle()
        return flow
    }

    private fun start(flow: MutableStateFlow<ChatUiState>, scope: CoroutineScope) {
        ContextProjectionDelegate(
            ContextProjectionHandle(ChatStateHandle(flow, scope)),
            agentRepository,
            endpointTokenRepository,
        ).start()
    }

    /** Emits what a live stream leaves in state, then its end (the delegate captures the edge). */
    private fun TestScope.streamThenEnd(
        flow: MutableStateFlow<ChatUiState>,
        during: List<Message>,
        after: List<Message>,
        liveReading: ContextUsage? = live,
    ) {
        // The stream starts with whatever the gauge showed; a reading, if any, arrives later.
        flow.value = flow.value.copy(
            content = flow.value.content.copy(displayMessages = during.map(::node), isStreaming = true),
        )
        advanceUntilIdle()
        if (liveReading != null) {
            flow.value = flow.value.copy(
                content = flow.value.content.copy(
                    contextUsage = liveReading,
                    contextUsageSource = ContextUsageSource.LIVE,
                ),
            )
            advanceUntilIdle()
        }
        flow.value = flow.value.copy(
            content = flow.value.content.copy(displayMessages = after.map(::node), isStreaming = false),
        )
        advanceUntilIdle()
    }

    // --- cache-first and offline ---

    @Test
    fun `a refresh that only fills in metadata replaces the estimate with the saved snapshot`() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = started(state(listOf(msg("u1"), msg("a1", "u1"))))
            assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.ESTIMATE)
            assertThat(flow.value.contextUsage?.usedTokens).isEqualTo(200)

            // Same ids, same tail: the network copy carries the snapshot the cache did not.
            flow.value = state(listOf(msg("u1"), msg("a1", "u1", saved = later)))
            advanceUntilIdle()

            assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.SNAPSHOT)
            assertThat(flow.value.contextUsage).isEqualTo(later)
        }

    @Test
    fun `a cached snapshot shows with every network call failing`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { endpointTokenRepository.getContextProjection(any()) } returns Result.Error(message = "offline")

        val flow = started(state(listOf(msg("u1"), msg("a1", "u1", saved = later)), windowOverride = null))

        assertThat(flow.value.contextUsage).isEqualTo(later)
        assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.SNAPSHOT)
    }

    @Test
    fun `without an override the estimate takes its window from token-config`() =
        runTest(UnconfinedTestDispatcher()) {
            coEvery { endpointTokenRepository.getTokenConfig() } returns
                Result.Success(mapOf("openai" to mapOf("gpt-4o" to ModelTokenomics(context = 4_000))))

            val flow = started(state(listOf(msg("u1"), msg("a1", "u1")), windowOverride = null))

            assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.ESTIMATE)
            assertThat(flow.value.contextUsage?.windowTokens).isEqualTo(4_000)
        }

    @Test
    fun `with no window anywhere the gauge stays hidden`() = runTest(UnconfinedTestDispatcher()) {
        val flow = started(state(listOf(msg("u1"), msg("a1", "u1")), windowOverride = null))

        assertThat(flow.value.contextUsage).isNull()
        assertThat(flow.value.contextUsageSource).isNull()
    }

    @Test
    fun `the estimate counts the overhead recorded for this config`() = runTest(UnconfinedTestDispatcher()) {
        every { endpointTokenRepository.contextOverhead("openai::gpt-4o") } returns 1_000

        val flow = started(state(listOf(msg("u1"), msg("a1", "u1"))))

        assertThat(flow.value.contextUsage?.usedTokens).isEqualTo(1_200)
    }

    // --- the end of a stream ---

    @Test
    fun `a final payload carrying the snapshot replaces the live reading`() = runTest(UnconfinedTestDispatcher()) {
        val flow = started(state(listOf(msg("u1"), msg("a1", "u1", saved = earlier))))
        val beforeReply = listOf(msg("u1"), msg("a1", "u1", saved = earlier), msg("u2", "a1"))

        streamThenEnd(flow, during = beforeReply, after = beforeReply + msg("a2", "u2", saved = later))

        assertThat(flow.value.contextUsage).isEqualTo(later)
        assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.SNAPSHOT)
    }

    @Test
    fun `the live reading holds until the same turn's snapshot arrives`() = runTest(UnconfinedTestDispatcher()) {
        val flow = started(state(listOf(msg("u1"), msg("a1", "u1", saved = earlier))))
        val beforeReply = listOf(msg("u1"), msg("a1", "u1", saved = earlier), msg("u2", "a1"))

        // The reply landed without metadata (the in-memory finalize).
        streamThenEnd(flow, during = beforeReply, after = beforeReply + msg("a2", "u2"))
        assertThat(flow.value.contextUsage).isEqualTo(live)
        assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.LIVE)

        // The cache write / refresh brings the server's copy of the same reply.
        flow.value = state(beforeReply + msg("a2", "u2", saved = later), usage = live, source = ContextUsageSource.LIVE)
        advanceUntilIdle()
        assertThat(flow.value.contextUsage).isEqualTo(later)
    }

    @Test
    fun `an end with the tail still at the user message keeps the live reading over an older snapshot`() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = started(state(listOf(msg("u1"), msg("a1", "u1", saved = earlier))))
            val truncated = listOf(msg("u1"), msg("a1", "u1", saved = earlier), msg("u2", "a1"))

            // Comparison and error ends flip isStreaming before the reply is in the branch.
            streamThenEnd(flow, during = truncated, after = truncated)
            assertThat(flow.value.contextUsage).isEqualTo(live)

            // The reload then brings the reply below the anchor, with its own snapshot.
            flow.value = state(truncated + msg("a2", "u2", saved = later), usage = live, source = ContextUsageSource.LIVE)
            advanceUntilIdle()
            assertThat(flow.value.contextUsage).isEqualTo(later)
        }

    @Test
    fun `a run that reported no live reading does not carry the previous run's over`() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = started(state(listOf(msg("u1"), msg("a1", "u1"))))
            val first = listOf(msg("u1"), msg("a1", "u1"), msg("u2", "a1"))
            streamThenEnd(flow, during = first, after = first + msg("a2", "u2"))
            assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.LIVE)

            // The previous turn's saved snapshot lands, then a second run ends without a reading
            // (an interrupted final call). The first run's LIVE stamp is still on the gauge going in.
            val second = first + msg("a2", "u2", saved = earlier) + msg("u3", "a2")
            flow.value = flow.value.copy(
                content = flow.value.content.copy(
                    contextUsage = live,
                    contextUsageSource = ContextUsageSource.LIVE,
                ),
            )
            streamThenEnd(flow, during = second, after = second + msg("a3", "u3"), liveReading = null)

            assertThat(flow.value.contextUsage).isEqualTo(earlier)
            assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.SNAPSHOT)
        }

    @Test
    fun `switching to a branch without the live reading's turn drops it`() = runTest(UnconfinedTestDispatcher()) {
        val flow = started(state(listOf(msg("u1"), msg("a1", "u1", saved = earlier))))
        val beforeReply = listOf(msg("u1"), msg("a1", "u1", saved = earlier), msg("u2", "a1"))
        streamThenEnd(flow, during = beforeReply, after = beforeReply + msg("a2", "u2"))
        assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.LIVE)

        flow.value = flow.value.copy(
            content = flow.value.content.copy(
                displayMessages = listOf(msg("u1"), msg("a1", "u1", saved = earlier), msg("u2b", "a1")).map(::node),
            ),
        )
        advanceUntilIdle()

        assertThat(flow.value.contextUsage).isEqualTo(earlier)
        assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.SNAPSHOT)
    }

    @Test
    fun `nothing is written while a stream owns the gauge`() = runTest(UnconfinedTestDispatcher()) {
        val flow = started(state(listOf(msg("u1"), msg("a1", "u1"))))

        flow.value = state(
            listOf(msg("u1"), msg("a1", "u1", saved = earlier), msg("u2", "a1")),
            streaming = true,
            usage = live,
            source = ContextUsageSource.LIVE,
        )
        advanceUntilIdle()

        assertThat(flow.value.contextUsage).isEqualTo(live)
        assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.LIVE)
    }

    @Test
    fun `a disabled gauge is left alone`() = runTest(UnconfinedTestDispatcher()) {
        val flow = started(
            state(listOf(msg("u1"), msg("a1", "u1", saved = later))).let {
                it.copy(gates = it.gates.copy(contextUsageEnabled = false))
            },
        )

        assertThat(flow.value.contextUsage).isNull()
    }

    // --- servers before v0.8.8-rc1, which still answer the projection ---

    @Test
    fun `an older server's projection re-runs when the tail advances`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { endpointTokenRepository.getContextProjection(any()) } returns Result.Success(later)

        val flow = started(state(listOf(msg("m1"))))
        assertThat(flow.value.contextUsage).isEqualTo(later)
        assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.PROJECTION)
        coVerify(exactly = 1) { endpointTokenRepository.getContextProjection(any()) }

        // An unchanged branch doesn't ask again.
        flow.value = state(listOf(msg("m1")), usage = later, source = ContextUsageSource.PROJECTION)
        advanceUntilIdle()
        coVerify(exactly = 1) { endpointTokenRepository.getContextProjection(any()) }

        flow.value = state(listOf(msg("m1"), msg("m2", "m1")), usage = later, source = ContextUsageSource.PROJECTION)
        advanceUntilIdle()
        coVerify(exactly = 2) { endpointTokenRepository.getContextProjection(any()) }
    }

    @Test
    fun `an older server's projection does not replace a live reading`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { endpointTokenRepository.getContextProjection(any()) } returns Result.Success(earlier)
        val flow = started(state(listOf(msg("m1"))))
        coVerify(exactly = 1) { endpointTokenRepository.getContextProjection(any()) }

        streamThenEnd(flow, during = listOf(msg("m1"), msg("u2", "m1")), after = listOf(msg("m1"), msg("u2", "m1"), msg("a2", "u2")))

        assertThat(flow.value.contextUsage).isEqualTo(live)
        coVerify(exactly = 1) { endpointTokenRepository.getContextProjection(any()) }
    }

    @Test
    fun `a failed projection refresh keeps the projection already shown`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { endpointTokenRepository.getContextProjection(any()) } returns Result.Success(later) andThen
            Result.Error(message = "flaky")
        val flow = started(state(listOf(msg("m1"))))

        flow.value = state(listOf(msg("m1"), msg("m2", "m1")), usage = later, source = ContextUsageSource.PROJECTION)
        advanceUntilIdle()

        assertThat(flow.value.contextUsage).isEqualTo(later)
        assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.PROJECTION)
    }
}
