package com.garfiec.librechat.feature.chat.util

/** A piece of reply text once its MCP-UI placement markers are taken out. */
sealed interface UiMarkerSegment {
    data class Text(val text: String) : UiMarkerSegment

    /** `\ui{id}` (one id) or `\ui{a,b,c}` (a carousel), in the order written. */
    data class Resources(val ids: List<String>) : UiMarkerSegment
}

/**
 * Splits [text] at its legacy MCP-UI placement markers (upstream `mcpUIResourcePlugin`,
 * `client/src/components/MCPUIResource/plugin.ts`: `/\\ui\{([\w]+(?:,[\w]+)*)\}/g` over markdown
 * **text nodes**). The server tells the model to write them where a `text/html` UI resource should
 * render (`packages/api/src/mcp/parsers.ts`).
 *
 * Because web matches text nodes, this works on the markdown source the way the parse would:
 * - Fenced code blocks and inline code spans are left alone, markers and all.
 * - Escapes resolve first. `\\ui{x}` is a marker on web (the escaped backslash leaves `\ui{x}` in the
 *   text node), and so are `\ui\{x\}` and `\ui{a\_b}`; the backslashes the escape consumed don't show.
 *   A backslash-escaped backtick never opens a code span.
 * - Ids are `[A-Za-z0-9_]` separated by commas, nothing else: `\ui{a, b}` and `\ui{}` stay text.
 *
 * Not modelled (rare in a reply): indented code blocks, link destinations, autolinks, raw HTML and
 * math, where web would also leave a marker alone, and blank lines inside a code span.
 *
 * With no marker the result is the one [UiMarkerSegment.Text] of [text], unchanged. Text that is
 * only whitespace between markers is dropped, since it renders nothing.
 */
fun splitUiResourceMarkers(text: String): List<UiMarkerSegment> {
    if (!mayHoldMarker(text)) return listOf(UiMarkerSegment.Text(text))
    val out = mutableListOf<UiMarkerSegment>()
    val pending = StringBuilder()
    var found = false
    fun flush() {
        if (pending.isNotEmpty()) {
            out += UiMarkerSegment.Text(pending.toString())
            pending.setLength(0)
        }
    }

    var fence: Fence? = null
    var region = StringBuilder()
    fun scanRegion() {
        if (region.isEmpty()) return
        found = scanInline(region.toString(), pending, onMarker = { ids ->
            flush()
            out += UiMarkerSegment.Resources(ids)
        }) || found
        region = StringBuilder()
    }

    var lineStart = 0
    while (lineStart < text.length) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = if (newline < 0) text.length else newline + 1
        val line = text.substring(lineStart, lineEnd)
        val open = fence
        if (open != null) {
            pending.append(line)
            if (open.closedBy(line)) fence = null
        } else {
            val opening = Fence.openedBy(line)
            if (opening != null) {
                scanRegion()
                pending.append(line)
                fence = opening
            } else {
                region.append(line)
            }
        }
        lineStart = lineEnd
    }
    scanRegion()
    flush()
    if (!found) return listOf(UiMarkerSegment.Text(text))
    return out.filterNot { it is UiMarkerSegment.Text && it.text.isBlank() }
}

/** True when [text] has at least one marker outside code. */
fun hasUiResourceMarkers(text: String): Boolean =
    mayHoldMarker(text) && splitUiResourceMarkers(text).any { it is UiMarkerSegment.Resources }

/**
 * [text] less an unfinished marker at its very end (`\ui`, `\ui{ab`, `\ui{a,`), so a reply that is
 * still streaming never flashes one before its closing brace arrives.
 */
fun withoutTrailingPartialUiMarker(text: String): String {
    val match = TRAILING_PARTIAL.find(text) ?: return text
    return text.substring(0, match.range.first)
}

private val TRAILING_PARTIAL = Regex("""\\ui(\\?\{[A-Za-z0-9_,\\]*)?$""")

private fun mayHoldMarker(text: String): Boolean = text.indexOf('\\') >= 0 && text.indexOf("ui") >= 0

/**
 * Copies [region] (text outside fenced code) into [out], handing each marker to [onMarker] instead.
 * Returns whether it found one.
 */
