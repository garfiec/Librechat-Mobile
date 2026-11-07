@file:Suppress("ForbiddenImport") // the composer styles its own field (ChatInputDefaults) for both interface styles

package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.rememberDraftField
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_open_tools_menu
import com.garfiec.librechat.feature.chat.resources.cd_paste_image
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.DuringRunSendTarget
import org.jetbrains.compose.resources.stringResource

@Composable
fun IosChatInput(
    inputText: String,
    isStreaming: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    /**
     * Opens the chat options sheet from the "+" button. The sheet is hosted by the chat screen,
     * not here — see `ChatOptionsSheetController`.
     */
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier,
    onQueue: () -> Unit = {},
    canQueue: Boolean = false,
    onDuringRunSend: () -> Unit = {},
    onSteer: () -> Unit = {},
    onInterrupt: () -> Unit = {},
    canSteer: Boolean = false,
    builtInSearchOn: Boolean = false,
    duringRunAction: DuringRunAction = DuringRunAction.STEER,
    duringRunSendTarget: DuringRunSendTarget = DuringRunSendTarget.QUEUE,
    chrome: ComposerChrome = ComposerChrome(),
    onSetDuringRunAction: (DuringRunAction) -> Unit = {},
    queuedPausedCount: Int = 0,
    queueHeldForRefusal: Boolean = false,
    isTemporaryChat: Boolean = false,
    onSendQueuedMessages: () -> Unit = {},
    isEditingQueued: Boolean = false,
    onCommitEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    isAwaitingUploadSend: Boolean = false,
    arePicksUnsettled: Boolean = false,
    comparison: ComposerComparison? = null,
    comparisonHint: ComposerComparisonHint = ComposerComparisonHint.NONE,
    onCancelPendingSend: () -> Unit = {},
    fontSizeMultiplier: Float = 1f,
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    selectedModelDisplay: String? = null,
    isCodeInterpreterAvailable: Boolean = true,
    hasClipboardImage: Boolean = false,
    onPasteImage: (() -> Unit)? = null,
    gates: ChatInputGates = ChatInputGates(),
    promptSuggestions: List<PromptMentionDisplayData> = emptyList(),
    skillSuggestions: List<PickerSkill> = emptyList(),
    isLoadingSkills: Boolean = false,
    skillsLoadFailed: Boolean = false,
    canPickSkills: Boolean = false,
    onRetrySkills: () -> Unit = {},
    onPickerSelect: (ComposerPickerItem) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val state = ChatInputState(
        inputText = inputText,
        isStreaming = isStreaming,
        isRecording = isRecording,
        isTranscribing = isTranscribing,
        chrome = chrome,
        selectedModelDisplay = selectedModelDisplay,
        isCodeInterpreterAvailable = isCodeInterpreterAvailable,
        gates = gates,
        canQueue = canQueue,
        canSteer = canSteer,
        builtInSearchOn = builtInSearchOn,
        duringRunAction = duringRunAction,
        duringRunSendTarget = duringRunSendTarget,
        isEditingQueued = isEditingQueued,
        isAwaitingUploadSend = isAwaitingUploadSend,
        arePicksUnsettled = arePicksUnsettled,
        comparisonHint = comparisonHint,
        promptSuggestions = promptSuggestions,
        skillSuggestions = skillSuggestions,
        isLoadingSkills = isLoadingSkills,
        skillsLoadFailed = skillsLoadFailed,
        canPickSkills = canPickSkills,
    )

    CommonChatInputCore(
        state = state,
        onSend = onSend,
        onStop = onStop,
        onSelectPickerItem = onPickerSelect,
        onQueue = onQueue,
        onDuringRunSend = onDuringRunSend,
        onSteer = onSteer,
        onInterrupt = onInterrupt,
        onRetrySkills = onRetrySkills,
        onSetDuringRunAction = onSetDuringRunAction,
        queuedPausedCount = queuedPausedCount,
        queueHeldForRefusal = queueHeldForRefusal,
        isTemporaryChat = isTemporaryChat,
        onSendQueuedMessages = onSendQueuedMessages,
        onCommitEdit = onCommitEdit,
        onCancelEdit = onCancelEdit,
        onCancelPendingSend = onCancelPendingSend,
        fontSizeMultiplier = fontSizeMultiplier,
        modifier = modifier,
        leadingButtons = {
            // "+" opens the options sheet, not a file picker, as on Android.
            Box {
                IconButton(
                    onClick = onOpenTools,
                    colors = ChatInputDefaults.toolsButtonColors(),
                    modifier = Modifier.size(BoxButtonSize),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.cd_open_tools_menu),
                    )
                }
                ToolsButtonDots(
                    compactSuggested = chrome.compactSuggested,
                    enabledTools = chrome.enabledTools,
                    selectedMcpServerNames = chrome.selectedMcpServerNames,
                    showEphemeralTools = gates.showEphemeralTools,
                )
            }

            // Paste image button (shown when clipboard has image content)
            if (hasClipboardImage && onPasteImage != null) {
                IconButton(
                    onClick = onPasteImage,
                    colors = ChatInputDefaults.toolsButtonColors(),
                    modifier = Modifier.size(BoxButtonSize),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = stringResource(Res.string.cd_paste_image),
                    )
                }
            }
        },
        textFieldContent = {
            Box(
                modifier = Modifier.weight(1f),
            ) {
                // inputText reaches here a frame or more after each keystroke; the field keeps its own
                // text and takes only the ViewModel's own writes (STT, prompt insertion, the clear on send).
                val field = rememberDraftField(inputText)
                val compactComparison = comparison?.takeIf { it.compact }
                val comparisonCollapsed = compactComparison?.collapsedFor(field.value.text) == true
                val comparisonCollapse by animateComposerComparisonCollapse(comparisonCollapsed)
                // The composer draws its own field (ChatInputDefaults), glass included; not the adaptive one.
                OutlinedTextField(
                    value = field.value,
                    onValueChange = { field.onValueChange(it, onInputChange) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 160.dp),
                    placeholder = {
                        ChatInputPlaceholder(
                            isRecording = isRecording,
                            selectedModelDisplay = selectedModelDisplay,
                            comparisonHint = comparisonHint,
                        )
                    },
                    leadingIcon = remember(comparison) { composerFieldLeadingIcon(comparison) },
                    prefix = compactComparison?.let {
                        { ComposerComparisonReserve(collapse = { comparisonCollapse }) }
                    },
                    suffix = { ComposerAffixAnchor() },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = ChatInputDefaults.textFieldColors(),
                    keyboardOptions = ChatInputDefaults.keyboardOptions,
                    keyboardActions = KeyboardActions.Default,
                    maxLines = 6,
                )

                if (compactComparison != null) {
                    ComposerComparisonMorph(
                        comparison = compactComparison,
                        collapsed = comparisonCollapsed,
                        collapse = { comparisonCollapse },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }
        },
        // The mic sits in the button row, apart from the field, so it never competes with the
        // field's text-magnifier gesture.
        trailingButtons = {
            IconButton(
                onClick = { if (isRecording) onStopRecording() else onStartRecording() },
                modifier = Modifier.size(BoxButtonSize),
                enabled = !isTranscribing,
            ) {
                VoiceMicIndicator(isRecording = isRecording, isTranscribing = isTranscribing)
            }
        },
        bottomContent = {
            // Snackbar for file attachment toast
            AdaptiveSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        },
    )
}
