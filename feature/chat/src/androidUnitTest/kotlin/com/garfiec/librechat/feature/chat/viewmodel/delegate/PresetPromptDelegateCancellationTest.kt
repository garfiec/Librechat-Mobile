package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.PresetRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.PresetPromptHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PresetPromptDelegateCancellationTest {

    @Test
    fun `cancelling a preset save mid-request writes no error`() = runTest {
        val presetRepository = mockk<PresetRepository>()
        coEvery { presetRepository.create(any()) } coAnswers { awaitCancellation() }
        val flow = MutableStateFlow(ChatUiState())
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val delegate = PresetPromptDelegate(
            handle = PresetPromptHandle(ChatStateHandle(flow, scope)),
            presetRepository = presetRepository,
            promptRepository = mockk<PromptRepository>(relaxed = true),
        )

        delegate.savePreset("My preset")
        runCurrent()
        scope.cancel()
        advanceUntilIdle()

        assertThat(flow.value.error).isNull()
    }
}
