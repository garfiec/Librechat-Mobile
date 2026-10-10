package com.garfiec.librechat.feature.chat.components

// Shared markdown + LaTeX parsing logic used by both Android and iOS MarkdownContent.

internal sealed interface MarkdownSegment {
    data class TextBlock(val text: String) : MarkdownSegment
    data class CodeBlock(val code: String, val language: String?) : MarkdownSegment
    data class LatexBlock(val latex: String) : MarkdownSegment
    data class InlineLatexText(val segments: List<InlineSegment>) : MarkdownSegment
    data class Table(
        val headers: List<String>,
        val alignments: List<TableCellAlignment>,
        val rows: List<List<String>>,
    ) : MarkdownSegment
}

internal enum class TableCellAlignment {
    LEFT, CENTER, RIGHT,
}

internal sealed interface InlineSegment {
    data class Text(val text: String) : InlineSegment
    data class Latex(val latex: String) : InlineSegment
}

// Pre-compiled regex patterns
private val CODE_BLOCK_REGEX = Regex("```(\\w*)[^\\S\\n]*\\n([\\s\\S]*?)```")
private val BLOCK_LATEX_REGEX = Regex("\\$\\$([\\s\\S]+?)\\$\\$|\\\\\\[([\\s\\S]+?)\\\\\\]")
internal val CITATION_DETECT_REGEX = Regex("""\[\d+]|\u3010\d+\u2020""")
private val TABLE_SEPARATOR_REGEX = Regex("^\\|?\\s*:?-{1,}:?\\s*(\\|\\s*:?-{1,}:?\\s*)*\\|?$")

private val HTML_BLOCK_TAG_NAMES = setOf(
    "html", "head", "body", "div", "section", "article", "main", "header", "footer",
    "nav", "aside", "table", "form", "fieldset", "details", "dialog", "figure",
    "figcaption", "template", "canvas", "svg", "video", "audio", "iframe",
    "p", "ul", "ol", "li", "dl", "dt", "dd", "pre", "blockquote", "hr",
)

private val HTML_OPENING_TAG_REGEX = Regex(
    "^\\s*<(!DOCTYPE\\s+html|\\w+)",
    RegexOption.IGNORE_CASE,
)

private const val INLINE_PAREN_OPEN = "\\("
private const val INLINE_PAREN_CLOSE = "\\)"
private const val FENCE_RUN = 3

/**
 * Splits [text] into prose and inline math, or returns null when it holds no inline math so the
 * caller keeps its `TextBlock` untouched.
 *
 * `$…$` follows Pandoc's `tex_math_dollars`. Nothing inspects the span's content; the decision is
 * made entirely by the delimiters' neighbours:
 *  - an opening `$` is not preceded by `$` and is followed by a non-space, non-`$` character;
 *  - the first unescaped `$` after it decides: it closes when preceded by a non-space and not
 *    followed by a digit, otherwise the opener is given up and scanning resumes right after it;
 *  - a backslash always consumes the next character, so `\$` never delimits and `\\$x$` is math.
 * One deliberate departure from Pandoc: a span never crosses a line break (Pandoc allows one soft
 * break). Pandoc's `\text{…}` brace tolerance is not ported.
 *
 * Code wins over math: a delimiter inside a backtick code span (`` `$HOME` and `$PATH` ``) is never
 * seen, and a fence still open at the start of a line — the rest of the message is code to Markdown
 * too — stops the scan, so a streaming ```` ```bash ```` + `$A/$B` never flashes `A/` as math. An
 * unmatched backtick run is plain text and hides nothing.
 *
 * `\(…\)`: same line, non-blank content, no neighbour rules.
 *
 * Must stay linear — it re-runs on every streaming flush over the whole message: no regex, each `$`
 * visited at most twice, and a failed `\(` search poisons the rest of its line (`parenDeadBefore`).
 *
 * [streaming] defers a candidate closer that is the last character of the buffer: it cannot see
 * whether a digit is about to arrive, so the span stays prose for this flush and is
 * re-decided when more text lands (`US$5 to US$` + `10` must not flash `5 to US` as math). Every
 * other verdict depends only on the delimiters' immediate neighbours and is final once text follows.
 * Openers need no deferral: a `$` at the end of the buffer has no following character and is already
 * not an opener.
 */
internal fun scanInlineLatex(text: String, streaming: Boolean = false): List<InlineSegment>? {
    if (text.indexOf('$') < 0 && text.indexOf(INLINE_PAREN_OPEN) < 0) return null
    return InlineLatexScanner(text, streaming).scan()
}

private class InlineLatexScanner(private val text: String, private val streaming: Boolean) {
    private val segments = mutableListOf<InlineSegment>()
    private var textStart = 0

    /** End of the line in which a `\(` search already failed; a later `\(` on that line cannot close either. */
    private var parenDeadBefore = -1

