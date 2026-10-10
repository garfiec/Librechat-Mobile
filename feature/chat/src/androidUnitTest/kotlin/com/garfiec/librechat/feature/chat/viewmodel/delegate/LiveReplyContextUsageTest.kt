package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.TokenBudgetBreakdown
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ContextUsageSource
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.StreamingHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A live `on_context_usage` reading drives the gauge and records the config's instruction
 * overhead for the estimate. The overhead is keyed by the event's own agent, as upstream does, so
 * the two readings of a comparison run land under separate keys.
 */
class LiveReplyContextUsageTest {

    private val repository = mockk<EndpointTokenRepository>(relaxed = true)

    private fun delegate(flow: MutableStateFlow<ChatUiState>, scope: CoroutineScope) =
        LiveReplyDelegate(StreamingHandle(ChatStateHandle(flow, scope)), mockk(relaxed = true), mockk(relaxed = true), repository)

    private fun reading(agentId: String?, instructions: Int, effective: Int? = null) = ContextUsage(
        breakdown = TokenBudgetBreakdown(maxContextTokens = 100_000, instructionTokens = instructions),
        effectiveInstructionTokens = effective,
        agentId = agentId,
    )

    @Test
    fun `a live reading is stamped live and records the overhead under the event's agent`() = runTest {
        val flow = MutableStateFlow(
            ChatUiState(selection = ModelSelectionState(selectedEndpoint = "agents", selectedModel = "agent_primary")),
        )
        val live = delegate(flow, backgroundScope)

        live.apply(StreamEvent.ContextUsageUpdate(reading("agent_primary", instructions = 3_000, effective = 3_500)))
        live.apply(StreamEvent.ContextUsageUpdate(reading("agent_secondary____1", instructions = 1_200)))

        assertThat(flow.value.contextUsageSource).isEqualTo(ContextUsageSource.LIVE)
        assertThat(flow.value.contextUsage?.agentId).isEqualTo("agent_secondary____1")
        verify { repository.recordContextOverhead("agent:agent_primary", 3_500) }
        verify { repository.recordContextOverhead("agent:agent_secondary____1", 1_200) }
    }

    @Test
    fun `a reading without an agent keys by endpoint and model`() = runTest {
        val flow = MutableStateFlow(
            ChatUiState(selection = ModelSelectionState(selectedEndpoint = "openAI", selectedModel = "gpt-4o")),
        )

        delegate(flow, backgroundScope).apply(StreamEvent.ContextUsageUpdate(reading(null, instructions = 900)))

        verify { repository.recordContextOverhead("openAI::gpt-4o", 900) }
    }
}
