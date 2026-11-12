package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.speech.voiceFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Extra DataStore preferences (separate combine since Kotlin combine maxes at 5). */
private data class ExtraPreferences(
    val autoRead: Boolean,
    val selectedVoiceId: String?,
    val showImageDescriptions: Boolean,
    val dismissKeyboardOnSend: Boolean,
    val ttsSource: String,
)

/** Device TTS preferences (speech rate, pitch, voice name). */
private data class DeviceTtsPreferences(
    val speechRate: Float,
    val pitch: Float,
    val voiceName: String,
)

/** Additional preferences combined separately to stay within the 5-arg combine limit. */
private data class AdditionalPreferences(
    val autoSendAfterStt: Boolean,
    val sttEngine: String,
    val sttLanguage: String,
    val sttOnDevice: Boolean = true,
    val sttEndOfSpeech: Boolean = false,
    val latexRenderer: LatexRenderer = LatexRenderer.KATEX,
    val inlineArtifactPrefs: InlineArtifactPrefs = InlineArtifactPrefs(),
    val artifactDisplayMode: ArtifactDisplayMode = ArtifactDisplayMode.AUTOMATIC,
    val artifactsMode: ArtifactsMode = ArtifactsMode.DEFAULT,
    val starredModelsDisplay: StarredModelsDisplay = StarredModelsDisplay.GROUPED,
    val duringRunAction: DuringRunAction = DuringRunAction.STEER,
    val uploadRoutingMode: UploadRoutingMode = UploadRoutingMode.AUTO,
    val showSiteIcons: Boolean = false,
    val preferLastUsedModel: Boolean = false,
    val webSearchFirst: WebSearchPreference = WebSearchPreference.LIBRECHAT_FIRST,
    val collapseLongUserMessages: Boolean = true,
    val clientImageResize: Boolean = true,
)

/**
 * Owns the DataStore side of the Chat tab: the read flows (the multi-stage `combine`
 * machinery, split across holders because Kotlin's `combine` maxes at 5 sources)
 * merged with the ViewModel's imperative [baseState] into a single [uiState], plus
 * the matching write setters. The ViewModel keeps only imperative state and
 * forwards preference reads/writes here.
 */