    fun scan(): List<InlineSegment>? {
        var i = 0
        while (i < text.length) {
            i = when (text[i]) {
                '\\' -> atBackslash(i)
                '$' -> atDollar(i)
                '`' -> atBacktick(i)
                else -> i + 1
            }
        }
        if (segments.isEmpty()) return null
        flushText(text.length)
        return segments
    }

    private fun atBackslash(i: Int): Int {
        if (i < parenDeadBefore || !text.startsWith(INLINE_PAREN_OPEN, i)) return i + 2
        val contentStart = i + INLINE_PAREN_OPEN.length
        val close = findParenCloser(contentStart)
        if (close < 0) {
            parenDeadBefore = lineEndFrom(i)
            return i + 2
        }
        // A blank `\( \)` is prose, but it is consumed whole so a later `\(…\)` on the line still closes.
        if (!isBlankRange(contentStart, close)) {
            emitLatex(start = i, contentStart = contentStart, contentEnd = close)
        }
        return close + INLINE_PAREN_CLOSE.length
    }

    /**
     * Skips a code span whose closing run matches [i]'s run; a line-leading run of three or more is
     * an open fence and ends the scan.
     */
    private fun atBacktick(i: Int): Int {
        val runEnd = backtickRunEnd(i)
        val run = runEnd - i
        if (run >= FENCE_RUN && (i == 0 || text[i - 1] == '\n')) return text.length
        var k = runEnd
        while (k < text.length) {
            k = text.indexOf('`', k)
            if (k < 0) return runEnd
            val end = backtickRunEnd(k)
            if (end - k == run) return end
            k = end
        }
        return runEnd
    }

    private fun backtickRunEnd(i: Int): Int {
        var k = i
        while (k < text.length && text[k] == '`') k++
        return k
    }

    private fun atDollar(i: Int): Int {
        if (!isDollarOpener(i)) return i + 1
        val close = nextUnescapedDollar(i + 1)
        if (close < 0 || !closesDollarSpan(close)) return i + 1
        emitLatex(start = i, contentStart = i + 1, contentEnd = close)
        return close + 1
    }

    private fun isDollarOpener(i: Int): Boolean {
        if (i > 0 && text[i - 1] == '$') return false
        val next = text.getOrNull(i + 1) ?: return false
        return next != '$' && !next.isWhitespace()
    }

    /** Index of the first `$` at or after [from] on the same line, skipping escaped pairs; -1 if none. */
    private fun nextUnescapedDollar(from: Int): Int {
        var k = from
        while (k < text.length) {
            when (text[k]) {
                '$' -> return k
                '\n' -> return -1
                '\\' -> k = afterEscape(k)
                else -> k++
            }
        }
        return -1
    }

    /** A backslash consumes the next character unless it is a line break, which no span may cross. */
    private fun afterEscape(k: Int): Int = if (text.getOrNull(k + 1) == '\n') k + 1 else k + 2

    private fun closesDollarSpan(j: Int): Boolean {
        if (text[j - 1].isWhitespace()) return false
        val after = text.getOrNull(j + 1) ?: return !streaming
        return !after.isDigit()
    }

    /** Index of the first `\)` at or after [from] on the same line, skipping escaped pairs; -1 if none. */
    private fun findParenCloser(from: Int): Int {
        var k = from
        while (k < text.length) {
            when {
                text[k] == '\n' -> return -1
                text.startsWith(INLINE_PAREN_CLOSE, k) -> return k
                text[k] == '\\' -> k = afterEscape(k)
                else -> k++
            }
        }
        return -1
    }

    private fun isBlankRange(start: Int, end: Int): Boolean {
        for (k in start until end) if (!text[k].isWhitespace()) return false
        return true
    }

    private fun lineEndFrom(i: Int): Int = text.indexOf('\n', i).let { if (it < 0) text.length else it }

    private fun emitLatex(start: Int, contentStart: Int, contentEnd: Int) {
        flushText(start)
        segments.add(InlineSegment.Latex(text.substring(contentStart, contentEnd).trim()))
        textStart = contentEnd + (if (text[start] == '$') 1 else INLINE_PAREN_CLOSE.length)
    }

    private fun flushText(end: Int) {
        if (end > textStart) segments.add(InlineSegment.Text(text.substring(textStart, end)))
    }
}

private fun looksLikeHtmlBlock(text: String): Boolean {
    val tagName = htmlOpeningTagName(text) ?: return false
    return hasHtmlClosingTag(text, tagName)
}

/**
 * Returns the normalized block-level HTML tag name that [text] opens with (the regex is
 * anchored at `^\s*<`, so leading whitespace/newlines are skipped), or null if [text] does
 * not begin with a recognized block tag. Split out from [looksLikeHtmlBlock] so callers can
 * test the opening tag against a single line without scanning a whole joined suffix.
 */
