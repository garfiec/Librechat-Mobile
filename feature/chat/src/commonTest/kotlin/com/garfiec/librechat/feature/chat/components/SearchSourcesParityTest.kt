package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.FileSearchData
import com.garfiec.librechat.core.model.FileSearchSource
import com.garfiec.librechat.core.model.FileSearchSourceMetadata
import com.garfiec.librechat.core.model.WebSearchAnswerBox
import com.garfiec.librechat.core.model.WebSearchData
import com.garfiec.librechat.core.model.WebSearchPlace
import com.garfiec.librechat.core.model.WebSearchShoppingItem
import com.garfiec.librechat.core.model.WebSearchSource
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.util.ContentGroup
import com.garfiec.librechat.feature.chat.util.IndexedContentPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The web client's search-source surfaces, as the pure decisions behind them (upstream
 * `WebSearch.tsx`, `verticals.tsx`, `RetrievalCall.tsx`, `ToolCallGroup.tsx`, `Sources.tsx`).
 */
class SearchSourcesParityTest {

    private fun web(id: String?, data: WebSearchData) = Attachment(type = "web_search", toolCallId = id, webSearch = data)

    private fun link(url: String, title: String? = null) = WebSearchSource(link = url, title = title)

    @Test
    fun `a repeated link keeps its first position and its last copy`() {
        val attachments = listOf(
            web("c1", WebSearchData(organic = listOf(link("https://a.com", "Old"), link("https://b.com")))),
            web("c1", WebSearchData(organic = listOf(WebSearchSource(link = "https://a.com", title = "New", date = "2 days ago")))),
        )
        val sources = collectWebSearchSources(attachments, "c1")
        assertEquals(listOf("https://a.com", "https://b.com"), sources.map { it.url })
        assertEquals("New", sources.first().title)
        assertEquals("2 days ago", sources.first().date)
    }

    @Test
    fun `turn and answer box come from the call's own payloads`() {
        val answer = WebSearchAnswerBox(snippetHighlighted = listOf("42", "units"))
        val data = webSearchData(
            listOf(web("c1", WebSearchData(turn = 2, answerBox = answer)), web("other", WebSearchData(turn = 5))),
            "c1",
        )
        assertEquals(2, webSearchTurn(data))
        assertEquals("42 units", webSearchAnswerBox(data)?.text)
        assertEquals(0, webSearchTurn(emptyList()))
    }

    @Test
    fun `failed searches fall back to the tool card and cancelled ones draw nothing`() {
        val toolFailed = "Error: [web_search] tool call failed: timeout"
        assertEquals(SearchOutcome.FAILED, searchOutcome(ToolCallVerdict.FAILED, null))
        assertEquals(SearchOutcome.FAILED, searchOutcome(ToolCallVerdict.COMPLETED, toolFailed))
        assertEquals(SearchOutcome.FAILED, searchOutcome(ToolCallVerdict.COMPLETED, "Error processing tool web_search"))
        assertEquals(SearchOutcome.HIDDEN, searchOutcome(ToolCallVerdict.CANCELLED, toolFailed))
        assertEquals(SearchOutcome.HIDDEN, searchOutcome(ToolCallVerdict.COMPLETED, "There was an error processing results"))
        assertEquals(SearchOutcome.SHOWN, searchOutcome(ToolCallVerdict.COMPLETED, "Found 8 results"))
        assertEquals(SearchOutcome.HIDDEN, searchOutcome(ToolCallVerdict.COMPLETED, null, finished = false))
        assertEquals(SearchOutcome.FAILED, searchOutcome(ToolCallVerdict.FAILED, null, finished = false))
    }

    @Test
    fun `only the built-in search or one with web sources gets the search card`() {
        assertTrue(claimsWebSearchCard("web_search", hasSources = false))
        assertTrue(claimsWebSearchCard("search_docs", hasSources = true))
        // An MCP tool whose output merely reads like a search error keeps the generic card.
        assertFalse(claimsWebSearchCard("search_docs", hasSources = false))
    }

    @Test
    fun `a live search moves from searching to processing to reading to settled`() {
        fun phase(complete: Boolean, sources: Boolean, latest: Boolean, movedOn: Boolean) =
            liveWebSearchPhase(isComplete = complete, hasSources = sources, isLatestStep = latest, replyMovedOn = movedOn)
        assertEquals(WebSearchPhase.SEARCHING, phase(complete = false, sources = false, latest = true, movedOn = false))
        assertEquals(WebSearchPhase.PROCESSING, phase(complete = false, sources = true, latest = true, movedOn = false))
        assertEquals(WebSearchPhase.READING, phase(complete = true, sources = true, latest = true, movedOn = false))
        assertEquals(WebSearchPhase.COMPLETE, phase(complete = true, sources = true, latest = true, movedOn = true))
        assertEquals(WebSearchPhase.COMPLETE, phase(complete = true, sources = true, latest = false, movedOn = false))
    }

