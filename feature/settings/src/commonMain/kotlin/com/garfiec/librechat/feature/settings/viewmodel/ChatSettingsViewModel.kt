package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.feature.settings.viewmodel.delegate.SpeechSettingsFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Backs the Chat tab: display and behaviour preferences, artifacts, speech, and fork mode. */
// TooManyFunctions: the members are one-line forwarders to controllers, one per setting, so the
// count tracks how many settings exist rather than how much this class does.
@Suppress("TooManyFunctions")
class ChatSettingsViewModel(
    settingsDataStore: SettingsDataStore,
    speechSettingsFactory: SpeechSettingsFactory,
) : ViewModel() {

    /** Raw state for everything not driven by DataStore flows. */
    private val _uiState = MutableStateFlow(ChatSettingsUiState())

    private val stateHandle = SettingsStateHandle(_uiState, viewModelScope)

    // --- Delegates ---
    private val speechDelegate = speechSettingsFactory.create(stateHandle)

    /** Owns the DataStore read flows + write setters; merges them with [_uiState]. */
    private val prefsController = ChatPreferencesController(
        settingsDataStore,
        _uiState,
        viewModelScope,
    )

    /** The single public UI state that merges DataStore preferences with imperative state. */
    val uiState: StateFlow<ChatSettingsUiState> = prefsController.uiState

    init {
        speechDelegate.loadVoices()
        speechDelegate.loadSpeechConfig()
        speechDelegate.loadDeviceVoices()
    }

    // ── Chat preferences ───────────────────────────────────────────

    fun setChatFontSize(size: ChatFontSize) {
        prefsController.setChatFontSize(size)
    }

    fun setStarredModelsDisplay(display: StarredModelsDisplay) {
        prefsController.setStarredModelsDisplay(display)
    }

    fun setChatHeaderContent(content: ChatHeaderContent) {
        prefsController.setChatHeaderContent(content)
    }

    fun setChatHeaderAlignment(alignment: ChatHeaderAlignment) {
        prefsController.setChatHeaderAlignment(alignment)
    }

    fun setAutoScrollEnabled(enabled: Boolean) {
        prefsController.setAutoScrollEnabled(enabled)
    }

    fun setShowThinkingBlocks(show: Boolean) {
        prefsController.setShowThinkingBlocks(show)
    }

    fun setContextBarPlacement(placement: ContextBarPlacement) {
        prefsController.setContextBarPlacement(placement)
    }

    fun setDuringRunAction(action: DuringRunAction) {
        prefsController.setDuringRunAction(action)
    }

    fun setUploadRoutingMode(mode: UploadRoutingMode) {
        prefsController.setUploadRoutingMode(mode)
    }

    fun setShowImageDescriptions(show: Boolean) {
        prefsController.setShowImageDescriptions(show)
    }

    fun setShowSiteIcons(show: Boolean) {
        prefsController.setShowSiteIcons(show)
    }

    fun setDismissKeyboardOnSend(enabled: Boolean) {
        prefsController.setDismissKeyboardOnSend(enabled)
    }

    fun setChatLayoutStyle(style: String) {
        prefsController.setChatLayoutStyle(style)
    }

    fun setShowAvatars(show: Boolean) {
        prefsController.setShowAvatars(show)
    }

    fun setShowBubbles(show: Boolean) {
        prefsController.setShowBubbles(show)
    }

    fun setLatexRenderer(renderer: LatexRenderer) {
        prefsController.setLatexRenderer(renderer)
    }

    fun setInlineArtifactMermaid(enabled: Boolean) {
        prefsController.setInlineArtifactMermaid(enabled)
    }

    fun setInlineArtifactSvg(enabled: Boolean) {
        prefsController.setInlineArtifactSvg(enabled)
    }

    fun setInlineArtifactHtml(enabled: Boolean) {
        prefsController.setInlineArtifactHtml(enabled)
    }

    fun setInlineArtifactReact(enabled: Boolean) {
        prefsController.setInlineArtifactReact(enabled)
    }

    fun setInlineArtifactMarkdown(enabled: Boolean) {
        prefsController.setInlineArtifactMarkdown(enabled)
    }

    fun setArtifactDisplayMode(mode: ArtifactDisplayMode) {
        prefsController.setArtifactDisplayMode(mode)
    }

    // ── Fork settings ──────────────────────────────────────────────

    fun showForkSettingsDialog() {
        _uiState.update { it.copy(showForkSettingsDialog = true) }
    }

    fun dismissForkSettingsDialog() {
        _uiState.update { it.copy(showForkSettingsDialog = false) }
    }

    fun setForkMode(mode: String) {
        _uiState.update { it.copy(forkMode = mode, showForkSettingsDialog = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Delegated public API ───────────────────────────────────────

    // Speech settings
    fun setAutoSendAfterStt(enabled: Boolean) = speechDelegate.setAutoSendAfterStt(enabled)
    fun setAutoReadEnabled(enabled: Boolean) = speechDelegate.setAutoReadEnabled(enabled)
    fun selectVoice(voice: TtsVoice) = speechDelegate.selectVoice(voice)
    fun testVoice() = speechDelegate.testVoice()
    fun previewDeviceTts(text: String, rate: Float, pitch: Float, voiceName: String?) =
        speechDelegate.previewDeviceTts(text, rate, pitch, voiceName)
    fun previewServerTts(text: String, voice: String?, model: String?) =
        speechDelegate.previewServerTts(text, voice, model)
    fun stopTtsPreview() = speechDelegate.stopTtsPreview()
    fun showSttDetailDialog() = speechDelegate.showSttDetailDialog()
    fun dismissSttDetailDialog() = speechDelegate.dismissSttDetailDialog()
    fun saveSttSettings(engine: String, language: String, onDevice: Boolean, endOfSpeech: Boolean) =
        speechDelegate.saveSttSettings(engine, language, onDevice, endOfSpeech)
    fun showTtsDetailDialog() = speechDelegate.showTtsDetailDialog()
    fun dismissTtsDetailDialog() = speechDelegate.dismissTtsDetailDialog()
    fun saveTtsSettings(
        engine: String,
        voice: String,
        rate: Float,
        pitch: Float,
        deviceVoiceName: String,
        caching: Boolean,
        source: String,
    ) = speechDelegate.saveTtsSettings(engine, voice, rate, pitch, deviceVoiceName, caching, source)

    override fun onCleared() {
        super.onCleared()
        speechDelegate.release()
    }
}