private fun htmlOpeningTagName(text: String): String? {
    val match = HTML_OPENING_TAG_REGEX.find(text) ?: return null
    val tagName = match.groupValues[1].lowercase().let {
        if (it.startsWith("!doctype")) "html" else it
    }
    return tagName.takeIf { it in HTML_BLOCK_TAG_NAMES }
}

private fun hasHtmlClosingTag(text: String, tagName: String): Boolean =
    text.contains("</$tagName", ignoreCase = true) || text.contains("/>")

private fun extractHtmlBlocks(segments: List<MarkdownSegment>): List<MarkdownSegment> {
    val result = mutableListOf<MarkdownSegment>()

    for (segment in segments) {
        if (segment !is MarkdownSegment.TextBlock) {
            result.add(segment)
            continue
        }

        val text = segment.text
        if (looksLikeHtmlBlock(text)) {
            result.add(MarkdownSegment.CodeBlock(text.trim(), "html"))
            continue
        }

        val lines = text.split('\n')
        // The original probed looksLikeHtmlBlock on the entire joined tail for every line
        // (an O(N^2) re-join). Equivalent, cheaper pass: the opening-tag regex is anchored
        // with `^\s*<`, so a suffix opens a block iff its first non-blank line opens one.
        // Walk the non-blank lines testing only that single line for an opening tag (no
        // join); a non-blank line that is not an opening tag can still be followed by one,
        // so keep scanning. Only when a line opens a recognized tag do we materialize the
        // tail once (folding in any contiguous leading blank run, which `^\s*` collapses) to
        // run the closing-tag check, then stop at that first match.
        var htmlStart = -1
        var lineIdx = 0
        while (lineIdx < lines.size) {
            val line = lines[lineIdx]
            if (line.isBlank()) {
                lineIdx++
                continue
            }
            val tagName = htmlOpeningTagName(line)
            if (tagName != null) {
                var start = lineIdx
                while (start > 0 && lines[start - 1].isBlank()) start--
                val suffix = lines.subList(start, lines.size).joinToString("\n")
                if (hasHtmlClosingTag(suffix, tagName)) {
                    htmlStart = start
                    break
                }
            }
            lineIdx++
        }

        if (htmlStart < 0) {
            result.add(segment)
            continue
        }

        if (htmlStart > 0) {
            val preceding = lines.subList(0, htmlStart).joinToString("\n").trim()
            if (preceding.isNotEmpty()) {
                result.add(MarkdownSegment.TextBlock(preceding))
            }
        }

        val htmlContent = lines.subList(htmlStart, lines.size).joinToString("\n").trim()
        result.add(MarkdownSegment.CodeBlock(htmlContent, "html"))
    }

    return result
}

/**
 * 5-phase markdown parser that extracts code blocks, HTML blocks, block LaTeX,
 * tables, and inline LaTeX from raw text.
 *
 * [streaming]: the message is still growing, so an inline `$` closer ending the buffer is deferred
 * (see [scanInlineLatex]). Decide that from the raw [text], not per segment: segments are trimmed,
 * so a last segment ending in `$` may really be followed by whitespace or a closing fence. Only the
 * last segment can hold it; everything else is parsed as settled.
 */
