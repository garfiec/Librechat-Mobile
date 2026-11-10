package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore.Decision
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore.Subject
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.FlaggedArtifactLibraries
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.core.model.response.clientImageResize
import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.feature.settings.viewmodel.delegate.SpeechSettingsFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which server-gated rows the Chats and Advanced pages show; each follows the gate chat or web uses. */
@Immutable
data class ChatSettingsGates(
    /** Chat's context gauge: a server from v0.8.7-rc1 whose `interface.contextUsage` isn't off. */
    val contextUsage: Boolean = false,
    /** Web's presets menu: `interface.presets && interface.modelSelect`. */
    val presets: Boolean = true,
    /** Web's API keys settings: the REMOTE_AGENTS USE permission. */
    val apiKeys: Boolean = true,
)

internal fun chatSettingsGates(
    config: StartupConfig?,
    backendVersion: String?,
    role: UserRolePermissions?,
): ChatSettingsGates {
    val iface = config?.interfaceConfig
    return ChatSettingsGates(
        contextUsage = backendVersion != null &&
            BackendVersion.isCompatibleOrNewer(backendVersion, CONTEXT_GAUGE_MIN_VERSION) &&
            (iface?.contextUsage ?: true),
        presets = (iface?.presets ?: true) && (iface?.modelSelect ?: true),
        apiKeys = role.hasAccessOrPermissive(PermissionType.REMOTE_AGENTS, Permission.USE),
    )
}

/** Must match chat's context gauge gate (`ChatConfigDelegate`). */
private const val CONTEXT_GAUGE_MIN_VERSION = "0.8.7-rc1"

