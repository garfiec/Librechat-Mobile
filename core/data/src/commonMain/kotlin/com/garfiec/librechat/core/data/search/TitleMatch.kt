package com.garfiec.librechat.core.data.search

/**
 * Whether a text holds a typed query the way the message index would match a message, so a chat
 * titled "Flow in Kotlin" turns up for `kotlin flow` in both sections.
 *
 * Every term must be a whole word of the text, except the last one while it's being typed, which
 * may start a word (the index's prefix query); case-insensitively unless the search matches case,
 * and never as a prefix under whole word. Text the index can't split into words is matched as a
 * substring.
 *
 * The index also folds diacritics, which this doesn't, so `cafe` won't match `Café`. For a server
 * hit that's a deliberate asymmetry: the server's engine also matches typos and partial words,
 * and this check is what holds its hits to the local rule, so a folded match is lost with them.
 */
object TermMatch {

    /** The ranges of [text] to highlight when it matches [query], or null when it doesn't. */
    fun of(text: String?, query: String, options: SearchOptions = SearchOptions.Default): List<IntRange>? {
        if (text.isNullOrBlank()) return null
        return when (val plan = FtsQuery.plan(query, prefixLastToken = !options.wholeWord)) {
            FtsQueryPlan.Empty -> null
            is FtsQueryPlan.Scan -> HighlightedSnippet.occurrences(
                text,
                listOf(plan.literal),
                MatchRule(wordStarts = false, matchCase = options.matchCase),
            ).ifEmpty { null }
            is FtsQueryPlan.Match -> {
                val found = plan.terms.withIndex().all { (index, term) ->
                    val asPrefix = plan.typingLast && index == plan.terms.lastIndex
                    HighlightedSnippet.occurrences(text, listOf(term), ruleFor(asPrefix, options)).isNotEmpty()
                }
                if (found) {
                    // Each word marked under the rule it matched by: whole words, the last one typed a prefix.
                    val rules = plan.terms.withIndex().map { (index, term) ->
                        term to ruleFor(asPrefix = plan.typingLast && index == plan.terms.lastIndex, options)
                    }
                    HighlightedSnippet.occurrences(text, rules)
                } else {
                    null
                }
            }
        }
    }

    fun matches(text: String?, query: String, options: SearchOptions = SearchOptions.Default): Boolean =
        of(text, query, options) != null

    private fun ruleFor(asPrefix: Boolean, options: SearchOptions) =
        MatchRule(wordStarts = true, wordEnds = !asPrefix, matchCase = options.matchCase)
}

/**
 * Whether a conversation title matches a typed query: [TermMatch] for words, the pattern itself
 * for a regex search (titles are short, so a pattern [RegexGuard] passed is cheap to run on them).
 *
 * This is also the only way to tell a title hit from the server's conversation search, which
 * returns every conversation whose title **or any message** matched (`getConvosByCursor`), with
 * nothing to say which.
 */
object TitleMatch {

    /** The ranges to highlight when [title] matches [query], or null when it doesn't. */
    fun of(title: String?, query: String, options: SearchOptions = SearchOptions.Default): List<IntRange>? {
        if (title.isNullOrBlank()) return null
        if (!options.regex) return TermMatch.of(title, query, options)
        val pattern = query.trim()
        if (pattern.isEmpty()) return null
        val regex = compiled(pattern, options.matchCase) ?: return null
        return regex.findAll(title.take(MAX_TITLE_CHARS))
            .map { it.range }
            .filterNot { it.isEmpty() }
            .toList()
            .ifEmpty { null }
    }

    fun matches(title: String?, query: String, options: SearchOptions = SearchOptions.Default): Boolean =
        of(title, query, options) != null

    /**
     * The last pattern compiled: one search checks it against every cached title, so it's
     * compiled once rather than once per title. Replaced whole, so a racing reader sees one pair.
     */
    private var lastCompiled: Triple<String, Boolean, Regex?>? = null

    private fun compiled(pattern: String, matchCase: Boolean): Regex? {
        lastCompiled?.let { (cachedPattern, cachedCase, regex) ->
            if (cachedPattern == pattern && cachedCase == matchCase) return regex
        }
        val regex = (RegexGuard.compile(pattern, matchCase) as? RegexCheck.Ok)?.regex
        lastCompiled = Triple(pattern, matchCase, regex)
        return regex
    }

    /** Server titles are capped at 100 characters, but a cached one could be anything. */
    private const val MAX_TITLE_CHARS = 500
}
