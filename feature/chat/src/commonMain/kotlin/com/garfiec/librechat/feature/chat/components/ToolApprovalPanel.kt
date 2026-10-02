package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.core.model.ToolApprovalDecisions
import com.garfiec.librechat.core.model.ToolApprovalRequest
import com.garfiec.librechat.core.model.request.ToolApprovalResolution
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.util.DEFAULT_ALLOWED_TOOL_DECISIONS
import com.garfiec.librechat.feature.chat.util.ToolDecisionDraft
import com.garfiec.librechat.feature.chat.util.allowedDecisionsByCallId
import com.garfiec.librechat.feature.chat.util.nextUndecidedCallIndex
import com.garfiec.librechat.feature.chat.util.parseToolArgumentsOrNull
import com.garfiec.librechat.feature.chat.util.toResolution
import com.garfiec.librechat.feature.chat.util.toolBatchResolutions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A run paused on a tool batch awaiting approval (v0.8.8 HITL), docked above the composer until
 * it resolves, one tool call at a time.
 *
 * Above the composer rather than in its place, unlike [AskUserQuestionPanel]: an approval is a
 * decision, not an answer, so the composer has nothing to compete with — and it keeps its Stop,
 * queue and drafts for the user who would rather redirect the run than approve it.
 *
 * Two layouts, split at the same width as the ask panel:
 * - **Compact** (phones, a folded foldable): the call's tool name heads the card with a
 *   `‹ 1 of 3 ›` pager; an Approve or Reject on a call not yet decided moves straight on, and the
 *   decision that completes the batch submits it.
 * - **Wide** (tablets, unfolded): one tab per call with explicit Back / Next / Continue.
 *
 * Both collapse to a single line so the reply above can be read.
 *
 * The submit is all-or-nothing because the server's is: the resume route 400s a partial batch and
 * an edit or respond with no payload, so nothing goes up until every call is decided and complete.
 * Allowed decisions are per `tool_call_id`, and a bulk shortcut is offered only when every call in
 * the batch permits it — a policy can restrict one call to reject/respond, and an "Approve all"
 * there would build a batch the server rejects with a 403.
 *
 * Every piece of editor state is hoisted ([drafts], [activeCallId], [collapsed], owned by
 * `PendingActionDelegate`), so it survives the layout switching on a fold or rotation.
 */
@Composable
fun ToolApprovalPanel(
    pendingAction: PendingAction,
    isResolving: Boolean,
    drafts: Map<String, ToolDecisionDraft>,
    activeCallId: String?,
    collapsed: Boolean,
    onDraftChange: (String, ToolDecisionDraft) -> Unit,
    onSelectCall: (String) -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onSubmit: (List<ToolApprovalResolution>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!pendingAction.isToolApproval) return
    val payload = pendingAction.payload ?: return
    val calls = payload.actionRequests
    if (calls.isEmpty()) return
    val allowed = remember(payload) { payload.allowedDecisionsByCallId() }
    val activeIndex = calls.indexOfFirst { it.toolCallId == activeCallId }.coerceAtLeast(0)

    val submit: (Map<String, ToolDecisionDraft>) -> Unit = { latest ->
        toolBatchResolutions(payload, latest)?.let(onSubmit)
    }
    val state = ToolPanelState(
        calls = calls,
        allowed = allowed,
        drafts = drafts,
        activeIndex = activeIndex,
        isResolving = isResolving,
        collapsed = collapsed,
    )
    val actions = ToolPanelActions(
        onDraftChange = onDraftChange,
        onSelect = { index -> onSelectCall(calls[index].toolCallId) },
        onCollapsedChange = onCollapsedChange,
        submit = submit,
        decideAll = { decision ->
            val all = calls.associate { it.toolCallId to ToolDecisionDraft(decision = decision) }
            all.forEach { (id, draft) -> onDraftChange(id, draft) }
            submit(all)
        },
        advanceOrSubmit = { fromIndex, latest ->
            val next = nextUndecidedCallIndex(calls.map { it.toolCallId }, latest, fromIndex)
            if (next == null) submit(latest) else onSelectCall(calls[next].toolCallId)
        },
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // The composer stays below this panel, so the body gets a smaller share of the screen
        // than the ask panel's, which stands in for the composer.
        val bodyMaxHeight = if (maxHeight == Dp.Infinity) FALLBACK_BODY_MAX_HEIGHT else maxHeight * BODY_HEIGHT_FRACTION
        val isWide = maxWidth >= PAUSE_PANEL_WIDE_MIN_WIDTH
        AdaptiveCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            if (isWide) {
                WideToolLayout(state = state, actions = actions, bodyMaxHeight = bodyMaxHeight)
            } else {
                CompactToolLayout(state = state, actions = actions, bodyMaxHeight = bodyMaxHeight)
            }
        }
    }
}

