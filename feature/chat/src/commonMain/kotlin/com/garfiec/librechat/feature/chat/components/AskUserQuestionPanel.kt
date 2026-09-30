package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.AskUserQuestionLimits
import com.garfiec.librechat.core.model.AskUserQuestionOption
import com.garfiec.librechat.core.model.AskUserQuestionRequest
import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.util.AskAnswerDraft
import com.garfiec.librechat.feature.chat.util.askFreeTextBudget
import com.garfiec.librechat.feature.chat.util.composeAskAnswer
import com.garfiec.librechat.feature.chat.util.nextBlankQuestionIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** One question as the panel shows it: a batch item, or a single-question pause. */
@Immutable
internal data class AskPanelQuestion(
    /** The batch item's id, or the action id for a single question — the key its draft lives under. */
    val id: String,
    val header: String?,
    val question: String,
    val description: String?,
    val options: List<AskUserQuestionOption>,
    val multiSelect: Boolean,
)

@Immutable
internal data class AskPanelModel(
    val questions: List<AskPanelQuestion>,
    /**
     * Whether the pause resolves through the batched `answers` map. `questions` — not `question` —
     * selects the resume channel: upstream keeps `question` populated with the first item as a
     * display fallback even on a batch, so branching on it would submit a body the route rejects.
     */
    val isBatch: Boolean,
)

internal fun PendingAction.toAskPanelModel(): AskPanelModel? {
    if (!isAskUserQuestion) return null
    val payload = payload ?: return null
    // The batch is bounded server-side and a batch outside 1..MAX_QUESTIONS is rejected as
    // invalid, so render at most that many — and drop items with no usable id, which cannot be
    // answered at all.
    val batch = payload.questions
        ?.filter { it.isAnswerable }
        ?.take(AskUserQuestionLimits.MAX_QUESTIONS)
        .orEmpty()
    if (batch.isNotEmpty()) {
        return AskPanelModel(
            questions = batch.map {
                AskPanelQuestion(it.id, it.header, it.question, it.description, it.options, it.multiSelect)
            },
            isBatch = true,
        )
    }
    val single = payload.question ?: AskUserQuestionRequest()
    val id = actionId?.takeIf { it.isNotEmpty() } ?: return null
    return AskPanelModel(
        questions = listOf(
            AskPanelQuestion(id, null, single.question, single.description, single.options, single.multiSelect),
        ),
        isBatch = false,
    )
}

/**
 * A run paused on `ask_user_question` (v0.8.8 HITL), shown in the composer's place until it
 * resolves, one question at a time.
 *
 * Docked rather than in the thread so the user never has to scroll to find it, and in place of
 * the composer rather than above it because the run is waiting on this answer — a second input
 * beside it only competes with it.
 *
 * Two layouts, split at the same width as the comparison panes:
 * - **Compact** (phones, a folded foldable): the question heads the card with a `‹ 1 of 4 ›`
 *   pager, options are numbered rows, and a pick or a per-question Skip moves straight on — the
 *   answer that fills the last blank question submits the batch.
 * - **Wide** (tablets, unfolded): one tab per question with explicit Back / Next / Send.
 *
 * Both collapse to a single line so the reply above can be read.
 *
 * The submit is all-or-nothing because the server's is: `resolveAskUserQuestionResume` requires
 * an answers map covering EVERY id in the payload and rejects both a partial map and an unknown
 * id. Nothing goes up until every question has an answer (a skipped one carries the declined
 * sentinel), and a purely local dismiss would leave the run paused until it expires.
 *
 * Every piece of editor state is hoisted ([drafts], [activeQuestionId], [collapsed], owned by
 * `PendingActionDelegate`), so it survives the layout switching on a fold or rotation.
 */
