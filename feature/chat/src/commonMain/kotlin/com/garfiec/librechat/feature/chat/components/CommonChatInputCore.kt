package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.pressBounce
import com.garfiec.librechat.core.ui.components.rememberPressBounce
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.drawInsetOutlineStroke
import com.garfiec.librechat.core.ui.glass.glassSurface
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.feature.chat.components.veil.LocalComposerFlare
import com.garfiec.librechat.feature.chat.components.veil.retonedSurface
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_add_to_queue
import com.garfiec.librechat.feature.chat.resources.cd_answer_question
import com.garfiec.librechat.feature.chat.resources.cd_cancel_edit
import com.garfiec.librechat.feature.chat.resources.cd_cancel_pending_send
import com.garfiec.librechat.feature.chat.resources.cd_cancel_queued_message
import com.garfiec.librechat.feature.chat.resources.cd_collapse
import com.garfiec.librechat.feature.chat.resources.cd_during_run_options
import com.garfiec.librechat.feature.chat.resources.cd_expand
import com.garfiec.librechat.feature.chat.resources.cd_interrupt_message
import com.garfiec.librechat.feature.chat.resources.cd_send_message
import com.garfiec.librechat.feature.chat.resources.cd_start_voice_recording
import com.garfiec.librechat.feature.chat.resources.cd_steer_message
import com.garfiec.librechat.feature.chat.resources.cd_stop_generation
import com.garfiec.librechat.feature.chat.resources.cd_update_queued_message
import com.garfiec.librechat.feature.chat.resources.compare_message_both
import com.garfiec.librechat.feature.chat.resources.compare_pick_to_send
import com.garfiec.librechat.feature.chat.resources.draft_uploads_failed
import com.garfiec.librechat.feature.chat.resources.editing_queued_message
import com.garfiec.librechat.feature.chat.resources.hint_message
import com.garfiec.librechat.feature.chat.resources.hint_message_model
import com.garfiec.librechat.feature.chat.resources.queue_held_after_refusal
import com.garfiec.librechat.feature.chat.resources.recording
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.DuringRunSendTarget
import com.garfiec.librechat.feature.chat.viewmodel.PendingSteerChip
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Immutable
data class ChatInputState(
    val inputText: String,
    val isStreaming: Boolean,
    val isRecording: Boolean,
    val isTranscribing: Boolean,
    val chrome: ComposerChrome,
    val selectedModelDisplay: String?,
    val isCodeInterpreterAvailable: Boolean,
    /** Ephemeral-tools gate drives local chip display; remaining gates are threaded to the sheet. */
    val gates: ChatInputGates = ChatInputGates(),
    /** Whether queueing a follow-up mid-stream is allowed (existing conversation only). When
     *  false, the send button stays plain Stop while streaming. */
    val canQueue: Boolean = false,
    /** Whether a mid-stream send could instead be steered into the running reply (v0.8.8).
     *  False leaves the composer with queueing alone and hides the during-run picker. */
    val canSteer: Boolean = false,
    /** Built-in (provider-hosted) web search is on for this send. Such a run can't be interrupted,
     *  so the during-run menu says so on its Interrupt row instead of promising to stop it. */
    val builtInSearchOn: Boolean = false,
    /** Which route the during-run picker marks as the standing default — already degraded to
     *  [DuringRunAction.QUEUE] by the ViewModel when steering is unavailable. This is the
     *  *preference*; it is NOT what the send button does. See [duringRunSendTarget]. */
    val duringRunAction: DuringRunAction = DuringRunAction.QUEUE,
    /** What a mid-stream tap on the send button actually does, resolved by the ViewModel. The
     *  two differ whenever a live `ask_user_question` pause overrides the preference, and the
     *  button must name THIS — reading the preference instead is what once labelled an answer
     *  as "add to queue". */
    val duringRunSendTarget: DuringRunSendTarget = DuringRunSendTarget.QUEUE,
    /** True while the composer is editing a queued item (queued-edit mode): the send button
     *  becomes "Update" and an editing banner shows above the input. */
    val isEditingQueued: Boolean = false,
    /** True while a tapped send is parked waiting for its attachment(s) to finish uploading: the
     *  send button becomes a spinner the user can tap to cancel the deferred send. */
    val isAwaitingUploadSend: Boolean = false,
    /** True while just-picked files are still being resolved or are staged awaiting a routing
     *  choice ([ChatUiState.arePicksUnsettled]). Every send path refuses in that window, so the
     *  send/update control is disabled rather than left looking live over a tap that does nothing. */
    val arePicksUnsettled: Boolean = false,
    /** Prompt groups the `/` and `$` picker lists. Filtering happens here, off [inputText]. */
    val promptSuggestions: List<PromptMentionDisplayData> = emptyList(),
    /** Skills the picker may offer here, already narrowed (`ChatUiState.pickerSkills`). */
    val skillSuggestions: List<PickerSkill> = emptyList(),
    /** The skill catalog is loading for the picker. */
    val isLoadingSkills: Boolean = false,
    /** The skill catalog failed to load; the open picker offers a retry. */
    val skillsLoadFailed: Boolean = false,
    /** A skill can be picked here (`ChatUiState.canPickSkills`): only then does a leading `$` open the picker. */
    val canPickSkills: Boolean = false,
    /** What the field says about an active or pending comparison; setup also holds sends. */
    val comparisonHint: ComposerComparisonHint = ComposerComparisonHint.NONE,
)