    @Test
    fun `the query is read from the call's arguments`() {
        assertEquals("kotlin release", parseSearchQuery("""{"intent":"Looking","query":" kotlin release "}"""))
        assertNull(parseSearchQuery("""{"q":"x"}"""))
        assertNull(parseSearchQuery("not json"))
    }

    @Test
    fun `verticals keep only results a row can act on`() {
        val data = WebSearchData(
            images = listOf(
                WebSearchSource(thumbnailUrl = "https://t.com/1.jpg", link = "https://page.com"),
                WebSearchSource(thumbnailUrl = "javascript:alert(1)", link = "https://page.com"),
                WebSearchSource(imageUrl = "https://i.com/2.jpg"),
            ),
            shopping = listOf(
                WebSearchShoppingItem(title = "Mug", link = "https://shop.com/mug"),
                WebSearchShoppingItem(link = "https://shop.com/untitled"),
                WebSearchShoppingItem(title = "Bad", link = "ftp://x"),
            ),
            places = listOf(WebSearchPlace(name = "Cafe"), WebSearchPlace(category = "Park")),
        )
        val verticals = collectSearchVerticals(listOf(data))
        assertEquals(2, verticals.images.size)
        assertEquals(listOf("Mug"), verticals.shopping.map { it.title })
        assertEquals(listOf("Cafe"), verticals.places.map { it.name })
    }

    @Test
    fun `provider numbers decode quoted or bare without failing the message`() {
        val json = Json { ignoreUnknownKeys = true }
        val data = json.decodeFromString(
            WebSearchData.serializer(),
            """{"shopping":[{"title":"A","link":"https://a","rating":"4.5","ratingCount":12}],
               "places":[{"name":"P","latitude":"40.7","longitude":-74.0,"rating":4}],
               "images":[{"imageUrl":"https://i","thumbnailWidth":"300","thumbnailHeight":150}]}""",
        )
        assertEquals(4.5, data.shopping!!.single().ratingValue)
        assertEquals(12L, data.shopping!!.single().ratingCountValue)
        assertEquals(40.7, data.places!!.single().latitudeValue)
        assertEquals(2f, data.images!!.single().thumbnailAspectRatio)
        assertEquals("4", formatRating(4.0))
        assertEquals("4.5", formatRating(4.5))
    }

