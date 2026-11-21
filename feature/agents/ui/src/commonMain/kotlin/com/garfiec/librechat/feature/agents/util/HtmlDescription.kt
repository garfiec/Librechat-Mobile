package com.garfiec.librechat.feature.agents.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import com.garfiec.librechat.core.ui.text.configHtmlToAnnotatedString

/**
 * An agent description as styled text. A description is HTML only when it starts with a tag the
 * web allows ([isAgentDescriptionHtml]); anything else, `List<String>`, `<name>` placeholders and a
 * tag mid-sentence included, is plain text and comes back as written, newlines and all.
 */
internal fun htmlDescriptionToAnnotatedString(
    text: String,
    linkStyles: TextLinkStyles? = null,
    baseUrl: String? = null,
): AnnotatedString {
    val source = text.trim()
    return if (source.isAgentDescriptionHtml()) configHtmlToAnnotatedString(source, linkStyles, baseUrl) else AnnotatedString(source)
}