/**
 * The composer box shared by both platforms. [leadingButtons], [textFieldContent] and
 * [trailingButtons] are each told when the box is compact (narrower than a 360dp phone's).
 */
@Composable
fun CommonChatInputCore(
    state: ChatInputState,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    /** Queue a follow-up while streaming. Null (or [ChatInputState.canQueue] false) keeps the
     *  button as plain Stop mid-stream (e.g. on a brand-new conversation). Also the during-run
     *  picker's explicit "add to queue" — never the default-action route. */
    onQueue: (() -> Unit)? = null,
    /** The send button's mid-stream action. Routes to steer or queue per the user's preference
     *  and what the run can take; the composer deliberately does not make that call itself. */
    onDuringRunSend: () -> Unit = {},
    /** The during-run picker's explicit "steer this one" (v0.8.8). */
    onSteer: () -> Unit = {},
    /** The during-run picker's explicit "interrupt and steer this one". */
    onInterrupt: () -> Unit = {},
    /** Retry the picker's failed skill catalog load. */
    onRetrySkills: () -> Unit = {},
    /** Persist a new default for what a mid-stream send does. */
    onSetDuringRunAction: (DuringRunAction) -> Unit = {},
    /** Number of queued messages held by a Stop/error pause. >0 shows the "Send queued" banner
     *  above the input; 0 hides it (queue empty or draining normally). */
    queuedPausedCount: Int = 0,
    /** The pause holds a message the server refused: explain that the queue waits for the user,
     *  even mid-run, where the "Send queued" button itself is hidden. */
    queueHeldForRefusal: Boolean = false,
    /** Temporary chat is on: the composer is tinted violet, as on web. */
    isTemporaryChat: Boolean = false,
    onSendQueuedMessages: () -> Unit = {},
    /** Commit / cancel the in-progress queued edit (see [ChatInputState.isEditingQueued]). */
    onCommitEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    /** Cancel a send parked behind an in-flight upload (see [ChatInputState.isAwaitingUploadSend]). */
    onCancelPendingSend: () -> Unit = {},
    fontSizeMultiplier: Float = 1f,
    /** A row picked from the `/` and `$` list: a prompt or a skill. */
    onSelectPickerItem: (ComposerPickerItem) -> Unit = {},
    leadingButtons: @Composable RowScope.(compact: Boolean) -> Unit = {},
    trailingButtons: @Composable RowScope.(compact: Boolean) -> Unit = {},
    bottomContent: @Composable BoxScope.() -> Unit = {},
    /** Draws no frame of its own ([ChatInputDefaults.textFieldColors]): the box is the frame. */
    textFieldContent: @Composable RowScope.(compact: Boolean) -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    // The fade keeps temporary chat's retone (see ModeVeil) rather than painting plain surface over it.
    val retonedSurfaceColor = retonedSurface(surfaceColor)
    val temporaryAmount = ChatInputDefaults.animateTemporaryAmount(isTemporaryChat)
    // Liquid Glass drops Material's fade: the glass capsule already separates the composer.
    val glass = rememberGlassStyle()
    val glassBackdrop = LocalGlassBackdrop.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (glass != null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val surface = lerp(surfaceColor, retonedSurfaceColor, temporaryAmount.value)
                        drawRect(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    surface.copy(alpha = 0.7f),
                                    surface.copy(alpha = 0.95f),
                                ),
                            ),
                        )
                    }
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            ComposerChromeStack(
                state = state,
                queuedPausedCount = queuedPausedCount,
                queueHeldForRefusal = queueHeldForRefusal,
                onSendQueuedMessages = onSendQueuedMessages,
                onCancelEdit = onCancelEdit,
                fontSizeMultiplier = fontSizeMultiplier,
            )

            val pickerQuery = remember(state.inputText, state.canPickSkills) {
                parsePickerQuery(state.inputText, state.canPickSkills)
            }
            val pickerRows = remember(pickerQuery, state.promptSuggestions, state.skillSuggestions) {
                pickerQuery?.let { buildPickerRows(it, state.promptSuggestions, state.skillSuggestions) }.orEmpty()
            }
            PromptSuggestionList(
                rows = pickerRows,
                isOpen = pickerQuery != null,
                hasCandidates = state.promptSuggestions.isNotEmpty() || state.skillSuggestions.isNotEmpty(),
                isLoadingSkills = state.isLoadingSkills,
                onSelect = onSelectPickerItem,
                skillsLoadFailed = state.skillsLoadFailed,
                onRetrySkills = onRetrySkills,
            )

            val temporaryWash = ChatInputDefaults.temporaryWash
            val temporaryBorder = ChatInputDefaults.temporaryBorderColor
            val boxFill = ChatInputDefaults.containerColor
            val boxBorder = ChatInputDefaults.borderColor
            val boxFocusedBorder = MaterialTheme.colorScheme.outline
            val temporaryAccent = ChatInputDefaults.temporaryAccent
            var boxFocused by remember { mutableStateOf(false) }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Below a 360dp screen the during-run caret folds into the send button, so the
                // settings line keeps its room.
                val compact = maxWidth < COMPACT_COMPOSER_ROW_WIDTH
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // A parent sees its children's focus: the box's border follows the field's.
                        .onFocusChanged { boxFocused = it.hasFocus }
                        .then(
                            if (glass != null) {
                                Modifier
                                    .glassSurface(glass, ComposerBoxShape, glassBackdrop, flare = LocalComposerFlare.current)
                                    .drawBehind {
                                        val amount = temporaryAmount.value
                                        if (amount > 0f) {
                                            val outline = ComposerBoxShape.createOutline(size, layoutDirection, this)
                                            drawOutline(outline, temporaryWash.copy(alpha = temporaryWash.alpha * amount))
                                            drawInsetOutlineStroke(
                                                ComposerBoxShape,
                                                temporaryBorder.copy(alpha = temporaryBorder.alpha * amount),
                                                1.dp.toPx(),
                                            )
                                        }
                                    }
                            } else {
                                Modifier.drawBehind {
                                    val amount = temporaryAmount.value
                                    val outline = ComposerBoxShape.createOutline(size, layoutDirection, this)
                                    drawOutline(outline, lerp(boxFill, temporaryWash.compositeOver(boxFill), amount))
                                    val border = if (boxFocused) {
                                        lerp(boxFocusedBorder, temporaryAccent, amount)
                                    } else {
                                        lerp(boxBorder, temporaryBorder.compositeOver(boxFill), amount)
                                    }
                                    drawInsetOutlineStroke(ComposerBoxShape, border, 1.dp.toPx())
                                }
                            },
                        )
                        .padding(4.dp),
                ) {
                    ComposerDraftStrip(state.chrome)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        textFieldContent(compact)
                    }
                    FailedUploadsLine(failedCount = state.chrome.draftItems.failedCount)
                    LocalCodeWorkspaceComposer.current?.let { CodeWorkspaceNotice(it) }
                    ComposerButtonRow(
                        state = state,
                        compact = compact,
                        leadingButtons = leadingButtons,
                        trailingButtons = trailingButtons,
                        onSend = onSend,
                        onStop = onStop,
                        onQueue = onQueue,
                        onSteer = onSteer,
                        onInterrupt = onInterrupt,
                        onDuringRunSend = onDuringRunSend,
                        onSetDuringRunAction = onSetDuringRunAction,
                        onCommitEdit = onCommitEdit,
                        onCancelPendingSend = onCancelPendingSend,
                    )
                }
            }
        }
        bottomContent()
    }
}