    @Test
    fun `map links open Apple Maps on iOS and Google Maps elsewhere`() {
        val place = WebSearchPlace(
            name = "Blue Bottle",
            address = "1 Main St",
            latitude = JsonPrimitive(37.7),
            longitude = JsonPrimitive(-122.4),
        )
        assertEquals("https://maps.apple.com/?q=Blue%20Bottle,%201%20Main%20St&ll=37.7,-122.4", mapLink(place, appleMaps = true))
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Blue%20Bottle,%201%20Main%20St",
            mapLink(place, appleMaps = false),
        )
    }

    @Test
    fun `retrieval output sections list in their own order and match their files by name`() {
        val output = "File: Report.pdf\nRelevance: 0.82\nContent: first passage\nsecond line\n---\n" +
            "File: notes.txt\nRelevance: 0.4\nContent: other"
        val source = FileSearchSource(
            fileId = "f1",
            fileName = "report.pdf",
            relevance = 0.5,
            pages = listOf(3),
            metadata = FileSearchSourceMetadata(fileType = "application/pdf"),
        )
        val attachments =
            listOf(Attachment(type = "file_search", toolCallId = "c1", fileSearch = FileSearchData(listOf(source))))
        val results = fileSearchResults(attachments, "c1", output)
        assertEquals(listOf("report.pdf", "notes.txt"), results.map { it.fileName })
        assertTrue(results[0].openable)
        assertEquals("f1", results[0].fileId)
        assertEquals(0.82, results[0].relevance)
        assertEquals("first passage\nsecond line", results[0].content)
        assertFalse(results[1].openable)
        assertNull(results[1].asAttachment())
        // No output: the attachments alone.
        assertEquals(listOf("f1"), fileSearchResults(attachments, "c1", null).map { it.fileId })
    }

    @Test
    fun `a name two different files share opens neither`() {
        val sources =
            listOf(FileSearchSource(fileId = "f1", fileName = "a.pdf"), FileSearchSource(fileId = "f2", fileName = "A.pdf"))
        val attachments = listOf(Attachment(type = "file_search", fileSearch = FileSearchData(sources)))
        assertFalse(fileSearchResults(attachments, null, "File: a.pdf\nContent: x").single().openable)
    }

    private fun call(id: String, name: String) =
        MessageContentPart(type = ContentType.TOOL_CALL, toolCall = AgentToolCall(id = id, name = name))

    private fun group(vararg parts: MessageContentPart, label: String = "") = ContentGroup.Activity(
        key = "g",
        entries = parts.mapIndexed { i, p -> IndexedContentPart(i, p) },
        labelText = label,
        failed = false,
        toolCount = parts.size,
        completed = true,
    )

    @Test
    fun `a group of searches only reads as searches with the sites it read`() {
        val attachments = listOf(
            web("w1", WebSearchData(organic = listOf(link("https://www.a.com/x"), link("https://b.com/y")))),
            web("w2", WebSearchData(organic = listOf(link("https://a.com/z"), link("https://c.com"), link("https://d.com")))),
        )
        val webOnly = searchGroupHeader(group(call("w1", "web_search"), call("w2", "web_search")), attachments)
        assertEquals(SearchGroupKind.WEB, webOnly?.kind)
        assertEquals(listOf("a.com", "b.com", "c.com"), webOnly?.hosts)
        val mixed = searchGroupHeader(group(call("w1", "web_search"), call("f", "file_search")), attachments)
        assertEquals(SearchGroupKind.WEB_AND_FILES, mixed?.kind)
        val files = searchGroupHeader(group(call("f", "file_search"), call("r", "retrieval")), attachments)
        assertEquals(SearchGroupKind.FILES, files?.kind)
        assertNull(searchGroupHeader(group(call("w1", "web_search"), call("x", "calculator")), attachments))
        val labelled = group(call("w1", "web_search"), call("w2", "web_search"), label = "Looked it up")
        assertNull(searchGroupHeader(labelled, attachments))
    }

    @Test
    fun `the sources panel sorts results into its tabs`() {
        val search = WebSearchData(
            organic = listOf(link("https://a.com", "A")),
            topStories = listOf(link("https://news.com", "N")),
            references = listOf(
                WebSearchSource(link = "https://ref.com", type = "link"),
                WebSearchSource(link = "https://img.com/p", type = "image"),
                WebSearchSource(link = "#file-1", type = "file"),
            ),
            images = listOf(WebSearchSource(imageUrl = "https://img.com/1.jpg", thumbnailUrl = "https://img.com/t.jpg")),
        )
        val files = FileSearchData(listOf(FileSearchSource(fileId = "f1", fileName = "a.pdf", pages = listOf(9, 2))))
        val panel = collectSourcesPanel(listOf(web("w1", search), Attachment(type = "file_search", fileSearch = files)))
        assertEquals(listOf("https://a.com", "https://ref.com"), panel.organic.map { it.link })
        assertEquals(listOf("https://news.com"), panel.news.map { it.link })
        assertEquals(listOf("https://img.com/p", "https://img.com/1.jpg"), panel.images.map { it.link })
        assertEquals(listOf(2, 9), panel.files.single().pages)
    }

    @Test
    fun `a snippet drops page markdown and keeps its words`() {
        val scraped = "#### [Race results](/f1/2026) for **Apple** and _more_\n| a | b |\n|---|---|\n> quoted ![logo](x.png)"
        assertEquals("Race results for Apple and more a b quoted logo", plainSnippet(scraped))
        assertEquals("snake_case stays", plainSnippet("snake_case stays"))
        assertNull(plainSnippet("  **  "))
        assertNull(plainSnippet(null))
    }

    @Test
    fun `a stopped search lists its sources from the tool output`() {
        val output = listOf(
            "=== Web Results, Turn 0 ===",
            "",
            "# Search 0: \"Perseverance breaks the driving record\"",
            "",
            "Anchor: \\ue202turn0search0",
            "URL: https://sciencedaily.com/releases/2026/10/1.htm",
            "Summary: - **Summary:** The rover passed",
            "Opportunity's record.",
            "Date: 2026-10-21T09:22:40Z",
            "Source: ScienceDaily",
            "",
            "## Highlights",
            "",
            "### Highlight 1 [Relevance: 0.00]",
            "URL: https://not-a-source.example",
            "",
            "=== News Results ===",
            "",
            "# News 0: (no title)",
            "",
            "Anchor: \\ue202turn0news0",
            "URL: https://nasa.gov/news",
            "Source: NASA",
            "",
            "# Search 1: \"Unsafe\"",
            "URL: javascript:alert(1)",
        ).joinToString("\n")
        val results = parseWebSearchResults(output)
        assertEquals(listOf("https://sciencedaily.com/releases/2026/10/1.htm", "https://nasa.gov/news"), results.map { it.url })
        assertEquals("Perseverance breaks the driving record", results[0].title)
        assertEquals("- **Summary:** The rover passed Opportunity's record.", results[0].snippet)
        assertEquals("2026-10-21T09:22:40Z", results[0].date)
        assertEquals("NASA", results[1].title)
    }
}
