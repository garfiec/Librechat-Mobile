package com.garfiec.librechat.feature.chat.viewmodel.delegate

import android.content.Context
import android.speech.tts.TextToSpeech
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.core.model.speech.SpeechConfig
import com.garfiec.librechat.core.model.speech.SpeechToTextResponse
import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.TtsHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextToSpeechDelegateTest {

    private val speech = SuspendingSpeechRepository()

    private fun TestScope.start(
        autoRead: Boolean = false,
        source: String = SettingsDataStore.TTS_SOURCE_SERVER,
        context: Context = mockk(relaxed = true),
    ): Pair<MutableStateFlow<ChatUiState>, TextToSpeechDelegate> {
        val settings = mockk<SettingsDataStore>(relaxed = true) {
            every { ttsSource } returns flowOf(source)
            every { ttsSpeechRate } returns flowOf(1f)
            every { ttsPitch } returns flowOf(1f)
            every { ttsVoiceName } returns flowOf("")
            every { autoReadEnabled } returns flowOf(autoRead)
            every { selectedVoiceId } returns flowOf(null)
        }
        val flow = MutableStateFlow(ChatUiState())
        val delegate = TextToSpeechDelegate(
            handle = TtsHandle(ChatStateHandle(flow, backgroundScope)),
            appContext = context,
            speechRepository = speech,
            settingsDataStore = settings,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            getMessageText = { "Text of $it" },
        )
        return flow to delegate
    }

    @Test
    fun `a completed send with auto-read off leaves the running read under Stop`() = runTest {
        val (flow, delegate) = start(autoRead = false)
        delegate.readAloud("m1")
        runCurrent()

        delegate.maybeAutoReadResponse("reply-2", "A new reply")
        runCurrent()
        assertThat(flow.value.voice.currentlyReadingMessageId).isEqualTo("m1")

        delegate.stopReading()
        runCurrent()
        assertThat(speech.requests.single().cancelled).isTrue()
        assertThat(flow.value.voice.currentlyReadingMessageId).isNull()
    }

    @Test
    fun `auto-read replaces a running read`() = runTest {
        val (flow, delegate) = start(autoRead = true)
        delegate.readAloud("m1")
        runCurrent()

        delegate.maybeAutoReadResponse("reply-2", "A new reply")
        runCurrent()

        assertThat(speech.requests.map { it.text }).containsExactly("Text of m1", "A new reply").inOrder()
        assertThat(speech.requests[0].cancelled).isTrue()
        assertThat(speech.requests[1].cancelled).isFalse()
        assertThat(flow.value.voice.currentlyReadingMessageId).isEqualTo("reply-2")
    }

    @Test
    fun `a Stop before the device engine is ready keeps it silent`() = runTest {
        val (_, delegate) = start(source = SettingsDataStore.TTS_SOURCE_DEVICE, context = RuntimeEnvironment.getApplication())
        delegate.readAloud("m1")
        runCurrent()
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())

        delegate.stopReading()
        engine.onInitListener.onInit(TextToSpeech.SUCCESS)

        assertThat(engine.spokenTextList).isEmpty()
    }

    @Test
    fun `a device read speaks once the engine is ready`() = runTest {
        val (_, delegate) = start(source = SettingsDataStore.TTS_SOURCE_DEVICE, context = RuntimeEnvironment.getApplication())
        delegate.readAloud("m1")
        runCurrent()
        val engine = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())

        engine.onInitListener.onInit(TextToSpeech.SUCCESS)

        assertThat(engine.spokenTextList).containsExactly("Text of m1")
    }

    private class SuspendingSpeechRepository : SpeechRepository {
        class Request(val text: String, val voice: String?) {
            val reply = CompletableDeferred<Result<ByteArray>>()
            var cancelled = false
        }

        val requests = mutableListOf<Request>()

        override suspend fun synthesizeSpeech(text: String, voice: String?, model: String?): Result<ByteArray> {
            val request = Request(text, voice).also { requests += it }
            try {
                return request.reply.await()
            } catch (e: CancellationException) {
                request.cancelled = true
                throw e
            }
        }

        override suspend fun transcribeAudio(audioData: ByteArray, mimeType: String): Result<SpeechToTextResponse> =
            error("unused")

        override suspend fun getVoices(): Result<List<TtsVoice>> = Result.Success(emptyList())
        override suspend fun getSpeechConfig(): Result<SpeechConfig> = error("unused")
        override suspend fun isServerSttEnabled(): Result<Boolean> = error("unused")
    }
}
