package com.garfiec.librechat.testfixture.speech

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent

/** Returns the fixture transcript immediately, like a recognize-speech overlay that heard it. */
class FixtureRecognizeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(
            RESULT_OK,
            Intent().putStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS,
                arrayListOf(FixtureRecognitionService.TRANSCRIPT),
            ),
        )
        finish()
    }
}
