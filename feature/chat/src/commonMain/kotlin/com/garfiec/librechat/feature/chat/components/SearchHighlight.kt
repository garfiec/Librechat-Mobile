package com.garfiec.librechat.feature.chat.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.garfiec.librechat.core.ui.theme.searchFocusedMatchColor
import com.garfiec.librechat.core.ui.theme.searchMatchColor
import kotlin.math.max
import kotlin.math.min

/**
 * Background color for all search matches (non-focused), adapted to theme.
 */
val SearchHighlightYellow: Color
    @Composable get() = searchMatchColor(isSurfaceDark())

/**
 * Background color for the currently focused search match, adapted to theme.
 */
val SearchHighlightOrange: Color
    @Composable get() = searchFocusedMatchColor(isSurfaceDark())

/**
 * Returns the non-focused search highlight color for the given theme.
 */
fun searchHighlightYellow(isDarkTheme: Boolean): Color =
    searchMatchColor(isDarkTheme)

/**
 * Returns the focused search highlight color for the given theme.
 */
fun searchHighlightOrange(isDarkTheme: Boolean): Color =
    searchFocusedMatchColor(isDarkTheme)

/**
 * Builds an [AnnotatedString] from [text] with background spans applied to every match of
 * [query] (see [occurrenceRanges]).
 *
 * All matches get a yellow background. If [focusedOccurrence] >= 0,
 * that specific occurrence (0-based within this text) gets an orange background
 * instead to indicate the currently focused search result.
 *
 * @param isDarkTheme When true, uses muted dark-mode highlight colors that maintain
 *   contrast with light text. When false, uses bright highlight colors for light mode.
 */
fun buildHighlightedString(
    text: String,
    query: String,
    focusedOccurrence: Int = -1,
    isDarkTheme: Boolean = false,
): AnnotatedString = addSearchSpans(AnnotatedString(text), query, focusedOccurrence, isDarkTheme)

/**
 * Overlays search-highlight background spans onto an existing [AnnotatedString],
 * preserving its current spans (e.g. code-block syntax coloring). Same yellow/orange
 * semantics as [buildHighlightedString].
 */
fun addSearchSpans(
    base: AnnotatedString,
    query: String,
    focusedOccurrence: Int = -1,
    isDarkTheme: Boolean = false,
): AnnotatedString {
    if (query.isBlank()) return base

    val highlightColor = searchHighlightYellow(isDarkTheme)
    val focusedColor = searchHighlightOrange(isDarkTheme)

    val builder = AnnotatedString.Builder(base)
    occurrenceRanges(base.text, query).forEachIndexed { occurrence, range ->
        val bgColor = if (occurrence == focusedOccurrence) focusedColor else highlightColor
        builder.addStyle(SpanStyle(background = bgColor), start = range.first, end = range.last + 1)
    }
    return builder.toAnnotatedString()
}

/**
 * Every match of a find [query] in [text], sorted and merged where they overlap: the one walk the
 * count, the spans and the focused rect all number matches by.
 *
 * A query is its words, each found on its own and case-insensitively: a word of
 * [MIN_ANYWHERE_CHARS] or more characters anywhere, a shorter one only as a whole word. That is how
 * the message index takes a short word, so a jump from a search for `so` lands on "so", not on the
 * start of "SoftEther", and a typed `so` doesn't light up every "some".
 *
 * A query in double quotes is one phrase, spaces included: how a regex jump carries the text its
 * pattern matched (`"SoftEther VPN"`), which split into words would also match every "VPN".
 *
 * Matching runs on the original string, not a lowercased copy: some characters change length when
 * lowercased (Turkish 'İ' → "i̇"), which would shift every later offset and misplace spans and rects.
 */
