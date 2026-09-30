package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.AskUserQuestionLimits
import com.garfiec.librechat.core.model.AskUserQuestionOption
import com.garfiec.librechat.feature.chat.components.ASK_USER_DECLINED_ANSWER

/**
 * One question's in-progress answer inside an `ask_user_question` pause, keyed by the batch
 * item's id (or the action id for a single question).
 *
 * Held in `MessagesState.askAnswerDrafts`, not in the panel's own `remember` — the panel's editor
 * and the composer's send both write the same answer; see that field.
 *
 * Deliberately not saveable across process death — the pause itself is re-read from the server on
 * a cold open, so there is nothing for a restored draft to attach to.
 */
@Immutable
data class AskAnswerDraft(
    /** Option *values* picked from the question's chips, in the order the payload lists them. */
    val selectedOptions: List<String> = emptyList(),
    val freeText: String = "",
    /**
     * Declined individually (the compact layout's per-question Skip). Composes to upstream's
     * declined sentinel; any edit to the question clears it.
     */
    val skipped: Boolean = false,
)

/**
 * Folds a draft's chip selection and free-text box into the single string the resume route takes.
 * Multi-select joins the selected option VALUES with ", " (upstream's rule); free text is appended
 * so a user can qualify a chip rather than having to choose between the two inputs.
 */
internal fun composeAskAnswer(
    options: List<AskUserQuestionOption>,
    selected: Collection<String>,
    freeText: String,
): String {
    val chosen = options.map { it.value }.filter { it in selected }
    val typed = freeText.trim()
    return (if (typed.isEmpty()) chosen else chosen + typed)
        .joinToString(ASK_ANSWER_SEPARATOR)
        // The route rejects an over-length answer with 400 and the run stays paused, so an answer
        // that cannot be sent is worse than a shortened one. [askFreeTextBudget] keeps the box
        // inside the cap while typing; this closes the one gap it cannot — a chip selected after
        // the box was already filled to the budget computed without it.
        .take(AskUserQuestionLimits.MAX_ANSWER_LENGTH)
}

internal fun composeAskAnswer(options: List<AskUserQuestionOption>, draft: AskAnswerDraft): String =
    if (draft.skipped) {
        ASK_USER_DECLINED_ANSWER
    } else {
        composeAskAnswer(options, draft.selectedOptions, draft.freeText)
    }

/** Upstream's join for a multi-select answer, and for a chip qualified by free text. */
private const val ASK_ANSWER_SEPARATOR = ", "

/**
 * The index of the question after [fromIndex] that still has no answer, searching forward and
 * wrapping back round to [fromIndex] itself, or null when every question has one. Shared by the
 * panel's own advance and the composer's send so both walk a batch in the same order.
 */
internal fun nextBlankQuestionIndex(
    questionIds: List<String>,
    answers: Map<String, String>,
    fromIndex: Int,
): Int? = (1..questionIds.size)
    .map { (fromIndex + it) % questionIds.size }
    .firstOrNull { answers[questionIds[it]].isNullOrBlank() }

/**
 * How much free text an answer box may still hold without pushing the composed answer past the
 * server's `MAX_ASK_ANSWER_LENGTH`.
 *
 * Budgeted against the chips because they land in the same string. Conservative in the safe
 * direction: [composeAskAnswer] trims the typed half, which can only shorten it.
 */
internal fun askFreeTextBudget(
    options: List<AskUserQuestionOption>,
    selected: Collection<String>,
): Int {
    val chips = composeAskAnswer(options, selected, "").length
    val separator = if (chips == 0) 0 else ASK_ANSWER_SEPARATOR.length
    return (AskUserQuestionLimits.MAX_ANSWER_LENGTH - chips - separator).coerceAtLeast(0)
}
