package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.DuringRunSendTarget
import kotlinx.coroutines.CoroutineScope

/**
 * The chat composer: [ChatInput] on Android, [IosChatInput] on iOS. The two differ in what they do
 * on their own (iOS offers to paste a copied image, handing it to [onPasteFiles]), so the screen
 * hands both everything and each takes what it uses. [pasteScope] must outlive the composer, which
 * a pause panel can replace while a paste is still decoding.
 */
@Suppress("LongParameterList", "LambdaParameterEventTrailing")
@Composable
internal expect fun ChatComposer(
    inputText: String,
    isStreaming: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenTools: () -> Unit,
    onDuringRunSend: () -> Unit,
    onQueue: () -> Unit,
    canQueue: Boolean,
    onSteer: () -> Unit,
    onInterrupt: () -> Unit,
    canSteer: Boolean,
    builtInSearchOn: Boolean,
    duringRunAction: DuringRunAction,
    duringRunSendTarget: DuringRunSendTarget,
    chrome: ComposerChrome,
    onSetDuringRunAction: (DuringRunAction) -> Unit,
    onPasteFiles: (List<Any>) -> Unit,
    pasteScope: CoroutineScope,
    promptSuggestions: List<PromptMentionDisplayData>,
    skillSuggestions: List<PickerSkill>,
    isLoadingSkills: Boolean,
    skillsLoadFailed: Boolean,
    canPickSkills: Boolean,
    onRetrySkills: () -> Unit,
    onPickerSelect: (ComposerPickerItem) -> Unit,
    isRecording: Boolean,
    isTranscribing: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    selectedModelDisplay: String?,
    isCodeInterpreterAvailable: Boolean,
    gates: ChatInputGates,
    queuedPausedCount: Int,
    queueHeldForRefusal: Boolean,
    isTemporaryChat: Boolean,
    onSendQueuedMessages: () -> Unit,
    isEditingQueued: Boolean,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    isAwaitingUploadSend: Boolean,
    arePicksUnsettled: Boolean,
    comparison: ComposerComparison?,
    comparisonHint: ComposerComparisonHint,
    onCancelPendingSend: () -> Unit,
    fontSizeMultiplier: Float,
    modifier: Modifier = Modifier,
)