fun occurrenceRanges(text: String, query: String): List<IntRange> {
    if (text.isEmpty()) return emptyList()
    val words = findWords(query)
    if (words.isEmpty()) return emptyList()
    val found = ArrayList<IntRange>()
    for (word in words) {
        val wholeWord = word.length < MIN_ANYWHERE_CHARS
        var at = text.indexOf(word, 0, ignoreCase = true)
        while (at >= 0) {
            val end = at + word.length
            val bounded = !wholeWord ||
                ((at == 0 || !text[at - 1].isLetterOrDigit()) && (end == text.length || !text[end].isLetterOrDigit()))
            if (bounded) found += at until end
            at = text.indexOf(word, if (bounded) end else at + 1, ignoreCase = true)
        }
    }
    found.sortBy { it.first }
    val merged = ArrayList<IntRange>(found.size)
    for (range in found) {
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
    }
    return merged
}

/** Whether any word of [query] occurs in [text] at all: a cheap check before parsing [text]. */
internal fun mayContain(text: String, query: String): Boolean =
    findWords(query).any { text.contains(it, ignoreCase = true) }

private fun findWords(query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.length > 2 && trimmed.first() == '"' && trimmed.last() == '"') {
        return listOf(trimmed.substring(1, trimmed.length - 1)).filter { it.isNotBlank() }
    }
    return trimmed.split(FIND_WHITESPACE).filter { it.isNotEmpty() }.distinct()
}

/** Words this long match anywhere; shorter ones only as whole words, as the message index takes them. */
private const val MIN_ANYWHERE_CHARS = 3
private val FIND_WHITESPACE = Regex("\\s+")

/**
 * Reports the focused search match's position for scroll targeting. When both
 * [onFocusedMatchPosition] and [focusedRange] are non-null, every layout/position pass reports
 * the reporting node's [LayoutCoordinates] plus the match's bounding [Rect] within it (computed
 * from [layout] via [matchBoundingRect]); a no-op otherwise. Shared by every search-highlightable
 * [Text] (plain segments, code blocks, table cells) so the wiring lives in exactly one place.
 */
internal fun Modifier.reportFocusedMatchPosition(
    layout: TextLayoutResult?,
    focusedRange: IntRange?,
    onFocusedMatchPosition: ((LayoutCoordinates, Rect) -> Unit)?,
): Modifier = if (onFocusedMatchPosition != null && focusedRange != null) {
    onGloballyPositioned { coordinates ->
        val laidOut = layout ?: return@onGloballyPositioned
        onFocusedMatchPosition(coordinates, matchBoundingRect(laidOut, focusedRange))
    }
} else {
    this
}

/**
 * Bounding rect of [range] within a laid-out text, in the text node's local coordinates.
 * For matches that wrap lines, spans from the first character's top to the last
 * character's bottom (the full-width union is irrelevant for vertical scrolling).
 */
internal fun matchBoundingRect(layout: TextLayoutResult, range: IntRange): Rect {
    val lastOffset = layout.layoutInput.text.length - 1
    if (lastOffset < 0) return Rect.Zero
    val start = range.first.coerceIn(0, lastOffset)
    val end = range.last.coerceIn(start, lastOffset)
    val startBox = layout.getBoundingBox(start)
    val endBox = layout.getBoundingBox(end)
    return Rect(
        left = min(startBox.left, endBox.left),
        top = min(startBox.top, endBox.top),
        right = max(startBox.right, endBox.right),
        bottom = max(startBox.bottom, endBox.bottom),
    )
}

/**
 * Scales both fontSize and lineHeight of a TextStyle by the given multiplier.
 * Uses explicit .sp conversion to avoid TextUnit.Unspecified edge cases.
 */
internal fun TextStyle.scaleFontSize(multiplier: Float): TextStyle {
    if (multiplier == 1.0f) return this
    val scaledFontSize = if (fontSize.isSpecified) (fontSize.value * multiplier).sp else fontSize
    val scaledLineHeight = if (lineHeight.isSpecified) (lineHeight.value * multiplier).sp else lineHeight
    return copy(fontSize = scaledFontSize, lineHeight = scaledLineHeight)
}

/** The number of matches of [query] in [text] (see [occurrenceRanges]). */
fun countOccurrences(text: String, query: String): Int {
    if (query.isBlank() || text.isBlank()) return 0
    return occurrenceRanges(text, query).size
}
