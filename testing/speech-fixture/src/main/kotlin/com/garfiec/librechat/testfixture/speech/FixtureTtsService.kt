package com.garfiec.librechat.testfixture.speech

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService

/**
 * An English-only engine that "speaks" a few milliseconds of silence for any text, at a sample rate
 * the AOSP image's own engine doesn't use, so a test can tell this engine produced the audio and not
 * a fallback.
 */
class FixtureTtsService : TextToSpeechService() {

    private var language = arrayOf("eng", "USA", "")

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        if (lang == "eng" || lang == "en") TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_NOT_SUPPORTED

    override fun onGetLanguage(): Array<String> = language

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val result = onIsLanguageAvailable(lang, country, variant)
        if (result >= TextToSpeech.LANG_AVAILABLE) language = arrayOf("eng", country.orEmpty(), "")
        return result
    }

    override fun onStop() = Unit

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        callback.start(SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)
        val silence = ByteArray(SAMPLE_RATE / 50 * 2)
        callback.audioAvailable(silence, 0, silence.size)
        callback.done()
    }

    private companion object {
        const val SAMPLE_RATE = 11_025
    }
}
