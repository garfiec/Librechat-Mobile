package com.garfiec.librechat.feature.settings.viewmodel.delegate

/** The platform half of the Voice page: server voices, device voices and the voice preview. */
interface SpeechSettingsContract {
    fun loadVoices()
    fun loadDeviceVoices()
    fun loadSpeechConfig()

    /** Whether a speech config read has answered; a failed read leaves it false. */
    val speechConfigLoaded: Boolean
    fun previewDeviceTts(text: String, rate: Float, pitch: Float, voiceName: String?)
    fun previewServerTts(text: String, voice: String?)
    fun stopTtsPreview()
    fun release()
}