@Suppress("TooManyFunctions") // debt: 30 functions, one setter per setting
class ChatPreferencesController(
    private val settingsDataStore: SettingsDataStore,
    private val preferenceStore: PreferenceStore,
    baseState: StateFlow<ChatSettingsUiState>,
    private val scope: CoroutineScope,
) {
    private val thinkingOpenPref: StateFlow<Boolean> = settingsDataStore.thinkingOpenByDefault
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val extraPreferences: StateFlow<ExtraPreferences> = combine(
        settingsDataStore.autoReadEnabled,
        settingsDataStore.selectedVoiceId,
        settingsDataStore.showImageDescriptions,
        settingsDataStore.dismissKeyboardOnSend,
        settingsDataStore.ttsSource,
    ) { autoRead, voiceId, showImgDesc, dismissKeyboard, ttsSource ->
        ExtraPreferences(autoRead, voiceId, showImgDesc, dismissKeyboard, ttsSource)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        ExtraPreferences(false, null, false, true, SettingsDataStore.TTS_SOURCE_DEVICE),
    )

    private val deviceTtsPreferences: StateFlow<DeviceTtsPreferences> = combine(
        settingsDataStore.ttsSpeechRate,
        settingsDataStore.ttsPitch,
        settingsDataStore.ttsVoiceName,
    ) { rate, pitch, voiceName ->
        DeviceTtsPreferences(rate, pitch, voiceName)
    }.stateIn(scope, SharingStarted.Eagerly, DeviceTtsPreferences(1.0f, 1.0f, ""))

    private val latexRendererPref: StateFlow<LatexRenderer> = settingsDataStore.latexRenderer
        .stateIn(scope, SharingStarted.Eagerly, LatexRenderer.KATEX)

    private val inlineArtifactPrefsFlow: StateFlow<InlineArtifactPrefs> = settingsDataStore.inlineArtifactPrefs
        .stateIn(scope, SharingStarted.Eagerly, InlineArtifactPrefs())

    private val artifactDisplayModePref: StateFlow<ArtifactDisplayMode> = preferenceStore.observe(AppPreferences.ArtifactDisplay)
        .stateIn(scope, SharingStarted.Eagerly, preferenceStore.initial(AppPreferences.ArtifactDisplay))

    private val artifactsModePref: StateFlow<ArtifactsMode> = settingsDataStore.artifactsMode
        .stateIn(scope, SharingStarted.Eagerly, ArtifactsMode.DEFAULT)

    private val starredModelsDisplayPref: StateFlow<StarredModelsDisplay> = settingsDataStore.starredModelsDisplay
        .stateIn(scope, SharingStarted.Eagerly, StarredModelsDisplay.GROUPED)

    private val duringRunActionPref: StateFlow<DuringRunAction> = settingsDataStore.duringRunAction
        .stateIn(scope, SharingStarted.Eagerly, DuringRunAction.STEER)

    private val uploadRoutingModePref: StateFlow<UploadRoutingMode> = settingsDataStore.uploadRoutingMode
        .stateIn(scope, SharingStarted.Eagerly, UploadRoutingMode.AUTO)

    private val baseAdditionalPreferences = combine(
        settingsDataStore.autoSendAfterStt,
        settingsDataStore.sttEngine,
        settingsDataStore.sttLanguage,
    ) { autoSendStt, sttEngine, sttLanguage ->
        AdditionalPreferences(autoSendStt, sttEngine, sttLanguage)
    }

    private val additionalPreferences: StateFlow<AdditionalPreferences> = baseAdditionalPreferences.combine(
        latexRendererPref,
    ) { base, latexRenderer ->
        base.copy(latexRenderer = latexRenderer)
    }.combine(inlineArtifactPrefsFlow) { additional, inlineArtifact ->
        additional.copy(inlineArtifactPrefs = inlineArtifact)
    }.combine(artifactDisplayModePref) { additional, artifactDisplay ->
        additional.copy(artifactDisplayMode = artifactDisplay)
    }.combine(artifactsModePref) { additional, artifactsMode ->
        additional.copy(artifactsMode = artifactsMode)
    }.combine(starredModelsDisplayPref) { additional, starredDisplay ->
        additional.copy(starredModelsDisplay = starredDisplay)
    }.combine(duringRunActionPref) { additional, duringRunAction ->
        additional.copy(duringRunAction = duringRunAction)
    }.combine(uploadRoutingModePref) { additional, uploadRoutingMode ->
        additional.copy(uploadRoutingMode = uploadRoutingMode)
    }.combine(settingsDataStore.sttOnDevice) { additional, sttOnDevice ->
        additional.copy(sttOnDevice = sttOnDevice)
    }.combine(settingsDataStore.sttEndOfSpeech) { additional, sttEndOfSpeech ->
        additional.copy(sttEndOfSpeech = sttEndOfSpeech)
    }.combine(preferenceStore.observe(AppPreferences.SiteIcons)) { additional, siteIcons ->
        // Never chosen reads as off: the chat screen asks before anything loads.
        additional.copy(showSiteIcons = siteIcons ?: false)
    }.combine(preferenceStore.observe(AppPreferences.PreferLastUsedModel)) { additional, preferLastUsed ->
        additional.copy(preferLastUsedModel = preferLastUsed)
    }.combine(preferenceStore.observe(AppPreferences.WebSearchFirst)) { additional, webSearchFirst ->
        additional.copy(webSearchFirst = webSearchFirst)
    }.combine(preferenceStore.observe(AppPreferences.CollapseLongUserMessages)) { additional, collapse ->
        additional.copy(collapseLongUserMessages = collapse)
    }.combine(preferenceStore.observe(AppPreferences.ClientImageResize)) { additional, resize ->
        additional.copy(clientImageResize = resize)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        // Seeded with the channel default so a store build's toggle doesn't flash off on open.
        AdditionalPreferences(false, "", "", showSiteIcons = preferenceStore.initial(AppPreferences.SiteIcons) ?: false),
    )

    /** The single public UI state that merges DataStore preferences with imperative state. */
    val uiState: StateFlow<ChatSettingsUiState> = combine(
        baseState,
        thinkingOpenPref,
        extraPreferences,
        deviceTtsPreferences,
        additionalPreferences,
    ) { state, thinkingOpen, extra, deviceTts, additional ->
        state.copy(
            thinkingOpenByDefault = thinkingOpen,
            autoReadEnabled = extra.autoRead,
            selectedVoice = state.availableVoices.voiceFor(extra.selectedVoiceId),
            showImageDescriptions = extra.showImageDescriptions,
            dismissKeyboardOnSend = extra.dismissKeyboardOnSend,
            ttsSource = extra.ttsSource,
            ttsSpeechRate = deviceTts.speechRate,
            ttsPitch = deviceTts.pitch,
            ttsDeviceVoiceName = deviceTts.voiceName,
            sttAutoSend = additional.autoSendAfterStt,
            sttEngine = additional.sttEngine,
            sttLanguage = additional.sttLanguage,
            sttOnDevice = additional.sttOnDevice,
            sttEndOfSpeech = additional.sttEndOfSpeech,
            latexRenderer = additional.latexRenderer,
            inlineArtifactPrefs = additional.inlineArtifactPrefs,
            artifactDisplayMode = additional.artifactDisplayMode,
            artifactsMode = additional.artifactsMode,
            starredModelsDisplay = additional.starredModelsDisplay,
            duringRunAction = additional.duringRunAction,
            uploadRoutingMode = additional.uploadRoutingMode,
            showSiteIcons = additional.showSiteIcons,
            preferLastUsedModel = additional.preferLastUsedModel,
            webSearchFirst = additional.webSearchFirst,
            collapseLongUserMessages = additional.collapseLongUserMessages,
            clientImageResizePreference = additional.clientImageResize,
        )
    }.stateIn(scope, SharingStarted.Eagerly, ChatSettingsUiState())

    // ── Write setters (fire-and-forget; return Unit, not the launched Job) ──

    fun setStarredModelsDisplay(display: StarredModelsDisplay) {
        scope.launch { settingsDataStore.setStarredModelsDisplay(display) }
    }

    fun setThinkingOpenByDefault(open: Boolean) {
        scope.launch { settingsDataStore.setThinkingOpenByDefault(open) }
    }

    fun setDuringRunAction(action: DuringRunAction) {
        scope.launch { settingsDataStore.setDuringRunAction(action) }
    }

    fun setUploadRoutingMode(mode: UploadRoutingMode) {
        scope.launch { settingsDataStore.setUploadRoutingMode(mode) }
    }

    fun setShowImageDescriptions(show: Boolean) {
        scope.launch { settingsDataStore.setShowImageDescriptions(show) }
    }

    fun setShowSiteIcons(show: Boolean) {
        scope.launch { preferenceStore.set(AppPreferences.SiteIcons, show) }
    }

    fun setClientImageResize(resize: Boolean) {
        scope.launch { preferenceStore.set(AppPreferences.ClientImageResize, resize) }
    }

    fun setCollapseLongUserMessages(collapse: Boolean) {
        scope.launch { preferenceStore.set(AppPreferences.CollapseLongUserMessages, collapse) }
    }

    fun setPreferLastUsedModel(prefer: Boolean) {
        scope.launch { preferenceStore.set(AppPreferences.PreferLastUsedModel, prefer) }
    }

    fun setWebSearchFirst(first: WebSearchPreference) {
        scope.launch { preferenceStore.set(AppPreferences.WebSearchFirst, first) }
    }

    fun setDismissKeyboardOnSend(enabled: Boolean) {
        scope.launch { settingsDataStore.setDismissKeyboardOnSend(enabled) }
    }

    fun setLatexRenderer(renderer: LatexRenderer) {
        scope.launch { settingsDataStore.setLatexRenderer(renderer) }
    }

    fun setInlineArtifactMermaid(enabled: Boolean) {
        scope.launch { settingsDataStore.setInlineArtifactMermaid(enabled) }
    }

    fun setInlineArtifactSvg(enabled: Boolean) {
        scope.launch { settingsDataStore.setInlineArtifactSvg(enabled) }
    }

    fun setInlineArtifactHtml(enabled: Boolean) {
        scope.launch { settingsDataStore.setInlineArtifactHtml(enabled) }
    }

    fun setInlineArtifactReact(enabled: Boolean) {
        scope.launch { settingsDataStore.setInlineArtifactReact(enabled) }
    }

    fun setInlineArtifactMarkdown(enabled: Boolean) {
        scope.launch { settingsDataStore.setInlineArtifactMarkdown(enabled) }
    }

    fun setArtifactDisplayMode(mode: ArtifactDisplayMode) {
        scope.launch { preferenceStore.set(AppPreferences.ArtifactDisplay, mode) }
    }

    fun setArtifactsMode(mode: ArtifactsMode) {
        scope.launch { settingsDataStore.setArtifactsMode(mode) }
    }

    fun setAutoSendAfterStt(enabled: Boolean) {
        scope.launch { settingsDataStore.setAutoSendAfterStt(enabled) }
    }

    fun setSttEndOfSpeech(enabled: Boolean) {
        scope.launch { settingsDataStore.setSttEndOfSpeech(enabled) }
    }

    fun setSttLanguage(language: String) {
        scope.launch { settingsDataStore.setSttLanguage(language) }
    }

    fun setSttEngine(engine: String) {
        scope.launch { settingsDataStore.setSttEngine(engine) }
    }

    fun setSttOnDevice(enabled: Boolean) {
        scope.launch { settingsDataStore.setSttOnDevice(enabled) }
    }

    fun setAutoReadEnabled(enabled: Boolean) {
        scope.launch { settingsDataStore.setAutoReadEnabled(enabled) }
    }

    fun setTtsSource(source: String) {
        scope.launch { settingsDataStore.setTtsSource(source) }
    }

    fun setServerVoice(voiceId: String) {
        scope.launch { settingsDataStore.setSelectedVoiceId(voiceId) }
    }

    fun setDeviceVoice(voiceName: String) {
        scope.launch { settingsDataStore.setTtsVoiceName(voiceName) }
    }

    fun setTtsSpeechRate(rate: Float) {
        scope.launch { settingsDataStore.setTtsSpeechRate(rate) }
    }

    fun setTtsPitch(pitch: Float) {
        scope.launch { settingsDataStore.setTtsPitch(pitch) }
    }
}
