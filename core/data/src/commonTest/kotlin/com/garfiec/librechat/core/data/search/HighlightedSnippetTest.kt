package com.garfiec.librechat.core.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HighlightedSnippetTest {

    private fun HighlightedSnippet.marked(): List<String> = highlights.map { text.substring(it) }

    @Test
    fun marksEveryTermCaseInsensitively() {
        val snippet = HighlightedSnippet.build("How do I debounce a Kotlin flow in Kotlin?", listOf("kotlin", "flow"))

        assertEquals(listOf("Kotlin", "flow", "Kotlin"), snippet.marked())
        assertFalse(snippet.truncatedStart)
    }

    @Test
    fun prefixTermsMarkOnlyTheTypedPart() {
        val snippet = HighlightedSnippet.build("coroutines and flows", listOf("corou"))

        assertEquals(listOf("corou"), snippet.marked())
    }

    @Test
    fun aShortLastWordMarksWholeWordsOnlyLikeTheIndex() {
        // The index takes a two-letter word whole (a two-letter prefix would match most of it).
        val snippet = HighlightedSnippet.build("SoftEther is so good", listOf("so"))

        assertEquals(listOf("so"), snippet.marked())
        assertEquals(13..14, snippet.highlights.single())
    }

    @Test
    fun wordsBeforeTheLastMarkWholeWordsOnly() {
        val snippet = HighlightedSnippet.build("I assumed you assume it is referring to that", listOf("assume", "refer"))

        assertEquals(listOf("assume", "refer"), snippet.marked())
    }

    @Test
    fun aFinishedLastWordIsNotAPrefix() {
        val snippet = HighlightedSnippet.build("flows and flow", listOf("flow"), lastIsPrefix = false)

        assertEquals(listOf(10..13), snippet.highlights)
    }

    @Test
    fun matchesWordStartsOnlyLikeTheIndex() {
        val snippet = HighlightedSnippet.build("an overflow of flow", listOf("flow"))

        assertEquals(listOf(15..18), snippet.highlights)
    }

    @Test
    fun scanHitsMatchAnywhere() {
        val snippet = HighlightedSnippet.build("我们的数据库很快", listOf("数据库"), wordStartsOnly = false)

        assertEquals(listOf("数据库"), snippet.marked())
    }

    @Test
    fun windowsAroundALateMatch() {
        val text = "word ".repeat(100) + "needle " + "tail ".repeat(100)

        val snippet = HighlightedSnippet.build(text, listOf("needle"), window = 60)

        assertTrue(snippet.truncatedStart)
        assertTrue(snippet.truncatedEnd)
        assertTrue(snippet.text.length <= 60)
        assertEquals(listOf("needle"), snippet.marked())
    }

    @Test
    fun aFoldedMatchShowsTheStartUnmarked() {
        val snippet = HighlightedSnippet.build("Un café au lait", listOf("cafe"))

        assertEquals(emptyList(), snippet.highlights)
        assertEquals("Un café au lait", snippet.text)
    }

    @Test
    fun collapsesWhitespaceAndMergesOverlaps() {
        val snippet = HighlightedSnippet.build("kotlin\n\n   kotlinx", listOf("kotlin", "kotlinx"))

        assertEquals("kotlin kotlinx", snippet.text)
        assertEquals(listOf("kotlin", "kotlinx"), snippet.marked())
    }

    @Test
    fun matchCaseMarksOnlyTheExactCase() {
        val snippet = HighlightedSnippet.build("RSA keys and rsa padding", listOf("rsa"), matchCase = true)

        assertEquals(listOf("rsa"), snippet.highlights.map { snippet.text.substring(it) })
        assertEquals(13, snippet.highlights.single().first)
    }

    @Test
    fun wholeWordSkipsLongerWords() {
        val snippet = HighlightedSnippet.build("kitchen kit", listOf("kit"), wholeWord = true)

        assertEquals(listOf(8..10), snippet.highlights)
    }
}