@Composable
fun AskUserQuestionPanel(
    pendingAction: PendingAction,
    isResolving: Boolean,
    drafts: Map<String, AskAnswerDraft>,
    activeQuestionId: String?,
    collapsed: Boolean,
    onDraftChange: (String, AskAnswerDraft) -> Unit,
    onSelectQuestion: (String) -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onSubmitAnswer: (String) -> Unit,
    onSubmitAnswers: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Aborts the paused run. The panel takes the composer's place, and the composer's Stop is the
     * only other way out of a pause — without this, one the client cannot resolve at all (a
     * fingerprint 403 fails every answer and Skip alike) strands the user until it expires.
     */
    onStop: (() -> Unit)? = null,
) {
    val model = remember(pendingAction) { pendingAction.toAskPanelModel() } ?: return
    val questions = model.questions
    val answers = questions.associate { item ->
        item.id to composeAskAnswer(item.options, drafts[item.id] ?: AskAnswerDraft())
    }
    val activeIndex = questions.indexOfFirst { it.id == activeQuestionId }.coerceAtLeast(0)

    val submit: (Map<String, String>) -> Unit = { filled ->
        if (model.isBatch) onSubmitAnswers(filled) else onSubmitAnswer(filled.getValue(questions.first().id))
    }
    val state = AskPanelState(
        questions = questions,
        drafts = drafts,
        answers = answers,
        activeIndex = activeIndex,
        isResolving = isResolving,
        collapsed = collapsed,
    )
    val actions = AskPanelActions(
        onDraftChange = onDraftChange,
        onSelect = { index -> onSelectQuestion(questions[index].id) },
        onCollapsedChange = onCollapsedChange,
        onStop = onStop,
        submit = submit,
        skipAll = { submit(questions.associate { it.id to ASK_USER_DECLINED_ANSWER }) },
        advanceOrSubmit = { fromIndex, filled ->
            val next = nextBlankQuestionIndex(questions.map { it.id }, filled, fromIndex)
            if (next == null) submit(filled) else onSelectQuestion(questions[next].id)
        },
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Cap the question body so a long description or option list can't push the card off
        // screen; the header and the action row stay pinned around it.
        val bodyMaxHeight = if (maxHeight == Dp.Infinity) FALLBACK_BODY_MAX_HEIGHT else maxHeight * BODY_HEIGHT_FRACTION
        val isWide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            if (isWide) {
                WideAskLayout(state = state, actions = actions, bodyMaxHeight = bodyMaxHeight)
            } else {
                CompactAskLayout(state = state, actions = actions, bodyMaxHeight = bodyMaxHeight)
            }
        }
    }
}

@Immutable
private data class AskPanelState(
    val questions: List<AskPanelQuestion>,
    val drafts: Map<String, AskAnswerDraft>,
    val answers: Map<String, String>,
    val activeIndex: Int,
    val isResolving: Boolean,
    val collapsed: Boolean,
) {
    val active: AskPanelQuestion get() = questions[activeIndex]
    val activeDraft: AskAnswerDraft get() = drafts[active.id] ?: AskAnswerDraft()
    val isLast: Boolean get() = activeIndex == questions.lastIndex
    val answeredCount: Int get() = answers.values.count { it.isNotBlank() }
    val allAnswered: Boolean get() = answeredCount == questions.size
    fun isAnswered(id: String): Boolean = answers[id].orEmpty().isNotBlank()
}

private class AskPanelActions(
    val onDraftChange: (String, AskAnswerDraft) -> Unit,
    val onSelect: (Int) -> Unit,
    val onCollapsedChange: (Boolean) -> Unit,
    val onStop: (() -> Unit)?,
    val submit: (Map<String, String>) -> Unit,
    val skipAll: () -> Unit,
    val advanceOrSubmit: (fromIndex: Int, filled: Map<String, String>) -> Unit,
)

// ── compact (phone) ───────────────────────────────────────────────────────

