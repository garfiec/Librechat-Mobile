package com.garfiec.librechat.feature.chat.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [scanInlineLatex] implements Pandoc's `tex_math_dollars` neighbour rules. Every row here is a
 * delimiter-context decision; none depends on what the span contains. The streaming cases cover
 * the one verdict that appended text can change: a closer at the end of the buffer.
 */
class InlineLatexDetectionTest {

    private fun mathIn(text: String, streaming: Boolean = false): List<String> =
        scanInlineLatex(text, streaming)
            ?.filterIsInstance<InlineSegment.Latex>()
            ?.map { it.latex }
            .orEmpty()

    private fun assertProse(text: String) {
        assertNull(scanInlineLatex(text), "expected no inline math in: $text")
    }

    // --- dollar amounts stay prose ---

    @Test
    fun `two prices on one line are prose regardless of the words between them`() {
        assertProse("costs \$5 for the interest, then \$10")
        assertProse("costs \$5 for the summary, then \$10")
        assertProse("costs \$5 for the limit, then \$10")
        assertProse("costs \$5 for the print job, then \$10")
    }

    @Test
    fun `pandoc's own example is prose`() {
        assertProse("\$20,000 and \$30,000")
    }

    @Test
    fun `amounts with unit suffixes are prose`() {
        assertProse("Revenue rose from \$13B to \$24B.")
    }

    @Test
    fun `escaped dollars never delimit`() {
        assertProse("\\\$5 and \\\$10")
    }

    @Test
    fun `whitespace inside the delimiters rejects the span`() {
        assertProse("\$ x \$")
        assertProse("\$x \$")
        assertProse("\$ x\$")
    }

    @Test
    fun `shell variables are prose`() {
        assertProse("my \$variable and \$other vars")
        assertProse("\$5 or \$USD")
    }

    @Test
    fun `a span never crosses a line break`() {
        assertProse("it's \$5 today and\n\$10 tomorrow")
    }

    @Test
    fun `a backslash before a line break does not carry a span across it`() {
        assertProse("costs \$5\\\n6\$ later")
        assertProse("\\(a\\\nb\\)")
    }

    // --- code wins over math ---

    @Test
    fun `dollars inside a code span are never delimiters`() {
        assertProse("Set `\$HOME` and `\$PATH` first")
        assertProse("run `\$HOME/\$USER` now")
        assertProse("`\$1` and `\$2`")
        assertProse("``\$a`\$b`` and `\$c`")
    }

    @Test
    fun `an unmatched backtick hides nothing`() {
        assertEquals(listOf("x"), mathIn("a stray ` then \$x\$"))
        assertEquals(listOf("x"), mathIn("\$x\$ then a stray `"))
    }

    @Test
    fun `an open fence stops the scan`() {
        assertProse("```bash\n\$FOO/\$BAR")
        assertProse("text\n```\n\$x\$")
        assertEquals(listOf("x"), mathIn("\$x\$\n```\n\$y\$"))
    }

    @Test
    fun `a mid-line triple backtick is a code span opener, not a fence`() {
        assertEquals(listOf("x"), mathIn("wrap it in ``` fences, then \$x\$"))
        assertProse("``` \$a \$b ``` and \$c")
    }

    @Test
    fun `a closer followed by a digit does not close`() {
        assertProse("\$x\$5")
    }

    @Test
    fun `a closer followed by a dollar still closes, as in pandoc`() {
        assertEquals(
            listOf(InlineSegment.Latex("x"), InlineSegment.Text("\$")),
            scanInlineLatex("\$x\$\$"),
        )
    }

    @Test
    fun `leftover double dollars and lone dollars are prose`() {
        assertProse("\$\$")
        assertProse("costs \$")
        assertProse("\$")
    }

    // --- real math is recognised ---

    @Test
    fun `a bare variable is math`() {
        assertEquals(listOf("x"), mathIn("\$x\$"))
    }

    @Test
    fun `a span starting with a digit is math when its closer is clean`() {
        assertEquals(listOf("2n = p + q"), mathIn("\$2n = p + q\$ is even"))
        assertEquals(listOf("1+1"), mathIn("\$1+1\$ equals two"))
    }

    @Test
    fun `a price earlier on the line does not steal a later formula`() {
        assertEquals(listOf("x^2"), mathIn("The price is \$5 and the total is \$x^2\$."))
    }

    @Test
    fun `a formula earlier on the line is not disturbed by a later price`() {
        assertEquals(listOf("x"), mathIn("Let \$x\$ cost \$5 today"))
    }

    @Test
    fun `a rejected opener is given up and the next dollar may open`() {
        assertEquals(listOf("b"), mathIn("\$a \$b\$"))
    }

