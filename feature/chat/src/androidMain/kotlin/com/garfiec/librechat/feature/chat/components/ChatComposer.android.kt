package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.DuringRunSendTarget
import kotlinx.coroutines.CoroutineScope

@Suppress("LongParameterList", "LambdaParameterEventTrailing", "UnusedParameter")
@Composable
internal actual fun ChatComposer(
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
    modifier: Modifier,
) {
    val context = LocalContext.current
    // On the whole composer, so an image dropped anywhere on it attaches; the field pastes into it.
    val receiver = remember(gates.fileUploadEnabled, context, pasteScope, onPasteFiles) {
        if (gates.fileUploadEnabled) Modifier.imageContentReceiver(context, pasteScope, onPasteFiles) else Modifier
    }
    OfferImageTypesToKeyboard {
        ChatInput(
            inputText = inputText,
            isStreaming = isStreaming,
            onInputChange = onInputChange,
            onSend = onSend,
            onStop = onStop,
            onOpenTools = onOpenTools,
            onDuringRunSend = onDuringRunSend,
            onQueue = onQueue,
            canQueue = canQueue,
            onSteer = onSteer,
            onInterrupt = onInterrupt,
            canSteer = canSteer,
            builtInSearchOn = builtInSearchOn,
            duringRunAction = duringRunAction,
            duringRunSendTarget = duringRunSendTarget,
            chrome = chrome,
            onSetDuringRunAction = onSetDuringRunAction,
            promptSuggestions = promptSuggestions,
            skillSuggestions = skillSuggestions,
            isLoadingSkills = isLoadingSkills,
            skillsLoadFailed = skillsLoadFailed,
            canPickSkills = canPickSkills,
            onRetrySkills = onRetrySkills,
            onPickerSelect = onPickerSelect,
            isRecording = isRecording,
            isTranscribing = isTranscribing,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            selectedModelDisplay = selectedModelDisplay,
            isCodeInterpreterAvailable = isCodeInterpreterAvailable,
            gates = gates,
            queuedPausedCount = queuedPausedCount,
            queueHeldForRefusal = queueHeldForRefusal,
            isTemporaryChat = isTemporaryChat,
            onSendQueuedMessages = onSendQueuedMessages,
            isEditingQueued = isEditingQueued,
            onCommitEdit = onCommitEdit,
            onCancelEdit = onCancelEdit,
            isAwaitingUploadSend = isAwaitingUploadSend,
            arePicksUnsettled = arePicksUnsettled,
            comparison = comparison,
            comparisonHint = comparisonHint,
            onCancelPendingSend = onCancelPendingSend,
            fontSizeMultiplier = fontSizeMultiplier,
            canFold = true,
            modifier = modifier.then(receiver),
        )
    }
}
