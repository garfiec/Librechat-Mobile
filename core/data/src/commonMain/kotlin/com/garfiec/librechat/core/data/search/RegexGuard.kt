package com.garfiec.librechat.core.data.search

/** Why a regex search can't run. */
enum class RegexProblem { Invalid, TooComplex }

sealed interface RegexCheck {
    data class Ok(val regex: Regex) : RegexCheck
    data class Rejected(val problem: RegexProblem) : RegexCheck
}

/**
 * Compiles a user's search pattern, refusing the shapes that backtrack catastrophically.
 *
 * A running match can't be interrupted: Android's engine copies the input and matches the copy
 * natively, so a deadline check inside the input is never consulted, and Kotlin/Native's engine
 * is no better documented. The scan engine therefore checks its budget only between rows, and
 * this guard refuses the shapes most likely to make a single row take forever:
 *
 * - a quantified group that itself contains a quantifier (`(a+)+`, `(\w*)*`, `(x+){2,}`), or an
 *   alternation (`(a|aa)+`): the classic exponential shapes;
 * - two unbounded wildcards on one branch (`.*foo.*bar.*`): polynomial, and quick to blow up on
 *   the long rows this searches.
 *
 * It is deliberately conservative: some safe patterns are refused with "too complex".
 *
 * **It is best-effort, not a guarantee.** The wildcard rule counts only `.`, so adjacent unbounded
 * runs over overlapping classes (`\w*\w*\w*!`, `[a-z]+[a-z]+!`, `\s*\S*\s*q`) still pass and
 * are polynomial on a long row of matching characters. Such a match can't be interrupted: it holds
 * a Default thread and the search mutex, and `LocalSearchActivity` stays in flight, so the index
 * backfill and repair wait behind it. Widening the rule to every class would also refuse common
 * safe patterns (`\w+@\w+`), so it's left narrow for now. The real fix is a linear-time engine:
 * RE2/J (JVM only) behind an expect/actual is the maintained option.
 */
object RegexGuard {

    fun compile(pattern: String, matchCase: Boolean): RegexCheck {
        if (isTooComplex(pattern)) return RegexCheck.Rejected(RegexProblem.TooComplex)
        val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return runCatching { Regex(pattern, options) }
            .fold(onSuccess = { RegexCheck.Ok(it) }, onFailure = { RegexCheck.Rejected(RegexProblem.Invalid) })
    }

    private class Group(var quantified: Boolean = false, var alternation: Boolean = false)

    internal fun isTooComplex(pattern: String): Boolean {
        val stack = ArrayDeque<Group>()
        var wildcardsOnBranch = 0
        var i = 0
        while (i < pattern.length) {
            when (val c = pattern[i]) {
                '\\' -> {
                    i += 2
                    if (isQuantifierAt(pattern, i)) stack.lastOrNull()?.quantified = true
                    continue
                }
                '[' -> i = skipClass(pattern, i)
                '(' -> {
                    stack.addLast(Group())
                    // `(?:`, `(?=`, `(?<name>`, `(?i)`: that `?` names the group's kind, it isn't a quantifier.
                    if (pattern.getOrNull(i + 1) == '?') i++
                }
                ')' -> {
                    val group = stack.removeLastOrNull() ?: Group()
                    if (isUnboundedQuantifierAt(pattern, i + 1) && (group.quantified || group.alternation)) return true
                    if (isQuantifierAt(pattern, i + 1)) stack.lastOrNull()?.quantified = true
                    stack.lastOrNull()?.let { it.quantified = it.quantified || group.quantified }
                }
                '|' -> {
                    stack.lastOrNull()?.alternation = true
                    if (stack.isEmpty()) wildcardsOnBranch = 0
                }
                '.' -> if (isUnboundedQuantifierAt(pattern, i + 1) && ++wildcardsOnBranch >= 2) return true
                else -> if (c in QUANTIFIERS) stack.lastOrNull()?.quantified = true
            }
            i++
        }
        return false
    }

    private fun isQuantifierAt(pattern: String, at: Int): Boolean = at < pattern.length && pattern[at] in QUANTIFIERS

    /**
     * A repeating quantifier (`*`, `+`, any `{…}`). `{n,m}` is bounded, but a repeated group that
     * itself repeats is still exponential in practice, so it counts; only `?` is exempt.
     */
    private fun isUnboundedQuantifierAt(pattern: String, at: Int): Boolean =
        at < pattern.length && pattern[at] in REPEATING

    /** Index of the class's closing `]`, honouring escapes and a leading `]`/`^]`. */
    private fun skipClass(pattern: String, open: Int): Int {
        var i = open + 1
        if (i < pattern.length && pattern[i] == '^') i++
        if (i < pattern.length && pattern[i] == ']') i++
        while (i < pattern.length && pattern[i] != ']') {
            if (pattern[i] == '\\') i++
            i++
        }
        return i
    }

    private val QUANTIFIERS = setOf('*', '+', '?', '{')
    private val REPEATING = setOf('*', '+', '{')
}
