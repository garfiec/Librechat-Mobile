package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

/**
 * [this] with the per-token streaming churn reset to at-rest values, letting the screen chrome
 * collect `map { it.neutralizeStreamingChurn() }.distinctUntilChanged()`. Chrome must not read
 * these fields — they are always empty there; if one is needed, drop it from this function.
 */
internal fun ChatUiState.neutralizeStreamingChurn(): ChatUiState = copy(
    content = content.copy(
        streamingContent = "",
        streamingThinking = "",
        activeToolCalls = emptyList(),
        sandboxStartingToolCallIds = emptySet(),
        ptcTraces = emptyMap(),
        streamingAttachments = emptyList(),
    ),
    comparisonState = comparisonState.copy(
        primaryStreamingContent = "",
        secondaryStreamingContent = "",
        primaryActiveToolCalls = emptyList(),
        secondaryActiveToolCalls = emptyList(),
    ),
)

/**
 * [this] without the live reply's text, which changes on every flush. The thread collects this, and
 * only the streaming item reads the text, through [streamingText], so a flush recomposes that item
 * alone. Tool calls and attachments stay: they change per event, not per flush.
 */
internal fun ChatUiState.withoutStreamingText(): ChatUiState = copy(
    content = content.copy(streamingContent = "", streamingThinking = ""),
    comparisonState = comparisonState.copy(primaryStreamingContent = "", secondaryStreamingContent = ""),
)

/** The live reply's text: what [withoutStreamingText] takes out. */
@Immutable
internal data class StreamingText(
    val content: String = "",
    val thinking: String = "",
    val primary: String = "",
    val secondary: String = "",
)

internal fun ChatUiState.streamingText(): StreamingText = StreamingText(
    content = streamingContent,
    thinking = streamingThinking,
    primary = comparisonState.primaryStreamingContent,
    secondary = comparisonState.secondaryStreamingContent,
)
