package com.garfiec.librechat.core.ui.media

import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechUtterance

/** AVSpeechUtterance's normal rate; Settings' rate (Android's scale, 1.0 = normal) is scaled onto it. */
private const val NORMAL_UTTERANCE_RATE = 0.5f
private const val MIN_UTTERANCE_RATE = 0.0f
private const val MAX_UTTERANCE_RATE = 1.0f
private const val MIN_PITCH = 0.5f
private const val MAX_PITCH = 2.0f

/**
 * The device voice Settings stored by identifier (a name stored by an older build still resolves),
 * or null when [stored] is blank or names no installed voice.
 */
fun findDeviceVoice(stored: String?): AVSpeechSynthesisVoice? =
    stored?.takeIf { it.isNotBlank() }?.let { id ->
        AVSpeechSynthesisVoice.voiceWithIdentifier(id)
            ?: AVSpeechSynthesisVoice.speechVoices()
                .filterIsInstance<AVSpeechSynthesisVoice>()
                .find { it.name == id }
    }

/** [findDeviceVoice], else the default voice for the device's language. */
fun resolveDeviceVoice(stored: String?): AVSpeechSynthesisVoice? =
    findDeviceVoice(stored) ?: AVSpeechSynthesisVoice.voiceWithLanguage(null)

/** An utterance of [text] at Settings' [rate] and [pitch] (both on Android's scale), in [voice]. */
fun deviceUtterance(text: String, rate: Float, pitch: Float, voice: AVSpeechSynthesisVoice?): AVSpeechUtterance {
    val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
    utterance.rate = (rate * NORMAL_UTTERANCE_RATE).coerceIn(MIN_UTTERANCE_RATE, MAX_UTTERANCE_RATE)
    utterance.pitchMultiplier = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
    utterance.voice = voice
    return utterance
}
