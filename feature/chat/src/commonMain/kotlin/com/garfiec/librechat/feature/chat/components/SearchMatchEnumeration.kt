package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.data.mapper.LegacyThinking
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactSegment
import com.garfiec.librechat.feature.chat.components.artifact.detectArtifacts
import com.garfiec.librechat.feature.chat.util.activityLabelText
import com.garfiec.librechat.feature.chat.util.findLateBatchLabelsConsumedByPhase
import com.garfiec.librechat.feature.chat.util.isActivityPhaseLabel
import com.garfiec.librechat.feature.chat.util.steerText

// Shared search-occurrence enumeration used by BOTH the ViewModel side
// (InConversationSearchDelegate, to build the flat match list) and the renderer side
// (MessageContentAndActions / MarkdownContent, to resolve which on-screen unit owns the
// focused occurrence). A message's occurrences are numbered by walking it in exact render
// order; any divergence between the two sides makes prev/next focus the wrong occurrence,
// so renderers must consume offsets via these same functions.
//
// Render-order contract (mirrors MessageContentAndActions → ContentPartDispatcher):
//  1. Content parts in list order; only TEXT/TEXT_DELTA (part.text) and THINK (part.think)
//     render searchable text. Messages without parts render a legacy `:::thinking` block first
//     (as reasoning, see [legacyTextRuns]), then the rest of message.text as one TEXT part
//     (clause 2) when [textFallbackSplitsArtifacts], else via MarkdownContent directly.
//  2. TEXT parts split on artifact directives first (TextContentPart). Plain Text segments are
//     enumerated. Artifact segments depend on Artifact.isComplete:
//       - complete   -> contributes 0. Content is highlighted but not navigable, because whether it
//                       renders inline at all depends on a UI preference the ViewModel cannot see.
//       - incomplete -> contributes its content's occurrences. An incomplete artifact always renders
//                       its source via IncompleteArtifact -> CodeBlock, regardless of that
//                       preference, so it IS on screen and must be navigable. Counted flat (not via
//                       parseMarkdownSegments) because CodeBlock lays the content out as one text
//                       block — including for mermaid, which is not routed to a WebView here.
//  3. Within a text run, parseMarkdownSegments order. LatexBlock and mermaid CodeBlocks
//     render as native/WebView content with no text layout, so they contribute nothing.
//     Table occurrences are counted per cell (headers left-to-right, then rows
//     row-major) because highlight spans cannot cross cell boundaries.

/** Occurrences contributed by one parsed [MarkdownSegment], in its render order. */
internal fun countSegmentOccurrences(segment: MarkdownSegment, query: String): Int = when (segment) {
    // What the markdown displays, not its source (see SearchableMarkdown).
    is MarkdownSegment.TextBlock -> countDisplayedOccurrences(segment.text, query)
    is MarkdownSegment.CodeBlock ->
        if (segment.language?.lowercase() == "mermaid") 0 else countOccurrences(segment.code, query)
    is MarkdownSegment.LatexBlock -> 0
    is MarkdownSegment.InlineLatexText -> segment.segments.sumOf { inline ->
        when (inline) {
            is InlineSegment.Text -> countDisplayedOccurrences(inline.text, query)
            is InlineSegment.Latex -> 0
        }
    }
    is MarkdownSegment.Table -> tableCellTexts(segment).sumOf { countOccurrences(it, query) }
}

/** Table cell texts as displayed, in highlight order: headers left-to-right, then rows row-major. */
internal fun tableCellTexts(segment: MarkdownSegment.Table): List<String> =
    (segment.headers + segment.rows.flatten()).map(::tableCellPlainText)

/** Occurrences in one markdown text run (a whole part, or one artifact Text segment). */
internal fun countMarkdownOccurrences(text: String, query: String): Int {
    if (cannotMatch(text, query)) return 0
    return parseMarkdownSegments(text).sumOf { countSegmentOccurrences(it, query) }
}

