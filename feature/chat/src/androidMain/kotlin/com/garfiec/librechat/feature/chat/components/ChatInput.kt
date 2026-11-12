@file:Suppress("ForbiddenImport") // the composer styles its own field (ChatInputDefaults) for both interface styles

package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.ui.components.pressBounce
import com.garfiec.librechat.core.ui.components.rememberDraftTextField
import com.garfiec.librechat.core.ui.components.rememberPressBounce
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.DuringRunSendTarget
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatInput(
    inputText: String,
    isStreaming: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    /**
     * Opens the chat options sheet from the "+" button. The sheet is hosted by `ChatScreen`, not
     * here, because the pull-up surface opens the same one — see `ChatOptionsSheetController`.
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
    duringRunAction: DuringRunAction = DuringRunAction.QUEUE,
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
    canFold: Boolean = false,
    promptSuggestions: List<PromptMentionDisplayData> = emptyList(),
    skillSuggestions: List<PickerSkill> = emptyList(),
    isLoadingSkills: Boolean = false,
    skillsLoadFailed: Boolean = false,
    canPickSkills: Boolean = false,
    onRetrySkills: () -> Unit = {},
    onPickerSelect: (ComposerPickerItem) -> Unit = {},
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    selectedModelDisplay: String? = null,
    isCodeInterpreterAvailable: Boolean = true,
    gates: ChatInputGates = ChatInputGates(),
) {
    val cdOpenToolsMenu = stringResource(Res.string.cd_open_tools_menu)
    val cdMessageInput = stringResource(Res.string.cd_message_input)
    val cdStopVoiceRec = stringResource(Res.string.cd_stop_voice_recording)
    val cdStartVoiceRec = stringResource(Res.string.cd_start_voice_recording)

    val focusRequester = remember { FocusRequester() }

    // inputText reaches here a frame or more after each keystroke; the field keeps its own text and
    // takes only the ViewModel's own writes (STT, prompt insertion, the clear on send).
    val field = rememberDraftTextField(inputText, onInputChange)
    val commandStyle = pickerCommandStyle()
    val pickerCommandToken = remember(commandStyle, canPickSkills) {
        OutputTransformation {
            pickerCommandRange(asCharSequence().toString(), canPickSkills)?.let {
                addStyle(commandStyle, it.first, it.last + 1)
            }
        }
    }

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
        onPickerTextChange = onInputChange,
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
        canFold = canFold,
        modifier = modifier,
        leadingButtons = {
            val toolsBounce = rememberPressBounce()
            Box(modifier = Modifier.pressBounce(toolsBounce, enabled = !isLiquidGlass)) {
                IconButton(
                    onClick = onOpenTools,
                    colors = ChatInputDefaults.toolsButtonColors(),
                    modifier = Modifier
                        .size(BoxButtonSize)
                        .semantics {
                            contentDescription = cdOpenToolsMenu
                            role = Role.Button
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                }
                ToolsButtonDots(
                    compactSuggested = chrome.compactSuggested,
                    enabledTools = chrome.enabledTools,
                    selectedMcpServerNames = chrome.selectedMcpServerNames,
                    showEphemeralTools = gates.showEphemeralTools,
                )
            }
        },
        textFieldContent = {
            Box(modifier = Modifier.weight(1f)) {
                val compactComparison = comparison?.takeIf { it.compact }
                val comparisonCollapsed = compactComparison?.collapsedFor(field.state.text.toString()) == true
                val comparisonCollapse by animateComposerComparisonCollapse(comparisonCollapsed)
                // The composer draws its own field (ChatInputDefaults), glass included; not the adaptive one.
                OutlinedTextField(
                    state = field.state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 160.dp)
                        .focusRequester(focusRequester)
                        .semantics {
                            contentDescription = cdMessageInput
                        },
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
                    outputTransformation = pickerCommandToken,
                    colors = ChatInputDefaults.textFieldColors(),
                    keyboardOptions = ChatInputDefaults.keyboardOptions,
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
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
        trailingButtons = {
            val micBounce = rememberPressBounce()
            IconButton(
                onClick = { if (isRecording) onStopRecording() else onStartRecording() },
                modifier = Modifier
                    .size(BoxButtonSize)
                    .pressBounce(micBounce, enabled = !isLiquidGlass && !isTranscribing)
                    .semantics {
                        contentDescription = if (isRecording) cdStopVoiceRec else cdStartVoiceRec
                        role = Role.Button
                    },
                enabled = !isTranscribing,
            ) {
                VoiceMicIndicator(isRecording = isRecording, isTranscribing = isTranscribing)
            }
        },
    )
}
