package com.garfiec.librechat.feature.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSegmentsTest {

    // --- CODE_BLOCK_REGEX resilience (Part 2 fix) ---

    @Test
    fun `code block with trailing space after language is parsed`() {
        val input = "```html \n<div>test</div>\n```"
        val segments = parseMarkdownSegments(input)
        assertEquals(1, segments.size)
        val code = segments[0] as MarkdownSegment.CodeBlock
        assertEquals("html", code.language)
        assertEquals("<div>test</div>", code.code)
    }

    @Test
    fun `code block with tab after language is parsed`() {
        val input = "```html\t\n<div>test</div>\n```"
        val segments = parseMarkdownSegments(input)
        assertEquals(1, segments.size)
        val code = segments[0] as MarkdownSegment.CodeBlock
        assertEquals("html", code.language)
        assertEquals("<div>test</div>", code.code)
    }

    @Test
    fun `standard code block still works`() {
        val input = "```python\nprint('hello')\n```"
        val segments = parseMarkdownSegments(input)
        assertEquals(1, segments.size)
        val code = segments[0] as MarkdownSegment.CodeBlock
        assertEquals("python", code.language)
        assertEquals("print('hello')", code.code)
    }

    @Test
    fun `code block without language still works`() {
        val input = "```\nsome code\n```"
        val segments = parseMarkdownSegments(input)
        assertEquals(1, segments.size)
        val code = segments[0] as MarkdownSegment.CodeBlock
        assertEquals(null, code.language)
        assertEquals("some code", code.code)
    }

    // --- HTML block detection (Part 1 fix) ---

    @Test
    fun `raw HTML document is converted to code block`() {
        val input = """<!DOCTYPE html>
<html>
<head><title>Pet Rock</title></head>
<body><h1>Rocky</h1></body>
</html>"""
        val segments = parseMarkdownSegments(input)
        assertEquals(1, segments.size)
        val code = segments[0] as MarkdownSegment.CodeBlock
        assertEquals("html", code.language)
        assertTrue(code.code.contains("<!DOCTYPE html>"))
        assertTrue(code.code.contains("</html>"))
    }

    @Test
    fun `raw HTML div block is converted to code block`() {
        val input = """<div class="container">
  <h1>Hello</h1>
  <p>World</p>
</div>"""
        val segments = parseMarkdownSegments(input)
        assertEquals(1, segments.size)
        val code = segments[0] as MarkdownSegment.CodeBlock
        assertEquals("html", code.language)
    }

    @Test
    fun `text before raw HTML is preserved as text block`() {
        val input = """Here's your web page:

<html>
<body><h1>Rocky</h1></body>
</html>"""
        val segments = parseMarkdownSegments(input)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is MarkdownSegment.TextBlock)
        assertEquals("Here's your web page:", (segments[0] as MarkdownSegment.TextBlock).text)
        val code = segments[1] as MarkdownSegment.CodeBlock
        assertEquals("html", code.language)
        assertTrue(code.code.contains("<html>"))
    }

    @Test
    fun `HTML inside fenced code block is not double-wrapped`() {
        val input = "```html\n<div>test</div>\n```"
        val segments = parseMarkdownSegments(input)
        assertEquals(1, segments.size)
        val code = segments[0] as MarkdownSegment.CodeBlock
        assertEquals("html", code.language)
        assertEquals("<div>test</div>", code.code)
    }

    @Test
    fun `plain text without HTML is not affected`() {
        val input = "This is a normal markdown paragraph with **bold** text."
        val segments = parseMarkdownSegments(input)
        assertEquals(1, segments.size)
        assertTrue(segments[0] is MarkdownSegment.TextBlock)
    }

    @Test
    fun `line starting with angle bracket but no closing tag is not HTML block`() {
        // A comparison operator should not be treated as HTML
        val input = "<p is a paragraph tag in HTML"
        val segments = parseMarkdownSegments(input)
        assertEquals(1, segments.size)
        assertTrue(segments[0] is MarkdownSegment.TextBlock)
    }

    @Test
    fun `self-closing HTML tags are detected`() {
        val input = "<div>\n  <img src=\"rock.png\" />\n  <br />\n</div>"
        val segments = parseMarkdownSegments(input)
        assertEquals(1, segments.size)
        val code = segments[0] as MarkdownSegment.CodeBlock
        assertEquals("html", code.language)
    }

    @Test
    fun `HTML with surrounding text splits correctly`() {
        val input = """Here is your page:

<html>
<head><title>Test</title></head>
<body>Hello</body>
</html>

Enjoy!"""
        // The fenced-code-block regex won't match this, so parseMarkdownSegments
        // will first produce text blocks, then Pass 2 (HTML detection) splits them.
        // "Enjoy!" ends up inside the HTML block text because it's part of the
        // same TextBlock after code-block extraction.
        val segments = parseMarkdownSegments(input)
        // First segment: "Here is your page:"
        assertTrue(segments[0] is MarkdownSegment.TextBlock)
        // Second segment: the HTML block (which includes trailing "Enjoy!" since
        // extractHtmlBlocks takes from htmlStart to end of the TextBlock)
        assertTrue(segments[1] is MarkdownSegment.CodeBlock)
    }
}
