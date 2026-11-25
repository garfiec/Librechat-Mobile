package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** `interface.retentionMode: "ephemeral"` (v0.8.9-rc1): every new chat is temporary. */
class ForcedTemporaryChatTest {

    private val enforced = FeatureGatesState(temporaryChatEnforced = true)

    @Test
    fun aNewChatIsTemporaryWhenTheServerForcesIt() {
        assertThat(ChatUiState(gates = enforced).isTemporaryChat).isTrue()
    }

    @Test
    fun anExistingChatIsNotMadeTemporaryByTheMode() {
        val state = ChatUiState(gates = enforced, conversation = ConversationMetaState(conversationId = "c1"))
        assertThat(state.isTemporaryChat).isFalse()
    }

    @Test
    fun aLatchedTemporaryChatStaysTemporaryOnceItHasAnId() {
        val state = ChatUiState(
            gates = enforced,
            conversation = ConversationMetaState(conversationId = "c1", isTemporaryChat = true),
        )
        assertThat(state.isTemporaryChat).isTrue()
    }

    @Test
    fun withoutTheModeTheToggleDecides() {
        assertThat(ChatUiState().isTemporaryChat).isFalse()
        assertThat(ChatUiState(conversation = ConversationMetaState(isTemporaryChat = true)).isTemporaryChat).isTrue()
    }

    @Test
    fun statusDecodesTheRunsTemporaryState() {
        val json = Json { ignoreUnknownKeys = true }
        assertThat(json.decodeFromString(ChatStatusResponse.serializer(), """{"active":true,"isTemporary":true}""").isTemporary).isTrue()
        assertThat(json.decodeFromString(ChatStatusResponse.serializer(), """{"active":true}""").isTemporary).isFalse()
    }
}