@Suppress("CyclomaticComplexMethod") // debt: complexity 28
internal fun parseMarkdownSegments(text: String, streaming: Boolean = false): List<MarkdownSegment> {
    // --- Pass 1: split on fenced code blocks ---
    val afterCodeBlocks = mutableListOf<MarkdownSegment>()
    var lastIndex = 0

    CODE_BLOCK_REGEX.findAll(text).forEach { match ->
        if (match.range.first > lastIndex) {
            val textBefore = text.substring(lastIndex, match.range.first).trim()
            if (textBefore.isNotEmpty()) {
                afterCodeBlocks.add(MarkdownSegment.TextBlock(textBefore))
            }
        }
        val language = match.groupValues[1].ifEmpty { null }
        val code = match.groupValues[2].trimEnd()
        val langLower = language?.lowercase()
        if (langLower == "latex" || langLower == "tex" || langLower == "math") {
            afterCodeBlocks.add(MarkdownSegment.TextBlock(code))
        } else {
            afterCodeBlocks.add(MarkdownSegment.CodeBlock(code, language))
        }
        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            afterCodeBlocks.add(MarkdownSegment.TextBlock(remaining))
        }
    }

    if (afterCodeBlocks.isEmpty() && text.isNotBlank()) {
        afterCodeBlocks.add(MarkdownSegment.TextBlock(text))
    }

    // --- Pass 2: detect raw HTML blocks and convert to CodeBlocks ---
    val afterHtmlBlocks = extractHtmlBlocks(afterCodeBlocks)

    // --- Pass 3: split TextBlocks on block LaTeX ($$...$$ or \[...\]) ---
    val afterBlockLatex = mutableListOf<MarkdownSegment>()

    for (segment in afterHtmlBlocks) {
        if (segment !is MarkdownSegment.TextBlock) {
            afterBlockLatex.add(segment)
            continue
        }
        var segLastIndex = 0
        val segText = segment.text

        BLOCK_LATEX_REGEX.findAll(segText).forEach { match ->
            if (match.range.first > segLastIndex) {
                val before = segText.substring(segLastIndex, match.range.first).trim()
                if (before.isNotEmpty()) {
                    afterBlockLatex.add(MarkdownSegment.TextBlock(before))
                }
            }
            val latexContent = (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim()
            if (latexContent.isNotEmpty()) {
                afterBlockLatex.add(MarkdownSegment.LatexBlock(latexContent))
            }
            segLastIndex = match.range.last + 1
        }

        if (segLastIndex < segText.length) {
            val remaining = segText.substring(segLastIndex).trim()
            if (remaining.isNotEmpty()) {
                afterBlockLatex.add(MarkdownSegment.TextBlock(remaining))
            }
        } else if (segLastIndex == 0) {
            afterBlockLatex.add(segment)
        }
    }

    // --- Pass 4: extract markdown tables from TextBlocks ---
    val afterTables = mutableListOf<MarkdownSegment>()

    for (segment in afterBlockLatex) {
        if (segment !is MarkdownSegment.TextBlock) {
            afterTables.add(segment)
            continue
        }
        afterTables.addAll(extractTables(segment.text))
    }

    // --- Pass 5: detect inline LaTeX ($...$ or \(...\)) within remaining TextBlocks ---
    val finalSegments = mutableListOf<MarkdownSegment>()

    val deferTrailingCloser = streaming && text.endsWith('$')
    afterTables.forEachIndexed { index, segment ->
        val inline = (segment as? MarkdownSegment.TextBlock)
            ?.let { scanInlineLatex(it.text, streaming = deferTrailingCloser && index == afterTables.lastIndex) }
        finalSegments.add(if (inline != null) MarkdownSegment.InlineLatexText(inline) else segment)
    }

    return finalSegments
}

private fun extractTables(text: String): List<MarkdownSegment> {
    val lines = text.split('\n')
    val result = mutableListOf<MarkdownSegment>()
    val buffer = mutableListOf<String>()
    var i = 0

    while (i < lines.size) {
        if (i + 2 < lines.size && isTableRow(lines[i]) && isTableSeparator(lines[i + 1])) {
            if (buffer.isNotEmpty()) {
                val preceding = buffer.joinToString("\n").trim()
                if (preceding.isNotEmpty()) {
                    result.add(MarkdownSegment.TextBlock(preceding))
                }
                buffer.clear()
            }

            val headerCells = parseTableRow(lines[i])
            val alignments = parseAlignments(lines[i + 1], headerCells.size)
            val dataRows = mutableListOf<List<String>>()
            var j = i + 2

            while (j < lines.size && isTableRow(lines[j])) {
                val rowCells = parseTableRow(lines[j])
                val normalized = List(headerCells.size) { col ->
                    rowCells.getOrElse(col) { "" }
                }
                dataRows.add(normalized)
                j++
            }

            if (dataRows.isNotEmpty()) {
                result.add(MarkdownSegment.Table(headerCells, alignments, dataRows))
                i = j
            } else {
                buffer.add(lines[i])
                buffer.add(lines[i + 1])
                i += 2
            }
        } else {
            buffer.add(lines[i])
            i++
        }
    }

    if (buffer.isNotEmpty()) {
        val remaining = buffer.joinToString("\n").trim()
        if (remaining.isNotEmpty()) {
            result.add(MarkdownSegment.TextBlock(remaining))
        }
    }

    return result
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    return trimmed.startsWith('|') || trimmed.endsWith('|') || trimmed.count { it == '|' } >= 2
}

private fun isTableSeparator(line: String): Boolean = TABLE_SEPARATOR_REGEX.matches(line.trim())

internal fun parseTableRow(line: String): List<String> {
    val inner = line.trim().removePrefix("|").removeSuffix("|")
    return inner.split('|').map { it.trim() }
}

internal fun parseAlignments(separatorLine: String, columnCount: Int): List<TableCellAlignment> {
    val cells = parseTableRow(separatorLine)
    return List(columnCount) { col ->
        val cell = cells.getOrElse(col) { "---" }.trim()
        when {
            cell.startsWith(':') && cell.endsWith(':') -> TableCellAlignment.CENTER
            cell.endsWith(':') -> TableCellAlignment.RIGHT
            else -> TableCellAlignment.LEFT
        }
    }
}
