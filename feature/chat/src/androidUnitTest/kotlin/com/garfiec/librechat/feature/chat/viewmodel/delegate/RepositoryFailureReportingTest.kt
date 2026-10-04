package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.data.repository.PresetRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonHandle
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.PresetPromptHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

/** Repository calls that return [Result.Error] rather than throwing must still surface the failure. */
class RepositoryFailureReportingTest {

    @Test
    fun `a failed comparison branch reports the error and keeps the comparison`() {
        val messageRepository = mockk<MessageRepository>()
        coEvery { messageRepository.branchMessage(any(), any(), any()) } returns Result.Error(message = "boom")
        val comparison = ComparisonState(parallelMessageId = "m-parallel")
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = "c1"),
                comparisonState = comparison,
            ),
        )
        var reloaded = false
        val delegate = ComparisonModeDelegate(
            handle = ComparisonHandle(ChatStateHandle(flow, CoroutineScope(Dispatchers.Unconfined))),
            messageRepository = messageRepository,
            reloadConversation = { reloaded = true },
        )

        delegate.branchFromComparison("agent_secondary____1")

        assertThat(flow.value.error).isEqualTo("Failed to continue with selected response")
        assertThat(flow.value.comparisonState).isEqualTo(comparison)
        assertThat(flow.value.pendingNavigationConversationId).isNull()
        assertThat(reloaded).isFalse()
    }

    @Test
    fun `a failed preset save reports the error`() {
        val presetRepository = mockk<PresetRepository>()
        coEvery { presetRepository.create(any()) } returns Result.Error(message = "boom")
        val flow = MutableStateFlow(ChatUiState())
        val delegate = PresetPromptDelegate(
            handle = PresetPromptHandle(ChatStateHandle(flow, CoroutineScope(Dispatchers.Unconfined))),
            presetRepository = presetRepository,
            promptRepository = mockk<PromptRepository>(relaxed = true),
        )

        delegate.savePreset("My preset")

        assertThat(flow.value.error).isEqualTo("Could not save preset")
    }
}