/**
 * Fast-path bail before the (relatively expensive) parse, keeping per-keystroke search enumeration
 * cheap for the many messages that don't match. Every match outside a table holds one of the
 * query's words as a substring of [text] ([mayContain]); a table cell is counted as displayed,
 * with its markdown markers gone (`**CO** alarm` shows `CO alarm`), so text that might hold a
 * table is always parsed.
 */
private fun cannotMatch(text: String, query: String): Boolean =
    text.isBlank() || query.isBlank() || (!mayContain(text, query) && '|' !in text)

/**
 * Whether a message without content parts renders its `text` with the artifact split, as a TEXT part
 * would. Assistant replies saved before LibreChat v0.7.9 by the classic endpoints have no `content`,
 * so their artifacts live only in `text`; user text is never split, as on web.
 *
 * The renderer (`MessageContentAndActions`), [countMessageOccurrences] and the Artifacts tab
 * (`extractConversationArtifacts`) must all branch on this, or search prev/next drifts from what is
 * on screen and the tab lists artifacts the thread never draws.
 */
internal fun Message.textFallbackSplitsArtifacts(): Boolean = !isCreatedByUser

/**
 * Occurrences contributed by one artifact segment (see contract above). Shared by both walks —
 * the ViewModel-side enumeration here and the renderer-side offset table in `TextContentPart` — so
 * that the two can never disagree about how many occurrences an artifact owns.
 */
internal fun countArtifactOccurrences(artifact: Artifact, query: String): Int =
    if (artifact.isComplete) 0 else countOccurrences(artifact.content, query)

/** Occurrences in a TEXT part's body, honoring the artifact split (see contract above). */
internal fun countTextPartOccurrences(text: String, query: String): Int {
    // Same fast-path bail: skip detectArtifacts + parsing when the query can't be here.
    if (cannotMatch(text, query)) return 0
    return detectArtifacts(text).sumOf { segment ->
        when (segment) {
            is ArtifactSegment.Text -> countMarkdownOccurrences(segment.text, query)
            is ArtifactSegment.ArtifactReference -> countArtifactOccurrences(segment.artifact, query)
        }
    }
}

/**
 * Occurrences contributed by one content part, in its render order.
 *
 * @param consumedLateBatchLabel true when this part's index is in
 *   [findLateBatchLabelsConsumedByPhase] for its message — such a label is not drawn, so it must
 *   not be counted either.
 */
internal fun countPartOccurrences(
    part: MessageContentPart,
    query: String,
    consumedLateBatchLabel: Boolean = false,
): Int = when (part.type) {
    ContentType.TEXT, ContentType.TEXT_DELTA -> countTextPartOccurrences(part.text.orEmpty(), query)
    ContentType.THINK -> countMarkdownOccurrences(part.think.orEmpty(), query)
    // The user's own mid-run steer renders as a turn inside the response, so it has to be
    // findable — it is the one thing in a reply they definitely wrote.
    ContentType.STEER -> countMarkdownOccurrences(part.steerText().orEmpty(), query)
    // Only a label that renders counts: a blank reservation is invisible, an orphan label is a
    // bare line, and a parent PHASE label is dropped by the grouping pass entirely. A group header
    // the search can reach is also how a collapsed group opens.
    //
    // This walk and the render walk must stay lockstep — a part counted here but not drawn shifts
    // every later match's index, so the focused result lands on the wrong text. Whatever
    // `groupContentParts` skips has to be skipped here in the same commit.
    ContentType.ACTIVITY_LABEL ->
        if (part.isActivityPhaseLabel() || consumedLateBatchLabel) {
            0
        } else {
            countMarkdownOccurrences(part.activityLabelText(), query)
        }
    else -> 0
}

