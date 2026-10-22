package com.garfiec.librechat.feature.chat.audio

import android.Manifest
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Dictation and read-aloud through a third-party provider, end to end. The fixture recognizer
 * answers the way Whisper-style recognizers do (one final result, no partials, no RMS), so this
 * also covers a recognizer [SpeechRecognizerController] was not tuned against. An activity is kept
 * in the foreground throughout: RECORD_AUDIO is a while-in-use permission, and the recognizer
 * service refuses a caller in the background.
 */
@RunWith(AndroidJUnit4::class)
class SpeechFixtureEndToEndInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @Before
    fun setUp() {
        SpeechFixture.assumeInstalledAsDefault()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun dictationReachesTheFixtureRecognizer() = assertDictation(preferOnDevice = false)

    // No-GMS ROMs configure no on-device recognizer, so the preference must fall through to the
    // user's default recognizer rather than fail.
    @Test
    fun dictationPreferringOnDeviceFallsThroughToTheFixtureRecognizer() = assertDictation(preferOnDevice = true)

    @Test
    fun readAloudSpeaksThroughTheFixtureEngine() {
        val initialized = CountDownLatch(1)
        val initStatus = AtomicInteger(Int.MIN_VALUE)
        val tts = AtomicReference<TextToSpeech>()
        instrumentation.runOnMainSync {
            tts.set(
                TextToSpeech(context) { status ->
                    initStatus.set(status)
                    initialized.countDown()
                },
            )
        }
        try {
            assertTrue("TTS never initialized", initialized.await(TIMEOUT_S, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, initStatus.get())
            val engine = tts.get()
            assertTrue(
                "fixture engine not listed; saw ${engine.engines.map { it.name }}",
                engine.engines.any { it.name == SpeechFixture.PACKAGE },
            )

            val done = CountDownLatch(1)
            val sampleRate = AtomicInteger(-1)
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onBeginSynthesis(id: String?, rate: Int, format: Int, channels: Int) {
                        sampleRate.set(rate)
                    }
                    override fun onDone(utteranceId: String?) = done.countDown()

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = done.countDown()
                },
            )
            assertEquals(TextToSpeech.SUCCESS, engine.speak("hello", TextToSpeech.QUEUE_FLUSH, null, "u1"))
            assertTrue("utterance never finished", done.await(TIMEOUT_S, TimeUnit.SECONDS))
            // A fallback engine would also finish; only the fixture synthesizes at this rate.
            assertEquals(SpeechFixture.TTS_SAMPLE_RATE, sampleRate.get())
        } finally {
            instrumentation.runOnMainSync { tts.get()?.shutdown() }
        }
    }

    private fun assertDictation(preferOnDevice: Boolean) {
        val segments = CopyOnWriteArrayList<String>()
        val error = AtomicReference<String?>()
        val ended = CountDownLatch(1)
        lateinit var controller: SpeechRecognizerController
        instrumentation.runOnMainSync {
            controller = SpeechRecognizerController(context).apply {
                onSegment = { segments += it }
                onError = {
                    error.set(it)
                    ended.countDown()
                }
                onFinished = { ended.countDown() }
            }
            controller.start(preferOnDevice = preferOnDevice, endOfSpeech = true, languageTag = null)
        }
        try {
            assertTrue("dictation never ended", ended.await(TIMEOUT_S, TimeUnit.SECONDS))
            assertNull(error.get())
            assertEquals(listOf(SpeechFixture.TRANSCRIPT), segments.toList())
        } finally {
            instrumentation.runOnMainSync { controller.destroy() }
        }
    }

    private companion object {
        const val TIMEOUT_S = 15L
    }
}
