package com.garfiec.librechat.core.data.search

/**
 * A window of a message's text around its first match, with every term occurrence inside it
 * marked. Built off the main thread by the repository, so composition only maps [highlights]
 * onto spans.
 *
 * One highlighter serves local and server hits alike (SQL `snippet()` can't see server hits).
 * A match the index found by folding diacritics (`cafe` for `café`) has no literal occurrence,
 * so it gets no highlight and the snippet shows the text's start.
 */
data class HighlightedSnippet(
    val text: String,
    /** Ranges into [text], sorted and non-overlapping. */
    val highlights: List<IntRange>,
    val truncatedStart: Boolean,
    val truncatedEnd: Boolean,
) {
    companion object {
        const val DEFAULT_WINDOW: Int = 140

        /**
         * [terms] are matched case-insensitively the way the FTS index matched them: whole words,
         * except the last term while [lastIsPrefix] (a word start). [wordStartsOnly] is false for
         * scan hits, which match anywhere. [matchCase] and [wholeWord] follow the search's advanced
         * options, so a snippet marks only what passed them.
         */
        fun build(
            source: String,
            terms: List<String>,
            window: Int = DEFAULT_WINDOW,
            wordStartsOnly: Boolean = true,
            matchCase: Boolean = false,
            wholeWord: Boolean = false,
            lastIsPrefix: Boolean = terms.lastOrNull().orEmpty().length >= FtsQuery.MIN_PREFIX_CHARS,
        ): HighlightedSnippet {
            val text = source.replace(WHITESPACE_RUN, " ").trim()
            val needles = terms.filter { it.isNotBlank() }.distinct()
            val last = terms.lastOrNull()
            val rules = needles.map { needle ->
                val prefix = !wholeWord && (!wordStartsOnly || (lastIsPrefix && needle == last))
                needle to MatchRule(wordStarts = wordStartsOnly || wholeWord, wordEnds = !prefix, matchCase = matchCase)
            }
            val first = rules.mapNotNull { (needle, rule) -> firstOccurrence(text, needle, 0, rule) }.minOrNull()
            val start = when {
                first == null || first < window / 3 -> 0
                else -> backToWordStart(text, first - window / 3)
            }
            val end = minOf(text.length, start + window)
            val slice = text.substring(start, end)
            return HighlightedSnippet(
                text = slice,
                highlights = occurrences(slice, rules),
                truncatedStart = start > 0,
                truncatedEnd = end < text.length,
            )
        }

        /**
         * A snippet marking every match of [pattern], windowed on the first. For a pattern search's
         * preview, which shows more of a message than the one match the scan recorded. The pattern
         * must have passed [RegexGuard]; at most [MAX_PATTERN_MARKS] matches are marked.
         */
        fun buildPattern(source: String, pattern: Regex, window: Int = DEFAULT_WINDOW): HighlightedSnippet {
            val text = source.replace(WHITESPACE_RUN, " ").trim()
            val matches = pattern.findAll(text).filter { !it.range.isEmpty() }.take(MAX_PATTERN_MARKS).map { it.range }.toList()
            val first = matches.firstOrNull()?.first
            val start = when {
                first == null || first < window / 3 -> 0
                else -> backToWordStart(text, first - window / 3)
            }
            val end = minOf(text.length, start + window)
            val highlights = matches
                .filter { it.first >= start && it.last < end }
                .map { (it.first - start)..(it.last - start) }
            return HighlightedSnippet(
                text = text.substring(start, end),
                highlights = highlights,
                truncatedStart = start > 0,
                truncatedEnd = end < text.length,
            )
        }

        /** Every occurrence of [needles] in [text], merged into sorted, non-overlapping ranges. */
        internal fun occurrences(text: String, needles: List<String>, rule: MatchRule): List<IntRange> =
            occurrences(text, needles.map { it to rule })

        /** Every occurrence of each needle under its own rule, merged into sorted, non-overlapping ranges. */
        internal fun occurrences(text: String, needles: List<Pair<String, MatchRule>>): List<IntRange> {
            val found = needles.flatMap { (needle, rule) ->
                generateSequence(firstOccurrence(text, needle, 0, rule)) { previous ->
                    firstOccurrence(text, needle, previous + needle.length, rule)
                }.map { it until it + needle.length }.toList()
            }.sortedBy { it.first }
            val merged = ArrayList<IntRange>()
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

        private fun firstOccurrence(text: String, needle: String, from: Int, rule: MatchRule): Int? {
            var at = text.indexOf(needle, from, ignoreCase = !rule.matchCase)
            while (at >= 0) {
                val startOk = !rule.wordStarts || at == 0 || !text[at - 1].isLetterOrDigit()
                val end = at + needle.length
                val endOk = !rule.wordEnds || end == text.length || !text[end].isLetterOrDigit()
                if (startOk && endOk) return at
                at = text.indexOf(needle, at + 1, ignoreCase = !rule.matchCase)
            }
            return null
        }

        private fun backToWordStart(text: String, from: Int): Int {
            var at = from.coerceAtLeast(0)
            val limit = (from - MAX_WORD_BACKTRACK).coerceAtLeast(0)
            while (at > limit && !text[at - 1].isWhitespace()) at--
            return at
        }

        private const val MAX_WORD_BACKTRACK = 20
        private const val MAX_PATTERN_MARKS = 500
        private val WHITESPACE_RUN = Regex("\\s+")
    }
}

/** Where a needle may occur: at a word start, also ending at a word end, and in its exact case. */
internal data class MatchRule(
    val wordStarts: Boolean,
    val wordEnds: Boolean = false,
    val matchCase: Boolean = false,
)
