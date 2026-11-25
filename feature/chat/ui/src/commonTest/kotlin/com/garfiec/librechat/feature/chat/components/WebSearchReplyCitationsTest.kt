package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.CitationIndex
import com.garfiec.librechat.core.model.content.CitationRef
import com.garfiec.librechat.core.model.content.CitationSource
import com.garfiec.librechat.core.model.content.citationGroupsIn
import com.garfiec.librechat.core.model.content.stripCitations
import com.garfiec.librechat.feature.chat.util.serializeMessageForClipboard
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The real reply in [WebSearchReplyFixture], end to end: decode, resolve, render, copy. */
class WebSearchReplyCitationsTest {

    private val message = Json { ignoreUnknownKeys = true }.decodeFromString(Message.serializer(), WebSearchReplyFixture)
    private val index = CitationIndex.from(message.attachments)
    private val text = message.content!!.single().text!!

    private fun String.hasMarkers() = contains("\\ue20") || any { it in '\uE200'..'\uE206' }

    @Test
    fun `every anchor in the reply resolves against its two searches`() {
        val source = index.resolve(CitationRef(turn = 1, type = "search", index = 3))
        assertTrue(source is CitationSource.Web && source.link.startsWith("http"))
        val segments = parseMarkdownSegments(text).withCitations(index, streaming = false)
        val rendered = segments.joinToString("\n") { (it as MarkdownSegment.TextBlock).text }
        val groups = citationGroupsIn(rendered)
        // Three composites (two of them identical) and five standalone anchors, all resolvable.
        assertEquals(
            listOf(
                listOf("0search0", "1search3"),
                listOf("1search2"),
                listOf("0search0"),
                listOf("1search3"),
                listOf("0search2", "1search3"),
                listOf("1search4"),
            ),
            groups.map { group -> group.refs.map { it.key } },
        )
        assertFalse(rendered.contains("\\ue20"), "no literal escape survives")
        assertFalse(rendered.contains("\uE203") || rendered.contains("\uE204"), "highlight markers are gone")
    }

    @Test
    fun `stripping leaves clean prose`() {
        val stripped = stripCitations(text)
        assertFalse(stripped.hasMarkers())
        assertTrue(stripped.contains("event on October 28 in Los Angeles, with invitations"))
    }

    @Test
    fun `copy numbers the cited links and lists them`() {
        val copied = serializeMessageForClipboard(message)
        assertFalse(copied.hasMarkers())
        assertTrue(copied.contains("**Apple will hold a special \"Pro and tell\" event on October 28 in Los Angeles** [1][2]"))
        val footer = copied.substringAfter("\nCitations:\n").lines().filter { it.isNotBlank() }
        val links = listOf("0search0", "1search3", "1search2", "0search2", "1search4")
            .map { key -> CitationRef(key.take(1).toInt(), "search", key.last().digitToInt()) }
            .map { (index.resolve(it) as CitationSource.Web).link }
            .distinct()
        assertEquals(links.mapIndexed { i, link -> "[${i + 1}] $link" }, footer)
    }
}
