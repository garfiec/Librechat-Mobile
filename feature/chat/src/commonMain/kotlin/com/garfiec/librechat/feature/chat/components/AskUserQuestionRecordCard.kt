package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.AskUserQuestionRequest
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * The durable record of an `ask_user_question` exchange — what was asked and what the user said —
 * shown in place of the generic tool card for that call.
 *
 * The generic card is wrong for this tool twice over: it labels the call by its internal name and
 * hides the exchange behind a JSON dump of `{question, options…}` and a raw answer string, and
 * while the run is paused it renders a spinner for a "call" that is really waiting on the user.
 * Everything rendered here is already on the wire — the question, its description and options come
 * from the call's own arguments, the answer from its output — so no extra request or client-side
 * bookkeeping stands behind it.
 *
 * Collapsed it reads as a two-line exchange (question, answer). Expanded it shows the full
 * question, the reason the agent gave for asking, and which of the offered options the answer
 * picked. See [askAnswerDisplay] for why an answer is shown by option *label* only when every
 * segment of it maps.
 */
@Composable
internal fun AskUserQuestionRecordCard(
    question: AskUserQuestionRequest?,
    answer: String,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
    header: String? = null,
) {
    val display = remember(question, answer) { askAnswerDisplay(question, answer) }
    val answered = answer.isNotBlank() && !failed
    val description = question?.description?.takeIf { it.isNotBlank() }
    val options = question?.options.orEmpty()

    var isExpanded by remember { mutableStateOf(false) }
    val hasExpandedSection = description != null || options.isNotEmpty()

    val headline = when {
        failed -> stringResource(Res.string.ask_user_question_record_failed)
        answered -> stringResource(Res.string.ask_user_question_record_asked)
        else -> stringResource(Res.string.ask_user_question_record_asking)
    }
    // Null when the call carries no arguments — a question the run abandoned before it streamed
    // any. The headline already names what this card is, so nothing stands in for the question:
    // repeating the headline underneath itself reads as a question the agent literally asked.
    val questionText = question?.question?.takeIf { it.isNotBlank() }
    val answerText = if (answered && !display.declined) answerText(display.label) else null
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val questionStyle = MaterialTheme.typography.bodyMedium
    val answerStyle = MaterialTheme.typography.bodySmall

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(8.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Whether the collapsed question or answer is cut short, measured with the collapsed
            // maxLines at the text column's width without a chevron: a line that fits there gets no
            // chevron and keeps that width, and one that doesn't still doesn't once the chevron narrows it.
            // Only consulted when there is no section to expand, which always offers the chevron.
            val truncated = !hasExpandedSection && constraints.hasBoundedWidth && run {
                val textWidth = with(density) {
                    constraints.maxWidth - 2 * ROW_PADDING.roundToPx() - ICON_SIZE.roundToPx() - ICON_GAP.roundToPx()
                }.coerceAtLeast(0)
                fun overflows(text: AnnotatedString, style: TextStyle, maxLines: Int) = textMeasurer.measure(
                    text = text,
                    style = style,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = maxLines,
                    constraints = Constraints(maxWidth = textWidth),
                ).hasVisualOverflow
                (questionText != null && overflows(AnnotatedString(questionText), questionStyle, COLLAPSED_QUESTION_LINES)) ||
                    (answerText != null && overflows(answerText, answerStyle, 1))
            }
            // Expandable only when expanding reveals something the collapsed rows cannot: the agent's
            // reason for asking, the options it offered, or a question or answer the collapsed rows cut.
            val hasDetail = hasExpandedSection || truncated
            Column {
                val toggleCd = stringResource(
                    if (isExpanded) Res.string.cd_collapse_ask_user_question else Res.string.cd_expand_ask_user_question,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (hasDetail) {
                                Modifier
                                    .clickable { isExpanded = !isExpanded }
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = toggleCd
                                    }
                            } else {
                                Modifier
                            },
                        )
                        .padding(ROW_PADDING),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = if (failed) Icons.Default.WarningAmber else Icons.Default.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(ICON_SIZE),
                        tint = if (failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(modifier = Modifier.width(ICON_GAP))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (failed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        header?.takeIf { it.isNotBlank() }?.let { heading ->
                            Text(
                                text = heading,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (questionText != null) {
                            Text(
                                text = questionText,
                                style = questionStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (isExpanded) Int.MAX_VALUE else COLLAPSED_QUESTION_LINES,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        AnswerLine(
                            display = display,
                            answerText = answerText,
                            style = answerStyle,
                            failed = failed,
                            isExpanded = isExpanded,
                        )
                    }
                    if (hasDetail) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(
                    // A card whose only detail is a cut line just lifts maxLines; it has no section to show.
                    visible = isExpanded && hasExpandedSection,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier.padding(start = 38.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (description != null) {
                            AdaptiveDivider()
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (options.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    if (question?.multiSelect == true) {
                                        Res.string.ask_user_question_record_options_multi
                                    } else {
                                        Res.string.ask_user_question_record_options
                                    },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            options.forEach { option ->
                                OptionRow(
                                    label = option.label.ifBlank { option.value },
                                    picked = option.value in display.selectedValues,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The durable record of a *batched* `ask_user_question` — one row per question the agent asked in
 * that call, each with the answer stamped against its own id.
 *
 * The single-question record cannot stand in for this. A batch's arguments carry no top-level
 * `question` and its output is a `{"answers": {…}}` map rather than a sentence, so that card
 * renders an unlabeled exchange whose answer is the raw JSON — on every reload, permanently.
 *
 * A question the answers map does not name reads as unanswered rather than borrowing a neighbour's
 * words, matching upstream `AskUserQuestionCall`: the two are joined by id, and a batch resolved
 * through any client covers every id or is not accepted at all.
 */
@Composable
internal fun AskUserQuestionBatchRecordCard(
    questions: List<AskUserQuestionEntry>,
    answers: Map<String, String>,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        questions.forEach { entry ->
            AskUserQuestionRecordCard(
                question = entry.question,
                answer = answers[entry.id].orEmpty(),
                failed = failed,
                header = entry.header,
            )
        }
    }
}

/**
 * The answer, or why there isn't one. The label and the answer are one text, clamped to one line
 * while collapsed; an answer that line cuts gives the card a chevron that shows it in full.
 */
// Every branch below returns after emitting exactly one thing; the rule cannot see through
// guard clauses, and giving it a single root would add a layout node per answered question.
@Suppress("MultipleEmitters")
@Composable
private fun AnswerLine(
    display: AskAnswerDisplay,
    answerText: AnnotatedString?,
    style: TextStyle,
    failed: Boolean,
    isExpanded: Boolean,
) {
    if (failed) {
        Text(
            text = stringResource(Res.string.ask_user_question_record_failed_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (answerText == null) {
        Text(
            text = stringResource(
                if (display.declined) {
                    Res.string.ask_user_question_record_skipped
                } else {
                    Res.string.ask_user_question_record_unanswered
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        text = answerText,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "Answer:" in the label colour, then the answer, as the one text [AnswerLine] draws and the card measures. */
@Composable
private fun answerText(answer: String): AnnotatedString {
    val label = stringResource(Res.string.ask_user_question_record_answer_label)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(label, labelColor, answer) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = labelColor)) { append(label) }
            append(' ')
            append(answer)
        }
    }
}

/** One offered option, marked when the answer picked it. */
@Composable
private fun OptionRow(label: String, picked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (picked) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (picked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (picked) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private const val COLLAPSED_QUESTION_LINES = 2

// The header row's fixed parts beside the text column, shared with the truncation measurement.
private val ROW_PADDING = 12.dp
private val ICON_SIZE = 18.dp
private val ICON_GAP = 8.dp
