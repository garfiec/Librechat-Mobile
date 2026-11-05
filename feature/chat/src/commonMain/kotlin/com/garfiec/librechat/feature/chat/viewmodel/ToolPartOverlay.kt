package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.response.SubagentAttachmentCallsEntry
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.ToolPartKey
import com.garfiec.librechat.feature.chat.util.withHydratedToolParts
import com.garfiec.librechat.feature.chat.util.withStoredSubagentHints

/**
 * Applies fetched full tool calls, then the server's subagent file hints, to
 * [ChatUiState.displayMessages], memoized on the identity of its three inputs. The state emits on
 * every streaming flush while the display path stays the same instance, so without the memo each
 * flush would re-key every previewed part.
 */
internal class ToolPartOverlay {
    private var lastDisplay: List<MessageNode>? = null
    private var lastLoaded: Map<ToolPartKey, AgentToolCall>? = null
    private var lastHints: Map<String, List<SubagentAttachmentCallsEntry>>? = null
    private var lastResult: List<MessageNode> = emptyList()

    fun apply(
        state: ChatUiState,
        loaded: Map<ToolPartKey, AgentToolCall>,
        hints: Map<String, List<SubagentAttachmentCallsEntry>> = emptyMap(),
    ): ChatUiState {
        if (loaded.isEmpty() && hints.isEmpty()) return state
        val display = state.content.displayMessages
        val hydrated = if (display === lastDisplay && loaded === lastLoaded && hints === lastHints) {
            lastResult
        } else {
            display.withHydratedToolParts(loaded).withStoredSubagentHints(hints).also {
                lastDisplay = display
                lastLoaded = loaded
                lastHints = hints
                lastResult = it
            }
        }
        return if (hydrated === display) state else state.copy(content = state.content.copy(displayMessages = hydrated))
    }
}