@Immutable
private data class ToolPanelState(
    val calls: List<ToolApprovalRequest>,
    val allowed: Map<String, List<String>>,
    val drafts: Map<String, ToolDecisionDraft>,
    val activeIndex: Int,
    val isResolving: Boolean,
    val collapsed: Boolean,
) {
    val active: ToolApprovalRequest get() = calls[activeIndex]
    val activeDraft: ToolDecisionDraft get() = drafts[active.toolCallId] ?: ToolDecisionDraft()
    val isBatch: Boolean get() = calls.size > 1
    val isLast: Boolean get() = activeIndex == calls.lastIndex
    val decidedCount: Int get() = calls.count { isDecided(it.toolCallId) }
    val allDecided: Boolean get() = decidedCount == calls.size

    fun isDecided(toolCallId: String): Boolean = drafts[toolCallId]?.toResolution(toolCallId) != null

    fun allowedFor(call: ToolApprovalRequest): List<String> =
        allowed[call.toolCallId] ?: DEFAULT_ALLOWED_TOOL_DECISIONS

    /** True when every call in the batch permits [decision], so a bulk shortcut can't 403. */
    fun isBulkAllowed(decision: String): Boolean = isBatch && calls.all { decision in allowedFor(it) }

    /** True when deciding the active call is all the batch still needs. */
    val activeIsLastUndecided: Boolean
        get() = calls.all { it.toolCallId == active.toolCallId || isDecided(it.toolCallId) }
}

private class ToolPanelActions(
    val onDraftChange: (String, ToolDecisionDraft) -> Unit,
    val onSelect: (Int) -> Unit,
    val onCollapsedChange: (Boolean) -> Unit,
    val submit: (Map<String, ToolDecisionDraft>) -> Unit,
    val decideAll: (String) -> Unit,
    val advanceOrSubmit: (fromIndex: Int, drafts: Map<String, ToolDecisionDraft>) -> Unit,
)

// ── compact (phone) ───────────────────────────────────────────────────────

