package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.speech.SpeechConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ServerSttGateConfigTest {

    private val speechRepository = mockk<SpeechRepository>()
    private val gate = ServerSttGate(speechRepository)

    @Test
    fun `a loaded config is returned and latches the gate`() = runTest {
        val config = SpeechConfig(sttExternal = false, ttsExternal = true)
        coEvery { speechRepository.getSpeechConfig() } returns Result.Success(config)

        assertThat(gate.refreshConfig()).isEqualTo(config)
        assertThat(gate.loaded).isTrue()
    }

    @Test
    fun `a failed fetch is unknown, not disabled`() = runTest {
        coEvery { speechRepository.getSpeechConfig() } returns Result.Error(RuntimeException("offline"))

        assertThat(gate.refreshConfig()).isNull()
        assertThat(gate.loaded).isFalse()
    }
}
