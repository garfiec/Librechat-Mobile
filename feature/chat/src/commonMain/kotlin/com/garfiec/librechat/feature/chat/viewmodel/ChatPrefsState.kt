package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.usage.ContextDetailPreset
import com.garfiec.librechat.core.model.usage.ContextDetailSections
import com.garfiec.librechat.core.model.usage.DEFAULT_COMPACT_NUDGE_THRESHOLD

/**
 * DataStore-backed chat preferences merged into [ChatUiState] by the `uiState` combine in
 * [ChatViewModel] (the sole writer). These are layered over the inner `_uiState` on every
 * emission, so a navigation reset need not carry them.
 */
@Immutable
data class ChatPrefsState(
    val serverUrl: String = "",
    val chatFontSize: ChatFontSize = ChatFontSize.MEDIUM,
    /**
     * Mobile-only preference for how pinned models/agents are surfaced in [ModelSelectorSheet]:
     * off (float within group), grouped (collapsible top section), or top (flat top list).
     */
    val starredModelsDisplay: StarredModelsDisplay = StarredModelsDisplay.GROUPED,
    /**
     * Mobile-only preferences for the chat floating top bar: what its bubble shows
     * ([chatHeaderContent]) and how the bubble is positioned ([chatHeaderAlignment]).
     */
    val chatHeaderContent: ChatHeaderContent = ChatHeaderContent.TITLE,
    val chatHeaderAlignment: ChatHeaderAlignment = ChatHeaderAlignment.LEFT,
    /** User preference (Settings → Chat) for where the context gauge is surfaced. */
    val contextBarPlacement: ContextBarPlacement = ContextBarPlacement.OPTIONS_SHEET,
    /** Whether the options-sheet context gauge's inline breakdown is expanded. */
    val contextGaugeExpanded: Boolean = false,
    /** Which parts of the context breakdown the user wants (Settings → Chat → Context usage). */
    val contextSections: ContextDetailSections = ContextDetailPreset.DEFAULT.sections,
    /** Usage percent at which compacting is suggested; 0 means never. */
    val compactNudgeThreshold: Int = DEFAULT_COMPACT_NUDGE_THRESHOLD,
    /** Per conversation, the suggestion band the user answered "Not now" to. */
    val compactNudgeSnoozes: Map<String, Int> = emptyMap(),
    /**
     * What the send control does while a reply is generating (v0.8.8 steering): inject into the
     * running turn, or queue for after it. Read through [ChatUiState.effectiveDuringRunAction],
     * which degrades to queueing when steering is unavailable.
     *
     * **Unlike every other field here this one drives BEHAVIOUR, not just rendering**, so
     * `ChatViewModel` mirrors it into the backing `_uiState` with its own collector rather than
     * relying on the `uiState` combine that fills the rest of this slice. A decision made from the
     * backing state would otherwise always read the default below: that is exactly how steering
     * became unreachable from the composer while the send button still drew itself as "Steer this
     * reply". Anything added here that a non-UI code path branches on needs the same treatment.
     */
    val duringRunAction: DuringRunAction = DuringRunAction.STEER,
)

/**
 * Consolidated chat-related user preferences from [SettingsDataStore].
 * Exposed as a single [StateFlow] to reduce the number of individual subscriptions
 * in the UI layer.
 */
@Immutable
data class ChatPreferences(
    val showImageDescriptions: Boolean = false,
    val dismissKeyboardOnSend: Boolean = false,
    val chatLayoutStyle: String = ChatLayoutConstants.THREAD,
    val showAvatars: Boolean = true,
    val showBubbles: Boolean = false,
    val latexRenderer: LatexRenderer = LatexRenderer.KATEX,
    val autoSendAfterStt: Boolean = false,
    val sttEngine: String = "",
    val sttLanguage: String = "",
    val inlineArtifactPrefs: InlineArtifactPrefs = InlineArtifactPrefs(),
    /**
     * Defaults to [SiteIconsState.OFF], not [SiteIconsState.ASK]: `chatPreferences` starts from
     * this default before DataStore's first emission, and an ASK default would flash the prompt at
     * a user who has already chosen.
     */
    val siteIcons: SiteIconsState = SiteIconsState.OFF,
)

/**
 * Whether web-search results may load site icons from external servers, which sends the result
 * domains to Google's favicon service (or to an icon host the result names).
 */
enum class SiteIconsState {
    /** Never chosen, and the prompt hasn't been closed this process. Off until the user picks. */
    ASK,
    ON,
    OFF,
    ;

    val showIcons: Boolean get() = this == ON
}

/**
 * Chat-screen display preferences (floating top bar, options-sheet context gauge, during-run
 * send action), bundled into a single flow so [ChatViewModel]'s `uiState` combine stays within
 * Kotlin's 5-arg typed limit.
 */
@Immutable
data class ChatDisplayPrefs(
    val content: ChatHeaderContent = ChatHeaderContent.TITLE,
    val alignment: ChatHeaderAlignment = ChatHeaderAlignment.LEFT,
    val contextBarPlacement: ContextBarPlacement = ContextBarPlacement.OPTIONS_SHEET,
    val context: ContextUsagePrefs = ContextUsagePrefs(),
    val duringRunAction: DuringRunAction = DuringRunAction.STEER,
)

/** The context gauge's preferences, folded into one flow for the same 5-argument reason. */
@Immutable
data class ContextUsagePrefs(
    val gaugeExpanded: Boolean = false,
    val sections: ContextDetailSections = ContextDetailPreset.DEFAULT.sections,
    val compactNudgeThreshold: Int = DEFAULT_COMPACT_NUDGE_THRESHOLD,
    val compactNudgeSnoozes: Map<String, Int> = emptyMap(),
)