/** Backs Chats, Voice, Models & keys, Privacy's website icons and Advanced's chat rows. */
// TooManyFunctions: the members are one-line forwarders to controllers, one per setting, so the
// count tracks how many settings exist rather than how much this class does.
@Suppress("TooManyFunctions")
class ChatSettingsViewModel(
    settingsDataStore: SettingsDataStore,
    preferenceStore: PreferenceStore,
    speechSettingsFactory: SpeechSettingsFactory,
    private val libraryConsent: ArtifactLibraryConsentStore,
    private val fileRepository: FileRepository,
    configRepository: ConfigRepository,
    roleRepository: RoleRepository,
) : ViewModel() {

    /** Raw state for everything not driven by DataStore flows. */
    private val _uiState = MutableStateFlow(ChatSettingsUiState())

    private val stateHandle = SettingsStateHandle(_uiState, viewModelScope)

    // --- Delegates ---
    private val speechDelegate = speechSettingsFactory.create(stateHandle)

    /** Owns the DataStore read flows + write setters; merges them with [_uiState]. */
    private val prefsController = ChatPreferencesController(
        settingsDataStore,
        preferenceStore,
        _uiState,
        viewModelScope,
    )

    /** The single public UI state that merges DataStore preferences with imperative state. */
    val uiState: StateFlow<ChatSettingsUiState> = prefsController.uiState

    val gates: StateFlow<ChatSettingsGates> = combine(
        configRepository.startupConfig,
        configRepository.detectedBackendVersion,
        roleRepository.userPermissions,
        ::chatSettingsGates,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, ChatSettingsGates())

    /**
     * Each flagged artifact library with its device-wide answer ([Decision.ASK] when each artifact
     * asks). Null until the stored answers are read.
     */
    val artifactLibraries: StateFlow<List<ArtifactLibraryChoice>?> = libraryConsent.snapshot
        .map { snapshot ->
            snapshot?.let {
                FlaggedArtifactLibraries.SUBJECTS.map { subject ->
                    val decision = when (it.remembered(subject)) {
                        true -> Decision.ALLOW
                        false -> Decision.DECLINE
                        null -> Decision.ASK
                    }
                    ArtifactLibraryChoice(subject, decision)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Also loads the server voices, once the config says the server has TTS.
        speechDelegate.loadSpeechConfig()
        speechDelegate.loadDeviceVoices()
        loadImageResizePolicy()
    }

    /** Whether the server's admin decides image resizing (`fileConfig.clientImageResize.enabled`). */
    private fun loadImageResizePolicy() {
        viewModelScope.launch {
            val config = (fileRepository.getFileConfig() as? Result.Success)?.data ?: return@launch
            val policy = config.clientImageResize(userPreference = true)
            _uiState.update { it.copy(clientImageResizeAdmin = policy.enabledByAdmin.takeIf { policy.enforced }) }
        }
    }

    fun setClientImageResize(resize: Boolean) {
        prefsController.setClientImageResize(resize)
    }

    // ── Chat preferences ───────────────────────────────────────────

    /** [Decision.ASK] drops the remembered answer; an answer given once in an artifact holds until restart. */
    fun setArtifactLibraryDecision(subject: Subject, decision: Decision) {
        viewModelScope.launch {
            when (decision) {
                Decision.ALLOW -> libraryConsent.remember(subject, allow = true)
                Decision.DECLINE -> libraryConsent.remember(subject, allow = false)
                Decision.ASK -> libraryConsent.forget(subject)
            }
        }
    }

    fun setStarredModelsDisplay(display: StarredModelsDisplay) {
        prefsController.setStarredModelsDisplay(display)
    }

    fun setThinkingOpenByDefault(open: Boolean) {
        prefsController.setThinkingOpenByDefault(open)
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

    fun setCollapseLongUserMessages(collapse: Boolean) {
        prefsController.setCollapseLongUserMessages(collapse)
    }

    fun setPreferLastUsedModel(prefer: Boolean) {
        prefsController.setPreferLastUsedModel(prefer)
    }

    fun setWebSearchFirst(first: WebSearchPreference) {
        prefsController.setWebSearchFirst(first)
    }

    fun setShowSiteIcons(show: Boolean) {
        prefsController.setShowSiteIcons(show)
    }

    fun setDismissKeyboardOnSend(enabled: Boolean) {
        prefsController.setDismissKeyboardOnSend(enabled)
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

    fun setArtifactsMode(mode: ArtifactsMode) {
        prefsController.setArtifactsMode(mode)
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Delegated public API ───────────────────────────────────────

    // Voice
    fun setAutoSendAfterStt(enabled: Boolean) = prefsController.setAutoSendAfterStt(enabled)
    fun setSttEndOfSpeech(enabled: Boolean) = prefsController.setSttEndOfSpeech(enabled)
    fun setSttLanguage(language: String) = prefsController.setSttLanguage(language)
    fun setSttEngine(engine: String) = prefsController.setSttEngine(engine)
    fun setSttOnDevice(enabled: Boolean) = prefsController.setSttOnDevice(enabled)
    fun setAutoReadEnabled(enabled: Boolean) = prefsController.setAutoReadEnabled(enabled)
    fun setTtsSource(source: String) = prefsController.setTtsSource(source)
    fun selectServerVoice(voice: TtsVoice) = prefsController.setServerVoice(voice.id)
    fun selectDeviceVoice(voiceName: String) = prefsController.setDeviceVoice(voiceName)
    fun setTtsSpeechRate(rate: Float) = prefsController.setTtsSpeechRate(rate)
    fun setTtsPitch(pitch: Float) = prefsController.setTtsPitch(pitch)
    fun retrySpeechConfig() = speechDelegate.retrySpeechConfig()
    fun stopTtsPreview() = speechDelegate.stopTtsPreview()

    /** Speaks [text] the way chat playback would, from the saved source, voice, speed and pitch. */
    fun previewVoice(text: String) {
        val state = uiState.value
        if (state.ttsSource == SettingsDataStore.TTS_SOURCE_SERVER) {
            speechDelegate.previewServerTts(text, state.selectedVoice?.id)
        } else {
            val voiceName = state.ttsDeviceVoiceName.ifBlank { null }
            speechDelegate.previewDeviceTts(text, state.ttsSpeechRate, state.ttsPitch, voiceName)
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechDelegate.release()
    }
}

/** A flagged artifact library and what applies to it on this device. */
data class ArtifactLibraryChoice(val subject: Subject, val decision: Decision)
