package com.garfiec.librechat.feature.agents.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.garfiec.librechat.core.common.extensions.decodeHtmlEntities

/**
 * An agent description as styled text.
 *
 * Admins write some descriptions as HTML in the web editor. A description is HTML only when its
 * first tag is one the web allows ([isAgentDescriptionHtml]); anything else, `List<String>` and
 * `<name>` placeholders included, is plain text and comes back as written, newlines and all.
 *
 * Of the markup, this renders the web's allowlist: bold, italic, code, links, line breaks,
 * paragraphs, divs and lists. Every other tag is dropped and its text kept, as the web's sanitiser
 * does; script and style lose their text too.
 *
 * Links are kept only for http(s), mailto and tel targets and for relative ones, which resolve
 * against [baseUrl] (the server) and stay plain text without one: the markup is somebody else's,
 * and a `javascript:` or app-scheme link must not become something to tap.
 */
internal fun htmlDescriptionToAnnotatedString(
    text: String,
    linkStyles: TextLinkStyles? = null,
    baseUrl: String? = null,
): AnnotatedString {
    val source = text.trim()
    if (!source.isAgentDescriptionHtml()) return AnnotatedString(source)
    val cleaned = DECLARATION.replace(COMMENT.replace(source, ""), "")
    val renderer = HtmlRenderer(linkStyles, baseUrl?.takeIf { it.isNotBlank() })
    var position = 0
    for (match in TAG.findAll(cleaned)) {
        renderer.text(cleaned.substring(position, match.range.first))
        renderer.tag(
            closing = match.groupValues[1] == "/",
            name = match.groupValues[2].lowercase(),
            attributes = match.groupValues[3],
        )
        position = match.range.last + 1
    }
    renderer.text(cleaned.substring(position))
    return renderer.build()
}

private class HtmlRenderer(private val linkStyles: TextLinkStyles?, private val baseUrl: String?) {
    private class OpenTag(val name: String, val styleIndex: Int)
    private class OpenList(val ordered: Boolean, var next: Int = 1)

    private val builder = AnnotatedString.Builder()
    private val openTags = ArrayList<OpenTag>()
    private val lists = ArrayList<OpenList>()

    /** Newlines owed before the next text: 1 = a line break, 2 = a blank line. Never at the start or end. */
    private var pendingBreaks = 0

    /** A space the source had at the end of the last text, held back in case a line break follows. */
    private var pendingSpace = false

    /** Nothing but a line start (or a list bullet) precedes the next text, so leading space is dropped. */
    private var atLineStart = true

    private var skipDepth = 0

    fun text(raw: String) {
        if (skipDepth > 0 || raw.isEmpty()) return
        val decoded = WHITESPACE.replace(raw, " ").decodeHtmlEntities()
        val leadingSpace = decoded.startsWith(' ')
        val trailingSpace = decoded.endsWith(' ')
        val content = decoded.trim(' ')
        if (content.isEmpty()) {
            if (leadingSpace && !atLineStart) pendingSpace = true
            return
        }
        flushBreaks()
        if ((pendingSpace || leadingSpace) && !atLineStart) builder.append(' ')
        builder.append(content)
        pendingSpace = trailingSpace
        atLineStart = false
    }

    // Only the allowlist renders; any other tag falls through and is dropped with its text kept.
    fun tag(closing: Boolean, name: String, attributes: String) {
        when (name) {
            in SKIPPED -> skipDepth = (skipDepth + if (closing) -1 else 1).coerceAtLeast(0)
            "br" -> lineBreak()
            "p" -> breakAtLeast(BLANK_LINE)
            "div" -> breakAtLeast(LINE)
            "ul", "ol" -> list(closing, ordered = name == "ol")
            "li" -> if (closing) breakAtLeast(LINE) else listItem()
            "b", "strong" -> inline(closing, name) { builder.pushStyle(BOLD) }
            "i", "em" -> inline(closing, name) { builder.pushStyle(ITALIC) }
            "code" -> inline(closing, name) { builder.pushStyle(CODE) }
            "a" -> inline(closing, name) {
                safeHref(attributes, baseUrl)?.let { builder.pushLink(LinkAnnotation.Url(it, linkStyles)) }
            }
        }
    }