@Composable
private fun CompactAskLayout(
    state: AskPanelState,
    actions: AskPanelActions,
    bodyMaxHeight: Dp,
) {
    val active = state.active
    val draft = state.activeDraft
    val coroutineScope = rememberCoroutineScope()
    val currentState by rememberUpdatedState(state)
    val currentActions by rememberUpdatedState(actions)
    // Per question, so each one opens scrolled to its first line.
    val questionScroll = key(active.id) { rememberScrollState() }

    Box {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = active.question,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (state.collapsed) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = bodyMaxHeight * HEADER_TO_BODY_HEIGHT_RATIO)
                        .verticalScroll(questionScroll)
                        .padding(vertical = 12.dp),
                )
                if (state.questions.size > 1) {
                    IconButton(
                        onClick = { actions.onSelect(state.activeIndex - 1) },
                        enabled = !state.collapsed && state.activeIndex > 0,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(Res.string.cd_previous_ask_user_question),
                        )
                    }
                    Text(
                        text = stringResource(
                            Res.string.ask_user_question_page_of,
                            state.activeIndex + 1,
                            state.questions.size,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
                        onClick = { actions.onSelect(state.activeIndex + 1) },
                        enabled = !state.collapsed && !state.isLast,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(Res.string.cd_next_ask_user_question),
                        )
                    }
                }
                actions.onStop?.let { StopRunButton(onStop = it) }
                CollapseToggle(collapsed = state.collapsed, onCollapsedChange = actions.onCollapsedChange)
            }

            if (state.collapsed) return@Column

            key(active.id) {
                Column(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .heightIn(max = bodyMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    active.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (active.multiSelect && active.options.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.ask_user_question_multi_select_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Column(modifier = if (active.multiSelect) Modifier else Modifier.selectableGroup()) {
                        active.options.forEachIndexed { index, option ->
                            val isSelected = option.value in draft.selectedOptions
                            CompactOptionRow(
                                number = index + 1,
                                label = option.label.ifBlank { option.value },
                                selected = isSelected,
                                multiSelect = active.multiSelect,
                                enabled = !state.isResolving,
                                onClick = {
                                    val wasAnswered = state.isAnswered(active.id)
                                    val next = toggledSelection(draft.selectedOptions, option.value, active.multiSelect)
                                    val updated = draft.copy(selectedOptions = next, skipped = false)
                                    actions.onDraftChange(active.id, updated)
                                    // A single-select pick is a complete answer: after a beat (so the
                                    // pick is seen landing) move on — or submit, when it filled the
                                    // last blank question. Re-picking an already answered question
                                    // never submits on its own; the user may still be reviewing.
                                    if (!active.multiSelect && !isSelected && !wasAnswered) {
                                        val from = active.id
                                        val picked = composeAskAnswer(active.options, updated)
                                        coroutineScope.launch {
                                            delay(AUTO_ADVANCE_DELAY_MS)
                                            // Decided on what is recorded NOW, not at the tap: a re-pick,
                                            // a deselect or a typed qualifier inside the beat means the
                                            // user is still editing, and the tap-time answers would submit
                                            // the pick they just replaced.
                                            val latest = currentState
                                            val stillPicked = latest.active.id == from && latest.answers[from] == picked
                                            if (stillPicked && !latest.isResolving) {
                                                currentActions.advanceOrSubmit(latest.activeIndex, latest.answers)
                                            }
                                        }
                                    }
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            CompactFreeTextRow(state = state, actions = actions)
        }
        if (state.collapsed) ExpandCollapsedOverlay(onExpand = { actions.onCollapsedChange(false) })
    }
}

@Composable
private fun CompactOptionRow(
    number: Int,
    label: String,
    selected: Boolean,
    multiSelect: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val rowModifier = if (multiSelect) {
        Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Inset from the dividers, rounded, and padded inside so the press/hover highlight
            // reads as a pill around the option rather than a slab flush to the card's edges.
            .padding(vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(rowModifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberBadge(selected = selected) {
            if (selected && multiSelect) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            } else {
                Text(text = number.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NumberBadge(selected: Boolean, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.primary else colors.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (selected) colors.onPrimary else colors.onSurfaceVariant,
        ) {
            content()
        }
    }
}

/**
 * The "Something else" row and the one action the compact card offers: Skip while the question
 * is blank, then Next — or Send once every question has an answer.
 */
@Composable
private fun CompactFreeTextRow(state: AskPanelState, actions: AskPanelActions) {
    val active = state.active
    val draft = state.activeDraft
    val answered = state.isAnswered(active.id)
    val proceed = { actions.advanceOrSubmit(state.activeIndex, state.answers) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberBadge(selected = false) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(modifier = Modifier.weight(1f)) {
            val colors = MaterialTheme.colorScheme
            BasicTextField(
                value = draft.freeText,
                onValueChange = { typed ->
                    actions.onDraftChange(
                        active.id,
                        draft.copy(
                            freeText = typed.take(askFreeTextBudget(active.options, draft.selectedOptions)),
                            skipped = false,
                        ),
                    )
                },
                enabled = !state.isResolving,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (answered) proceed() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.freeText.isEmpty()) {
                Text(
                    text = stringResource(
                        when {
                            draft.skipped -> Res.string.ask_user_question_skipped
                            active.options.isEmpty() -> Res.string.ask_user_question_answer_hint
                            else -> Res.string.ask_user_question_other_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (state.isResolving) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        when {
            !answered -> OutlinedButton(
                onClick = {
                    actions.onDraftChange(active.id, AskAnswerDraft(skipped = true))
                    actions.advanceOrSubmit(
                        state.activeIndex,
                        state.answers + (active.id to ASK_USER_DECLINED_ANSWER),
                    )
                },
                enabled = !state.isResolving,
            ) {
                Text(stringResource(Res.string.ask_user_question_skip))
            }
            state.allAnswered -> Button(onClick = proceed, enabled = !state.isResolving) {
                Text(stringResource(Res.string.ask_user_question_send))
            }
            else -> Button(onClick = proceed, enabled = !state.isResolving) {
                Text(stringResource(Res.string.ask_user_question_next))
            }
        }
    }
}

// ── wide (tablet) ─────────────────────────────────────────────────────────

@Composable
private fun WideAskLayout(
    state: AskPanelState,
    actions: AskPanelActions,
    bodyMaxHeight: Dp,
) {
    val questions = state.questions
    val active = state.active
    val coroutineScope = rememberCoroutineScope()
    val currentActiveId by rememberUpdatedState(active.id)
    val isTabbed = questions.size > 1

    Box {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isTabbed) {
                    AskQuestionTabs(
                        questions = questions,
                        activeIndex = state.activeIndex,
                        isAnswered = state::isAnswered,
                        onSelect = actions.onSelect,
                        enabled = !state.collapsed,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = active.question,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = if (state.collapsed) 1 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = bodyMaxHeight * HEADER_TO_BODY_HEIGHT_RATIO)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                actions.onStop?.let { StopRunButton(onStop = it) }
                CollapseToggle(collapsed = state.collapsed, onCollapsedChange = actions.onCollapsedChange)
            }

            if (state.collapsed) return@Column

            Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Keyed per tab so each question opens scrolled to its top.
                key(active.id) {
                    AskQuestionBody(
                        modifier = Modifier
                            .heightIn(max = bodyMaxHeight)
                            .verticalScroll(rememberScrollState()),
                        item = active,
                        showQuestion = isTabbed,
                        draft = state.activeDraft,
                        isResolving = state.isResolving,
                        onDraftChange = { draft -> actions.onDraftChange(active.id, draft) },
                        onPickedSingle = {
                            // A single-select pick is a complete answer, so move on to the next tab —
                            // after a beat, so the pick is seen landing.
                            if (!state.isLast) {
                                val from = active.id
                                coroutineScope.launch {
                                    delay(AUTO_ADVANCE_DELAY_MS)
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
                    TextButton(onClick = actions.skipAll, enabled = !state.isResolving) {
                        Text(
                            stringResource(
                                if (isTabbed) Res.string.ask_user_question_skip_all else Res.string.ask_user_question_skip,
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (state.isResolving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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
                        Button(
                            onClick = { actions.onSelect(state.activeIndex + 1) },
                            enabled = !state.isResolving,
                        ) {
                            Text(stringResource(Res.string.ask_user_question_next))
                        }
                    } else {
                        Button(
                            onClick = { actions.submit(state.answers) },
                            enabled = !state.isResolving && state.allAnswered,
                        ) {
                            Text(
                                if (isTabbed) {
                                    stringResource(
                                        Res.string.ask_user_question_send_progress,
                                        state.answeredCount,
                                        questions.size,
                                    )
                                } else {
                                    stringResource(Res.string.ask_user_question_send)
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

@Composable
private fun AskQuestionTabs(
    questions: List<AskPanelQuestion>,
    activeIndex: Int,
    isAnswered: (String) -> Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ScrollableTabRow(
        selectedTabIndex = activeIndex,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        divider = {},
        modifier = modifier,
    ) {
        questions.forEachIndexed { index, item ->
            Tab(
                selected = index == activeIndex,
                onClick = { onSelect(index) },
                enabled = enabled,
                // Rounds the hover/press highlight to match the rest of the app.
                modifier = Modifier.clip(MaterialTheme.shapes.medium),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isAnswered(item.id)) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(Res.string.cd_ask_user_question_answered),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = item.header?.takeIf { it.isNotBlank() }
                                ?: stringResource(Res.string.ask_user_question_tab_fallback, index + 1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = MAX_TAB_LABEL_WIDTH),
                        )
                    }
                },
            )
        }
    }
}

/** One question: prompt, description, numbered option rows and the free-text box. */
@Composable
private fun AskQuestionBody(
    item: AskPanelQuestion,
    showQuestion: Boolean,
    draft: AskAnswerDraft,
    isResolving: Boolean,
    onDraftChange: (AskAnswerDraft) -> Unit,
    onPickedSingle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = draft.selectedOptions
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showQuestion && item.question.isNotBlank()) {
            Text(text = item.question, style = MaterialTheme.typography.bodyLarge)
        }
        item.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (item.options.isNotEmpty()) {
            if (item.multiSelect) {
                Text(
                    text = stringResource(Res.string.ask_user_question_multi_select_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = if (item.multiSelect) Modifier else Modifier.selectableGroup()) {
                item.options.forEachIndexed { index, option ->
                    val isSelected = option.value in selected
                    val onToggle = {
                        val next = toggledSelection(selected, option.value, item.multiSelect)
                        onDraftChange(draft.copy(selectedOptions = next, skipped = false))
                        if (!item.multiSelect && !isSelected) onPickedSingle()
                    }
                    val rowModifier = if (item.multiSelect) {
                        Modifier.toggleable(
                            value = isSelected,
                            enabled = !isResolving,
                            role = Role.Checkbox,
                            onValueChange = { onToggle() },
                        )
                    } else {
                        Modifier.selectable(
                            selected = isSelected,
                            enabled = !isResolving,
                            role = Role.RadioButton,
                            onClick = onToggle,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .then(rowModifier)
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.multiSelect) {
                            Checkbox(checked = isSelected, onCheckedChange = null, enabled = !isResolving)
                        } else {
                            RadioButton(selected = isSelected, onClick = null, enabled = !isResolving)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${index + 1}. ${option.label.ifBlank { option.value }}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = draft.freeText,
            onValueChange = { typed ->
                onDraftChange(
                    draft.copy(freeText = typed.take(askFreeTextBudget(item.options, selected)), skipped = false),
                )
            },
            enabled = !isResolving,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    stringResource(
                        if (item.options.isEmpty()) {
                            Res.string.ask_user_question_answer_hint
                        } else {
                            Res.string.ask_user_question_other_hint
                        },
                    ),
                )
            },
            maxLines = 4,
        )
    }
}

/**
 * Laid over a collapsed panel so the whole card is one target that expands it. The controls
 * underneath are inert while collapsed (switching to a question you can't see is meaningless),
 * and a tap that lands on one should still open the panel rather than do nothing.
 */
@Composable
private fun BoxScope.ExpandCollapsedOverlay(onExpand: () -> Unit) {
    val restoreLabel = stringResource(Res.string.cd_restore_ask_user_question)
    Box(
        modifier = Modifier
            .matchParentSize()
            .clickable(onClickLabel = restoreLabel, role = Role.Button, onClick = onExpand)
            .semantics { contentDescription = restoreLabel },
    )
}

private fun toggledSelection(selected: List<String>, value: String, multiSelect: Boolean): List<String> = when {
    !multiSelect -> if (value in selected) emptyList() else listOf(value)
    value in selected -> selected - value
    else -> selected + value
}

/** Left enabled while a resume is in flight: aborting then is legitimate, and Stop guards its own re-entry. */
@Composable
private fun StopRunButton(onStop: () -> Unit) {
    IconButton(onClick = onStop) {
        Icon(
            imageVector = Icons.Default.Stop,
            contentDescription = stringResource(Res.string.cd_stop_generation),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CollapseToggle(collapsed: Boolean, onCollapsedChange: (Boolean) -> Unit) {
    IconButton(onClick = { onCollapsedChange(!collapsed) }) {
        Icon(
            imageVector = if (collapsed) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = stringResource(
                if (collapsed) Res.string.cd_restore_ask_user_question else Res.string.cd_minimize_ask_user_question,
            ),
        )
    }
}

/**
 * The one-line stand-in the thread keeps for a docked question pause, so the reply still reads
 * as waiting on the user.
 */
@Composable
fun AskUserQuestionWaitingMarker(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.HelpOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.ask_user_question_waiting_marker),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Same breakpoint the comparison panes use to go side by side. */
private val WIDE_LAYOUT_MIN_WIDTH = 600.dp
private const val BODY_HEIGHT_FRACTION = 0.45f

/**
 * The header question's cap, relative to the body's. Upstream allows a 2000-character question,
 * and the Column measures the answer controls into whatever height the header leaves — unbounded,
 * a long one squashes them to nothing.
 */
private const val HEADER_TO_BODY_HEIGHT_RATIO = 0.5f
private val FALLBACK_BODY_MAX_HEIGHT = 320.dp
private val MAX_TAB_LABEL_WIDTH = 140.dp
private const val AUTO_ADVANCE_DELAY_MS = 250L
