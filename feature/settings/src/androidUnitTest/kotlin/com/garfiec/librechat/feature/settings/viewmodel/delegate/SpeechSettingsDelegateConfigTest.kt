package com.garfiec.librechat.feature.settings.viewmodel.delegate

import android.content.Context
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.core.model.speech.SpeechConfig
import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.SettingsStateHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The voices route answers a 500 on every server without `speech.tts`, so it is fetched only after
 * `config/get` reports `ttsExternal`, and the Server TTS source is offered on the same flag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpeechSettingsDelegateConfigTest {

    private val dispatcher = StandardTestDispatcher()
    private val speechRepository = mockk<SpeechRepository>()
    private val state = MutableStateFlow(ChatSettingsUiState())

    private fun TestScope.delegate() = SpeechSettingsDelegate(
        stateHandle = SettingsStateHandle(state, this),
        context = mockk<Context>(relaxed = true),
        speechRepository = speechRepository,
        settingsDataStore = mockk<SettingsDataStore>(relaxed = true),
        ioDispatcher = dispatcher,
    )

    @Test
    fun `a server without TTS is never asked for voices`() = runTest(dispatcher) {
        coEvery { speechRepository.getSpeechConfig() } returns
            Result.Success(SpeechConfig(sttExternal = true, ttsExternal = false))

        delegate().loadSpeechConfig()
        advanceUntilIdle()

        coVerify(exactly = 0) { speechRepository.getVoices() }
        assertThat(state.value.serverTtsEnabled).isFalse()
        assertThat(state.value.serverSttEnabled).isTrue()
    }

    @Test
    fun `a server with TTS lists its voices`() = runTest(dispatcher) {
        val voices = listOf(TtsVoice(id = "alloy", name = "alloy"))
        coEvery { speechRepository.getSpeechConfig() } returns Result.Success(SpeechConfig(ttsExternal = true))
        coEvery { speechRepository.getVoices() } returns Result.Success(voices)

        delegate().loadSpeechConfig()
        advanceUntilIdle()

        coVerify(exactly = 1) { speechRepository.getVoices() }
        assertThat(state.value.serverTtsEnabled).isTrue()
        assertThat(state.value.availableVoices).isEqualTo(voices)
    }

    @Test
    fun `a failed config fetch asks for no voices and is retried when the TTS dialog opens`() = runTest(dispatcher) {
        coEvery { speechRepository.getSpeechConfig() } returns Result.Error(RuntimeException("offline"))
        val delegate = delegate()

        delegate.loadSpeechConfig()
        advanceUntilIdle()
        coVerify(exactly = 0) { speechRepository.getVoices() }
        assertThat(state.value.serverTtsEnabled).isFalse()

        coEvery { speechRepository.getSpeechConfig() } returns Result.Success(SpeechConfig(ttsExternal = true))
        coEvery { speechRepository.getVoices() } returns Result.Success(emptyList())
        delegate.showTtsDetailDialog()
        advanceUntilIdle()

        coVerify(exactly = 2) { speechRepository.getSpeechConfig() }
        assertThat(state.value.serverTtsEnabled).isTrue()
        assertThat(state.value.showTtsDetailDialog).isTrue()
    }

    @Test
    fun `opening the TTS dialog after a successful load does not refetch the config`() = runTest(dispatcher) {
        coEvery { speechRepository.getSpeechConfig() } returns Result.Success(SpeechConfig())
        val delegate = delegate()

        delegate.loadSpeechConfig()
        advanceUntilIdle()
        delegate.showTtsDetailDialog()
        advanceUntilIdle()

        coVerify(exactly = 1) { speechRepository.getSpeechConfig() }
    }
}
