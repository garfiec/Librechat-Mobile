package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.feature.chat.util.UiMarkerSegment.Resources
import com.garfiec.librechat.feature.chat.util.UiMarkerSegment.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Legacy MCP-UI `\ui{…}` markers, split as upstream's remark plugin finds them in text nodes. */
class UiResourceMarkersTest {

    private fun split(text: String) = splitUiResourceMarkers(text)

    @Test
    fun textWithoutMarkersIsReturnedWhole() {
        val text = "Plain reply with a path C:\\users and the word ui."
        assertEquals(listOf(Text(text)), split(text))
        assertFalse(hasUiResourceMarkers(text))
    }

    @Test
    fun aSingleMarkerSplitsTheTextAroundIt() {
        assertEquals(
            listOf(Text("Here is the forecast:\n\n"), Resources(listOf("abc123")), Text("\n\nEnjoy.")),
            split("Here is the forecast:\n\n\\ui{abc123}\n\nEnjoy."),
        )
    }

    @Test
    fun aMarkerMidSentenceKeepsBothHalves() {
        assertEquals(
            listOf(Text("See "), Resources(listOf("a1")), Text(" below.")),
            split("See \\ui{a1} below."),
        )
    }

    @Test
    fun commaSeparatedIdsAreOneCarousel() {
        assertEquals(listOf(Resources(listOf("a", "b_2", "C3"))), split("\\ui{a,b_2,C3}"))
    }

    @Test
    fun separateMarkersStaySeparateAndBlankTextBetweenIsDropped() {
        assertEquals(listOf(Resources(listOf("a")), Resources(listOf("b"))), split("\\ui{a} \\ui{b}"))
        assertEquals(
            listOf(Text("x"), Resources(listOf("a")), Text("y"), Resources(listOf("b"))),
            split("x\\ui{a}y\\ui{b}"),
        )
    }

    @Test
    fun malformedMarkersStayText() {
        for (text in listOf("\\ui{}", "\\ui{a, b}", "\\ui{a,}", "\\ui{,a}", "\\ui{a-b}", "\\ui{a", "\\ui a", "\\uix{a}", "ui{a}")) {
            assertEquals(listOf(Text(text)), split(text), text)
        }
    }

    @Test
    fun markersInFencedCodeAreLeftAlone() {
        val text = "```\n\\ui{a}\n```\nafter \\ui{b}"
        assertEquals(listOf(Text("```\n\\ui{a}\n```\nafter "), Resources(listOf("b"))), split(text))
        val tilde = "~~~~md\n\\ui{a}\n~~~\n~~~~\n\\ui{b}"
        assertEquals(listOf(Text("~~~~md\n\\ui{a}\n~~~\n~~~~\n"), Resources(listOf("b"))), split(tilde))
    }

    @Test
    fun anUnclosedFenceRunsToTheEnd() {
        val text = "```kotlin\nval x = \"\\ui{a}\""
        assertEquals(listOf(Text(text)), split(text))
    }

    @Test
    fun markersInInlineCodeAreLeftAlone() {
        assertEquals(listOf(Text("Write `\\ui{a}` to place it.")), split("Write `\\ui{a}` to place it."))
        assertEquals(
            listOf(Text("``x ` \\ui{a}`` then "), Resources(listOf("b"))),
            split("``x ` \\ui{a}`` then \\ui{b}"),
        )
    }

    @Test
    fun anUnmatchedBacktickIsLiteralAndDoesNotHideAMarker() {
        assertEquals(listOf(Text("a ` b "), Resources(listOf("c"))), split("a ` b \\ui{c}"))
    }

    @Test
    fun anEscapedBacktickOpensNoCodeSpan() {
        assertEquals(
            listOf(Text("\\` "), Resources(listOf("a")), Text(" `")),
            split("\\` \\ui{a} `"),
        )
    }

    @Test
    fun aCodeSpanEndsAtABlankLine() {
        assertEquals(listOf(Text("`open\n\n"), Resources(listOf("a")), Text(" `")), split("`open\n\n\\ui{a} `"))
    }

    @Test
    fun theEscapeThatProducedTheMarkersBackslashIsConsumedToo() {
        // Markdown turns `\\` into `\`, so web's text node reads `\ui{a}`.
        assertEquals(listOf(Resources(listOf("a"))), split("\\\\ui{a}"))
        // Three: `\\` is a literal backslash, then the marker.
        assertEquals(listOf(Text("\\\\"), Resources(listOf("a"))), split("\\\\\\ui{a}"))
        // Four: two literal backslashes; the last one starts the marker in the text node.
        assertEquals(listOf(Text("\\\\"), Resources(listOf("a"))), split("\\\\\\\\ui{a}"))
    }

    @Test
    fun escapedBracesAndUnderscoresResolveAsMarkdownWould() {
        assertEquals(listOf(Resources(listOf("a_b"))), split("\\ui\\{a\\_b\\}"))
        assertEquals(listOf(Resources(listOf("a", "b"))), split("\\ui{a,b\\}"))
    }

    @Test
    fun unknownIdsAreStillMarkersTheRendererDrops() {
        assertTrue(hasUiResourceMarkers("x \\ui{never_sent}"))
    }

    @Test
    fun aPartialMarkerAtTheEndIsHiddenWhileStreaming() {
        assertEquals("Look: ", withoutTrailingPartialUiMarker("Look: \\ui{ab"))
        assertEquals("Look: ", withoutTrailingPartialUiMarker("Look: \\ui"))
        assertEquals("Look: ", withoutTrailingPartialUiMarker("Look: \\ui{a,b,"))
        assertEquals("Look: \\ui{a} ok", withoutTrailingPartialUiMarker("Look: \\ui{a} ok"))
        assertEquals("path C:\\u", withoutTrailingPartialUiMarker("path C:\\u"))
    }
}
