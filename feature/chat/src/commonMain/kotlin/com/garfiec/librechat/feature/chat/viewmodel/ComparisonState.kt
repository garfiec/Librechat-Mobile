package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

@Immutable
data class ComparisonState(
    val isEnabled: Boolean = false,
    // The user turned Compare on and is choosing the second model; nothing is enabled yet.
    val isSettingUp: Boolean = false,
    // Secondary agent/model selection (set by user via model selector)
    val secondaryEndpoint: String? = null,
    val secondaryModel: String? = null,
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