@Suppress("LongParameterList")
@Composable
private fun ComposerChromeStack(
    state: ChatInputState,
    queuedPausedCount: Int,
    queueHeldForRefusal: Boolean,
    onSendQueuedMessages: () -> Unit,
    onCancelEdit: () -> Unit,
    fontSizeMultiplier: Float,
) {
    val chrome = state.chrome
    Column(modifier = Modifier.fillMaxWidth().passVerticalDragsToThread()) {
        // The chat's pull request on a narrow pane: the least urgent row, so it sits furthest
        // from the text field.
        PullRequestComposerRow(
            isEditingQueued = state.isEditingQueued,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        SteerAndQueueRegion(
            pendingSteers = chrome.pendingSteers,
            onCancelSteer = chrome.onCancelSteer,
            onInterruptSteer = chrome.onInterruptSteer,
            queuedMessages = chrome.queuedMessages,
            onEditQueuedItem = chrome.onEditQueuedMessage,
            onCancelQueuedItem = chrome.onCancelQueuedMessage,
            onReorderQueue = chrome.onReorderQueuedMessages,
            fontSizeMultiplier = fontSizeMultiplier,
        )

        // Edit-mode banner takes priority over the paused-queue banner.
        if (state.isEditingQueued) {
            EditingQueuedBanner(
                onCancel = onCancelEdit,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            // Not gated on isStreaming, unlike the button: a refusal usually lands mid-run,
            // which is exactly when a held queue looks like one waiting on the run to finish.
            if (queueHeldForRefusal) QueueHeldNotice(modifier = Modifier.padding(bottom = 8.dp))
            if (queuedPausedCount > 0 && !state.isStreaming) {
                // Gated on !isStreaming: resume() lifts the pause and POPS the head, but
                // doSendWithSpec early-returns while a run is live, so the message would be
                // removed from the queue and never sent. A HITL pause is the long-lived
                // isStreaming window where the composer stays fully interactive.
                SendQueuedBanner(
                    count = queuedPausedCount,
                    onClick = onSendQueuedMessages,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

/** Steers, then the queue: the order they will reach the model. */
@Composable
private fun SteerAndQueueRegion(
    pendingSteers: List<PendingSteerChip>,
    onCancelSteer: (steerId: String) -> Unit,
    onInterruptSteer: (steerId: String) -> Unit,
    queuedMessages: List<QueuedMessage>,
    onEditQueuedItem: (localId: String) -> Unit,
    onCancelQueuedItem: (localId: String) -> Unit,
    onReorderQueue: (fromIndex: Int, toIndex: Int) -> Unit,
    fontSizeMultiplier: Float,
) {
    if (pendingSteers.isEmpty() && queuedMessages.isEmpty()) return
    val scroll = rememberScrollState()
    val threadDrags = LocalThreadScrollHandle.current
    val handOff = remember(threadDrags) { ThreadHandOffConnection(threadDrags) }
    val reorderScroll = remember(scroll) { ReorderScrollRegion(scroll, reversed = true) }
    SteerAndQueueColumn(
        scroll = scroll,
        modifier = Modifier.nestedScroll(handOff),
    ) {
        PendingSteerChipsSection(
            pendingSteers = pendingSteers,
            onCancel = onCancelSteer,
            onInterrupt = onInterruptSteer,
            fontSizeMultiplier = fontSizeMultiplier,
        )
        QueuedMessagesSection(
            queuedMessages = queuedMessages,
            onEdit = onEditQueuedItem,
            onCancel = onCancelQueuedItem,
            onReorder = onReorderQueue,
            fontSizeMultiplier = fontSizeMultiplier,
            reorderScroll = reorderScroll,
        )
    }
}

/** Scrolls only while it overflows, so until then the stack's pass-through takes the drag. */
@Composable
internal fun SteerAndQueueColumn(
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = STEER_AND_QUEUE_MAX_HEIGHT)
            .verticalScroll(scroll, enabled = scroll.maxValue > 0, reverseScrolling = true),
        content = content,
    )
}

@Composable
internal fun FailedUploadsLine(failedCount: Int) {
    if (failedCount == 0) return
    Text(
        text = pluralStringResource(Res.plurals.draft_uploads_failed, failedCount, failedCount),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** About two and a half steer or queue rows. */
internal val STEER_AND_QUEUE_MAX_HEIGHT = 168.dp

@Suppress("LongParameterList")
@Composable
private fun ComposerButtonRow(
    state: ChatInputState,
    compact: Boolean,
    leadingButtons: @Composable RowScope.(compact: Boolean) -> Unit,
    trailingButtons: @Composable RowScope.(compact: Boolean) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onQueue: (() -> Unit)?,
    onSteer: () -> Unit,
    onInterrupt: () -> Unit,
    onDuringRunSend: () -> Unit,
    onSetDuringRunAction: (DuringRunAction) -> Unit,
    onCommitEdit: () -> Unit,
    onCancelPendingSend: () -> Unit,
) {
    val chrome = state.chrome
    Row(
        modifier = Modifier.fillMaxWidth().passVerticalDragsToThread(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingButtons(compact)
        Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            ComposerSettingsLine(
                chips = chrome.settingChips,
                onChipTap = chrome.onSettingChipTap,
                onCancelSignIn = chrome.onCancelMcpSignIn,
                onStartSignInOver = chrome.onStartMcpSignInOver,
                ring = chrome.contextRing(),
                leading = codeWorkspaceLineLeading(LocalCodeWorkspaceComposer.current),
            )
        }
        trailingButtons(compact)
        // A comparison still choosing its second model has nowhere to send yet. Committing a queued
        // edit sends nothing (the queue is held through setup), so Update stays available.
        val blockedBySetup = state.comparisonHint == ComposerComparisonHint.SETTING_UP && !state.isEditingQueued
        // A new turn waits on the workspace row (its lines say why); a mid-run queue does not.
        val blockedByWorkspace = LocalCodeWorkspaceComposer.current?.state?.holdsSend == true &&
            !state.isStreaming && !state.isEditingQueued
        val draft = state.chrome.draftItems
        val hasComposerContent = !blockedBySetup && !blockedByWorkspace && composerCanSend(
            inputText = state.inputText,
            hasAttachments = draft.hasFiles,
            arePicksUnsettled = state.arePicksUnsettled,
            hasFailedUploads = draft.hasFailedUploads,
        )
        // Mid-stream + typed content + queueing allowed → morph Stop into a during-run send. A failed
        // upload keeps Stop: every during-run send would be held (`UploadIntakeDelegate.withUploadGate`).
        val duringRunSend = state.canQueue && hasComposerContent && onQueue != null &&
            !state.isEditingQueued
        // The picker only earns its space when both routes are open; with steering
        // unavailable the send button alone says everything there is to say. `canSteer`
        // is false while a run is paused for review, which is also what keeps the picker
        // from offering a steer/queue choice over a send that will answer the pause.
        val picker = duringRunSend && state.canSteer
        val foldPicker = picker && compact
        val menuOptions = DuringRunMenuOptions(
            defaultAction = state.duringRunAction,
            // Steering is text-only on mobile: a live send with attachments goes to the queue, so
            // the two live rows would promise something the tap won't do.
            liveRowsEnabled = !draft.hasFiles,
            builtInSearchOn = state.builtInSearchOn,
        )
        // Keyed on the fold, so a menu left open when the run ends, the draft clears or
        // the window widens doesn't pop back open on the next mid-run draft.
        var foldedMenuOpen by remember(foldPicker) { mutableStateOf(false) }
        if (picker && !compact) {
            DuringRunSendMenu(
                options = menuOptions,
                onSteerOnce = onSteer,
                onInterruptOnce = onInterrupt,
                onQueueOnce = onQueue ?: {},
                onSetDefault = onSetDuringRunAction,
            )
        }
        Box {
            SendStopButton(
                buttonSize = BoxButtonSize,
                isStreaming = state.isStreaming,
                canSend = hasComposerContent,
                canQueue = duringRunSend,
                duringRunTarget = state.duringRunSendTarget,
                onSend = onSend,
                onStop = onStop,
                // Folded, a tap opens the picker, whose first two entries are the two sends.
                onQueue = if (foldPicker) {
                    { foldedMenuOpen = true }
                } else {
                    onDuringRunSend
                },
                opensMenu = foldPicker,
                // In queued-edit mode the button commits the edit instead of send/stop/queue.
                isEditingQueued = state.isEditingQueued,
                onUpdate = onCommitEdit,
                isQueuedEditEmptied = state.inputText.isBlank() && !draft.hasFiles &&
                    !state.arePicksUnsettled,
                // A send waiting on an in-flight upload: spinner, tap to cancel.
                isAwaitingUploadSend = state.isAwaitingUploadSend,
                onCancelPendingSend = onCancelPendingSend,
            )
            DuringRunSendDropdown(
                expanded = foldPicker && foldedMenuOpen,
                onDismiss = { foldedMenuOpen = false },
                options = menuOptions,
                onSteerOnce = onSteer,
                onInterruptOnce = onInterrupt,
                onQueueOnce = onQueue ?: {},
                onSetDefault = onSetDuringRunAction,
            )
        }
    }
}

private val ComposerBoxShape = RoundedCornerShape(28.dp)

/** The box's round buttons: 40dp drawn, a 48dp touch target from Compose's touch-bounds expansion. */
internal val BoxButtonSize = 40.dp

/** Null without wide Compare badges, so the field keeps M3's plain start padding (any slot takes 48dp+). */
internal fun composerFieldLeadingIcon(comparison: ComposerComparison?): (@Composable () -> Unit)? {
    val badges = comparison?.takeUnless { it.compact } ?: return null
    return { ComposerComparisonBadges(badges) }
}

/** The input row's width on a 360dp phone, inside the bar's 12dp side padding. */
private val COMPACT_COMPOSER_ROW_WIDTH = 336.dp

/** Visual mode of the trailing composer button. */
internal enum class SendButtonMode { SEND, STOP, QUEUE, STEER, INTERRUPT, ANSWER, UPDATE, REMOVE_QUEUED, AWAITING }

/**
 * Whether the composer holds something the trailing control can act on. Drives both that control's
 * enabled state and whether a mid-stream tap morphs Stop into a during-run send.
 *
 * A file that failed to upload holds every send until it is retried or removed, so it disables the
 * control too; the line under the field says why ([FailedUploadsLine]).
 *
 * Files that have been picked but not settled deliberately do NOT count, even though the user can
 * see them coming: every send path refuses while a pick is in flight or staged for the routing
 * sheet, so counting them would leave Send, Steer, Queue and Update looking live over a tap that
 * silently does nothing for as long as the provider takes to resolve.
 *
 * Pulled out of the composable for the same reason as [sendButtonModeFor]: an affordance that
 * disagrees with the action behind it is exactly the defect no behavioural test catches.
 */
internal fun composerCanSend(
    inputText: String,
    hasAttachments: Boolean,
    arePicksUnsettled: Boolean,
    hasFailedUploads: Boolean = false,
): Boolean = !arePicksUnsettled && !hasFailedUploads && (inputText.isNotBlank() || hasAttachments)

/**
 * Which face the composer's trailing button shows. Pulled out of the composable so the mapping
 * from state to affordance is asserted as a pure function: the one defect this rule has produced
 * was a *label* that disagreed with the action behind it, which no behavioural test could catch.
 */
@Suppress("LongParameterList")
internal fun sendButtonModeFor(
    isStreaming: Boolean,
    canQueue: Boolean,
    duringRunTarget: DuringRunSendTarget,
    isEditingQueued: Boolean,
    isAwaitingUploadSend: Boolean,
    isQueuedEditEmptied: Boolean = false,
): SendButtonMode = when {
    // Emptying a queued message and confirming withdraws it, so the button stays enabled.
    isEditingQueued && isQueuedEditEmptied -> SendButtonMode.REMOVE_QUEUED
    isEditingQueued -> SendButtonMode.UPDATE
    // A send parked behind an in-flight upload only takes over the button when NOT streaming —
    // during a stream the Stop control must stay reachable, so a queued-message send parked
    // behind its upload keeps Stop (the parked enqueue still fires once the upload settles).
    isAwaitingUploadSend && !isStreaming -> SendButtonMode.AWAITING
    isAwaitingUploadSend -> SendButtonMode.STOP
    !isStreaming -> SendButtonMode.SEND
    canQueue -> when (duringRunTarget) {
        DuringRunSendTarget.ANSWER_PAUSE -> SendButtonMode.ANSWER
        DuringRunSendTarget.STEER -> SendButtonMode.STEER
        DuringRunSendTarget.INTERRUPT -> SendButtonMode.INTERRUPT
        DuringRunSendTarget.QUEUE -> SendButtonMode.QUEUE
    }

    else -> SendButtonMode.STOP
}

/**
 * Animated send / stop / steer / add-to-queue / answer / update button shared between platforms.
 *
 * A send parked behind an in-flight upload ([isAwaitingUploadSend]) shows a cancellable **spinner**
 * — but only when not streaming, so a mid-stream Stop is never hidden behind it. Otherwise, in
 * queued-edit mode ([isEditingQueued]) it is **Update** (commit the edit); while streaming it is
 * **Stop** by default but morphs into whatever [duringRunTarget] names when the composer has
 * content and queueing is allowed ([canQueue]) — the "clear the box to reveal Stop" rule; and when
 * not streaming it is the usual **Send** (enabled on [canSend]).
 *
 * The during-run mode is derived from [duringRunTarget] and nothing else — never from the user's
 * steer/queue *preference*, which a live `ask_user_question` pause overrides: the button would
 * announce "add to queue" over a tap that answers the question. Behaviour right, label lying, and
 * nothing fails — an affordance read from a different source than the action it triggers drifts
 * silently.
 */
@Composable
fun SendStopButton(
    isStreaming: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    canQueue: Boolean = false,
    onQueue: () -> Unit = {},
    /** What the during-run button names. [onQueue] carries whichever action this names — the
     *  caller resolves it, so the button stays one control. */
    duringRunTarget: DuringRunSendTarget = DuringRunSendTarget.QUEUE,
    isEditingQueued: Boolean = false,
    onUpdate: () -> Unit = {},
    /** In queued-edit mode the composer was emptied: [onUpdate] withdraws the message. */
    isQueuedEditEmptied: Boolean = false,
    isAwaitingUploadSend: Boolean = false,
    onCancelPendingSend: () -> Unit = {},
    /** A during-run tap opens the steer/queue picker rather than sending: the caret is folded in. */
    opensMenu: Boolean = false,
    buttonSize: Dp = 56.dp,
) {
    val mode = sendButtonModeFor(
        isStreaming = isStreaming,
        canQueue = canQueue,
        duringRunTarget = duringRunTarget,
        isEditingQueued = isEditingQueued,
        isAwaitingUploadSend = isAwaitingUploadSend,
        isQueuedEditEmptied = isQueuedEditEmptied,
    )
    // On the slot, not each button, so a send's bounce survives the swap to Stop.
    val bounce = rememberPressBounce()
    val bounces = !isLiquidGlass && (canSend || (mode != SendButtonMode.SEND && mode != SendButtonMode.UPDATE))
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
        },
        label = "send_stop_toggle",
        modifier = modifier.pressBounce(bounce, enabled = bounces),
    ) { buttonMode ->
        when (buttonMode) {
            SendButtonMode.UPDATE -> IconButton(
                onClick = onUpdate,
                modifier = Modifier.size(buttonSize),
                enabled = canSend,
                colors = ChatInputDefaults.sendButtonColors(),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(Res.string.cd_update_queued_message),
                )
            }

            SendButtonMode.REMOVE_QUEUED -> IconButton(
                onClick = onUpdate,
                modifier = Modifier.size(buttonSize),
                colors = ChatInputDefaults.sendButtonColors(MaterialTheme.colorScheme.error),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.cd_cancel_queued_message),
                )
            }

            SendButtonMode.AWAITING -> IconButton(
                onClick = onCancelPendingSend,
                modifier = Modifier.size(buttonSize),
                colors = ChatInputDefaults.sendButtonColors(),
            ) {
                // Spinner (upload still in flight) with a small ✕ so it reads as "tap to cancel".
                Box(contentAlignment = Alignment.Center) {
                    AdaptiveCircularProgressIndicator(
                        modifier = Modifier.size(buttonSize / 2),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.cd_cancel_pending_send),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            SendButtonMode.STOP -> IconButton(
                onClick = onStop,
                modifier = Modifier.size(buttonSize),
                colors = ChatInputDefaults.sendButtonColors(MaterialTheme.colorScheme.error),
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = stringResource(Res.string.cd_stop_generation),
                    modifier = Modifier.size(buttonSize / 2),
                )
            }

            SendButtonMode.QUEUE -> IconButton(
                onClick = onQueue,
                modifier = Modifier.size(buttonSize),
                colors = ChatInputDefaults.sendButtonColors(),
            ) {
                DuringRunSendIcon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = stringResource(Res.string.cd_add_to_queue),
                    opensMenu = opensMenu,
                )
            }

            SendButtonMode.STEER -> IconButton(
                onClick = onQueue,
                modifier = Modifier.size(buttonSize),
                colors = ChatInputDefaults.sendButtonColors(),
            ) {
                DuringRunSendIcon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringResource(Res.string.cd_steer_message),
                    opensMenu = opensMenu,
                )
            }

            SendButtonMode.INTERRUPT -> IconButton(
                onClick = onQueue,
                modifier = Modifier.size(buttonSize),
                colors = ChatInputDefaults.sendButtonColors(),
            ) {
                DuringRunSendIcon(
                    imageVector = Icons.Default.FlashOff,
                    contentDescription = stringResource(Res.string.cd_interrupt_message),
                    opensMenu = opensMenu,
                )
            }

            // Deliberately looks like Send, because that is what it does: the run is parked
            // waiting for this text, so the tap delivers it rather than deferring it. Only the
            // click target differs (onQueue → sendDuringRun → resolve the pause).
            SendButtonMode.ANSWER -> IconButton(
                onClick = onQueue,
                modifier = Modifier.size(buttonSize),
                colors = ChatInputDefaults.sendButtonColors(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(Res.string.cd_answer_question),
                )
            }

            SendButtonMode.SEND -> IconButton(
                onClick = onSend,
                modifier = Modifier.size(buttonSize),
                enabled = canSend,
                colors = ChatInputDefaults.sendButtonColors(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(Res.string.cd_send_message),
                )
            }
        }
    }
}

/**
 * A during-run send's icon. When the button opens the picker instead ([opensMenu]), the picker's
 * caret sits above it and the button is announced as the picker, which is what a tap does.
 */
@Composable
private fun DuringRunSendIcon(imageVector: ImageVector, contentDescription: String, opensMenu: Boolean) {
    if (!opensMenu) {
        Icon(imageVector = imageVector, contentDescription = contentDescription)
        return
    }
    val pickerDescription = stringResource(Res.string.cd_during_run_options)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { this.contentDescription = pickerDescription },
    ) {
        Icon(imageVector = Icons.Default.ExpandLess, contentDescription = null, modifier = Modifier.size(16.dp))
        Icon(imageVector = imageVector, contentDescription = null)
    }
}

