package com.garfiec.librechat.core.common.extensions

/**
 * This string with any HTML markup reduced to plain text, for a one- or two-line preview.
 *
 * Only tag-shaped runs are removed (`<p>`, `</b>`, `<br/>`, `<!-- … -->`), so a plain "a < b"
 * survives. Block-level tags become line breaks, entities are decoded, and runs of spaces and
 * blank lines collapse to one. Text without markup comes back trimmed and otherwise untouched.
 */
fun String.htmlToPlainText(): String {
    if ('<' !in this && '&' !in this) return trim()
    val withBreaks = COMMENT.replace(this, "").let { BLOCK_TAG.replace(it, "\n") }
    val withoutTags = ANY_TAG.replace(withBreaks, "")
    return withoutTags.decodeHtmlEntities()
        .lineSequence()
        .map { SPACES.replace(it, " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

/**
 * This string with its HTML character references (`&amp;`, `&#39;`, `&#x1F600;`, a common named
 * set) replaced by the characters they stand for. A reference it doesn't know stays as written.
 */
fun String.decodeHtmlEntities(): String = ENTITY.replace(this) { match ->
    val body = match.groupValues[1]
    when {
        body.startsWith("#x") || body.startsWith("#X") -> body.drop(2).toIntOrNull(HEX_RADIX)?.let(::codePointText)
        body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(::codePointText)
        else -> NAMED_ENTITIES[body]
    } ?: match.value
}

// A lone surrogate or NUL is not text; the entity is left as written.
private fun codePointText(codePoint: Int): String? = when {
    codePoint == 0 || codePoint in HIGH_SURROGATE_START..SURROGATE_END -> null
    codePoint <= BMP_MAX -> codePoint.toChar().toString()
    codePoint in SUPPLEMENTARY_START..CODE_POINT_MAX -> {
        val offset = codePoint - SUPPLEMENTARY_START
        charArrayOf(
            (HIGH_SURROGATE_START + (offset shr SURROGATE_BITS)).toChar(),
            (LOW_SURROGATE_START + (offset and SURROGATE_MASK)).toChar(),
        ).concatToString()
    }
    else -> null
}

private val COMMENT = Regex("<!--[\\s\\S]*?-->")

// Tags that end a line of text when rendered.
private val BLOCK_TAG = Regex(
    "</?(?:p|div|br|li|ul|ol|h[1-6]|tr|blockquote|pre|hr|section|article|header|footer)\\b[^>]*>",
    RegexOption.IGNORE_CASE,
)

// A letter, `/` or `!` right after `<` is what makes it a tag; "a < b" is left alone.
private val ANY_TAG = Regex("</?[A-Za-z!][^>]*>")
private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9A-Fa-f]{1,6}|[A-Za-z]{2,8});")
private val SPACES = Regex("[ \\t\\u00A0]+")

private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "ndash" to "–",
    "mdash" to "—",
    "hellip" to "…",
    "lsquo" to "‘",
    "rsquo" to "’",
    "ldquo" to "“",
    "rdquo" to "”",
    "copy" to "©",
    "reg" to "®",
    "trade" to "™",
)

private const val HEX_RADIX = 16
private const val BMP_MAX = 0xFFFF
private const val SUPPLEMENTARY_START = 0x10000
private const val CODE_POINT_MAX = 0x10FFFF
private const val HIGH_SURROGATE_START = 0xD800
private const val LOW_SURROGATE_START = 0xDC00
private const val SURROGATE_END = 0xDFFF
private const val SURROGATE_BITS = 10
private const val SURROGATE_MASK = 0x3FF
