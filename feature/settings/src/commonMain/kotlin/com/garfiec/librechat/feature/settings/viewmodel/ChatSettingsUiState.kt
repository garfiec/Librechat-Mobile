package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayPrefs
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.feature.settings.screen.DeviceVoiceInfo

@Immutable
data class ChatSettingsUiState(
    /** A voice test, preview or playback failure for the snackbar; cleared by [ChatSettingsViewModel.dismissError]. */
    val error: SpeechFailure? = null,
    // Chat preferences
    /** Whether a reply's reasoning block starts expanded; the block itself is always shown. */
    val thinkingOpenByDefault: Boolean = false,
    val contextBarPlacement: ContextBarPlacement = ContextBarPlacement.OPTIONS_SHEET,
    /** What the composer's send does mid-run (v0.8.8 steering): inject into the running reply,
     *  or queue for after it. Honoured only where the server supports steering. */
    val duringRunAction: DuringRunAction = DuringRunAction.STEER,
    val uploadRoutingMode: UploadRoutingMode = UploadRoutingMode.AUTO,
    val showImageDescriptions: Boolean = false,
    val showSiteIcons: Boolean = false,
    val dismissKeyboardOnSend: Boolean = true,
    // Speech settings
    val autoReadEnabled: Boolean = false,
    val selectedVoice: TtsVoice? = null,
    val availableVoices: List<TtsVoice> = emptyList(),
    // Speech detail dialogs
    val showSttDetailDialog: Boolean = false,
    val showTtsDetailDialog: Boolean = false,
    val sttEngine: String = "",
    val sttLanguage: String = "",
    val sttAutoSend: Boolean = false,
    /** Mobile-only: prefer the platform on-device recognizer for the Browser engine. Default ON. */
    val sttOnDevice: Boolean = true,
    /** Mobile-only: stop dictation at end-of-speech (hands-free) vs. run continuously. Default OFF. */
    val sttEndOfSpeech: Boolean = false,
    /** Whether the server has external STT (Whisper) configured — gates the External engine option. */
    val serverSttEnabled: Boolean = false,
    /**
     * Whether the server has TTS configured (`ttsExternal`). Gates the Server TTS source and the
     * voices fetch, which a server without TTS answers with a 500. False until the config loads.
     */
    val serverTtsEnabled: Boolean = false,
    val ttsEngine: String = "",
    val ttsVoice: String = "",
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsDeviceVoiceName: String = "",
    val ttsCaching: Boolean = true,
    val ttsSource: String = "device",
    val availableDeviceVoices: List<DeviceVoiceInfo> = emptyList(),
    // TTS preview
    val isTtsPreviewPlaying: Boolean = false,
    val latexRenderer: LatexRenderer = LatexRenderer.KATEX,
    // Mobile-only: how pinned models/agents surface in the model-selection sheet
    val starredModelsDisplay: StarredModelsDisplay = StarredModelsDisplay.GROUPED,
    // Mobile-only: open new chats on the last-used model instead of the server's prioritized spec
    val preferLastUsedModel: Boolean = false,
    // Mobile-only: which web search the chat's one web-search control tries first
    val webSearchFirst: WebSearchPreference = WebSearchPreference.LIBRECHAT_FIRST,
    /** Long user messages render collapsed with Show more (web's `collapseLongUserMessages`). */
    val collapseLongUserMessages: Boolean = true,
    /** Settings › Chat "Resize images before upload", as the user set it. */
    val clientImageResizePreference: Boolean = true,
    /** The admin's `clientImageResize.enabled` when set (the user's choice is then ignored); null otherwise. */
    val clientImageResizeAdmin: Boolean? = null,
    // Inline artifact rendering (per-type toggles)
    val inlineArtifactPrefs: InlineArtifactPrefs = InlineArtifactPrefs(),
    // Artifact viewer presentation (bottom sheet vs full screen + selector visibility)
    val artifactDisplayPrefs: ArtifactDisplayPrefs = ArtifactDisplayPrefs(),
    // What the Artifacts tool is taught when on; app-wide, read by chat's request
    val artifactsMode: ArtifactsMode = ArtifactsMode.DEFAULT,
)

/** Speech failures the page shows; the screen maps each to its string. */
enum class SpeechFailure { VOICE_TEST, VOICE_PREVIEW, PLAYBACK }
