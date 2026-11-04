package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.ToolPartKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The overlay touches only the display path, and only when a fetched part applies. */
class ToolPartOverlayTest {

    private val preview =
        AgentToolCall(id = "a", output = "head", outputTruncated = true, outputLength = 9, previewRevision = "r")
    private val message = Message(
        messageId = "m1",
        conversationId = "c",
        content = listOf(MessageContentPart(type = ContentType.TOOL_CALL, toolCall = preview)),
    )
    private val state = ChatUiState(
        content = MessagesState(
            messages = listOf(message),
            displayMessages = listOf(MessageNode(message, emptyList(), 0, 1)),
            activeBranches = mapOf("parent" to 1),
        ),
    )
    private val loaded =
        mapOf(requireNotNull(ToolPartKey.of(preview, null)) to AgentToolCall(id = "a", output = "full body"))

    @Test
    fun a_loaded_part_replaces_its_preview_on_the_display_path_only() {
        val result = ToolPartOverlay().apply(state, loaded)

        assertEquals("full body", result.displayMessages.single().message.content!!.single().toolCall?.output)
        // The backing messages and the branch selection are what delegates read; neither moves.
        assertSame(state.messages, result.messages)
        assertEquals(state.activeBranches, result.activeBranches)
    }

    @Test
    fun nothing_loaded_returns_the_same_state() {
        assertSame(state, ToolPartOverlay().apply(state, emptyMap()))
    }

    @Test
    fun the_same_inputs_reuse_the_last_result() {
        val overlay = ToolPartOverlay()
        val first = overlay.apply(state, loaded)
        val second = overlay.apply(state.copy(error = "flush"), loaded)
        assertSame(first.displayMessages, second.displayMessages)
    }
}
