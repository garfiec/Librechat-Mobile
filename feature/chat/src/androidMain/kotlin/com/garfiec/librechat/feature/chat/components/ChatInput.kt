@file:Suppress("ForbiddenImport") // the composer styles its own field (ChatInputDefaults) for both interface styles

package com.garfiec.librechat.feature.chat.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.ui.components.pressBounce
import com.garfiec.librechat.core.ui.components.rememberDraftField
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
    /** The rows around the field and what they fire (see [ComposerChrome]). */
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
    onImagePaste: ((Uri) -> Unit)? = null,
    selectedModelDisplay: String? = null,
    isCodeInterpreterAvailable: Boolean = true,
    gates: ChatInputGates = ChatInputGates(),
) {
    val cdOpenToolsMenu = stringResource(Res.string.cd_open_tools_menu)
    val cdPasteImage = stringResource(Res.string.cd_paste_image)
    val cdMessageInput = stringResource(Res.string.cd_message_input)
    val cdStopVoiceRec = stringResource(Res.string.cd_stop_voice_recording)
    val cdStartVoiceRec = stringResource(Res.string.cd_start_voice_recording)

    val focusRequester = remember { FocusRequester() }

    // inputText reaches here a frame or more after each keystroke; the field keeps its own text and
    // takes only the ViewModel's own writes (STT, prompt insertion, the clear on send).
    val field = rememberDraftField(inputText)

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
        leadingButtons = { compact ->
            // "+" button to open tools bottom sheet
            val toolsBounce = rememberPressBounce()
            Box(modifier = Modifier.pressBounce(toolsBounce, enabled = !isLiquidGlass)) {
                FilledTonalIconButton(
                    onClick = onOpenTools,
                    colors = ChatInputDefaults.toolsButtonColors(),
                    modifier = Modifier
                        .size(48.dp)
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
                ToolsButtonDots(chrome = chrome, showEphemeralTools = gates.showEphemeralTools)
            }

            // Paste image button (shown only when clipboard has image content)
            if (onImagePaste != null) {
                val pasteBounce = rememberPressBounce()
                IconButton(
                    onClick = {
                        // Clipboard image pasting is handled by the screen
                        onImagePaste(Uri.EMPTY)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .pressBounce(pasteBounce, enabled = !isLiquidGlass)
                        .semantics {
                            contentDescription = cdPasteImage
                            role = Role.Button
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!compact) Spacer(modifier = Modifier.width(8.dp))
        },
        textFieldContent = { compact ->
            Box(modifier = Modifier.weight(1f)) {
                val compactComparison = comparison?.takeIf { it.compact }
                val comparisonCollapsed = compactComparison?.collapsedFor(field.value.text) == true
                val comparisonCollapse by animateComposerComparisonCollapse(comparisonCollapsed)
                // The stack takes the leading slot, so the phone's A·B pill moves past it.
                val afterStack = !chrome.draftItems.isEmpty
                // The composer draws its own field (ChatInputDefaults), glass included; not the adaptive one.
                OutlinedTextField(
                    value = field.value,
                    onValueChange = { field.onValueChange(it, onInputChange) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 160.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused) chrome.onFieldFocused() }
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
                    leadingIcon = composerFieldLeadingIcon(comparison, chrome, compact),
                    prefix = compactComparison?.let {
                        { ComposerComparisonReserve(collapse = { comparisonCollapse }, afterStack = afterStack) }
                    },
                    suffix = { ComposerAffixAnchor() },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = ChatInputDefaults.shape,
                    colors = ChatInputDefaults.textFieldColors(isTemporaryChat),
                    keyboardOptions = ChatInputDefaults.keyboardOptions,
                    keyboardActions = KeyboardActions.Default,
                    maxLines = 6,
                    trailingIcon = {
                        val micBounce = rememberPressBounce()
                        IconButton(
                            onClick = {
                                if (isRecording) {
                                    onStopRecording()
                                } else {
                                    onStartRecording()
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .pressBounce(micBounce, enabled = !isLiquidGlass && !isTranscribing)
                                .semantics {
                                    contentDescription = if (isRecording) {
                                        cdStopVoiceRec
                                    } else {
                                        cdStartVoiceRec
                                    }
                                    role = Role.Button
                                },
                            enabled = !isTranscribing,
                        ) {
                            VoiceMicIndicator(
                                isRecording = isRecording,
                                isTranscribing = isTranscribing,
                            )
                        }
                    },
                )
                if (compactComparison != null) {
                    ComposerComparisonMorph(
                        comparison = compactComparison,
                        collapsed = comparisonCollapsed,
                        collapse = { comparisonCollapse },
                        modifier = Modifier.align(Alignment.TopStart),
                        afterStack = afterStack,
                    )
                }
            }
        },
        trailingSpacer = { compact ->
            if (!compact) Spacer(modifier = Modifier.width(8.dp))
        },
    )
}
