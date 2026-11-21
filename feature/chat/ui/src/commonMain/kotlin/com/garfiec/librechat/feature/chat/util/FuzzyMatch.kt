package com.garfiec.librechat.feature.chat.util

/**
 * Simple fuzzy string matching using Levenshtein-based ratio.
 * Replaces fuzzywuzzy for KMP compatibility.
 */
object FuzzyMatch {
    /**
     * Returns a score 0-100 representing how similar [s1] and [s2] are.
     * Uses partial ratio: best substring match ratio.
     */
    fun partialRatio(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()

        // Quick exact contains check
        if (a in b || b in a) return 100

        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a

        if (shorter.isEmpty()) return 0

        var bestScore = 0
        for (i in 0..longer.length - shorter.length) {
            val sub = longer.substring(i, i + shorter.length)
            val score = ratio(shorter, sub)
            if (score > bestScore) bestScore = score
            if (bestScore == 100) break
        }
        return bestScore
    }

    /**
     * Returns a score 0-100 for the simple ratio between two strings.
     */
    fun ratio(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()
        if (a == b) return 100
        if (a.isEmpty() || b.isEmpty()) return 0

        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        return ((1.0 - dist.toDouble() / maxLen) * 100).toInt()
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[n]
    }
}
