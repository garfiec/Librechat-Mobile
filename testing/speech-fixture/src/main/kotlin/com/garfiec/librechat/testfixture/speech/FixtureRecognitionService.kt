package com.garfiec.librechat.testfixture.speech

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Answers every session with one final result and nothing else: no partials, no RMS, no
 * beginning/end-of-speech. That is how Whisper-style recognizers behave, and the opposite of
 * Google's, which the app's recognizer controller was tuned against.
 */
class FixtureRecognitionService : RecognitionService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        handler.removeCallbacksAndMessages(null)
        listener.readyForSpeech(Bundle())
        handler.postDelayed({
            listener.results(
                Bundle().apply {
                    putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(TRANSCRIPT))
                },
            )
        }, RESULT_DELAY_MS)
    }

    override fun onStopListening(listener: Callback) = Unit

    override fun onCancel(listener: Callback) {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val TRANSCRIPT = "fixture transcript"
        private const val RESULT_DELAY_MS = 300L
    }
}
