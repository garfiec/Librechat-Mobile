package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.content.SummaryBoundary
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * [ChatUiState.canCompactNow] — the gate on the Compact action (v0.8.8-rc3).
 *
 * `compactionEnabled` is an ANNOUNCED capability, so its absence is the whole gate: no version
 * check stacks on top, and a server that never sent the flag is withheld rather than defaulted on,
 * because it would answer a `compact` send as an ordinary empty turn.
 */
class ChatUiStateCompactionTest {

    private fun node(message: Message) =
        MessageNode(message = message, children = emptyList(), siblingIndex = 0, siblingCount = 1)

    private fun reply(id: String = "leaf-1", parent: String? = "user-1") = Message(
        messageId = id,
        conversationId = "conv-1",
        parentMessageId = parent,
        isCreatedByUser = false,
    )

    private fun compactedLeaf() = reply().copy(
        content = listOf(
            MessageContentPart(
                type = ContentType.SUMMARY,
                content = JsonPrimitive("already summarized"),
                boundary = SummaryBoundary(messageId = "user-1", contentIndex = 0),
            ),
        ),
    )

    private fun state(
        compactionEnabled: Boolean = true,
        endpoint: String = "openAI",
        conversationId: String? = "conv-1",
        isStreaming: Boolean = false,
        leaf: Message? = reply(),
    ) = ChatUiState(
        conversation = ConversationMetaState(conversationId = conversationId),
        selection = ModelSelectionState(selectedEndpoint = endpoint),
        gates = FeatureGatesState(compactionEnabled = compactionEnabled),
        content = MessagesState(
            isStreaming = isStreaming,
            displayMessages = listOfNotNull(leaf?.let(::node)),
        ),
    )

    @Test
    fun `offered on a settled conversation with a compactable leaf`() {
        assertThat(state().canCompactNow).isTrue()
        assertThat(state().compactionLeaf?.messageId).isEqualTo("leaf-1")
    }

    @Test
    fun `withheld when the server never announced the capability`() {
        assertThat(state(compactionEnabled = false).canCompactNow).isFalse()
    }

    @Test
    fun `withheld on the assistants endpoints`() {
        assertThat(state(endpoint = "assistants").canCompactNow).isFalse()
        assertThat(state(endpoint = "azureAssistants").canCompactNow).isFalse()
    }

    @Test
    fun `withheld before the conversation exists and while a run is live`() {
        assertThat(state(conversationId = null).canCompactNow).isFalse()
        assertThat(state(isStreaming = true).canCompactNow).isFalse()
    }

    @Test
    fun `withheld on an empty branch and on a root with no parent`() {
        assertThat(state(leaf = null).canCompactNow).isFalse()
        assertThat(state(leaf = reply(parent = null)).canCompactNow).isFalse()
    }

    @Test
    fun `withheld when the leaf is already a finished compaction`() {
        // Otherwise the action summarizes a summary.
        val compacted = state(leaf = compactedLeaf())
        assertThat(compacted.compactionLeaf).isNull()
        assertThat(compacted.canCompactNow).isFalse()
    }
}
