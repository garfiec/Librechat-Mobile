package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.util.laneBSpecFor
import com.garfiec.librechat.feature.chat.util.parseEphemeralAgentId

@Immutable
data class ComparisonState(
    val isEnabled: Boolean = false,
    // The user turned Compare on and is choosing the second model; nothing is enabled yet.
    val isSettingUp: Boolean = false,
    // Secondary agent/model selection (set by user via model selector)
    val secondaryEndpoint: String? = null,
    val secondaryModel: String? = null,
    val secondarySpec: String? = null,
    /**
     * The ephemeral agent id a reopened comparison's second lane was saved with; null for a live
     * pick or a saved agent. It names lane B's spec when the spec config had not loaded at reopen,
     * or when no spec matched yet; see [laneBSpec].
     */
    val reopenedSecondaryId: String? = null,
    // Resolved agent IDs from SSE events (set during streaming)
    val primaryAgentId: String? = null,
    val secondaryAgentId: String? = null,
    // Per-agent streaming content
    val primaryStreamingContent: String = "",
    val secondaryStreamingContent: String = "",
    val primaryIsStreaming: Boolean = false,
    val secondaryIsStreaming: Boolean = false,
    val primaryActiveToolCalls: List<ActiveToolCall> = emptyList(),
    val secondaryActiveToolCalls: List<ActiveToolCall> = emptyList(),
    // The message containing parallel content (for branching)
    val parallelMessageId: String? = null,
    // Captured final content from streaming buffers (server may not preserve per-agent content)
    val primaryFinalContent: String? = null,
    val secondaryFinalContent: String? = null,
)

/** One Compare lane's pick: a model, and the spec it came from when it was a spec row. */
@Immutable
data class ComparisonPick(val endpoint: String, val model: String, val spec: String? = null)

/**
 * Lane B's spec: the one picked, else the one a reopened comparison was sent with, read against
 * [specs] ([laneBSpecFor]). Null for a raw model or a saved agent.
 */
internal fun ComparisonState.laneBSpec(specs: ModelSpecs?): String? {
    secondarySpec?.let { return it }
    val reopened = reopenedSecondaryId?.let(::parseEphemeralAgentId) ?: return null
    return specs.laneBSpecFor(reopened.endpoint, reopened.model, reopened.sender)
}
