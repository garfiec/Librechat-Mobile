package com.garfiec.librechat.core.common.extensions

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTextTest {
    @Test
    fun plain_text_comes_back_trimmed_and_otherwise_untouched() {
        assertEquals("Helps with code reviews.", "  Helps with code reviews.  ".htmlToPlainText())
    }

    @Test
    fun inline_tags_are_removed() {
        assertEquals(
            "Writes read-only queries against the warehouse.",
            "<p>Writes <b>read-only</b> queries against the <a href=\"https://x\">warehouse</a>.</p>".htmlToPlainText(),
        )
    }

    @Test
    fun block_tags_become_line_breaks() {
        assertEquals("First\nSecond\nThird", "<p>First</p><p>Second</p>Third".htmlToPlainText())
        assertEquals("One\nTwo", "One<br>Two".htmlToPlainText())
        assertEquals("One\nTwo", "One<BR/>Two".htmlToPlainText())
        assertEquals("Items\nA\nB", "Items<ul><li>A</li><li>B</li></ul>".htmlToPlainText())
    }

    @Test
    fun entities_are_decoded() {
        assertEquals("Tom & Jerry <3 \"quotes\" 'apos'", "Tom &amp; Jerry &lt;3 &quot;quotes&quot; &#39;apos&apos;".htmlToPlainText())
        assertEquals("café — 🚀", "caf&#233; &mdash; &#x1F680;".htmlToPlainText())
    }

    @Test
    fun an_entity_decoded_to_a_bracket_is_not_then_read_as_a_tag() {
        assertEquals("<b>literal</b>", "&lt;b&gt;literal&lt;/b&gt;".htmlToPlainText())
    }

    @Test
    fun unknown_or_invalid_entities_are_left_as_written() {
        assertEquals("&bogus; &#xD800; &#0; & alone", "&bogus; &#xD800; &#0; & alone".htmlToPlainText())
    }

    @Test
    fun whitespace_collapses_and_blank_lines_drop() {
        assertEquals("A spaced line\nNext", "<div>  A \t spaced&nbsp;&nbsp;line  </div>\n\n\n<div>Next</div>".htmlToPlainText())
    }

    @Test
    fun a_bare_angle_bracket_is_not_a_tag() {
        assertEquals("a < b and c > d", "a < b and c > d".htmlToPlainText())
        assertEquals("1 <2", "1 <2".htmlToPlainText())
    }

    @Test
    fun comments_are_removed() {
        assertEquals("Shown", "<!-- hidden <b>bold</b> -->Shown".htmlToPlainText())
    }

    @Test
    fun markup_only_text_becomes_empty() {
        assertEquals("", "<p><br></p>".htmlToPlainText())
    }
}