@Composable
private fun CompactToolLayout(
    state: ToolPanelState,
    actions: ToolPanelActions,
    bodyMaxHeight: Dp,
) {
    val active = state.active
    val coroutineScope = rememberCoroutineScope()
    val currentState by rememberUpdatedState(state)
    val currentActions by rememberUpdatedState(actions)

    Box {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolNameTitle(name = active.name, collapsed = state.collapsed, modifier = Modifier.weight(1f))
                if (state.isBatch) {
                    PausePanelPager(
                        index = state.activeIndex,
                        count = state.calls.size,
                        enabled = !state.collapsed,
                        onSelect = actions.onSelect,
                        previousLabel = stringResource(Res.string.cd_previous_tool_call),
                        nextLabel = stringResource(Res.string.cd_next_tool_call),
                    )
                }
                CollapseToggle(collapsed = state.collapsed, onCollapsedChange = actions.onCollapsedChange)
            }

            if (state.collapsed) return@Column

            Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                key(active.toolCallId) {
                    ToolCallBody(
                        call = active,
                        showName = false,
                        allowedDecisions = state.allowedFor(active),
                        draft = state.activeDraft,
                        isResolving = state.isResolving,
                        bodyMaxHeight = bodyMaxHeight,
                        onDraftChange = { draft -> actions.onDraftChange(active.toolCallId, draft) },
                        onPickedFinal = { decision ->
                            // Approve and Reject are complete decisions: after a beat (so the pick is
                            // seen landing) move on — or submit, when it completed the batch. A call
                            // that was already decided never moves on by itself; the user is reviewing.
                            val from = active.toolCallId
                            coroutineScope.launch {
                                delay(PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS)
                                // Decided on what is recorded NOW, not at the tap: a different pick
                                // inside the beat means the user is still deciding.
                                val latest = currentState
                                val stillPicked = latest.active.toolCallId == from &&
                                    latest.drafts[from]?.decision == decision
                                if (stillPicked && !latest.isResolving) {
                                    currentActions.advanceOrSubmit(latest.activeIndex, latest.drafts)
                                }
                            }
                        },
                    )
                }

                // Approve and Reject move on by themselves, so the explicit action only appears once
                // the call holds a decision that doesn't (an Edit or Respond, or a call revisited).
                val canProceed = state.isDecided(active.toolCallId)
                val hasBulk = state.isBulkAllowed(ToolApprovalDecisions.REJECT) ||
                    state.isBulkAllowed(ToolApprovalDecisions.APPROVE)
                if (canProceed || hasBulk || state.isResolving) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BulkButtons(state = state, actions = actions)
                        Spacer(modifier = Modifier.weight(1f))
                        if (state.isResolving) {
                            AdaptiveCircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        if (canProceed) {
                            AdaptiveButton(
                                onClick = { actions.advanceOrSubmit(state.activeIndex, state.drafts) },
                                enabled = !state.isResolving,
                            ) {
                                Text(
                                    stringResource(
                                        if (state.activeIsLastUndecided) {
                                            Res.string.tool_approval_submit
                                        } else {
                                            Res.string.ask_user_question_next
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state.collapsed) ExpandCollapsedOverlay(onExpand = { actions.onCollapsedChange(false) })
    }
}

// ── wide (tablet) ─────────────────────────────────────────────────────────

@Composable
private fun WideToolLayout(
    state: ToolPanelState,
    actions: ToolPanelActions,
    bodyMaxHeight: Dp,
) {
    val active = state.active
    val coroutineScope = rememberCoroutineScope()
    val currentActiveId by rememberUpdatedState(active.toolCallId)

    Box {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isBatch) {
                    PausePanelTabs(
                        labels = state.calls.mapIndexed { index, call ->
                            call.name.ifBlank { stringResource(Res.string.tool_approval_tab_fallback, index + 1) }
                        },
                        done = state.calls.map { state.isDecided(it.toolCallId) },
                        activeIndex = state.activeIndex,
                        onSelect = actions.onSelect,
                        doneLabel = stringResource(Res.string.cd_tool_call_decided),
                        enabled = !state.collapsed,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    ToolNameTitle(name = active.name, collapsed = state.collapsed, modifier = Modifier.weight(1f))
                }
                CollapseToggle(collapsed = state.collapsed, onCollapsedChange = actions.onCollapsedChange)
            }

            if (state.collapsed) return@Column

            Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Keyed per tab so each call opens scrolled to its top.
                key(active.toolCallId) {
                    ToolCallBody(
                        call = active,
                        showName = state.isBatch,
                        allowedDecisions = state.allowedFor(active),
                        draft = state.activeDraft,
                        isResolving = state.isResolving,
                        bodyMaxHeight = bodyMaxHeight,
                        onDraftChange = { draft -> actions.onDraftChange(active.toolCallId, draft) },
                        onPickedFinal = {
                            // A complete decision moves on to the next tab, after a beat so the pick
                            // is seen landing. The wide layout never submits on a pick: Continue does.
                            if (!state.isLast) {
                                val from = active.toolCallId
                                coroutineScope.launch {
                                    delay(PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS)
                                    if (currentActiveId == from) actions.onSelect(state.activeIndex + 1)
                                }
                            }
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BulkButtons(state = state, actions = actions)
                    Spacer(modifier = Modifier.weight(1f))
                    if (state.isResolving) {
                        AdaptiveCircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    if (state.activeIndex > 0) {
                        TextButton(
                            onClick = { actions.onSelect(state.activeIndex - 1) },
                            enabled = !state.isResolving,
                        ) {
                            Text(stringResource(Res.string.ask_user_question_back))
                        }
                    }
                    if (!state.isLast) {
                        AdaptiveButton(
                            onClick = { actions.onSelect(state.activeIndex + 1) },
                            enabled = !state.isResolving,
                        ) {
                            Text(stringResource(Res.string.ask_user_question_next))
                        }
                    } else {
                        AdaptiveButton(
                            onClick = { actions.submit(state.drafts) },
                            enabled = !state.isResolving && state.allDecided,
                        ) {
                            Text(
                                if (state.isBatch) {
                                    stringResource(
                                        Res.string.tool_approval_submit_progress,
                                        state.decidedCount,
                                        state.calls.size,
                                    )
                                } else {
                                    stringResource(Res.string.tool_approval_submit)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (state.collapsed) ExpandCollapsedOverlay(onExpand = { actions.onCollapsedChange(false) })
    }
}

// ── shared pieces ─────────────────────────────────────────────────────────

@Composable
private fun ToolNameTitle(name: String, collapsed: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name.ifBlank { stringResource(Res.string.tool_approval_title) },
            style = MaterialTheme.typography.titleSmall,
            maxLines = if (collapsed) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

/**
 * One call: its description and arguments (scrolling within [bodyMaxHeight], so a long payload
 * can't push the decisions off screen), then the decisions its policy allows, then the field an
 * Edit or Respond decision needs.
 */
@Composable
private fun ToolCallBody(
    call: ToolApprovalRequest,
    showName: Boolean,
    allowedDecisions: List<String>,
    draft: ToolDecisionDraft,
    isResolving: Boolean,
    bodyMaxHeight: Dp,
    onDraftChange: (ToolDecisionDraft) -> Unit,
    /** An Approve or Reject on a call that had no complete decision yet. */
    onPickedFinal: (String) -> Unit,
) {
    val arguments = remember(call.arguments) { call.arguments.asDisplayText() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier
                .heightIn(max = bodyMaxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showName && call.name.isNotBlank()) {
                Text(text = call.name, style = MaterialTheme.typography.titleSmall)
            }
            call.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (arguments.isNotBlank()) {
                Text(
                    text = stringResource(Res.string.tool_approval_arguments),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = arguments,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    // Tool args are frequently one long JSON line; scroll rather than wrap it into a
                    // wall that pushes the decision controls off screen.
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }

        DecisionRow(
            allowedDecisions = allowedDecisions,
            selected = draft.decision,
            enabled = !isResolving,
            onPick = { decision ->
                val wasComplete = draft.toResolution(call.toolCallId) != null
                val updated = if (decision == ToolApprovalDecisions.EDIT && draft.editedArguments.isBlank()) {
                    draft.copy(decision = decision, editedArguments = arguments)
                } else {
                    draft.copy(decision = decision)
                }
                onDraftChange(updated)
                val isFinal = decision == ToolApprovalDecisions.APPROVE || decision == ToolApprovalDecisions.REJECT
                if (isFinal && !wasComplete) onPickedFinal(decision)
            },
        )

        if (draft.decision == ToolApprovalDecisions.EDIT) {
            val invalid = draft.editedArguments.isNotBlank() && draft.editedArguments.parseToolArgumentsOrNull() == null
            AdaptiveOutlinedTextField(
                value = draft.editedArguments,
                onValueChange = { onDraftChange(draft.copy(editedArguments = it)) },
                enabled = !isResolving,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                label = { Text(stringResource(Res.string.tool_approval_edited_arguments)) },
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(Res.string.tool_approval_invalid_json)) }
                } else {
                    null
                },
                minLines = 2,
                maxLines = 6,
            )
        }

        if (draft.decision == ToolApprovalDecisions.RESPOND) {
            AdaptiveOutlinedTextField(
                value = draft.responseText,
                onValueChange = { onDraftChange(draft.copy(responseText = it)) },
                enabled = !isResolving,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.tool_approval_response_text)) },
                minLines = 2,
                maxLines = 4,
            )
        }
    }
}

/**
 * The decisions one call's policy allows: Edit and Respond as chips, since they open a field
 * rather than finish the call, then Reject and Approve as the two buttons that do. The recorded
 * decision carries a check, so a call revisited from the pager or tabs shows what was chosen.
 */
@Composable
private fun DecisionRow(
    allowedDecisions: List<String>,
    selected: String?,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    val ordered = DECISION_ORDER.filter { it in allowedDecisions }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ordered.forEach { decision ->
            val label = stringResource(decision.decisionLabel())
            val isSelected = selected == decision
            val check: @Composable () -> Unit = {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            }
            when (decision) {
                ToolApprovalDecisions.APPROVE -> AdaptiveButton(
                    onClick = { onPick(decision) },
                    enabled = enabled,
                    contentPadding = DECISION_BUTTON_PADDING,
                ) {
                    if (isSelected) check()
                    Text(label)
                }
                ToolApprovalDecisions.REJECT -> AdaptiveOutlinedButton(
                    onClick = { onPick(decision) },
                    enabled = enabled,
                    contentPadding = DECISION_BUTTON_PADDING,
                ) {
                    if (isSelected) check()
                    Text(label)
                }
                else -> AdaptiveFilterChip(
                    selected = isSelected,
                    enabled = enabled,
                    onClick = { onPick(decision) },
                    label = { Text(label) },
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
private fun BulkButtons(state: ToolPanelState, actions: ToolPanelActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.isBulkAllowed(ToolApprovalDecisions.REJECT)) {
            TextButton(onClick = { actions.decideAll(ToolApprovalDecisions.REJECT) }, enabled = !state.isResolving) {
                Text(stringResource(Res.string.tool_approval_reject_all))
            }
        }
        if (state.isBulkAllowed(ToolApprovalDecisions.APPROVE)) {
            TextButton(onClick = { actions.decideAll(ToolApprovalDecisions.APPROVE) }, enabled = !state.isResolving) {
                Text(stringResource(Res.string.tool_approval_approve_all))
            }
        }
    }
}

@Composable
private fun CollapseToggle(collapsed: Boolean, onCollapsedChange: (Boolean) -> Unit) {
    PausePanelCollapseToggle(
        collapsed = collapsed,
        onCollapsedChange = onCollapsedChange,
        minimizeLabel = stringResource(Res.string.cd_minimize_tool_approval),
        restoreLabel = stringResource(Res.string.cd_restore_tool_approval),
    )
}

@Composable
private fun BoxScope.ExpandCollapsedOverlay(onExpand: () -> Unit) {
    PausePanelExpandOverlay(restoreLabel = stringResource(Res.string.cd_restore_tool_approval), onExpand = onExpand)
}

/**
 * The one-line stand-in the thread keeps for a docked tool-approval pause, so the reply still
 * reads as waiting on the user.
 */
@Composable
fun ToolApprovalWaitingMarker(modifier: Modifier = Modifier) {
    PausePanelWaitingMarker(
        text = stringResource(Res.string.tool_approval_waiting_marker),
        icon = Icons.Default.Shield,
        modifier = modifier,
    )
}

/** Renders `string | object` tool arguments without assuming either shape. */
private fun JsonElement?.asDisplayText(): String = when (this) {
    null -> ""
    is JsonPrimitive -> if (isString) content else toString()
    else -> toString()
}

/**
 * Display order. A decision kind this build doesn't render is dropped rather than shown unlabeled;
 * the remaining ones still resolve the call.
 */
private val DECISION_ORDER = listOf(
    ToolApprovalDecisions.EDIT,
    ToolApprovalDecisions.RESPOND,
    ToolApprovalDecisions.REJECT,
    ToolApprovalDecisions.APPROVE,
)

private fun String.decisionLabel(): StringResource = when (this) {
    ToolApprovalDecisions.APPROVE -> Res.string.tool_approval_approve
    ToolApprovalDecisions.REJECT -> Res.string.tool_approval_reject
    ToolApprovalDecisions.EDIT -> Res.string.tool_approval_edit
    else -> Res.string.tool_approval_respond
}

/** Narrower than the default padding so all four decisions fit one row at phone width. */
private val DECISION_BUTTON_PADDING = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
private const val BODY_HEIGHT_FRACTION = 0.3f
private val FALLBACK_BODY_MAX_HEIGHT = 240.dp