/**
 * Banner shown above the composer while editing a queued item, with a cancel affordance that
 * discards the edit and restores the previous draft.
 */
@Composable
private fun EditingQueuedBanner(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.editing_queued_message),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.cd_cancel_edit),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Says why the queue is holding: the server refused a message and it will not send on its own.
 * Without it a held queue reads as one waiting on the run, and the user waits for nothing.
 *
 * Held to [QUEUE_HELD_COLLAPSED_LINES] until tapped open: at a large font scale the whole of it
 * would take the screen the keyboard leaves.
 */
@Composable
private fun QueueHeldNotice(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var truncated by remember { mutableStateOf(false) }
    val toggleLabel = stringResource(if (expanded) Res.string.cd_collapse else Res.string.cd_expand)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .then(
                if (expanded || truncated) {
                    Modifier.clickable(onClickLabel = toggleLabel) { expanded = !expanded }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.queue_held_after_refusal),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (expanded) Int.MAX_VALUE else QUEUE_HELD_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) truncated = it.hasVisualOverflow },
            modifier = Modifier.weight(1f),
        )
        if (expanded || truncated) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private const val QUEUE_HELD_COLLAPSED_LINES = 3

/**
 * Voice mic visual indicator: shows transcription spinner, recording pulse, or idle mic icon.
 * Used inside both platform mic buttons (which differ in placement).
 */
