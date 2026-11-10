package com.garfiec.librechat.feature.settings.viewmodel.delegate

/** The platform half of the Voice page: server voices, device voices and the voice preview. */
interface SpeechSettingsContract {
    fun loadVoices()
    fun loadDeviceVoices()
    fun loadSpeechConfig()

    /** Retries what failed at launch: the speech config, or the voices once the config says the server has TTS. */
    fun retrySpeechConfig()
    fun previewDeviceTts(text: String, rate: Float, pitch: Float, voiceName: String?)
    fun previewServerTts(text: String, voice: String?)
    fun stopTtsPreview()
    fun release()
}
