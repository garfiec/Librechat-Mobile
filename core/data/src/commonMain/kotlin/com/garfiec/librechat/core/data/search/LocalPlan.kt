package com.garfiec.librechat.core.data.search

/** How the local half of a search runs, from the query and its options. */
internal sealed interface LocalPlan {
    data object Empty : LocalPlan

    /** The regex can't run; nothing is searched. */
    data class Rejected(val problem: RegexProblem) : LocalPlan

    /** The FTS index, then (with case or whole-word set) a re-check of each candidate. */
    data class Fts(val match: FtsQueryPlan.Match) : LocalPlan

    /** The scan engine, as a substring (scripts the tokenizer can't split). */
    data class Literal(val text: String) : LocalPlan

    /** The scan engine, as a pattern. */
    data class Regex(val regex: kotlin.text.Regex) : LocalPlan

    companion object {
        fun of(query: String, options: SearchOptions): LocalPlan {
            if (options.regex) {
                val pattern = query.trim()
                if (pattern.isEmpty()) return Empty
                return when (val check = RegexGuard.compile(pattern, options.matchCase)) {
                    is RegexCheck.Ok -> Regex(check.regex)
                    is RegexCheck.Rejected -> Rejected(check.problem)
                }
            }
            // A whole-word search must not widen its last word to a prefix.
            return when (val plan = FtsQuery.plan(query, prefixLastToken = !options.wholeWord)) {
                is FtsQueryPlan.Match -> Fts(plan)
                is FtsQueryPlan.Scan -> Literal(plan.literal)
                FtsQueryPlan.Empty -> Empty
            }
        }
    }
}

/**
 * Re-checks a candidate's text against the typed terms under match-case and whole-word rules. The
 * index matched case-insensitively at word starts, so a candidate can fail either rule. Every term
 * must pass, as every term had to match.
 */
internal object TextVerifier {

    /** Null when neither rule is on: every candidate the index found already qualifies. */
    fun of(terms: List<String>, options: SearchOptions): ((String) -> Boolean)? {
        if (!options.matchCase && !options.wholeWord) return null
        val regexOptions = if (options.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val patterns = terms.filter { it.isNotBlank() }.map { term ->
            val literal = Regex.escape(term)
            Regex(if (options.wholeWord) "(?<![\\p{L}\\p{N}])$literal(?![\\p{L}\\p{N}])" else literal, regexOptions)
        }
        return { text -> patterns.all { it.containsMatchIn(text) } }
    }
}
