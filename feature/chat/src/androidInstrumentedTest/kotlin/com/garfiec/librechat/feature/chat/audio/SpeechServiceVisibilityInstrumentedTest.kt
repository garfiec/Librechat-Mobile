package com.garfiec.librechat.feature.chat.audio

import android.content.Intent
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A speech provider the user installed must be visible to us: on API 30+ another app's services and
 * activities are hidden unless the manifest declares a matching `<queries>` intent (#601). Every
 * lookup here goes through this module's own merged manifest, which is the one `:app` inherits.
 */
@RunWith(AndroidJUnit4::class)
class SpeechServiceVisibilityInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun requireFixture() = SpeechFixture.assumeInstalledAsDefault()

    @Test
    fun userInstalledRecognizerIsVisible() {
        val services = context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { it.serviceInfo.name }
        assertTrue("fixture recognizer not visible; saw $services", SpeechFixture.RECOGNITION_SERVICE in services)
        assertTrue(SpeechRecognizer.isRecognitionAvailable(context))
    }

    @Test
    fun userInstalledTtsEngineIsVisible() {
        val services = context.packageManager
            .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { it.serviceInfo.packageName }
        assertTrue("fixture TTS engine not visible; saw $services", SpeechFixture.PACKAGE in services)
    }

    @Test
    fun userInstalledRecognizeSpeechActivityIsVisible() {
        val activities = context.packageManager
            .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
            .map { it.activityInfo.name }
        assertTrue(
            "fixture recognize-speech activity not visible; saw $activities",
            SpeechFixture.RECOGNIZE_ACTIVITY in activities,
        )
    }
}
