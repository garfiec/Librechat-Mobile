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
 * Whether this description is HTML: its FIRST tag-shaped run (`<p>`, `</b>`, `<br/>`, comments
 * aside) names an allowlisted tag. "Reply with <name>" or "List<String>" starts with a tag the
 * web doesn't allow, so it is plain text and shown as written; "a < b" has no tag at all.
 */
internal fun String.isAgentDescriptionHtml(): Boolean {
    val first = FIRST_TAG.find(COMMENT.replace(this, "")) ?: return false
    return first.groupValues[1].lowercase() in AGENT_DESCRIPTION_TAGS
}

/**
 * A description as a card's two lines: HTML reduced to one run of text (block tags become spaces),
 * plain text as written but with its line breaks and runs of space collapsed. Null when empty.
 */
internal fun String.agentDescriptionPreview(): String? {
    val text = if (isAgentDescriptionHtml()) htmlToPlainText() else SPACES.replace(this, " ").trim()
    return text.takeIf { it.isNotEmpty() }
}

private val FIRST_TAG = Regex("</?([A-Za-z][A-Za-z0-9]*)[^>]*>")
private val COMMENT = Regex("<!--[\\s\\S]*?-->")
private val SPACES = Regex("[\\s\\u00A0]+")
