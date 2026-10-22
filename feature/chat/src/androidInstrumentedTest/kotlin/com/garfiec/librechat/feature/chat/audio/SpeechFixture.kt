package com.garfiec.librechat.feature.chat.audio

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

/**
 * The stand-in third-party speech provider built from `:testing:speech-fixture`. The speech tests
 * only mean something on a device where it is installed as its own APK and set as the default
 * recognizer and TTS engine, which `scripts/verify-speech-services.py` arranges on a no-GMS emulator.
 * Anywhere else they skip; the script fails a run that skipped them.
 */
internal object SpeechFixture {
    const val PACKAGE = "com.garfiec.librechat.testfixture.speech"
    const val RECOGNITION_SERVICE = "$PACKAGE.FixtureRecognitionService"
    const val RECOGNIZE_ACTIVITY = "$PACKAGE.FixtureRecognizeActivity"
    const val TRANSCRIPT = "fixture transcript"
    const val TTS_SAMPLE_RATE = 11_025

    /**
     * Skips unless the fixture is installed and is the default recognizer and TTS engine. Asked
     * through the shell, which sees every package: asking our own PackageManager would be asking the
     * very question under test, and a hidden fixture would turn a failure into a skip.
     */
    fun assumeInstalledAsDefault() {
        assumeTrue(
            "speech fixture not installed; run scripts/verify-speech-services.py",
            shell("pm list packages $PACKAGE").lines().any { it.trim() == "package:$PACKAGE" },
        )
        assumeTrue(
            "speech fixture is not the default recognizer",
            shell("settings get secure voice_recognition_service").trim()
                .startsWith("$PACKAGE/"),
        )
        assumeTrue(
            "speech fixture is not the default TTS engine",
            shell("settings get secure tts_default_synth").trim() == PACKAGE,
        )
    }

    private fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes().decodeToString() }
            .also { pfd.close() }
    }
}
