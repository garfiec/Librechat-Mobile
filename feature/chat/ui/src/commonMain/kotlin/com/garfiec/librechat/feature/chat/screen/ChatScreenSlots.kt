package com.garfiec.librechat.feature.chat.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.preferences.DuringRunAction
import com.garfiec.librechat.feature.chat.components.ChatAttachmentActions
import com.garfiec.librechat.feature.chat.components.ComposerChrome
import com.garfiec.librechat.feature.chat.components.ComposerComparison
import com.garfiec.librechat.feature.chat.components.ComposerComparisonHint
import com.garfiec.librechat.feature.chat.components.ContextRingDialogs
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.DuringRunSendTarget
import kotlinx.coroutines.CoroutineScope

/** What [ChatScreenContent] hands the platform composer (`ChatComposer` in `:feature:chat`). */
@Suppress("LongParameterList")
class ChatComposerParams(

    val inputText: String,
    val isStreaming: Boolean,
    val onInputChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onStop: () -> Unit,
    val onOpenTools: () -> Unit,
    val onDuringRunSend: () -> Unit,
    val onQueue: () -> Unit,
    val canQueue: Boolean,
    val onSteer: () -> Unit,
    val onInterrupt: () -> Unit,
    val canSteer: Boolean,
    val builtInSearchOn: Boolean,
    val duringRunAction: DuringRunAction,
    val duringRunSendTarget: DuringRunSendTarget,
    val chrome: ComposerChrome,
    val onSetDuringRunAction: (DuringRunAction) -> Unit,
    val onPasteFiles: (List<Any>) -> Unit,
    val pasteScope: CoroutineScope,
    val promptSuggestions: List<PromptMentionDisplayData>,
    val skillSuggestions: List<PickerSkill>,
    val isLoadingSkills: Boolean,
    val skillsLoadFailed: Boolean,
    val canPickSkills: Boolean,
    val onRetrySkills: () -> Unit,
    val onPickerSelect: (ComposerPickerItem) -> Unit,
    val isRecording: Boolean,
    val isTranscribing: Boolean,
    val onStartRecording: () -> Unit,
    val onStopRecording: () -> Unit,
    val selectedModelDisplay: String?,
    val isCodeInterpreterAvailable: Boolean,
    val gates: ChatInputGates,
    val queuedPausedCount: Int,
    val queueHeldForRefusal: Boolean,
    val isTemporaryChat: Boolean,
    val onSendQueuedMessages: () -> Unit,
    val isEditingQueued: Boolean,
    val onCommitEdit: () -> Unit,
    val onCancelEdit: () -> Unit,
    val isAwaitingUploadSend: Boolean,
    val arePicksUnsettled: Boolean,
    val comparison: ComposerComparison?,
    val comparisonHint: ComposerComparisonHint,
    val onCancelPendingSend: () -> Unit,
    val fontSizeMultiplier: Float,
    val modifier: Modifier = Modifier,
)

/** What [ChatScreenContent] hands the chat's top bar (`ChatFloatingTopBar` in `:feature:chat`). */
class ChatTopBarParams(

    val uiState: ChatUiState,
    val onLoadPreset: () -> Unit,
    val onSavePreset: () -> Unit,
    val onRename: () -> Unit,
    val contextRingDialogs: ContextRingDialogs,
    val modifier: Modifier = Modifier,
    val onOpenDrawer: (() -> Unit)? = null,
    val onShowAllMedia: (() -> Unit)? = null,
    val onOpenPromptsLibrary: (() -> Unit)? = null,
    val widePane: Boolean = false,
    val railMode: Boolean = false,
    val onNewChat: (() -> Unit)? = null,
)

/**
 * The pieces of the chat screen that need a ViewModel, Koin or a platform API, provided by the
 * stateful `ChatScreen` in `:feature:chat`.
 */
class ChatScreenSlots(
    /** Offers reply notifications while a reply streams. */
    val replyNotificationAsk: @Composable (streaming: Boolean) -> Unit,
    /** The microphone action: asks for the permission, then starts the chosen engine. */
    val startRecording:
        @Composable (sttEngine: String, sttLanguage: String, coroutineScope: CoroutineScope) -> () -> Unit,
    /** The platform's file, camera and photo pickers. */
    val attachmentActions: @Composable (filePickerMimeTypes: List<String>) -> ChatAttachmentActions,
    /** The screen's ViewModel effects and outcomes (navigation, notices, lifecycle). */
    val effects: @Composable (uiState: ChatUiState) -> Unit,
    /** The MCP App providers around the chat's content. */
    val mcpProviders: @Composable (content: @Composable () -> Unit) -> Unit,
    /** The child-thread viewer; see `ChatRootContent`. */
    val subagentThreadsSheet: @Composable (
        parentConversationId: String,
        focusParentToolCallId: String?,
        focusThreadId: String?,
        onDismiss: () -> Unit,
    ) -> Unit,
    /** The thread's "Add to chat" text-selection item. */
    val selectionItem: @Composable (enabled: Boolean, onAddToChat: (String) -> Unit) -> Modifier,
    /** Acknowledges the open chat's reply once it is on screen. */
    val conversationSeenEffect: @Composable (
        conversationId: String?,
        displayMessages: List<MessageNode>,
        isStreaming: Boolean,
        nearBottom: Boolean,
    ) -> Unit,
    val composer: @Composable (ChatComposerParams) -> Unit,
    val topBar: @Composable (ChatTopBarParams) -> Unit,
)
