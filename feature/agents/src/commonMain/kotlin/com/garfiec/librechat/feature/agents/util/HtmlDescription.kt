package com.garfiec.librechat.feature.agents.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.garfiec.librechat.core.common.extensions.decodeHtmlEntities

/**
 * An agent description as styled text.
 *
 * Admins write some descriptions as HTML in the web editor. This renders what a description uses —
 * bold, italic, links, line breaks, paragraphs and lists — and drops every other tag while keeping
 * its text. A description with no tags is plain text and comes back as written, newlines and all.
 *
 * Links are kept only for http(s) and mailto targets: the markup is somebody else's, and a
 * `javascript:` or app-scheme link must not become something to tap.
 */
internal fun htmlDescriptionToAnnotatedString(text: String, linkStyles: TextLinkStyles? = null): AnnotatedString {
    val source = text.trim()
    if (!TAG.containsMatchIn(source)) return AnnotatedString(source)
    val cleaned = DECLARATION.replace(COMMENT.replace(source, ""), "")
    val renderer = HtmlRenderer(linkStyles)
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

private class HtmlRenderer(private val linkStyles: TextLinkStyles?) {
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

    fun tag(closing: Boolean, name: String, attributes: String) {
        when (name) {
            in SKIPPED -> skipDepth = (skipDepth + if (closing) -1 else 1).coerceAtLeast(0)
            "br" -> lineBreak()
            "p", "hr" -> breakAtLeast(BLANK_LINE)
            "div", "blockquote", "pre", "tr", "section", "article", "header", "footer" -> breakAtLeast(LINE)
            "ul", "ol" -> list(closing, ordered = name == "ol")
            "li" -> if (closing) breakAtLeast(LINE) else listItem()
            in HEADINGS -> {
                breakAtLeast(BLANK_LINE)
                inline(closing, name) { builder.pushStyle(BOLD) }
            }
            "b", "strong" -> inline(closing, name) { builder.pushStyle(BOLD) }
            "i", "em" -> inline(closing, name) { builder.pushStyle(ITALIC) }
            "a" -> inline(closing, name) { safeHref(attributes)?.let { builder.pushLink(LinkAnnotation.Url(it, linkStyles)) } }
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

    private fun safeHref(attributes: String): String? {
        val match = HREF.find(attributes) ?: return null
        val url = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.decodeHtmlEntities()?.trim() ?: return null
        return url.takeIf { candidate -> SAFE_SCHEMES.any { candidate.startsWith(it, ignoreCase = true) } }
    }
}

private const val LINE = 1
private const val BLANK_LINE = 2
private const val NO_STYLE = -1
private const val BULLET = "•"
private const val INDENT = "    "

private val BOLD = SpanStyle(fontWeight = FontWeight.Bold)
private val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)

private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private val SKIPPED = setOf("script", "style", "head", "title")
private val SAFE_SCHEMES = listOf("https://", "http://", "mailto:")

// A letter right after `<` (or `</`) is what makes it a tag; "a < b" is text.
private val TAG = Regex("<(/?)([A-Za-z][A-Za-z0-9]*)([^>]*)>")
private val COMMENT = Regex("<!--[\\s\\S]*?-->")
private val DECLARATION = Regex("<![^>]*>")
private val WHITESPACE = Regex("[\\s\\u00A0]+")
private val HREF = Regex("""\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""", RegexOption.IGNORE_CASE)