private fun scanInline(region: String, out: StringBuilder, onMarker: (List<String>) -> Unit): Boolean {
    var found = false
    var i = 0
    val n = region.length
    while (i < n) {
        when (region[i]) {
            '\\' -> {
                var j = i
                while (j < n && region[j] == '\\') j++
                val run = j - i
                val marker = if (region.startsWith("ui", j)) parseMarkerBody(region, j + 2) else null
                if (marker != null) {
                    // The marker's backslash, plus the one escaping it when the run is even.
                    val consumed = if (run % 2 == 0) 2 else 1
                    out.append(region, i, j - consumed)
                    onMarker(marker.ids)
                    found = true
                    i = marker.end
                } else if (run % 2 == 1 && j < n && region[j].isAsciiPunctuation()) {
                    out.append(region, i, j + 1)
                    i = j + 1
                } else {
                    out.append(region, i, j)
                    i = j
                }
            }
            '`' -> {
                var j = i
                while (j < n && region[j] == '`') j++
                val close = closingBacktickRun(region, j, j - i)
                val end = if (close >= 0) close else j
                out.append(region, i, end)
                i = end
            }
            else -> {
                out.append(region[i])
                i++
            }
        }
    }
    return found
}

/** The end of the backtick run of exactly [length] that closes a code span opened before [from], or -1. */
private fun closingBacktickRun(region: String, from: Int, length: Int): Int {
    var i = from
    while (i < region.length) {
        val c = region[i]
        if (c == '\n' && isBlankLineAt(region, i + 1)) return -1
        if (c == '`') {
            var j = i
            while (j < region.length && region[j] == '`') j++
            if (j - i == length) return j
            i = j
        } else {
            i++
        }
    }
    return -1
}

private fun isBlankLineAt(region: String, start: Int): Boolean {
    var i = start
    while (i < region.length && region[i] != '\n') {
        if (!region[i].isWhitespace()) return false
        i++
    }
    return true
}

private class MarkerBody(val ids: List<String>, val end: Int)

/** `{id,id}` at [start], allowing the escapes markdown would resolve (`\{`, `\}`, `\_`). */
private fun parseMarkerBody(region: String, start: Int): MarkerBody? {
    var i = start
    if (region.startsWith("\\{", i)) i += 2 else if (region.startsWith("{", i)) i += 1 else return null
    val ids = mutableListOf<String>()
    val id = StringBuilder()
    while (i < region.length) {
        val c = region[i]
        when {
            c.isIdChar() -> {
                id.append(c)
                i++
            }
            c == '\\' && region.getOrNull(i + 1) == '_' -> {
                id.append('_')
                i += 2
            }
            c == ',' -> {
                if (id.isEmpty()) return null
                ids += id.toString()
                id.setLength(0)
                i++
            }
            c == '}' || (c == '\\' && region.getOrNull(i + 1) == '}') -> {
                if (id.isEmpty()) return null
                ids += id.toString()
                return MarkerBody(ids, if (c == '}') i + 1 else i + 2)
            }
            else -> return null
        }
    }
    return null
}

private fun Char.isIdChar(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '_'

private fun Char.isAsciiPunctuation(): Boolean = this in '!'..'/' || this in ':'..'@' || this in '['..'`' || this in '{'..'~'

/** A CommonMark fenced code block opener: up to three spaces, then three or more ` or ~. */
private class Fence(val marker: Char, val length: Int) {
    fun closedBy(line: String): Boolean {
        val body = line.trimEnd('\n', '\r')
        val indent = body.length - body.trimStart(' ').length
        if (indent > MAX_FENCE_INDENT) return false
        val rest = body.substring(indent)
        var count = 0
        while (count < rest.length && rest[count] == marker) count++
        return count >= length && rest.substring(count).isBlank()
    }

    companion object {
        private const val MAX_FENCE_INDENT = 3
        private const val MIN_FENCE = 3

        fun openedBy(line: String): Fence? {
            val body = line.trimEnd('\n', '\r')
            val indent = body.length - body.trimStart(' ').length
            if (indent > MAX_FENCE_INDENT) return null
            val rest = body.substring(indent)
            val marker = rest.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
            var count = 0
            while (count < rest.length && rest[count] == marker) count++
            if (count < MIN_FENCE) return null
            // A backtick fence's info string may not contain a backtick (that's an inline span).
            if (marker == '`' && rest.substring(count).contains('`')) return null
            return Fence(marker, count)
        }
    }
}
