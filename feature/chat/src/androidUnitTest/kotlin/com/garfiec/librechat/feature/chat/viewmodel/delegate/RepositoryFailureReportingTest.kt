package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.data.repository.PresetRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonHandle
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.PresetPromptHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
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
            reloadConversation = { _, _ -> reloaded = true },
            currentModelSpecs = { null },
        )

        delegate.branchFromComparison("agent_secondary____1")

        assertThat(flow.value.error).isEqualTo(ChatNotice.CONTINUE_RESPONSE_FAILED.marker())
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
            handle = PresetPromptHandle(
                ChatStateHandle(flow, CoroutineScope(Dispatchers.Unconfined)),
                persistDraft = {},
            ),
            presetRepository = presetRepository,
            promptRepository = mockk<PromptRepository>(relaxed = true),
        )

        delegate.savePreset("My preset")

        assertThat(flow.value.error).isEqualTo(ChatNotice.PRESET_SAVE_FAILED.marker())
    }

    @Test
    fun `a failed live read of an external slash prompt reports the error and inserts nothing`() {
        val group = PromptGroup(
            id = "g-1",
            name = "Ext",
            author = "a",
            authorName = "A",
            command = "ext",
            source = "langfuse",
        )
        val promptRepository = mockk<PromptRepository>(relaxed = true)
        every { promptRepository.revision } returns MutableStateFlow(0L)
        coEvery { promptRepository.getAllGroups() } returns Result.Success(listOf(group))
        coEvery { promptRepository.withResolvedText(any()) } returns Result.Error()
        val flow = MutableStateFlow(ChatUiState())
        val delegate = PresetPromptDelegate(
            handle = PresetPromptHandle(
                ChatStateHandle(flow, CoroutineScope(Dispatchers.Unconfined)),
                persistDraft = {},
            ),
            presetRepository = mockk<PresetRepository>(relaxed = true),
            promptRepository = promptRepository,
        )
        delegate.loadAvailablePrompts()
        flow.value = flow.value.copy(composer = flow.value.composer.copy(inputText = "/ext"))

        delegate.handleSlashCommand(flow.value.presetPrompts.availablePrompts.single())

        assertThat(flow.value.error).isEqualTo(ChatNotice.PROMPT_RESOLVE_FAILED.marker())
        assertThat(flow.value.composer.inputText).isEqualTo("/ext")
    }

    @Test
    fun `a failed preset load is a failure, not no presets, and reopening the picker retries it`() {
        val presetRepository = mockk<PresetRepository>()
        coEvery { presetRepository.getAll() } returns Result.Error(message = "offline")
        val flow = MutableStateFlow(ChatUiState())
        val delegate = PresetPromptDelegate(
            handle = PresetPromptHandle(
                ChatStateHandle(flow, CoroutineScope(Dispatchers.Unconfined)),
                persistDraft = {},
            ),
            presetRepository = presetRepository,
            promptRepository = mockk<PromptRepository>(relaxed = true),
        )

        delegate.loadPresets()
        assertThat(flow.value.presetPrompts.presetsLoadFailed).isTrue()

        coEvery { presetRepository.getAll() } returns Result.Success(listOf(Preset(presetId = "p1", title = "Mine")))
        delegate.reloadPresetsIfFailed()

        assertThat(flow.value.presetPrompts.presetsLoadFailed).isFalse()
        assertThat(flow.value.presets.map { it.title }).containsExactly("Mine")
    }
}
