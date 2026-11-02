package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.ToolPartKey
import com.garfiec.librechat.feature.chat.util.withHydratedToolParts

/**
 * Applies fetched full tool calls to [ChatUiState.displayMessages], memoized on the identity of
 * its two inputs. The state emits on every streaming flush while the display path stays the same
 * instance, so without the memo each flush would re-key every previewed part.
 */
internal class ToolPartOverlay {
    private var lastDisplay: List<MessageNode>? = null
    private var lastLoaded: Map<ToolPartKey, AgentToolCall>? = null
    private var lastResult: List<MessageNode> = emptyList()

    fun apply(state: ChatUiState, loaded: Map<ToolPartKey, AgentToolCall>): ChatUiState {
        if (loaded.isEmpty()) return state
        val display = state.content.displayMessages
        val hydrated = if (display === lastDisplay && loaded === lastLoaded) {
            lastResult
        } else {
            display.withHydratedToolParts(loaded).also {
                lastDisplay = display
                lastLoaded = loaded
                lastResult = it
            }
        }
        return if (hydrated === display) state else state.copy(content = state.content.copy(displayMessages = hydrated))
    }
}
