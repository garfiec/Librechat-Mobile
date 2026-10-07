package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayPrefs
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.feature.settings.screen.DeviceVoiceInfo

@Immutable
data class SettingsUiState(
    /**
     * Transient errors surfaced via snackbar; cleared by [SettingsViewModel.dismissError]
     * after the snackbar is shown/acted on.
     */
    val error: String? = null,
    // Chat preferences
    val chatFontSize: ChatFontSize = ChatFontSize.MEDIUM,
    val autoScrollEnabled: Boolean = true,
    val showThinkingBlocks: Boolean = true,
    val contextBarPlacement: ContextBarPlacement = ContextBarPlacement.OPTIONS_SHEET,
    /** What the composer's send does mid-run (v0.8.8 steering): inject into the running reply,
     *  or queue for after it. Honoured only where the server supports steering. */
    val duringRunAction: DuringRunAction = DuringRunAction.QUEUE,
    val uploadRoutingMode: UploadRoutingMode = UploadRoutingMode.AUTO,
    val showImageDescriptions: Boolean = false,
    val showSiteIcons: Boolean = false,
    val dismissKeyboardOnSend: Boolean = false,
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
    // Fork settings
    val forkMode: String = "targetLevel",
    val showForkSettingsDialog: Boolean = false,
    // Chat layout
    val chatLayoutStyle: String = ChatLayoutConstants.THREAD,
    val showAvatars: Boolean = true,
    val showBubbles: Boolean = false,
    val latexRenderer: LatexRenderer = LatexRenderer.KATEX,
    // Mobile-only: how pinned models/agents surface in the model-selection sheet
    val starredModelsDisplay: StarredModelsDisplay = StarredModelsDisplay.OFF,
    // Mobile-only: what the chat floating top bar shows + how its bubble is aligned
    val chatHeaderContent: ChatHeaderContent = ChatHeaderContent.TITLE,
    val chatHeaderAlignment: ChatHeaderAlignment = ChatHeaderAlignment.LEFT,
    // Inline artifact rendering (per-type toggles)
    val inlineArtifactPrefs: InlineArtifactPrefs = InlineArtifactPrefs(),
    // Artifact viewer presentation (bottom sheet vs full screen + selector visibility)
    val artifactDisplayPrefs: ArtifactDisplayPrefs = ArtifactDisplayPrefs(),
)
