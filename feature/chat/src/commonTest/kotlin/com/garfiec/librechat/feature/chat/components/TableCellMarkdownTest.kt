package com.garfiec.librechat.feature.chat.components

import kotlin.test.Test
import kotlin.test.assertEquals

class TableCellMarkdownTest {

    @Test
    fun emphasisMarkersAreRenderedNotShown() {
        assertEquals("CO alarm", tableCellPlainText("**CO alarm**"))
        assertEquals("Life-safety warning for CO", tableCellPlainText("**Life-safety warning** for _CO_"))
        assertEquals("gone", tableCellPlainText("~~gone~~"))
    }

    @Test
    fun aLinkShowsItsLabelAndCodeItsContent() {
        assertEquals("see cpsc.gov", tableCellPlainText("see [cpsc.gov](https://www.cpsc.gov/Safety)"))
        // The library pads a code span with a space each side, as it does in body text.
        assertEquals("run  ls -la ", tableCellPlainText("run `ls -la`"))
    }

    @Test
    fun aCellTheParserReadsAsABlockKeepsItsRawText() {
        assertEquals("1. Step", tableCellPlainText("1. Step"))
        assertEquals("# Tag", tableCellPlainText("# Tag"))
    }

    @Test
    fun dollarSignsAreNotReadAsMath() {
        assertEquals("\$5 and \$6", tableCellPlainText("\$5 and \$6"))
        assertEquals("\$10–\$20 per month", tableCellPlainText("\$10–\$20 per **month**"))
    }

    @Test
    fun aBrTagBreaksTheLine() {
        assertEquals("Step 1\nStep 2", tableCellPlainText("Step 1<br>Step 2"))
        assertEquals("a\nb", tableCellPlainText("a<BR />b"))
        assertEquals("bold text", tableCellPlainText("<b>bold</b> text"))
    }

    @Test
    fun anImageKeepsItsSource() {
        assertEquals("see ![logo](https://x.test/a.png)", tableCellPlainText("see ![logo](https://x.test/a.png)"))
    }

    @Test
    fun aQuerySpanningRemovedMarkersIsStillCounted() {
        val text = """
            | Alarm | Gas |
            |---|---|
            | **CO** alarm | carbon monoxide |
        """.trimIndent()
        // Quoted, so it is one phrase: unquoted, find counts each word, and "Alarm" in the header
        // and "CO" and "alarm" in the cell would make three.
        assertEquals(1, countMarkdownOccurrences(text, "\"CO alarm\""))
    }

    @Test
    fun searchCountsTheDisplayedTextNotTheMarkers() {
        val table = MarkdownSegment.Table(
            headers = listOf("**Feature**", "CO"),
            alignments = listOf(TableCellAlignment.LEFT, TableCellAlignment.LEFT),
            rows = listOf(listOf("**Detects**", "[gas](https://example.com)")),
        )
        assertEquals(listOf("Feature", "CO", "Detects", "gas"), tableCellTexts(table))
        assertEquals(0, countSegmentOccurrences(table, "**"))
    }
}
