package com.garfiec.librechat.core.common.extensions

/**
 * The first emoji grapheme cluster, without corrupting the common multi-code-unit emoji a naive
 * "first code point" cut would break: variation-selector emoji (❤️), skin-tone modifiers (👍🏽), flags
 * and subdivision flags (🇺🇸), and ZWJ sequences (👨‍👩‍👧). Empty for an empty string or a leading lone
 * surrogate, which would render as a replacement box.
 */
fun String.firstGrapheme(): String {
    if (isEmpty()) return this
    val (first, firstEnd) = codePointAt(this, 0)
    if (first < 0) return ""
    // A flag is a pair of regional-indicator symbols.
    if (first in 0x1F1E6..0x1F1FF) {
        if (firstEnd < length) {
            val (next, nextEnd) = codePointAt(this, firstEnd)
            if (next in 0x1F1E6..0x1F1FF) return substring(0, nextEnd)
        }
        return substring(0, firstEnd)
    }
    var end = firstEnd
    while (end < length) {
        val (cp, cpEnd) = codePointAt(this, end)
        val extends = cp == 0x200D || // ZWJ
            cp in 0xFE00..0xFE0F || // variation selectors
            cp in 0x1F3FB..0x1F3FF || // skin-tone modifiers
            cp == 0x20E3 || // enclosing keycap
            cp in 0x0300..0x036F || // combining marks
            cp in 0xE0020..0xE007F // tag chars (subdivision flags)
        if (!extends) break
        end = cpEnd
        // A ZWJ joins the following emoji into the same cluster — pull it in too.
        if (cp == 0x200D && end < length) end = codePointAt(this, end).second
    }
    return substring(0, end)
}

/** Reads the code point starting at [index], returning it with the index just past it; -1 for a lone surrogate. */
private fun codePointAt(s: String, index: Int): Pair<Int, Int> {
    val c = s[index]
    if (c.isHighSurrogate() && index + 1 < s.length && s[index + 1].isLowSurrogate()) {
        val cp = 0x10000 + ((c.code - 0xD800) shl 10) + (s[index + 1].code - 0xDC00)
        return cp to index + 2
    }
    if (c.isHighSurrogate() || c.isLowSurrogate()) return -1 to index + 1
    return c.code to index + 1
}
