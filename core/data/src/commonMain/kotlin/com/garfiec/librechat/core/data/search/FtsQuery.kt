package com.garfiec.librechat.core.data.search

/** How a typed query is answered locally. */
sealed interface FtsQueryPlan {
    /** Too short to search. */
    data object Empty : FtsQueryPlan

    /**
     * An FTS4 MATCH expression built only from quoted tokens, so no user input reaches the query
     * parser as an operator. [terms] are the tokens as typed, for highlighting.
     */
    data class Match(
        val expression: String,
        val terms: List<String>,
        /**
         * The last term is still being typed (the input doesn't end in whitespace), whether or not
         * [expression] made it a prefix: titles, matched in memory, take it as a prefix at any length.
         */
        val typingLast: Boolean = false,
        /** [expression] made the last term a prefix; every other term matches whole words only. */
        val lastIsPrefix: Boolean = false,
    ) : FtsQueryPlan

    /**
     * Text the tokenizer can't split into words (Chinese, Japanese, Korean, Thai and their
     * neighbours: `unicode61` keeps a whole run of those characters as one token, so a word inside
     * it can't be matched). Answered by the bounded scan engine as a case-insensitive substring.
     */
    data class Scan(val literal: String) : FtsQueryPlan
}

/**
 * Turns what the user typed into an [FtsQueryPlan].
 *
 * - Tokens are split on whitespace, stripped of `"` and `*`, and each one is double-quoted, which
 *   makes FTS read it as a phrase and never as `AND`/`OR`/`NOT`/`NEAR`, a column filter or a
 *   syntax error. Tokens are implicitly ANDed.
 * - A token with no letter or digit is dropped: it would quote to a phrase with no words in it.
 * - The last token becomes a prefix (`"kot*"`) while the user is still typing it, meaning the
 *   input doesn't end in whitespace, and only from 3 characters: a 1–2 character prefix expands
 *   to most of the index's vocabulary, which costs more than it finds.
 */
object FtsQuery {

    const val MIN_QUERY_CHARS: Int = 2
    const val MIN_PREFIX_CHARS: Int = 3

    fun plan(input: String, prefixLastToken: Boolean = true): FtsQueryPlan {
        val trimmed = input.trim()
        if (trimmed.any(::isUnsegmentedScript)) {
            return if (trimmed.isEmpty()) FtsQueryPlan.Empty else FtsQueryPlan.Scan(trimmed)
        }
        if (trimmed.length < MIN_QUERY_CHARS) return FtsQueryPlan.Empty
        val tokens = trimmed.split(WHITESPACE)
            .map { token -> token.filterNot { it == '"' || it == '*' } }
            .filter { token -> token.any { it.isLetterOrDigit() } }
        if (tokens.isEmpty()) return FtsQueryPlan.Empty
        val typingLast = prefixLastToken && !input.last().isWhitespace()
        val lastIsPrefix = typingLast && tokens.last().length >= MIN_PREFIX_CHARS
        val expression = tokens.mapIndexed { index, token ->
            val prefix = lastIsPrefix && index == tokens.lastIndex
            if (prefix) "\"$token*\"" else "\"$token\""
        }.joinToString(" ")
        return FtsQueryPlan.Match(expression = expression, terms = tokens, typingLast = typingLast, lastIsPrefix = lastIsPrefix)
    }

    /**
     * Scripts `unicode61` doesn't break into words. Ranges, not `Character.UnicodeScript`, because
     * this is common code; it only has to be right enough to route a query, and a false positive
     * just means a slower, still-correct scan.
     */
    internal fun isUnsegmentedScript(char: Char): Boolean {
        val code = char.code
        return UNSEGMENTED_RANGES.any { code in it }
    }

    private val WHITESPACE = Regex("\\s+")

    private val UNSEGMENTED_RANGES = listOf(
        0x0E00..0x0EFF, // Thai, Lao
        0x1000..0x109F, // Myanmar
        0x1780..0x17FF, // Khmer
        0x1100..0x11FF, // Hangul Jamo
        0x2E80..0x2FDF, // CJK radicals
        0x3000..0x303F, // CJK symbols and punctuation
        0x3040..0x30FF, // Hiragana, Katakana
        0x3100..0x31FF, // Bopomofo, Hangul compatibility Jamo, Katakana extensions
        0x3400..0x4DBF, // CJK Extension A
        0x4E00..0x9FFF, // CJK Unified Ideographs
        0xAC00..0xD7AF, // Hangul syllables
        0xF900..0xFAFF, // CJK compatibility ideographs
        0xFF66..0xFF9F, // Half-width Katakana
    )
}