    @Test
    fun `an escaped dollar inside a span is content`() {
        assertEquals(listOf("a\\\$b"), mathIn("\$a\\\$b\$"))
    }

    @Test
    fun `an escaped backslash before the opener is not an escape of the dollar`() {
        assertEquals(listOf("x"), mathIn("\\\\\$x\$"))
    }

    @Test
    fun `an escaped backslash before the closer is content`() {
        assertEquals(listOf("a\\\\"), mathIn("\$a\\\\\$"))
    }

    @Test
    fun `prose and math runs are emitted in order`() {
        assertEquals(
            listOf(
                InlineSegment.Text("Let "),
                InlineSegment.Latex("x"),
                InlineSegment.Text(" cost \$5 today"),
            ),
            scanInlineLatex("Let \$x\$ cost \$5 today"),
        )
    }

    // --- backslash-paren delimiters keep their historic behaviour ---

    @Test
    fun `paren delimiters are math without neighbour rules`() {
        assertEquals(listOf("a+b"), mathIn("\\(a+b\\)"))
        assertEquals(listOf("a+b"), mathIn("see\\( a+b \\)here"))
    }

    @Test
    fun `blank or unterminated paren delimiters are prose`() {
        assertProse("\\( \\)")
        assertProse("\\(a+b")
        assertProse("\\(a+b\n\\)")
    }

    @Test
    fun `a blank paren pair does not hide a later formula on the same line`() {
        assertEquals(listOf("a+b"), mathIn("\\( \\) and \\(a+b\\)"))
    }

    @Test
    fun `a paren formula between a price and a stray dollar is still found`() {
        assertEquals(listOf("a+b"), mathIn("\$5 \\(a+b\\) and \$x"))
    }

    // --- streaming: the end-of-buffer closer is deferred ---

    @Test
    fun `a closer at the end of the buffer is deferred while streaming`() {
        val partial = "from US\$5 to US\$"
        assertEquals(emptyList(), mathIn(partial, streaming = true))
        assertEquals(listOf("5 to US"), mathIn(partial, streaming = false))
        assertProse("${partial}10")
    }

    @Test
    fun `a closer preceded by whitespace is prose in both modes`() {
        assertProse("costs \$5 and later \$")
        assertEquals(emptyList(), mathIn("costs \$5 and later \$", streaming = true))
    }

    @Test
    fun `a closer followed by text is decided even while streaming`() {
        assertEquals(listOf("x"), mathIn("\$x\$ ", streaming = true))
    }

    @Test
    fun `an opener at the end of the buffer is prose in both modes`() {
        assertProse("costs \$")
        assertEquals(emptyList(), mathIn("costs \$", streaming = true))
    }

    @Test
    fun `a closer followed by trailing whitespace in the raw text is decided while streaming`() {
        assertEquals(
            listOf(MarkdownSegment.InlineLatexText(listOf(InlineSegment.Latex("x")))),
            parseMarkdownSegments("\$x\$ ", streaming = true),
        )
        assertEquals(
            listOf(MarkdownSegment.InlineLatexText(listOf(InlineSegment.Latex("x")))),
            parseMarkdownSegments("\$x\$\n", streaming = true),
        )
    }

    @Test
    fun `a trailing latex fence ending in a dollar is decided while streaming`() {
        assertEquals(
            listOf(MarkdownSegment.InlineLatexText(listOf(InlineSegment.Latex("x")))),
            parseMarkdownSegments("```latex\n\$x\$\n```", streaming = true),
        )
    }

    @Test
    fun `only the last segment of a streaming parse is deferred`() {
        val segments = parseMarkdownSegments("\$x\$\n\n```a\n```", streaming = true)
        assertEquals(
            MarkdownSegment.InlineLatexText(listOf(InlineSegment.Latex("x"))),
            segments.first(),
        )
    }

    @Test
    fun `the last segment of a streaming parse is deferred through the full parser`() {
        val segments = parseMarkdownSegments("from US\$5 to US\$", streaming = true)
        assertEquals(listOf(MarkdownSegment.TextBlock("from US\$5 to US\$")), segments)
    }

    // --- performance guards: linear on inputs that defeat backtracking regexes ---

    @Test
    fun `a long run of rejected dollar openers stays linear`() {
        val text = "\$a ".repeat(20_000)
        assertEquals(listOf(MarkdownSegment.TextBlock(text.trim())), parseMarkdownSegments(text))
    }

    @Test
    fun `a long run of unterminated paren openers stays linear`() {
        val text = "\\(a ".repeat(20_000)
        assertEquals(listOf(MarkdownSegment.TextBlock(text.trim())), parseMarkdownSegments(text))
    }

    @Test
    fun `prose without delimiters takes the early exit`() {
        assertNull(scanInlineLatex("no math here at all"))
        assertTrue(scanInlineLatex("") == null)
    }
}