/**
 * Total searchable occurrences in a message, numbering the render walk 0-based.
 *
 * Two kinds of message are deliberately left out and contribute nothing:
 * - a persisted failed turn ([isPersistedTurnError]) renders through the error part, which draws
 *   no search highlight, so a match there is one the jump cannot land on. Its text can still be on
 *   screen: an unclassified error shows the saved text as its headline, and an
 *   `upstream_model_error` shows the provider's message under it;
 * - a wake-up turn ([systemEventFor]) renders as a system row. Collapsed, it shows none of its
 *   stored text; expanded, it does show each task's result, but that text is still not searched.
 *   A match has to be reachable whatever state the row is in, a jump cannot expand a collapsed row
 *   to reach one, and the rest of the stored text (the header sentence and the JSON payload) is
 *   never drawn at all.
 * Keep these two checks in step with the branches `MessageList` and `MessageContentAndActions` take.
 */
internal fun countMessageOccurrences(message: Message, query: String): Int {
    if (isPersistedTurnError(message) || systemEventFor(message, isEditing = false) != null) return 0
    val parts = message.content
    return if (!parts.isNullOrEmpty()) {
        val consumed = findLateBatchLabelsConsumedByPhase(parts)
        parts.withIndex().sumOf { (index, part) ->
            countPartOccurrences(part, query, consumedLateBatchLabel = index in consumed)
        }
    } else {
        val runs = message.legacyTextRuns()
        countMarkdownOccurrences(runs.reasoning.orEmpty(), query) + countAnswerOccurrences(message, runs.answer, query)
    }
}

/** A text-only message's answer: split for artifacts or not, as [MessageContentAndActions] renders it. */
private fun countAnswerOccurrences(message: Message, answer: String, query: String): Int =
    if (message.textFallbackSplitsArtifacts()) countTextPartOccurrences(answer, query) else countMarkdownOccurrences(answer, query)

/**
 * For each of [message]'s occurrences of [query], in [countMessageOccurrences]' numbering, whether it
 * is in the message's reasoning (a THINK part, or a legacy `:::thinking` block) rather than its reply.
 */
internal fun occurrencesInReasoning(message: Message, query: String): List<Boolean> {
    if (isPersistedTurnError(message) || systemEventFor(message, isEditing = false) != null) return emptyList()
    val parts = message.content
    return if (!parts.isNullOrEmpty()) {
        val consumed = findLateBatchLabelsConsumedByPhase(parts)
        parts.withIndex().flatMap { (index, part) ->
            val count = countPartOccurrences(part, query, consumedLateBatchLabel = index in consumed)
            List(count) { part.type == ContentType.THINK }
        }
    } else {
        val runs = message.legacyTextRuns()
        List(countMarkdownOccurrences(runs.reasoning.orEmpty(), query)) { true } +
            List(countAnswerOccurrences(message, runs.answer, query)) { false }
    }
}

/** A text-only message's legacy reasoning block (null when it has none) and the reply around it. */
internal class LegacyTextRuns(val reasoning: String?, val answer: String)

/**
 * A text-only message split the way the web client renders it (`parseThinkingContent`): a
 * `:::thinking … :::` block is reasoning, drawn first as a reasoning block, and the rest is the
 * reply. Messages saved before reasoning had its own content part carry it this way. The renderer,
 * [countMessageOccurrences] and the Artifacts tab all read this one split.
 */
internal fun Message.legacyTextRuns(): LegacyTextRuns {
    val split = LegacyThinking.split(text) ?: return LegacyTextRuns(reasoning = null, answer = text)
    return LegacyTextRuns(reasoning = split.reasoning.ifBlank { null }, answer = split.answer)
}

/**
 * Character range of the [occurrence]-th (0-based) match of [query] in [text], or null when out of
 * range. The same walk as [buildHighlightedString] ([occurrenceRanges]), so the orange span and the
 * reported rect agree.
 */
internal fun findOccurrenceRange(text: String, query: String, occurrence: Int): IntRange? {
    if (occurrence < 0 || query.isBlank() || text.isEmpty()) return null
    return occurrenceRanges(text, query).getOrNull(occurrence)
}