@Composable
fun VoiceMicIndicator(
    isRecording: Boolean,
    isTranscribing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isTranscribing) {
        AdaptiveCircularProgressIndicator(
            modifier = modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    } else if (isRecording) {
        val infiniteTransition = rememberInfiniteTransition(
            label = "recording_pulse",
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_alpha",
        )
        Box(
            modifier = modifier
                .size(20.dp)
                .alpha(pulseAlpha)
                .background(
                    color = MaterialTheme.colorScheme.error,
                    shape = CircleShape,
                ),
        )
    } else {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = stringResource(Res.string.cd_start_voice_recording),
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shared placeholder text for the chat input field.
 */
@Composable
fun ChatInputPlaceholder(
    isRecording: Boolean,
    selectedModelDisplay: String?,
    modifier: Modifier = Modifier,
    comparisonHint: ComposerComparisonHint = ComposerComparisonHint.NONE,
) {
    Text(
        text = if (isRecording) {
            stringResource(Res.string.recording)
        } else if (comparisonHint == ComposerComparisonHint.SETTING_UP) {
            stringResource(Res.string.compare_pick_to_send)
        } else if (comparisonHint == ComposerComparisonHint.BOTH) {
            stringResource(Res.string.compare_message_both)
        } else if (!selectedModelDisplay.isNullOrBlank()) {
            stringResource(Res.string.hint_message_model, selectedModelDisplay)
        } else {
            stringResource(Res.string.hint_message)
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