    fun build(): AnnotatedString = builder.toAnnotatedString()

    private fun list(closing: Boolean, ordered: Boolean) {
        breakAtLeast(LINE)
        if (closing) lists.removeLastOrNull() else lists += OpenList(ordered)
    }

    private fun listItem() {
        breakAtLeast(LINE)
        flushBreaks()
        val list = lists.lastOrNull()
        val indent = INDENT.repeat((lists.size - 1).coerceAtLeast(0))
        val marker = if (list?.ordered == true) "${list.next++}." else BULLET
        builder.append("$indent$marker ")
        pendingSpace = false
        atLineStart = true
    }

    /** Opens [name] with whatever [push] pushed (its style index, or none), or closes the latest open [name]. */
    private inline fun inline(closing: Boolean, name: String, push: () -> Int?) {
        if (!closing) {
            // A space before the tag belongs outside it: "see <b>this</b>" bolds "this", not " this".
            flushSpace()
            openTags += OpenTag(name, push() ?: NO_STYLE)
            return
        }
        val at = openTags.indexOfLast { it.name == name }
        if (at < 0) return
        // Tags closed out of order: everything opened after this one closes with it.
        val closed = openTags.subList(at, openTags.size)
        closed.filter { it.styleIndex != NO_STYLE }.minOfOrNull { it.styleIndex }?.let(builder::pop)
        closed.clear()
    }

    private fun lineBreak() {
        if (builder.length == 0) return
        pendingBreaks = (pendingBreaks + 1).coerceAtMost(BLANK_LINE)
        pendingSpace = false
    }

    private fun breakAtLeast(count: Int) {
        if (builder.length == 0) return
        pendingBreaks = maxOf(pendingBreaks, count)
        pendingSpace = false
    }

    private fun flushSpace() {
        if (!pendingSpace || atLineStart || pendingBreaks > 0) return
        builder.append(' ')
        pendingSpace = false
    }

    private fun flushBreaks() {
        if (pendingBreaks == 0) return
        if (builder.length > 0) builder.append("\n".repeat(pendingBreaks))
        pendingBreaks = 0
        pendingSpace = false
        atLineStart = true
    }
}

/**
 * The link an `href` may become, or null. Mirrors the web's `CONFIG_HTML_SAFE_URI`
 * (upstream `client/src/utils/configHtml.ts`, registered in `scripts/mirrors.json`): http(s),
 * mailto and tel, or a relative URL (no scheme, not `//`). A relative one resolves against
 * [baseUrl], the server the web client is served from; without it, no link.
 */
internal fun safeHref(attributes: String, baseUrl: String?): String? {
    val match = HREF.find(attributes) ?: return null
    val url = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.decodeHtmlEntities()?.trim() ?: return null
    if (url.isEmpty()) return null
    if (ABSOLUTE_SAFE.containsMatchIn(url)) return url
    if (ANY_SCHEME.containsMatchIn(url) || url.startsWith("//")) return null
    val base = baseUrl?.trimEnd('/') ?: return null
    return when {
        url.startsWith("/") || url.startsWith("?") || url.startsWith("#") -> "$base$url"
        else -> "$base/$url"
    }
}

private const val LINE = 1
private const val BLANK_LINE = 2
private const val NO_STYLE = -1
private const val BULLET = "•"
private const val INDENT = "    "

private val BOLD = SpanStyle(fontWeight = FontWeight.Bold)
private val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)
private val CODE = SpanStyle(fontFamily = FontFamily.Monospace)

private val SKIPPED = setOf("script", "style", "head", "title")

// A letter right after `<` (or `</`) is what makes it a tag; "a < b" is text.
private val TAG = Regex("<(/?)([A-Za-z][A-Za-z0-9]*)([^>]*)>")
private val COMMENT = Regex("<!--[\\s\\S]*?-->")
private val DECLARATION = Regex("<![^>]*>")
private val WHITESPACE = Regex("[\\s\\u00A0]+")
private val HREF = Regex("""\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""", RegexOption.IGNORE_CASE)
private val ABSOLUTE_SAFE = Regex("^(?:https?|mailto|tel):", RegexOption.IGNORE_CASE)
private val ANY_SCHEME = Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE)
