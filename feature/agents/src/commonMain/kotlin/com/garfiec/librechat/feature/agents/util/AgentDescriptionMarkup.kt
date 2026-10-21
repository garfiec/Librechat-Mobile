package com.garfiec.librechat.feature.agents.util

import com.garfiec.librechat.core.common.extensions.htmlToPlainText

/**
 * The tags an agent description may use as HTML: the web's description allowlist (from
 * upstream `client/src/utils/configHtml.ts`; registered in `scripts/mirrors.json`).
 */
internal val AGENT_DESCRIPTION_TAGS = setOf(
    "a", "strong", "b", "em", "i", "br", "code", "span", "p", "div", "ul", "ol", "li",
)

/**
 * The tags that make a description HTML: the allowlist plus `img`, as the web's
 * `descriptionHtmlTags` (rich-text tags plus media tags). An `img` is never drawn here, as on the
 * web's agent card and page; it only decides that the text is markup, which is then dropped.
 */
private val HTML_LEADING_TAGS = AGENT_DESCRIPTION_TAGS + "img"

/**
 * Whether this description is HTML: once trimmed, it STARTS with a tag (`<p>`, `</b>`, `< br/>`)
 * that [HTML_LEADING_TAGS] names, as upstream `isHtmlDescription` (`client/src/components/ui/Description.tsx`)
 * decides it. A tag further in ("Ask me about <b>pricing</b>"), a leading comment, or a leading
 * tag the web doesn't allow ("Reply with <name>", "List<String>") leaves it plain text, shown as
 * written.
 */
internal fun String.isAgentDescriptionHtml(): Boolean {
    val first = LEADING_TAG.find(trim()) ?: return false
    return first.groupValues[1].lowercase() in HTML_LEADING_TAGS
}

/**
 * A description as a card's two lines: HTML reduced to one run of text (block tags become spaces),
 * plain text as written but with its line breaks and runs of space collapsed. Null when empty.
 */
internal fun String.agentDescriptionPreview(): String? {
    val text = if (isAgentDescriptionHtml()) htmlToPlainText() else SPACES.replace(this, " ").trim()
    return text.takeIf { it.isNotEmpty() }
}

// Upstream: /^<\s*\/?\s*([a-z][a-z0-9]*)\b/i. The lookahead is JavaScript's ASCII `\b` after a tag
// name; a Kotlin `\b` would also count a non-ASCII letter as part of the word.
private val LEADING_TAG = Regex("^<\\s*/?\\s*([A-Za-z][A-Za-z0-9]*)(?![A-Za-z0-9_])")
private val SPACES = Regex("[\\s\\u00A0]+")
